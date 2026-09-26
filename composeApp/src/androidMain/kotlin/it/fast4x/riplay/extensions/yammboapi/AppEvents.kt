package it.fast4x.riplay.extensions.yammboapi

import com.yambo.music.BuildConfig
import it.fast4x.riplay.utils.appContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Feature-use analytics for the admin stats page: which features people actually use, the
 * searches that found nothing and the songs that would not play.
 *
 * Events are held for a few seconds and sent together, since they come in bursts (a share
 * right after a video, a settings snapshot at launch). A batch that fails for lack of network
 * stays in memory for the next one; nothing is written to disk, so losing a few events when
 * the process dies offline is accepted.
 */
object AppEvents {
    const val VIDEO_PLAY = "video_play"
    const val CAST_START = "cast_start"
    const val SHARE_SONG = "share_song"
    const val SHARE_LYRICS = "share_lyrics"
    const val DOWNLOAD_SEND = "download_send"
    const val AUTO_DOWNLOAD = "auto_download"
    const val SETTINGS = "settings"
    const val SEARCH_EMPTY = "search_empty"
    const val PLAYBACK_ERROR = "playback_error"
    const val UPDATE_INSTALL = "update_install"

    private const val SEND_DELAY_MS = 5_000L
    private const val MAX_BATCH = 50
    private const val MAX_PENDING = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayList<AppEventItem>()
    private var sendJob: Job? = null

    fun log(name: String, detail: String? = null, videoId: String? = null) {
        val item = AppEventItem(
            name = name,
            detail = detail?.trim()?.take(190)?.ifEmpty { null },
            videoId = videoId?.take(24),
            at = System.currentTimeMillis(),
        )
        synchronized(pending) {
            if (pending.size >= MAX_PENDING) pending.removeAt(0)
            pending.add(item)
            if (sendJob?.isActive == true) return
            sendJob = scope.launch {
                delay(SEND_DELAY_MS)
                flush()
            }
        }
    }

    /** Send one finished listen, on a scope that outlives the player service. */
    fun reportPlay(request: PlayReportRequest) {
        scope.launch {
            val token = runCatching { YammboAuthManager(appContext()).getAccessToken() }.getOrNull()
            YammboApiService.reportPlay(request, token)
        }
    }

    private suspend fun flush() {
        while (true) {
            val batch = synchronized(pending) { pending.take(MAX_BATCH) }
            if (batch.isEmpty()) return
            val token = runCatching { YammboAuthManager(appContext()).getAccessToken() }.getOrNull()
            val done = YammboApiService.reportEvents(
                AppEventsRequest(events = batch, appVersion = BuildConfig.VERSION_NAME),
                token,
            )
            if (!done) {
                // Keep them for the next event to carry along; retrying here would only spin
                // while the phone is offline.
                Timber.d("AppEvents flush postponed, ${batch.size} pending")
                return
            }
            synchronized(pending) { pending.removeAll(batch.toSet()) }
        }
    }
}
