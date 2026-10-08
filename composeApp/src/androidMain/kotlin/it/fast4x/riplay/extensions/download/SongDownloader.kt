package it.fast4x.riplay.extensions.download

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment as AndroidEnvironment
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import it.fast4x.environment.utils.AudioStreamData
import it.fast4x.environment.utils.NewPipeUtils
import it.fast4x.riplay.commonutils.LOCAL_KEY_PREFIX
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Format
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.utils.isAtLeastAndroid10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Downloads the audio of one YouTube song into Music/Yammbo Music, tagged, and registers it as a
 * local song whose mediaId is the video id. That link is all the player needs: PlayerService
 * already swaps an online song for its local copy (playLocalCopyKey), so the download plays
 * offline from every list, not just from "On device".
 *
 * File name "Artist - Title [videoId].m4a": the on-device scan reads the id from the brackets,
 * so a rescan, a reinstall or a restored backup keep the link without reading the file.
 */
object SongDownloader {

    const val FOLDER_NAME = "Yammbo Music"

    sealed class Outcome {
        data class Done(val uri: Uri) : Outcome()
        data object AlreadyDownloaded : Outcome()
        /** Worth retrying later: no network, timeouts, YouTube refusing for a while. */
        data class Retry(val reason: String) : Outcome()
        /** Will fail again the same way: no audio for this video, no storage permission. */
        data class Failed(val reason: String) : Outcome()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Measured: 1 MB ranges download a song in 1-3 s and keep a 403 cheap to recover from.
    private const val CHUNK_BYTES = 1_000_000L
    private const val MAX_EXTRACTIONS = 5
    private const val MAX_IO_RETRIES = 4
    private const val COVER_PX = 600

    @WorkerThread
    fun localCopyOf(videoId: String): Song? = Database.songOnDeviceNow(videoId)

    /** A local row whose file was deleted outside the app must not block a new download. */
    @WorkerThread
    fun hasPlayableCopy(context: Context, videoId: String): Boolean =
        localCopyOf(videoId)?.let { isReadable(context, it) } == true

    suspend fun download(
        context: Context,
        song: Song,
        albumTitle: String?,
        onProgress: (Float) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val videoId = song.id
        if (hasPlayableCopy(context, videoId)) {
            return@withContext Outcome.AlreadyDownloaded
        }

        if (!isAtLeastAndroid10 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) return@withContext Outcome.Failed("No storage permission")

        val partDir = File(context.cacheDir, "downloads").apply { mkdirs() }
        val part = File(partDir, "$videoId.part")
        val tagged = File(partDir, "$videoId.tagged")
        try {
            val stream = when (val r = fetchAudio(videoId, part, onProgress)) {
                is FetchResult.Ok -> r.stream
                is FetchResult.Error -> return@withContext r.outcome
            }

            val title = song.title.ifBlank { videoId }
            val artist = song.artistsText.orEmpty()
            var fileToStore = part
            if (stream.extension == "m4a") {
                val cover = downloadCover(song.thumbnailUrl)
                val ok = runCatching {
                    tagged.outputStream().use {
                        Mp4Tagger.copyWithTags(
                            part, it,
                            Mp4Tagger.Tags(
                                title = title,
                                artist = artist,
                                album = albumTitle,
                                comment = "https://music.youtube.com/watch?v=$videoId",
                                cover = cover,
                            )
                        )
                    }
                }.onFailure { Timber.w(it, "SongDownloader: tagging failed for $videoId") }
                    .getOrDefault(false)
                // Untagged still plays and still links by the [id] in its name.
                if (ok && tagged.length() > 0) fileToStore = tagged
            }

            val displayName = fileNameFor(artist, title, videoId, stream.extension)
            val uri = store(context, fileToStore, displayName, stream.mimeType, title, artist, albumTitle)
                ?: return@withContext Outcome.Failed("Could not write to Music/$FOLDER_NAME")

            register(context, uri, song, stream, fileToStore.length())
            Outcome.Done(uri)
        } catch (e: SecurityException) {
            Timber.e(e, "SongDownloader: storage permission")
            Outcome.Failed("No storage permission")
        } catch (e: IOException) {
            Timber.w(e, "SongDownloader: I/O while saving $videoId")
            Outcome.Retry(e.message ?: "I/O error")
        } finally {
            part.delete()
            tagged.delete()
        }
    }

    // ---- fetching ----

    private sealed class FetchResult {
        data class Ok(val stream: AudioStreamData) : FetchResult()
        data class Error(val outcome: Outcome) : FetchResult()
    }

    private suspend fun fetchAudio(
        videoId: String,
        part: File,
        onProgress: (Float) -> Unit,
    ): FetchResult {
        part.delete()
        var stream: AudioStreamData? = null
        var extractions = 0
        var ioFailures = 0
        var written = 0L
        // When NewPipe does not know the size, the server's Content-Range tells it.
        var rangeTotal = -1L

        part.outputStream().use { out ->
            while (true) {
                if (stream == null) {
                    if (extractions >= MAX_EXTRACTIONS) {
                        return FetchResult.Error(Outcome.Retry("YouTube kept refusing the audio"))
                    }
                    extractions++
                    val extracted = NewPipeUtils.bestAudioStream(videoId)
                    val e = extracted.exceptionOrNull()
                    if (e != null) {
                        Timber.w(e, "SongDownloader: extraction $extractions failed for $videoId")
                        val message = e.message.orEmpty()
                        if (isPermanent(e)) {
                            return FetchResult.Error(Outcome.Failed(message.ifBlank { e.javaClass.simpleName }))
                        }
                        if (e is IOException && ++ioFailures > MAX_IO_RETRIES) {
                            return FetchResult.Error(Outcome.Retry(message.ifBlank { "Network error" }))
                        }
                        delay(1_500L * extractions)
                        continue
                    }
                    stream = extracted.getOrThrow()
                }
                val s = stream!!
                val total = if (s.contentLength > 0) s.contentLength else rangeTotal
                if (total in 1..written) break

                val end = if (total > 0) minOf(written + CHUNK_BYTES, total) - 1 else written + CHUNK_BYTES - 1
                val result = runCatching {
                    val request = Request.Builder()
                        .url(s.url)
                        .header("Range", "bytes=$written-$end")
                        .build()
                    client.newCall(request).execute().use { response ->
                        when (response.code) {
                            200, 206 -> {
                                response.header("Content-Range")?.substringAfterLast('/')
                                    ?.toLongOrNull()?.let { rangeTotal = it }
                                val bytes = response.body?.bytes() ?: ByteArray(0)
                                // A 200 means the server ignored the range and sent everything.
                                if (response.code == 200 && written > 0) -1 else {
                                    out.write(bytes)
                                    bytes.size
                                }
                            }
                            403, 410 -> -2      // URL expired or refused: extract a fresh one
                            416 -> 0            // past the end: nothing left
                            else -> throw IOException("HTTP ${response.code}")
                        }
                    }
                }
                val n = result.getOrNull()
                if (n == null) {
                    val e = result.exceptionOrNull()
                    Timber.w(e, "SongDownloader: range failed for $videoId at $written")
                    if (++ioFailures > MAX_IO_RETRIES) {
                        return FetchResult.Error(Outcome.Retry(e?.message ?: "Network error"))
                    }
                    delay(2_000L * ioFailures)
                    continue
                }
                when {
                    n == -2 -> { stream = null; continue }
                    n == -1 -> return FetchResult.Error(Outcome.Retry("Server ignored the byte range"))
                    n == 0 -> break
                    else -> {
                        written += n
                        if (total > 0) onProgress(written.toFloat() / total)
                    }
                }
            }
        }

        val s = stream ?: return FetchResult.Error(Outcome.Retry("No stream"))
        // The whole point: never store a truncated file as if it were the song. A file whose
        // size nobody announced cannot be checked, so it is not stored either.
        val expected = if (s.contentLength > 0) s.contentLength else rangeTotal
        if (expected <= 0) return FetchResult.Error(Outcome.Retry("Unknown file size"))
        if (part.length() != expected) {
            return FetchResult.Error(Outcome.Retry("Incomplete download ${part.length()}/$expected"))
        }
        if (part.length() == 0L) return FetchResult.Error(Outcome.Retry("Empty download"))
        return FetchResult.Ok(s)
    }

    /** Errors that the next attempt would hit again, so not worth retrying. */
    private fun isPermanent(e: Throwable): Boolean =
        e.message.orEmpty().contains("No downloadable audio") ||
            e.javaClass.simpleName in setOf(
                "ContentNotAvailableException", "AgeRestrictedContentException",
                "GeographicRestrictionException", "PaidContentException", "PrivateContentException",
            )

    /** Square JPEG for the tags; null when there is no cover or it cannot be decoded. */
    private fun downloadCover(url: String?): ByteArray? = runCatching {
        val sized = url.thumbnail(COVER_PX) ?: return null
        if (!sized.startsWith("http")) return null
        val bytes = client.newCall(Request.Builder().url(sized).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes()
        } ?: return null
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        // Video thumbnails are 16:9 with the art in the middle.
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > COVER_PX) Bitmap.createScaledBitmap(square, COVER_PX, COVER_PX, true) else square
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.onFailure { Timber.w(it, "SongDownloader: cover failed") }.getOrNull()

    // ---- storing ----

    private fun fileNameFor(artist: String, title: String, videoId: String, extension: String): String {
        val base = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ")
            .replace(Regex("[\\\\/:*?\"<>|\\[\\]\\p{Cntrl}]"), "_")
            .trim().take(120)
        return "$base [$videoId].$extension"
    }

    private suspend fun store(
        context: Context,
        file: File,
        displayName: String,
        mimeType: String,
        title: String,
        artist: String,
        album: String?,
    ): Uri? {
        if (isAtLeastAndroid10) {
            val resolver = context.contentResolver
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${AndroidEnvironment.DIRECTORY_MUSIC}/$FOLDER_NAME")
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                album?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("No output stream for $uri")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return uri
        }

        // Android 7-9: a real file in the public Music folder, then ask the scanner for its uri.
        @Suppress("DEPRECATION")
        val dir = File(AndroidEnvironment.getExternalStoragePublicDirectory(AndroidEnvironment.DIRECTORY_MUSIC), FOLDER_NAME)
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, displayName)
        file.copyTo(target, overwrite = true)
        return suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mimeType)) { _, uri ->
                if (cont.isActive) cont.resume(uri)
            }
        }
    }

    /**
     * Writes the local row right away, so the song plays offline without waiting for the next
     * on-device scan. The scan later rewrites the same row from the same tags.
     */
    private fun register(context: Context, uri: Uri, song: Song, stream: AudioStreamData, size: Long) {
        val mediaStoreId = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return
        var albumArt: String? = null
        val relativePath: String? = if (isAtLeastAndroid10) "${AndroidEnvironment.DIRECTORY_MUSIC}/$FOLDER_NAME/" else null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val albumId = c.getLong(0)
                    if (albumId != 0L) albumArt = "content://media/external/audio/albumart/$albumId"
                }
            }
        }
        val local = Song(
            id = "$LOCAL_KEY_PREFIX$mediaStoreId",
            mediaId = song.id,
            title = song.title,
            artistsText = song.artistsText,
            durationText = song.durationText,
            thumbnailUrl = albumArt ?: song.thumbnailUrl,
            likedAt = song.likedAt,
            folder = relativePath,
        )
        Database.upsert(
            local,
            Format(
                songId = local.id,
                itag = 0,
                mimeType = stream.mimeType,
                bitrate = stream.bitrate,
                contentLength = size,
                lastModified = System.currentTimeMillis() / 1000,
            )
        )
    }

    private fun isReadable(context: Context, local: Song): Boolean = runCatching {
        val id = local.id.removePrefix(LOCAL_KEY_PREFIX).toLong()
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}
