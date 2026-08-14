package it.fast4x.riplay.extensions.scheduled.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yambo.music.R
import it.fast4x.riplay.utils.fetchLatestVersion
import it.fast4x.riplay.utils.getVersionCode
import timber.log.Timber
import java.io.File
import okhttp3.OkHttpClient

class CheckUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "checkUpdate"
        const val NOTIFICATION_ID = 2
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        return try {
            Timber.d("CheckUpdateWorker: Start (GitHub Releases)...")

            // Resolve the latest version via the shared helper: our VPS probe first
            // (music.yammbo.com/download/version.json), GitHub Releases API as fallback.
            // Avoids the unauthenticated GitHub API rate limit on shared carrier IPs.
            val (remoteVersionCode, remoteVersionName) = fetchLatestVersion(OkHttpClient())
                ?: run {
                    Timber.d("CheckUpdateWorker: could not resolve latest version")
                    return Result.retry()
                }

            val productName = "Yammbo Music"
            val fileLine = "$remoteVersionCode-$remoteVersionName-$productName\n"

            try {
                File(context.filesDir, "UpdatedVersionCode.ver").writeText(fileLine)
                Timber.d("CheckUpdateWorker: cached remote $remoteVersionName ($remoteVersionCode)")
            } catch (e: Exception) {
                Timber.e(e, "CheckUpdateWorker: writing .ver failed")
                return Result.failure()
            }

            val localVersionCode = getVersionCode()
            if (remoteVersionCode <= localVersionCode) {
                Timber.d("CheckUpdateWorker: up to date (local=$localVersionCode remote=$remoteVersionCode)")
                return Result.success()
            }

            showNotification(
                context,
                "Nueva versión disponible: $remoteVersionName"
            )

            Result.success()

        } catch (e: Exception) {
            Timber.e(e, "CheckUpdateWorker: ${e.message}")
            Result.retry()
        }
    }

    private fun showNotification(context: Context, message: String) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Scheduled"
            val descriptionText = "Check update"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_yammbo)
            .setContentTitle("Check update")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID, builder.build())
        }
    }
}