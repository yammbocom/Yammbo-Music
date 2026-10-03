package it.fast4x.riplay.utils.spotify

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import com.yambo.music.R
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.SongPlaylistMap
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.utils.asMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Imports Spotify playlists and albums (public ones, by link) into local playlists, matching
 * every track to a YouTube Music song. The link of each imported playlist is remembered, so it
 * can be brought up to date ("Update from Spotify", and silently at most once a day at start).
 *
 * The link is kept in SharedPreferences, not in the Playlist table, to spare a Room migration:
 * key = local playlist id, value = url, name, last import time and the track uri -> video id
 * map of the last import (which songs came from Spotify, and the matches to reuse).
 */
object SpotifyImport {

    private const val PREFS = "spotify_imports"
    private const val KEY_PREFIX = "p_"
    /** Map value prefix: matched, but the song was already in the playlist (the user's, never removed). */
    private const val USER_OWNED = "~"
    private const val REFRESH_EVERY_MS = 24L * 60 * 60 * 1000
    private const val AUTO_REFRESH_DELAY_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val autoRefreshStarted = AtomicBoolean(false)
    @Volatile
    private var pendingLink: String? = null

    // ---- UI state, drawn by SpotifyImportHost ----

    var dialogVisible by mutableStateOf(false)
        private set
    var linkDraft by mutableStateOf("")
    var running by mutableStateOf(false)
        private set
    var runningName by mutableStateOf<String?>(null)
        private set
    var progressDone by mutableIntStateOf(0)
        private set
    var progressTotal by mutableIntStateOf(0)
        private set
    var errorText by mutableStateOf<String?>(null)
        private set

    fun openDialog(prefill: String? = null) {
        if (!running) {
            errorText = null
            if (!prefill.isNullOrBlank()) linkDraft = prefill
        }
        dialogVisible = true
    }

    fun closeDialog() {
        dialogVisible = false
        if (!running) errorText = null
    }

    /** Text shared to the app or opened as a link. True when it was a Spotify playlist/album. */
    fun onSharedText(text: String?): Boolean {
        if (!SpotifyEmbed.looksLikeSpotifyCollection(text)) return false
        val link = SpotifyEmbed.parseRef(text)?.url ?: SpotifyEmbed.findShortLink(text) ?: return false
        if (!running) linkDraft = link
        openDialog()
        startFromLink(link)
        return true
    }

    fun startFromLink(link: String) {
        if (!SpotifyEmbed.looksLikeSpotifyCollection(link)) {
            errorText = string(R.string.spotify_import_err_link)
            return
        }
        // A link that arrives during another import waits for it instead of being dropped.
        if (running) {
            pendingLink = link
            SmartMessage(string(R.string.spotify_import_queued), type = PopupType.Info, context = appContext())
            return
        }
        launchManual(null) {
            val ref = SpotifyEmbed.resolve(link)
            val collection = SpotifyEmbed.fetch(ref)
            runningName = collection.name
            // The same list imported before: bring that playlist up to date instead of a copy.
            val existing = sourcesWithUrl(ref.url).firstOrNull { playlistExists(it) }
            importCollection(collection, ref.url, existing, retryMisses = true)
        }
    }

    /** Exportify CSV rows: no link to come back to, so no refresh. */
    fun startFromTracks(name: String, tracks: List<SpotifyTrack>) {
        launchManual(name) {
            importCollection(SpotifyCollection(null, name, tracks), null, null, retryMisses = true)
        }
    }

    fun startUpdate(playlistId: Long) {
        val source = readSource(playlistId) ?: return
        launchManual(source.name) {
            val ref = SpotifyEmbed.parseRef(source.url)
                ?: throw SpotifyImportException(SpotifyImportException.Reason.InvalidLink, source.url)
            importCollection(SpotifyEmbed.fetch(ref), ref.url, playlistId, retryMisses = true)
        }
    }

    fun isImported(playlistId: Long): Boolean =
        prefs().contains(KEY_PREFIX + playlistId)

    /** True once any list has been imported from a link (CSV imports keep no source). */
    fun hasImports(): Boolean =
        prefs().all.keys.any { it.startsWith(KEY_PREFIX) }

    /** Once per process, in the background: refreshes the imports older than a day. */
    fun autoRefreshOnce() {
        if (!autoRefreshStarted.compareAndSet(false, true)) return
        scope.launch {
            delay(AUTO_REFRESH_DELAY_MS)
            val now = System.currentTimeMillis()
            val due = allSources().filter { now - it.lastImport >= REFRESH_EVERY_MS }
            Timber.d("SpotifyImport auto refresh: ${due.size} due")
            for (source in due) {
                mutex.withLock {
                    runCatching {
                        if (!playlistExists(source.playlistId)) {
                            removeSource(source.playlistId)
                            return@runCatching
                        }
                        val ref = SpotifyEmbed.parseRef(source.url) ?: return@runCatching
                        // Stamp the attempt first: a failing list is retried tomorrow, not at every start.
                        writeSource(source.copy(lastImport = now))
                        val collection = SpotifyEmbed.fetch(ref)
                        importCollection(collection, ref.url, source.playlistId, retryMisses = false, reportProgress = false)
                    }.onFailure { Timber.w("SpotifyImport auto refresh ${source.url} failed: ${it.message}") }
                }
            }
        }
    }

    // ---- import ----

    private data class Outcome(
        val name: String,
        val isUpdate: Boolean,
        val added: Int,
        val removed: Int,
        val notFound: Int,
        /** Read from a link that lists 100 tracks: Spotify may have cut the list there. */
        val truncated: Boolean,
    )

    private fun launchManual(name: String?, block: suspend () -> Outcome) {
        if (running) {
            SmartMessage(string(R.string.spotify_import_busy), type = PopupType.Info, context = appContext())
            return
        }
        running = true
        runningName = name
        progressDone = 0
        progressTotal = 0
        errorText = null
        scope.launch {
            val result = runCatching { mutex.withLock { block() } }
            running = false
            result.onSuccess { outcome ->
                dialogVisible = false
                linkDraft = ""
                val message = if (outcome.isUpdate)
                    string(R.string.spotify_import_updated, outcome.name, outcome.added, outcome.removed, outcome.notFound)
                else string(R.string.spotify_import_done, outcome.name, outcome.added, outcome.notFound)
                val limit = if (outcome.truncated) "\n" + string(R.string.spotify_import_limit_reached) else ""
                SmartMessage(message + limit, type = PopupType.Success, durationLong = true, context = appContext())
            }.onFailure { error ->
                Timber.e("SpotifyImport failed: ${error.stackTraceToString()}")
                val text = errorMessage(error)
                errorText = text
                if (!dialogVisible || pendingLink != null)
                    SmartMessage(text, type = PopupType.Warning, durationLong = true, context = appContext())
            }
            // A link shared while this import ran: its turn now.
            pendingLink?.let { next ->
                pendingLink = null
                linkDraft = next
                openDialog()
                startFromLink(next)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun importCollection(
        collection: SpotifyCollection,
        sourceUrl: String?,
        targetPlaylistId: Long?,
        retryMisses: Boolean,
        reportProgress: Boolean = true,
    ): Outcome {
        val tracks = collection.tracks
        val previous = targetPlaylistId?.let { readSource(it) }
        val previousMap = previous?.map.orEmpty()

        val toSearch = tracks
            .filter { track -> previousMap[track.uri].let { it == null || (it.isEmpty() && retryMisses) } }
            .distinctBy { it.uri }
        val found = SpotifyMatcher.matchAll(toSearch) { done, total ->
            if (reportProgress) {
                progressDone = done
                progressTotal = total
            }
        }
        val resultByUri: Map<String, SpotifyMatcher.Result> = toSearch.map { it.uri }.zip(found).toMap()
        val foundByVideoId = found.filterIsInstance<SpotifyMatcher.Result.Found>()
            .map { it.item }.associateBy { it.key }

        // Songs already in the playlist before this run: if Spotify later matches one of them,
        // it stays the user's (marked with USER_OWNED) and is never removed by a refresh.
        val current = targetPlaylistId
            ?.let { Database.playlistSongs(it).first().orEmpty().map { song -> song.id } }
            .orEmpty()
        val previouslyImported = previousMap.values.filter { it.isNotEmpty() && !it.startsWith(USER_OWNED) }.toSet()

        val map = LinkedHashMap<String, String>()
        for (track in tracks) {
            when (val result = resultByUri[track.uri]) {
                is SpotifyMatcher.Result.Found -> {
                    val videoId = result.item.key
                    map[track.uri] = if (videoId in current && videoId !in previouslyImported) USER_OWNED + videoId else videoId
                }
                SpotifyMatcher.Result.NoMatch -> map[track.uri] = ""
                // Network failure: not remembered at all (or the old value kept), so it is asked again.
                SpotifyMatcher.Result.Failed, null -> previousMap[track.uri]?.let { map[track.uri] = it }
            }
        }
        val desired = tracks.mapNotNull { map[it.uri]?.removePrefix(USER_OWNED)?.takeIf(String::isNotEmpty) }.distinct()
        val notFound = tracks.count { map[it.uri].isNullOrEmpty() }
        val name = collection.name.ifBlank { "Spotify" }

        if (targetPlaylistId == null) {
            if (desired.isEmpty())
                throw SpotifyImportException(SpotifyImportException.Reason.Empty, "No song matched")
            val playlistId = dbWrite {
                val id = insert(Playlist(name = name))
                desired.forEachIndexed { index, videoId ->
                    foundByVideoId[videoId]?.let { insert(it.asMediaItem) }
                    insert(SongPlaylistMap(songId = videoId, playlistId = id, position = index).default())
                }
                id
            }
            if (sourceUrl != null)
                writeSource(Source(playlistId, sourceUrl, name, System.currentTimeMillis(), map))
            return Outcome(name, false, desired.size, 0, notFound, sourceUrl != null && tracks.size >= SpotifyEmbed.MAX_TRACKS)
        }

        // Imported once and since taken out of the playlist by hand: not put back.
        val removedByUser = previouslyImported - current.toSet()
        val toRemove = current.filter { it in previouslyImported && it !in desired }
        val toAdd = desired.filter { it !in current && it !in removedByUser && it in foundByVideoId }

        // The user's arrangement is kept as it is: nothing moves unless songs come or go, and
        // then each new song goes right after the Spotify track that precedes it (or before
        // the one that follows it, or at the end). A mere reorder on Spotify is not followed.
        if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
            val order = current.filterTo(ArrayList()) { it !in toRemove }
            for (videoId in toAdd) {
                val index = desired.indexOf(videoId)
                val before = (index - 1 downTo 0).firstOrNull { desired[it] in order }?.let { desired[it] }
                val after = (index + 1 until desired.size).firstOrNull { desired[it] in order }?.let { desired[it] }
                when {
                    before != null -> order.add(order.indexOf(before) + 1, videoId)
                    after != null -> order.add(order.indexOf(after), videoId)
                    else -> order.add(videoId)
                }
            }
            dbWrite {
                toRemove.forEach { deleteSongFromPlaylist(it, targetPlaylistId) }
                toAdd.forEach { videoId ->
                    foundByVideoId[videoId]?.let { insert(it.asMediaItem) }
                    insert(SongPlaylistMap(songId = videoId, playlistId = targetPlaylistId, position = order.indexOf(videoId)).default())
                }
                order.forEachIndexed { index, videoId -> updateSongPosition(targetPlaylistId, videoId, index) }
            }
        }
        if (sourceUrl != null)
            writeSource(Source(targetPlaylistId, sourceUrl, name, System.currentTimeMillis(), map))
        Timber.d("SpotifyImport update $targetPlaylistId: +${toAdd.size} -${toRemove.size} missing $notFound")
        return Outcome(name, true, toAdd.size, toRemove.size, notFound, sourceUrl != null && tracks.size >= SpotifyEmbed.MAX_TRACKS)
    }

    /** Runs on the database's own executor, in line with every other write of the app. */
    private suspend fun <T> dbWrite(block: Database.() -> T): T = suspendCancellableCoroutine { cont ->
        Database.asyncTransaction {
            runCatching { block() }
                .onSuccess { cont.resume(it) }
                .onFailure { cont.resumeWithException(it) }
        }
    }

    private suspend fun playlistExists(playlistId: Long): Boolean =
        Database.playlistWithSongs(playlistId).first() != null

    private fun errorMessage(error: Throwable): String = when ((error as? SpotifyImportException)?.reason) {
        SpotifyImportException.Reason.InvalidLink -> string(R.string.spotify_import_err_link)
        SpotifyImportException.Reason.NotFound -> string(R.string.spotify_import_err_not_found)
        SpotifyImportException.Reason.Network -> string(R.string.spotify_import_err_network)
        SpotifyImportException.Reason.Unparsable -> string(R.string.spotify_import_err_parse)
        SpotifyImportException.Reason.Empty -> string(R.string.spotify_import_err_none)
        null -> string(R.string.spotify_import_err_parse)
    }

    private fun string(id: Int, vararg args: Any): String = appContext().getString(id, *args)

    // ---- remembered sources ----

    private data class Source(
        val playlistId: Long,
        val url: String,
        val name: String,
        val lastImport: Long,
        val map: Map<String, String>,
    )

    private fun prefs(): SharedPreferences =
        appContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readSource(playlistId: Long): Source? =
        prefs().getString(KEY_PREFIX + playlistId, null)?.let { decode(playlistId, it) }

    private fun allSources(): List<Source> =
        prefs().all.mapNotNull { (key, value) ->
            val id = key.removePrefix(KEY_PREFIX).toLongOrNull() ?: return@mapNotNull null
            (value as? String)?.let { decode(id, it) }
        }

    private fun sourcesWithUrl(url: String): List<Long> =
        allSources().filter { it.url == url }.map { it.playlistId }

    private fun writeSource(source: Source) {
        val map = JSONObject()
        source.map.forEach { (uri, videoId) -> map.put(uri, videoId) }
        val json = JSONObject()
            .put("url", source.url)
            .put("name", source.name)
            .put("last", source.lastImport)
            .put("map", map)
        prefs().edit().putString(KEY_PREFIX + source.playlistId, json.toString()).apply()
    }

    private fun removeSource(playlistId: Long) {
        prefs().edit().remove(KEY_PREFIX + playlistId).apply()
    }

    private fun decode(playlistId: Long, raw: String): Source? = runCatching {
        val json = JSONObject(raw)
        val mapJson = json.optJSONObject("map")
        val map = LinkedHashMap<String, String>()
        mapJson?.keys()?.forEach { key -> map[key] = mapJson.optString(key) }
        Source(playlistId, json.getString("url"), json.optString("name"), json.optLong("last"), map)
    }.getOrNull()
}
