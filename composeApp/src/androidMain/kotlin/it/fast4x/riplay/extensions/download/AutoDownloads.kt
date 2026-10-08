package it.fast4x.riplay.extensions.download

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.extensions.fastshare.autoDownloadCandidates
import it.fast4x.riplay.extensions.fastshare.markAutoQueued
import it.fast4x.riplay.extensions.preferences.autoDownloadFavoritesKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.scheduled.syncAutoDownloadFavoritesSchedule
import it.fast4x.riplay.extensions.scheduled.workers.AutoDownloadFavoritesWorker
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

/**
 * "Download automatically on Wi-Fi" for favorites, playlists and albums. Favorites keep their own
 * preference (the settings switch reads it); playlists and albums are a set of keys. One worker,
 * run hourly and right after a switch is turned on, only on an unmetered network, queues what is
 * missing; the downloads themselves go through [Downloads] like any other.
 */
object AutoDownloads {

    const val FAVORITES = "favorites"
    private const val COLLECTIONS_KEY = "autoDownloadCollections"
    private const val PLAYLIST = "playlist:"
    private const val ALBUM = "album:"
    private const val RUN_NOW = "autoDownloadNow"

    /** Per run, across every playlist and album, so a big one never floods the queue at once. */
    private const val MAX_COLLECTION_SONGS_PER_RUN = 40

    fun playlistKey(playlistId: Long) = "$PLAYLIST$playlistId"
    fun albumKey(albumId: String) = "$ALBUM$albumId"

    private fun collections(context: Context): Set<String> =
        context.preferences.getStringSet(COLLECTIONS_KEY, emptySet()).orEmpty()

    fun isEnabled(context: Context, key: String): Boolean =
        if (key == FAVORITES) context.preferences.getBoolean(autoDownloadFavoritesKey, false)
        else key in collections(context)

    fun anyEnabled(context: Context): Boolean =
        context.preferences.getBoolean(autoDownloadFavoritesKey, false) || collections(context).isNotEmpty()

    /** Turns one switch on or off and keeps the schedule in line; turning on also runs it soon. */
    fun setEnabled(context: Context, key: String, enabled: Boolean) {
        val app = context.applicationContext
        if (key == FAVORITES) app.preferences.edit { putBoolean(autoDownloadFavoritesKey, enabled) }
        else {
            val updated = collections(app).let { if (enabled) it + key else it - key }
            app.preferences.edit { putStringSet(COLLECTIONS_KEY, updated) }
        }
        syncAutoDownloadFavoritesSchedule(app)
        if (enabled) runSoon(app)
    }

    /**
     * One run as soon as there is Wi-Fi, instead of waiting for the next hourly one. Appended,
     * not replacing: a second switch turned on right after the first must not cancel its run.
     */
    fun runSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoDownloadFavoritesWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RUN_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Queues what the kept playlists and albums are missing. Returns how many were added. */
    suspend fun enqueueCollections(context: Context): Int {
        val app = context.applicationContext
        val keys = collections(app)
        if (keys.isEmpty()) return 0

        val wanted = mutableListOf<Song>()
        val gone = mutableSetOf<String>()
        for (key in keys) {
            val songs = runCatching {
                when {
                    key.startsWith(PLAYLIST) -> {
                        val id = key.removePrefix(PLAYLIST).toLongOrNull()
                        val playlist = id?.let { Database.singlePlaylistPreview(it).firstOrNull()?.playlist }
                        if (playlist == null) { gone += key; emptyList() }
                        else resolvePlaylistSongs(playlist)
                    }
                    key.startsWith(ALBUM) -> resolveAlbumSongs(key.removePrefix(ALBUM))
                    else -> { gone += key; emptyList() }
                }
            }.getOrElse {
                if (it is CancellationException) throw it
                // Offline or a failed page: try again next run, keep the switch.
                Timber.w("AutoDownloads: $key not resolved: ${it.message}")
                emptyList()
            }
            wanted += songs
        }
        // A deleted playlist has nothing left to keep downloaded.
        if (gone.isNotEmpty())
            app.preferences.edit { putStringSet(COLLECTIONS_KEY, collections(app) - gone) }

        val missing = wanted.asSequence()
            .distinctBy { it.id }
            .filterNot { SongDownloader.hasPlayableCopy(app, it.id) }
            .toList()
        val toQueue = autoDownloadCandidates(app, missing).take(MAX_COLLECTION_SONGS_PER_RUN)
        if (toQueue.isEmpty()) return 0
        val added = Downloads.enqueue(app, toQueue)
        toQueue.forEach { markAutoQueued(app, it.id) }
        return added
    }
}
