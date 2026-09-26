package it.fast4x.riplay.extensions.fastshare

import it.fast4x.riplay.extensions.yammboapi.AppEvents
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.extensions.preferences.autoDownloadSentKey
import it.fast4x.riplay.extensions.preferences.preferences
import org.json.JSONObject
import timber.log.Timber

/** At most this many songs are handed to YTDLnis per run, so one tap never floods its queue. */
const val AUTO_DOWNLOAD_MAX_PER_RUN = 20

// A song sent recently is most likely still downloading or already on disk but not yet
// picked up by MediaStore; sending it again would only produce a duplicate.
private const val RESEND_AFTER_MS = 3L * 24 * 60 * 60 * 1000
// Past this the entry says nothing useful any more and only grows the preference.
private const val FORGET_AFTER_MS = 30L * 24 * 60 * 60 * 1000
// Sent this many times and still no local copy: YTDLnis most likely cannot download it, or saves
// it somewhere (or tagged in a way) we cannot match back. Sending it again would only repeat
// that, so it is left out from then on, by the reminder and by the manual button alike.
private const val MAX_AUTOMATIC_SENDS = 2

// Favorites also hold podcast episodes and other ids that are not YouTube videos; YTDLnis
// would reject those, and the canonical link below only makes sense for a video id.
private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")

private val sentLock = Any()

fun isYtdlnisInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(YTDLNIS_APP.packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

fun openYtdlnisInstallPage(context: Context) = openExternalUrl(context, YTDLNIS_APP.githubUrl)

/**
 * Always the same form of link: YTDLnis spots a duplicate by comparing the link text, so a
 * music.youtube.com or youtu.be variant of the same video would be downloaded twice.
 */
fun canonicalYouTubeUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

/**
 * Hands one video to YTDLnis to download as audio without showing its card. With BACKGROUND
 * set it queues the job and finishes at once. Only ever one link: it reads just the first
 * one, and a playlist is one yt-dlp job where a single unavailable video fails them all.
 * Must be called from an activity that is on screen, or Android blocks the launch.
 */
fun sendToYtdlnisInBackground(context: Context, videoId: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, canonicalYouTubeUrl(videoId))
        putExtra("TYPE", "audio")
        putExtra("BACKGROUND", true)
        setPackage(YTDLNIS_APP.packageName)
    }
    return try {
        context.startActivity(intent)
        AppEvents.log(AppEvents.AUTO_DOWNLOAD, videoId = videoId)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        Timber.e("sendToYtdlnisInBackground failed for $videoId: ${e.message}")
        false
    }
}

/** When a video was last handed to YTDLnis, and how many times it has been. */
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

fun markSentToYtdlnis(context: Context, videoId: String) = synchronized(sentLock) {
    val map = readSentMap(context)
    map[videoId] = SentEntry(System.currentTimeMillis(), (map[videoId]?.sends ?: 0) + 1)
    writeSentMap(context, map)
}

/** Pending favorites split into those to send now and how many were given up on. */
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
        .filter { videoIdPattern.matches(it.id) }
    val (givenUp, candidates) = pending.partition { song ->
        (sent[song.id]?.sends ?: 0) >= MAX_AUTOMATIC_SENDS
    }
    val toSend = candidates.filter { song -> sent[song.id]?.let { now - it.at < RESEND_AFTER_MS } != true }
    return toSend to givenUp.size
}

/**
 * Favorites with no downloaded copy that were not handed to YTDLnis in the last few days,
 * most recently liked first, leaving out those already sent MAX_AUTOMATIC_SENDS times.
 * Also prunes old bookkeeping.
 */
@WorkerThread
fun pendingFavoritesToSend(context: Context): List<Song> = classifyPendingFavorites(context).first

/** Favorites still without a local copy after MAX_AUTOMATIC_SENDS sends, no longer sent. */
@WorkerThread
fun favoritesGivenUpCount(context: Context): Int = classifyPendingFavorites(context).second
