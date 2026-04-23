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
import it.fast4x.riplay.utils.getAvailableUpdateInfo
import it.fast4x.riplay.utils.getVersionCode
import timber.log.Timber
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class CheckUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "checkUpdate"
        const val NOTIFICATION_ID = 2
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        return try {
            Timber.d("CheckUpdateWorker: Start (GitHub Releases)...")

            val client = OkHttpClient()
            // GitHub Releases API — the /latest endpoint always points to the
            // most recent non-prerelease, non-draft release. No auth needed for
            // public repos; GitHub's 60 req/h unauth limit per IP is plenty for
            // a daily/weekly check.
            val releasesUrl =
                "https://api.github.com/repos/yammbocom/Yammbo-Music/releases/latest"

            val request = Request.Builder()
                .url(releasesUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Yammbo-Music-UpdateChecker")
                .build()
            val response: Response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Timber.e("CheckUpdateWorker: GitHub API returned ${response.code}")
                return if (response.code in 500..599) Result.retry() else Result.success()
            }

            val body = response.body?.string()
                ?: run {
                    Timber.e("CheckUpdateWorker: empty response body")
                    return Result.retry()
                }

            val json = org.json.JSONObject(body)
            val tagName = json.optString("tag_name").ifBlank {
                Timber.e("CheckUpdateWorker: tag_name missing in release JSON")
                return Result.success()
            }
            // Tag format: "v0.7.73" → versionName "0.7.73"
            val remoteVersionName = tagName.removePrefix("v").trim()
            // versionCode derived from the last dotted segment (project invariant:
            // versionName "0.7.N" pairs with versionCode N).
            val remoteVersionCode = remoteVersionName.substringAfterLast('.')
                .toIntOrNull() ?: 0

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
            .setSmallIcon(R.drawable.app_icon)
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