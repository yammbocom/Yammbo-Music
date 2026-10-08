package it.fast4x.riplay.extensions.scheduled

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.fast4x.riplay.extensions.download.AutoDownloads
import it.fast4x.riplay.extensions.scheduled.workers.AutoDownloadFavoritesWorker
import java.util.concurrent.TimeUnit

const val workNameAutoDownloadFavorites = "autoDownloadFavorites"

/**
 * Every hour, and only on an unmetered network with a healthy battery: the worker queues what
 * favorites and kept playlists/albums are missing while the listener is on Wi-Fi. A run that
 * finds nothing only reads the database. UPDATE keeps the period running across app starts and
 * moves installs still on the old 12-hour period to this one.
 */
fun scheduleAutoDownloadFavorites(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    val request = PeriodicWorkRequestBuilder<AutoDownloadFavoritesWorker>(1, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        workNameAutoDownloadFavorites,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

fun cancelAutoDownloadFavorites(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(workNameAutoDownloadFavorites)
}

/** Brings the scheduled work in line with the preference; safe to call at every app start. */
fun syncAutoDownloadFavoritesSchedule(context: Context) {
    runCatching {
        if (AutoDownloads.anyEnabled(context))
            scheduleAutoDownloadFavorites(context)
        else
            cancelAutoDownloadFavorites(context)
    }
}
