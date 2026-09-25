package it.fast4x.riplay.utils

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.WorkerThread
import timber.log.Timber
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer

/**
 * YTDLnis names its audio files "uploader - title", with no video id in them, but by default it
 * embeds the source link in the tags (purl / comment = https://www.youtube.com/watch?v=<ID>).
 * That link is the only thing tying a downloaded file back to the song it came from.
 *
 * Tags live at the start of the file (ID3, Vorbis comments in Ogg/Opus) or, for the m4a files
 * ffmpeg writes, inside the moov atom that usually sits at the END. So only the head and the
 * tail are read; the audio in between is never touched.
 */
private const val HEAD_BYTES = 256 * 1024
private const val TAIL_BYTES = 1024 * 1024

private val youTubeLinkPattern = Regex(
    """(?:youtube\.com/watch\?v=|music\.youtube\.com/watch\?v=|youtu\.be/)([A-Za-z0-9_-]{11})"""
)

@WorkerThread
fun readYouTubeIdFromFile(context: Context, uri: Uri): String? {
    // Reading up to 1.25 MB from storage would freeze the UI; callers are all on IO already.
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Timber.w("readYouTubeIdFromFile called on the main thread, ignored")
        return null
    }
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.let { pfd ->
            // Owns the descriptor, so closing the stream closes it exactly once.
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                val channel = input.channel
                val size = pfd.statSize.takeIf { it > 0 } ?: channel.size()
                if (size <= 0) return@use null

                val chunks = if (size <= HEAD_BYTES + TAIL_BYTES) {
                    listOf(readAt(channel, 0, size.toInt()))
                } else {
                    listOf(
                        readAt(channel, 0, HEAD_BYTES),
                        readAt(channel, size - TAIL_BYTES, TAIL_BYTES)
                    )
                }
                chunks.firstNotNullOfOrNull { findYouTubeId(it) }
            }
        }
    } catch (e: Throwable) {
        Timber.d("readYouTubeIdFromFile failed for $uri: ${e.message}")
        null
    }
}

private fun readAt(channel: java.nio.channels.FileChannel, position: Long, length: Int): ByteArray {
    val buffer = ByteBuffer.allocate(length)
    var offset = position
    while (buffer.hasRemaining()) {
        val read = channel.read(buffer, offset)
        if (read <= 0) break
        offset += read
    }
    return buffer.array().copyOf(buffer.position())
}

// How far back from a link the name of the tag holding it is looked for: enough for the frame or
// atom header plus a language code and a short description, not enough to reach a previous tag.
private const val TAG_KEY_WINDOW = 48

// Names of the tags YTDLnis writes the source link into: ID3 "purl" / COMM, Vorbis "comment",
// the "©cmt" atom of an m4a.
private val sourceLinkTagKeys = listOf("purl", "comment", "cmt", "COMM")

private fun findYouTubeId(bytes: ByteArray): String? {
    // ISO-8859-1 maps every byte to one char, so offsets and ASCII survive whatever binary
    // surrounds the tag.
    pickYouTubeId(String(bytes, Charsets.ISO_8859_1))?.let { return it }
    // ID3 frames may be UTF-16, which puts a zero byte next to every ASCII character.
    // Dropping the zeros turns such a link back into plain text.
    val withoutZeros = ByteArray(bytes.size)
    var count = 0
    for (b in bytes) if (b != 0.toByte()) withoutZeros[count++] = b
    return pickYouTubeId(String(withoutZeros, 0, count, Charsets.ISO_8859_1))
}

/**
 * A description tag, written before the source link, can hold links to OTHER videos (the
 * uploader's channel, the previous single...). Taking the first link found would tie the file to
 * one of those. So the link sitting in the purl / comment tag wins; without one, an id is only
 * trusted when every link in the text agrees, and a disagreement is no answer at all.
 */
private fun pickYouTubeId(text: String): String? {
    val matches = youTubeLinkPattern.findAll(text).toList()
    if (matches.isEmpty()) return null
    matches.firstOrNull { match ->
        val start = match.range.first
        val window = text.substring((start - TAG_KEY_WINDOW).coerceAtLeast(0), start)
        sourceLinkTagKeys.any { window.contains(it, ignoreCase = true) }
    }?.let { return it.groupValues[1] }
    val ids = matches.map { it.groupValues[1] }.distinct()
    return ids.singleOrNull()
}
