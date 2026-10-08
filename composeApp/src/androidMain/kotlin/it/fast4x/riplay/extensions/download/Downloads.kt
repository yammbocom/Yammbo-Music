package it.fast4x.riplay.extensions.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yambo.music.R
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.isRadio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * In-app downloads. One WorkManager chain ("yammbo_downloads"), one song at a time:
 * - it survives the app being closed or killed, and waits for a network when there is none;
 * - one at a time keeps YouTube from throttling and the notification simple.
 *
 * A worker never returns failure: in a chain that would cancel every song queued after it.
 * A song that cannot be downloaded is counted and reported in the summary instead.
 */
object Downloads {

    private const val QUEUE = "yammbo_downloads"
    private const val TAG = "yammbo_download"
    private const val ID_TAG_PREFIX = "yammbo_download:"
    internal const val CHANNEL_ID = "downloads_progress"
    internal const val PROGRESS_NOTIFICATION_ID = 2201
    internal const val SUMMARY_NOTIFICATION_ID = 2202

    private const val PREFS = "yammbo_downloads"
    private const val KEY_DONE = "session_done"
    private const val KEY_FAILED = "session_failed"

    internal val json = Json { ignoreUnknownKeys = true }

    /**
     * Queues every song that can be downloaded and is not downloaded or queued already.
     * Returns how many were added. Call from a coroutine; it touches the database.
     */
    suspend fun enqueue(context: Context, songs: List<Song>, albumTitle: String? = null): Int =
        // Check-then-enqueue must not interleave (double tap, favorites worker + button).
        enqueueMutex.withLock { withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val workManager = WorkManager.getInstance(app)
            var added = 0
            songs.asSequence()
                .filterNot { it.isLocal || it.isRadio || it.id.isBlank() }
                .distinctBy { it.id }
                .forEach { song ->
                    if (SongDownloader.hasPlayableCopy(app, song.id)) return@forEach
                    if (isQueued(workManager, song.id)) return@forEach
                    val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(
                            workDataOf(
                                DownloadWorker.KEY_SONG to json.encodeToString(Song.serializer(), song),
                                DownloadWorker.KEY_ALBUM to albumTitle,
                            )
                        )
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .addTag(TAG)
                        .addTag(ID_TAG_PREFIX + song.id)
                        .build()
                    // APPEND_OR_REPLACE: if the previous chain ended badly, start a fresh one
                    // instead of inheriting its failure.
                    workManager.enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                    added++
                }
            Timber.d("Downloads: queued $added of ${songs.size}")
            added
        } }

    private val enqueueMutex = Mutex()

    suspend fun enqueue(context: Context, song: Song): Int = enqueue(context, listOf(song))

    private fun isQueued(workManager: WorkManager, videoId: String): Boolean =
        runCatching {
            workManager.getWorkInfosByTag(ID_TAG_PREFIX + videoId).get()
                .any { !it.state.isFinished }
        }.getOrDefault(false)

    internal fun pendingCount(context: Context): Int = runCatching {
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(QUEUE).get()
            .count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
    }.getOrDefault(0)

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(QUEUE)
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
    }

    // ---- session summary ----

    internal fun recordResult(context: Context, title: String, ok: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (ok) prefs.edit().putInt(KEY_DONE, prefs.getInt(KEY_DONE, 0) + 1).apply()
        else {
            val failed = prefs.getStringSet(KEY_FAILED, emptySet()).orEmpty() + title
            prefs.edit().putStringSet(KEY_FAILED, failed).apply()
        }
    }

    /** When nothing is left in the queue, tell how it went once, and reset the counters. */
    internal fun postSummaryIfIdle(context: Context) {
        if (pendingCount(context) > 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val done = prefs.getInt(KEY_DONE, 0)
        val failed = prefs.getStringSet(KEY_FAILED, emptySet()).orEmpty()
        prefs.edit().remove(KEY_DONE).remove(KEY_FAILED).apply()
        if (done == 0 && failed.isEmpty()) return

        val title = context.resources.getQuantityString(R.plurals.download_summary_done, done, done)
        val text = if (failed.isEmpty()) context.getString(R.string.download_summary_offline)
        else context.resources.getQuantityString(R.plurals.download_summary_failed, failed.size, failed.size) +
            "\n" + failed.take(10).joinToString("\n")
        notify(
            context, SUMMARY_NOTIFICATION_ID,
            baseNotification(context)
                .setContentTitle(title)
                .setContentText(text.substringBefore('\n'))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
        )
    }

    // ---- notifications ----

    internal fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    internal fun baseNotification(context: Context): NotificationCompat.Builder {
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_yammbo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
    }

    internal fun notify(context: Context, id: Int, notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG = "song"
        const val KEY_ALBUM = "album"
        private const val MAX_ATTEMPTS = 4
    }

    override suspend fun doWork(): Result = try {
        work()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A failure would cancel every song queued after this one: count it and move on.
        Timber.e(e, "DownloadWorker: unexpected error")
        val title = runCatching {
            Downloads.json.decodeFromString(Song.serializer(), inputData.getString(KEY_SONG).orEmpty()).title
        }.getOrDefault("")
        Downloads.recordResult(applicationContext, title, ok = false)
        finish(applicationContext)
    }

    private suspend fun work(): Result {
        val context = applicationContext
        val song = runCatching {
            Downloads.json.decodeFromString(Song.serializer(), inputData.getString(KEY_SONG).orEmpty())
        }.getOrNull() ?: return Result.success()
        val album = inputData.getString(KEY_ALBUM)

        // Without being in the foreground a long queue gets cut at 10 minutes. If Android
        // refuses (app in background on 12+), the song still downloads, just without that guarantee.
        runCatching { setForeground(foregroundInfo(song.title, 0f)) }
            .onFailure { Timber.w("DownloadWorker: no foreground: ${it.message}") }

        var lastShown = -1
        val outcome = SongDownloader.download(context, song, album) { fraction ->
            val percent = (fraction * 100).toInt()
            if (percent >= lastShown + 5) {
                lastShown = percent
                Downloads.notify(context, Downloads.PROGRESS_NOTIFICATION_ID, progressNotification(song.title, fraction))
            }
        }
        Timber.i("DownloadWorker: ${song.id} -> $outcome (attempt $runAttemptCount)")

        return when (outcome) {
            is SongDownloader.Outcome.Done -> {
                Downloads.recordResult(context, song.title, ok = true)
                finish(context)
            }
            SongDownloader.Outcome.AlreadyDownloaded -> finish(context)
            is SongDownloader.Outcome.Retry ->
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry()
                else {
                    Downloads.recordResult(context, song.title, ok = false)
                    finish(context)
                }
            is SongDownloader.Outcome.Failed -> {
                Downloads.recordResult(context, song.title, ok = false)
                finish(context)
            }
        }
    }

    private fun finish(context: Context): Result {
        NotificationManagerCompat.from(context).cancel(Downloads.PROGRESS_NOTIFICATION_ID)
        Downloads.postSummaryIfIdle(context)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("", 0f)

    private fun progressNotification(title: String, fraction: Float): android.app.Notification {
        val context = applicationContext
        val pending = Downloads.pendingCount(context)
        val subtitle = if (pending > 0)
            context.resources.getQuantityString(R.plurals.download_remaining, pending, pending)
        else context.getString(R.string.download_in_progress)
        return Downloads.baseNotification(context)
            .setContentTitle(title.ifBlank { context.getString(R.string.download_in_progress) })
            .setContentText(subtitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (fraction * 100).toInt(), fraction <= 0f)
            .build()
    }

    private fun foregroundInfo(title: String, fraction: Float): ForegroundInfo {
        val notification = progressNotification(title, fraction)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(Downloads.PROGRESS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(Downloads.PROGRESS_NOTIFICATION_ID, notification)
    }
}
