package it.fast4x.riplay.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yambo.music.R
import timber.log.Timber

/**
 * Receives the result of the PackageInstaller session opened by [UpdateDownloader].
 *
 * - PENDING_USER_ACTION: Android wants the user to confirm (always the first time, since the app
 *   is not yet its own installer of record). The confirmation screen is started from here; that
 *   only reliably works while the app is in the foreground, which is why installs are started
 *   from the dialog button or from MainActivity while it is resumed.
 * - SUCCESS: nothing to do, the process is about to be replaced ([UpdatedReceiver] takes over).
 * - ABORTED: the user said no; do not insist.
 * - Anything else: fall back to the old ACTION_VIEW installer intent.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        Timber.d("UpdateInstallReceiver status=$status message=$message")
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    UpdateDownloader.installViaView(context)
                    return
                }
                runCatching {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    Timber.e("UpdateInstallReceiver could not show the confirmation: ${it.message}")
                    UpdateDownloader.installViaView(context)
                }
            }

            PackageInstaller.STATUS_SUCCESS,
            PackageInstaller.STATUS_FAILURE_ABORTED -> Unit

            else -> {
                Timber.e("UpdateInstallReceiver install failed ($status): $message")
                UpdateDownloader.installViaView(context)
            }
        }
    }
}

/**
 * Runs in the new process right after the app has been replaced: says so with a quiet
 * notification that opens the app. Skipped silently without the notifications permission.
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            @Suppress("DEPRECATION")
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.app_update_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            val pending = PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_yammbo)
                .setContentTitle(context.getString(R.string.app_update_done_title))
                .setContentText(context.getString(R.string.app_update_done_text, versionName))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }.onFailure { Timber.e("UpdatedReceiver failed: ${it.message}") }
    }

    private companion object {
        const val CHANNEL_ID = "appUpdated"
        const val NOTIFICATION_ID = 3
    }
}
