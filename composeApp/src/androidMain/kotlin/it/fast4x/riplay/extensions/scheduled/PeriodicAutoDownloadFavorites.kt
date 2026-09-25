package it.fast4x.riplay.extensions.scheduled

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import it.fast4x.riplay.extensions.preferences.autoDownloadFavoritesKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.scheduled.workers.AutoDownloadFavoritesWorker
import java.util.concurrent.TimeUnit

const val workNameAutoDownloadFavorites = "autoDownloadFavorites"

/**
 * Every 12 hours, and only on an unmetered network with a healthy battery: the worker itself
 * never downloads anything, it only tells the listener there is something worth downloading
 * while they are on Wi-Fi. KEEP, so calling this at every app start does not reset the period.
 */
fun scheduleAutoDownloadFavorites(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build()

    val request = PeriodicWorkRequestBuilder<AutoDownloadFavoritesWorker>(12, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        workNameAutoDownloadFavorites,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

fun cancelAutoDownloadFavorites(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(workNameAutoDownloadFavorites)
}

/** Brings the scheduled work in line with the preference; safe to call at every app start. */
fun syncAutoDownloadFavoritesSchedule(context: Context) {
    runCatching {
        if (context.preferences.getBoolean(autoDownloadFavoritesKey, false))
            scheduleAutoDownloadFavorites(context)
        else
            cancelAutoDownloadFavorites(context)
    }
}
