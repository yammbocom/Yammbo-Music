package it.fast4x.riplay.extensions.fastshare

import it.fast4x.riplay.extensions.yammboapi.AppEvents
import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import com.yambo.music.R
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.ads.PremiumFeature
import it.fast4x.riplay.extensions.ads.PremiumGuard
import it.fast4x.riplay.extensions.download.Downloads
import it.fast4x.riplay.extensions.preferences.autoDownloadSentKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.isConnectionMetered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/** At most this many songs are queued per run, so one tap never floods the download queue. */
const val AUTO_DOWNLOAD_MAX_PER_RUN = 20

// A song queued recently is most likely still downloading or already on disk but not yet
// picked up by MediaStore; queuing it again would only produce a duplicate.
private const val RESEND_AFTER_MS = 3L * 24 * 60 * 60 * 1000
// Past this the entry says nothing useful any more and only grows the preference.
private const val FORGET_AFTER_MS = 30L * 24 * 60 * 60 * 1000
// Queued this many times and still no local copy: the video most likely cannot be downloaded
// (removed, region locked). Trying again would only repeat that, so it is left out from then on,
// by the scheduled run and by the manual button alike.
private const val MAX_AUTOMATIC_SENDS = 2

// Favorites also hold podcast episodes and other ids that are not YouTube videos; the
// downloader would reject those.
private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

private val sentLock = Any()

/** When a video was last queued for download, and how many times it has been. */
private class SentEntry(val at: Long, val sends: Int)

private fun readSentMap(context: Context): MutableMap<String, SentEntry> {
    val raw = context.preferences.getString(autoDownloadSentKey, null) ?: return mutableMapOf()
    return runCatching {
        val json = JSONObject(raw)
        val map = mutableMapOf<String, SentEntry>()
        for (id in json.keys()) {
            when (val value = json.opt(id)) {
                is JSONObject -> map[id] = SentEntry(value.optLong("at"), value.optInt("sends", 1))
                // Entries written before the count existed held only the time: one send.
                is Number -> map[id] = SentEntry(value.toLong(), 1)
            }
        }
        map
    }.getOrDefault(mutableMapOf())
}

private fun writeSentMap(context: Context, map: Map<String, SentEntry>) {
    val json = JSONObject()
    for ((id, entry) in map) json.put(id, JSONObject().put("at", entry.at).put("sends", entry.sends))
    context.preferences.edit { putString(autoDownloadSentKey, json.toString()) }
}

private fun markQueued(context: Context, videoId: String) = synchronized(sentLock) {
    val map = readSentMap(context)
    map[videoId] = SentEntry(System.currentTimeMillis(), (map[videoId]?.sends ?: 0) + 1)
    writeSentMap(context, map)
}

/** Pending favorites split into those to queue now and how many were given up on. */
@WorkerThread
private fun classifyPendingFavorites(context: Context): Pair<List<Song>, Int> {
    val now = System.currentTimeMillis()
    val sent = synchronized(sentLock) {
        val map = readSentMap(context)
        // A given-up entry is kept however old it is: forgetting it would start the sends over.
        if (map.values.removeAll { now - it.at > FORGET_AFTER_MS && it.sends < MAX_AUTOMATIC_SENDS })
            writeSentMap(context, map)
        map
    }
    val pending = runCatching { Database.pendingFavoriteDownloads() }
        .getOrElse {
            Timber.e("pendingFavoritesToSend query failed: ${it.message}")
            emptyList()
        }
    return applySendRules(pending, sent, now)
}

/** Songs worth queuing now out of [songs], and how many were given up on. */
private fun applySendRules(songs: List<Song>, sent: Map<String, SentEntry>, now: Long): Pair<List<Song>, Int> {
    val (givenUp, candidates) = songs.filter { videoIdPattern.matches(it.id) }.partition { song ->
        (sent[song.id]?.sends ?: 0) >= MAX_AUTOMATIC_SENDS
    }
    val toSend = candidates.filter { song -> sent[song.id]?.let { now - it.at < RESEND_AFTER_MS } != true }
    return toSend to givenUp.size
}

/**
 * The same rules as favorites, for any automatic download (kept playlists and albums): a song
 * queued in the last days, or already queued MAX_AUTOMATIC_SENDS times, is left out.
 */
fun autoDownloadCandidates(context: Context, songs: List<Song>): List<Song> {
    val sent = synchronized(sentLock) { readSentMap(context) }
    return applySendRules(songs, sent, System.currentTimeMillis()).first
}

/** Records an automatic queuing of [videoId], for [autoDownloadCandidates]. */
fun markAutoQueued(context: Context, videoId: String) = markQueued(context, videoId)

/**
 * Favorites with no downloaded copy that were not queued in the last few days,
 * most recently liked first, leaving out those already queued MAX_AUTOMATIC_SENDS times.
 * Also prunes old bookkeeping.
 */
@WorkerThread
fun pendingFavoritesToSend(context: Context): List<Song> = classifyPendingFavorites(context).first

/** Favorites still without a local copy after MAX_AUTOMATIC_SENDS attempts, no longer queued. */
@WorkerThread
fun favoritesGivenUpCount(context: Context): Int = classifyPendingFavorites(context).second

/**
 * Queues the pending favorites in the in-app downloader. Returns how many were added.
 * Safe from a background worker: nothing here needs an activity on screen.
 */
suspend fun enqueuePendingFavorites(context: Context): Int {
    val app = context.applicationContext
    val pending = withContext(Dispatchers.IO) { pendingFavoritesToSend(app) }
        .take(AUTO_DOWNLOAD_MAX_PER_RUN)
    if (pending.isEmpty()) return 0
    val added = Downloads.enqueue(app, pending)
    withContext(Dispatchers.IO) { pending.forEach { markQueued(app, it.id) } }
    if (added > 0) AppEvents.log(AppEvents.AUTO_DOWNLOAD, detail = added.toString())
    return added
}

/** The manual "download pending favorites now": same gates as the button, and tells the result. */
suspend fun downloadPendingFavoritesNow(context: Context) {
    val app = context.applicationContext
    if (!PremiumGuard.checkFeature(app, PremiumFeature.Download)) return
    val message = when {
        app.isConnectionMetered() -> app.getString(R.string.auto_download_needs_wifi)
        else -> enqueuePendingFavorites(app).let { added ->
            if (added > 0) app.resources.getQuantityString(R.plurals.download_queued, added, added)
            else app.getString(R.string.auto_download_nothing_pending)
        }
    }
    SmartMessage(message, PopupType.Info, context = app)
}
