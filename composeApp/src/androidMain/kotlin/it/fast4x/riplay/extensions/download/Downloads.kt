package it.fast4x.riplay.extensions.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
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
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.preferences.downloadWifiOnlyKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.isConnectionMetered
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.isRadio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * A worker never returns failure or retry: in a chain that would cancel (or stall) every song
 * queued after it. A song gets a few attempts inside the worker; if they all fail it is counted
 * and reported in the summary instead.
 */
object Downloads {

    private const val QUEUE = "yammbo_downloads"
    private const val TAG = "yammbo_download"
    private const val ID_TAG_PREFIX = "yammbo_download:"
    internal const val CHANNEL_ID = "downloads_progress"
    internal const val DONE_CHANNEL_ID = "downloads_done"
    internal const val PROGRESS_NOTIFICATION_ID = 2201
    internal const val SUMMARY_NOTIFICATION_ID = 2202
    internal const val ACTION_CANCEL = "it.fast4x.riplay.extensions.download.CANCEL"

    private const val PREFS = "yammbo_downloads"
    private const val KEY_TOTAL = "session_total"
    private const val KEY_PROCESSED = "session_processed"
    private const val KEY_DONE = "session_done"
    private const val KEY_FAILED = "session_failed"
    private const val KEY_WARNED_NOTIFICATIONS = "warned_notifications_off"

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
            val wifiOnly = runCatching { app.preferences.getBoolean(downloadWifiOnlyKey, false) }.getOrDefault(false)
            val candidates = songs
                .filterNot { it.isLocal || it.isRadio || it.id.isBlank() }
                .distinctBy { it.id }
            if (candidates.isNotEmpty()) warnIfNotificationsOff(app)

            var added = 0
            candidates.forEachIndexed { index, song ->
                if (SongDownloader.hasPlayableCopy(app, song.id)) return@forEachIndexed
                if (isQueued(workManager, song.id)) return@forEachIndexed
                val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        workDataOf(
                            DownloadWorker.KEY_SONG to json.encodeToString(Song.serializer(), song),
                            DownloadWorker.KEY_ALBUM to albumTitle,
                            // Position in the album batch (1-based), for the track number tag.
                            DownloadWorker.KEY_TRACK to if (albumTitle.isNullOrBlank()) 0 else index + 1,
                            DownloadWorker.KEY_TRACK_TOTAL to candidates.size,
                        )
                    )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag(TAG)
                    .addTag(ID_TAG_PREFIX + song.id)
                    .build()
                // APPEND_OR_REPLACE: if the previous chain ended badly, start a fresh one
                // instead of inheriting its failure.
                workManager.enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                added++
            }
            if (added > 0) {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + added).apply()
                if (wifiOnly && runCatching { app.isConnectionMetered() }.getOrDefault(false)) {
                    postWaitingForWifi(app)
                }
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

    /** Cancels what is running and queued, forgets the session and removes the notification. */
    fun cancelAll(context: Context) {
        val app = context.applicationContext
        WorkManager.getInstance(app).cancelUniqueWork(QUEUE)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TOTAL).remove(KEY_PROCESSED).remove(KEY_DONE).remove(KEY_FAILED).apply()
        NotificationManagerCompat.from(app).cancel(PROGRESS_NOTIFICATION_ID)
    }

    /** Without notifications the user cannot see progress; say so once instead of looking broken. */
    private suspend fun warnIfNotificationsOff(app: Context) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_WARNED_NOTIFICATIONS, false)) return
        if (NotificationManagerCompat.from(app).areNotificationsEnabled()) return
        prefs.edit().putBoolean(KEY_WARNED_NOTIFICATIONS, true).apply()
        withContext(Dispatchers.Main) {
            SmartMessage(app.getString(R.string.download_notifications_off), PopupType.Info, durationLong = true, context = app)
        }
    }

    // ---- session bookkeeping ----

    internal fun recordResult(context: Context, title: String, ok: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = prefs.edit().putInt(KEY_PROCESSED, prefs.getInt(KEY_PROCESSED, 0) + 1)
        if (ok) edit.putInt(KEY_DONE, prefs.getInt(KEY_DONE, 0) + 1)
        else edit.putStringSet(KEY_FAILED, prefs.getStringSet(KEY_FAILED, emptySet()).orEmpty() + title.ifBlank { "?" })
        edit.apply()
    }

    /** A song that turned out to be downloaded already: it still counts as handled. */
    internal fun recordSkipped(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_PROCESSED, prefs.getInt(KEY_PROCESSED, 0) + 1).apply()
    }

    /** (position of the song being downloaded, songs in this session). */
    internal fun sessionPosition(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val total = prefs.getInt(KEY_TOTAL, 0)
        val position = (prefs.getInt(KEY_PROCESSED, 0) + 1).coerceAtMost(maxOf(total, 1))
        return position to total
    }

    /** When nothing is left in the queue, tell how it went once, and reset the counters. */
    internal fun postSummaryIfIdle(context: Context) {
        if (pendingCount(context) > 0) return
        // Only now is it safe to drop the progress notification: removing it between two songs flickers.
        NotificationManagerCompat.from(context).cancel(PROGRESS_NOTIFICATION_ID)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val done = prefs.getInt(KEY_DONE, 0)
        val failed = prefs.getStringSet(KEY_FAILED, emptySet()).orEmpty()
        prefs.edit().remove(KEY_TOTAL).remove(KEY_PROCESSED).remove(KEY_DONE).remove(KEY_FAILED).apply()
        if (done == 0 && failed.isEmpty()) return

        val title = when {
            failed.isEmpty() -> context.resources.getQuantityString(R.plurals.download_summary_done, done, done)
            done == 0 -> context.resources.getQuantityString(R.plurals.download_summary_failed, failed.size, failed.size)
            else -> context.getString(R.string.download_summary_mixed, done, failed.size)
        }
        val builder = baseNotification(context, DONE_CHANNEL_ID)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (failed.isEmpty()) {
            builder.setContentText(context.getString(R.string.download_summary_offline))
        } else {
            val shown = failed.take(10)
            builder.setContentText(shown.first())
            builder.setStyle(
                NotificationCompat.InboxStyle().also { inbox ->
                    shown.forEach { inbox.addLine(it) }
                    if (failed.size > shown.size) inbox.addLine("…")
                }
            )
        }
        notify(context, SUMMARY_NOTIFICATION_ID, builder.build())
    }

    // ---- notifications ----

    internal fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        // Default importance so the "finished" message actually shows up (the progress one stays quiet).
        manager.createNotificationChannel(
            NotificationChannel(DONE_CHANNEL_ID, context.getString(R.string.download_channel_done_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    internal fun baseNotification(context: Context, channelId: String = CHANNEL_ID): NotificationCompat.Builder {
        ensureChannels(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_yammbo)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
    }

    /** Progress notification: "Cancel" stops everything that is queued or running. */
    internal fun progressBuilder(context: Context): NotificationCompat.Builder {
        val cancel = PendingIntent.getBroadcast(
            context, 1,
            Intent(ACTION_CANCEL).setComponent(ComponentName(context, DownloadCancelReceiver::class.java)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return baseNotification(context)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.download_cancel), cancel)
    }

    /** Shown while the queue is blocked by the "only on Wi-Fi" constraint; the first song replaces it. */
    private fun postWaitingForWifi(context: Context) {
        notify(
            context, PROGRESS_NOTIFICATION_ID,
            progressBuilder(context)
                .setContentTitle(context.getString(R.string.download_waiting_wifi))
                .setProgress(0, 0, true)
                .build()
        )
    }

    internal fun notify(context: Context, id: Int, notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

/** Handles the "Cancel" button of the progress notification. */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Downloads.ACTION_CANCEL) Downloads.cancelAll(context)
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG = "song"
        const val KEY_ALBUM = "album"
        const val KEY_TRACK = "track"
        const val KEY_TRACK_TOTAL = "track_total"
        // Attempts inside one run, with these waits in between; the chain never waits on a backoff.
        private const val MAX_ATTEMPTS = 3
        private val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L)
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
        val track = inputData.getInt(KEY_TRACK, 0)
        val trackTotal = inputData.getInt(KEY_TRACK_TOTAL, 0)

        // Without being in the foreground a long queue gets cut at 10 minutes. If Android
        // refuses (app in background on 12+), the song still downloads, just without that guarantee.
        runCatching { setForeground(foregroundInfo(song.title, 0f)) }
            .onFailure { Timber.w("DownloadWorker: no foreground: ${it.message}") }

        var attempt = 0
        var outcome: SongDownloader.Outcome
        while (true) {
            var lastShown = -1
            outcome = SongDownloader.download(context, song, album, track, trackTotal) { fraction ->
                val percent = (fraction * 100).toInt()
                if (percent >= lastShown + 5) {
                    lastShown = percent
                    Downloads.notify(context, Downloads.PROGRESS_NOTIFICATION_ID, progressNotification(song.title, fraction))
                }
            }
            Timber.i("DownloadWorker: ${song.id} -> $outcome (attempt ${attempt + 1})")
            if (outcome !is SongDownloader.Outcome.Retry || ++attempt >= MAX_ATTEMPTS) break
            // delay() is cancellable, so "Cancel" stops a song that is waiting to be retried too.
            delay(RETRY_DELAYS_MS[(attempt - 1).coerceAtMost(RETRY_DELAYS_MS.size - 1)])
        }

        when (outcome) {
            is SongDownloader.Outcome.Done -> Downloads.recordResult(context, song.title, ok = true)
            SongDownloader.Outcome.AlreadyDownloaded -> Downloads.recordSkipped(context)
            is SongDownloader.Outcome.Retry, is SongDownloader.Outcome.Failed ->
                Downloads.recordResult(context, song.title, ok = false)
        }
        return finish(context)
    }

    private fun finish(context: Context): Result {
        // The progress notification is only removed when nothing is queued (no flicker between songs).
        Downloads.postSummaryIfIdle(context)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("", 0f)

    private fun progressNotification(title: String, fraction: Float): android.app.Notification {
        val context = applicationContext
        val (position, total) = Downloads.sessionPosition(context)
        val subtitle = if (total > 1) context.getString(R.string.download_progress_count, position, total)
        else context.getString(R.string.download_in_progress)
        return Downloads.progressBuilder(context)
            .setContentTitle(title.ifBlank { context.getString(R.string.download_in_progress) })
            .setContentText(subtitle)
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
