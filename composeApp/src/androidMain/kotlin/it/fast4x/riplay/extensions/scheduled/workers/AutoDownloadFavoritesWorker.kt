package it.fast4x.riplay.extensions.scheduled.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import it.fast4x.riplay.extensions.ads.PremiumGuard
import it.fast4x.riplay.extensions.download.AutoDownloads
import it.fast4x.riplay.extensions.fastshare.enqueuePendingFavorites
import it.fast4x.riplay.extensions.preferences.autoDownloadFavoritesKey
import it.fast4x.riplay.extensions.preferences.preferences
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Queues favorites, kept playlists and kept albums without a downloaded copy in the in-app
 * downloader. The schedule only runs it on an unmetered network; the downloads then report
 * their own progress and summary.
 */
class AutoDownloadFavoritesWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            if (!AutoDownloads.anyEnabled(context)) return Result.success()
            // Same gate as the download button: without a subscription there is nothing to offer.
            if (!PremiumGuard.isPremium(context)) return Result.success()

            val favorites =
                if (context.preferences.getBoolean(autoDownloadFavoritesKey, false)) enqueuePendingFavorites(context)
                else 0
            val collections = AutoDownloads.enqueueCollections(context)
            Timber.d("AutoDownloadFavoritesWorker: $favorites favorites, $collections from playlists/albums queued")
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "AutoDownloadFavoritesWorker: ${e.message}")
            Result.success()
        }
    }
}
