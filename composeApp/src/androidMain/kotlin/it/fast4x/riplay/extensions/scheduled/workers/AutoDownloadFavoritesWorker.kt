package it.fast4x.riplay.extensions.scheduled.workers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yambo.music.R
import it.fast4x.riplay.extensions.ads.PremiumGuard
import it.fast4x.riplay.extensions.fastshare.AutoDownloadActivity
import it.fast4x.riplay.extensions.fastshare.isYtdlnisInstalled
import it.fast4x.riplay.extensions.fastshare.pendingFavoritesToSend
import it.fast4x.riplay.extensions.preferences.autoDownloadFavoritesKey
import it.fast4x.riplay.extensions.preferences.preferences
import timber.log.Timber

/**
 * Looks for favorites without a downloaded copy and, if there are any, posts a notification.
 *
 * It cannot hand them to YTDLnis itself: Android does not let a background worker open another
 * app's activity, and YTDLnis only takes links through one. A notification tap is exempt from
 * that rule, so the tap opens AutoDownloadActivity, which does the sending while on screen.
 */
class AutoDownloadFavoritesWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2101
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            if (!context.preferences.getBoolean(autoDownloadFavoritesKey, false)) return Result.success()
            if (!isYtdlnisInstalled(context)) return Result.success()
            // Same gate as the download button: without a subscription there is nothing to offer.
            if (!PremiumGuard.isPremium(context)) return Result.success()

            val pending = pendingFavoritesToSend(context).size
            Timber.d("AutoDownloadFavoritesWorker: $pending favorites pending")
            if (pending > 0) showNotification(context, pending)
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "AutoDownloadFavoritesWorker: ${e.message}")
            Result.success()
        }
    }

    private fun showNotification(context: Context, pending: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.auto_download_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, AutoDownloadActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = context.resources.getQuantityString(
            R.plurals.auto_download_notification_text, pending, pending
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_yammbo)
            .setContentTitle(context.getString(R.string.auto_download_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }
}
