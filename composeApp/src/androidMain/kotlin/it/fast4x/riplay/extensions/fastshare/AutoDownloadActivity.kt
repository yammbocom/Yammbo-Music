package it.fast4x.riplay.extensions.fastshare

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.yambo.music.R
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.ads.PremiumFeature
import it.fast4x.riplay.extensions.ads.PremiumGuard
import it.fast4x.riplay.extensions.scheduled.workers.AutoDownloadFavoritesWorker
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.isConnectionMetered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private val CardBlack = Color(0xFF0A0A0A)

/**
 * Sends pending favorites to YTDLnis, one link at a time, while this small card is on screen.
 *
 * It exists because of two Android rules: an app in the background cannot open another app's
 * activity, and YTDLnis only takes links through an activity. So the sending happens from here,
 * opened by the user (settings button) or by a notification tap, both of which are allowed.
 * Each link opens YTDLnis' transparent activity, which queues the download and closes at once;
 * this activity getting resumed again is the sign it is gone and the next link can follow.
 */
class AutoDownloadActivity : ComponentActivity() {

    companion object {
        // One run at a time, whether it came from the notification or from settings. Not a
        // plain flag: a run left behind by Home waits forever for a resume that never comes, and
        // a flag would block every later run until the process died. So the owner records when
        // it last made progress, and a run silent for longer than STALE_RUN_MS can be taken over.
        private val runLock = Any()
        private var runOwner = 0L
        private var lastProgressAt = 0L
        private var lastToken = 0L
        private const val STALE_RUN_MS = 60_000L
        // A short pause after YTDLnis hands control back, so its Toast and window transition
        // finish before the next one starts.
        private const val SETTLE_AFTER_RETURN_MS = 700L
        // If we are not back on screen this long after opening YTDLnis, the user has left
        // (Home, another app) and nothing more can be sent: the run stops instead of waiting.
        private const val RETURN_TIMEOUT_MS = 20_000L

        /** The token of the new run, or 0 while another run is still making progress. */
        private fun claimRun(): Long {
            synchronized(runLock) {
                val now = SystemClock.elapsedRealtime()
                if (runOwner != 0L && now - lastProgressAt < STALE_RUN_MS) return 0L
                if (runOwner != 0L) Timber.w("AutoDownloadActivity taking over a run stalled for ${now - lastProgressAt} ms")
                runOwner = ++lastToken
                lastProgressAt = now
                return runOwner
            }
        }

        private fun isRunOwner(token: Long): Boolean = synchronized(runLock) { token != 0L && runOwner == token }

        private fun reportProgress(token: Long) = synchronized(runLock) {
            if (token != 0L && runOwner == token) lastProgressAt = SystemClock.elapsedRealtime()
        }

        private fun releaseRun(token: Long) = synchronized(runLock) {
            if (token != 0L && runOwner == token) runOwner = 0L
        }
    }

    private var runToken = 0L
    private var runJob: Job? = null
    private var cancelled = false
    private var stoppedByMeteredNetwork = false
    private var sentCount = 0

    private var current by mutableIntStateOf(0)
    private var total by mutableIntStateOf(0)

    private val onScreen = MutableStateFlow(false)
    private val resumeCount = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runToken = claimRun()
        if (runToken == 0L) {
            finish()
            return
        }
        // Whatever brought us here, the reminder has done its job.
        runCatching {
            NotificationManagerCompat.from(this).cancel(AutoDownloadFavoritesWorker.NOTIFICATION_ID)
        }

        setContent {
            AutoDownloadCard(
                current = current,
                total = total,
                onCancel = {
                    cancelled = true
                    runJob?.cancel()
                    finishRun()
                }
            )
        }

        runJob = lifecycleScope.launch { sendPending() }
    }

    override fun onResume() {
        super.onResume()
        onScreen.value = true
        resumeCount.value = resumeCount.value + 1
    }

    override fun onPause() {
        super.onPause()
        onScreen.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseRun(runToken)
    }

    private suspend fun sendPending() {
        if (!isYtdlnisInstalled(this)) {
            SmartMessage(getString(R.string.ytdlnis_not_installed), PopupType.Warning, context = applicationContext)
            finish()
            return
        }
        // The same gate, and the same message, as the download button.
        // Application context: this activity is about to finish and the message must outlive it.
        if (!PremiumGuard.checkFeature(applicationContext, PremiumFeature.Download)) {
            finish()
            return
        }
        if (isConnectionMetered()) {
            SmartMessage(getString(R.string.auto_download_needs_wifi), PopupType.Warning, context = applicationContext)
            finish()
            return
        }

        val pending = withContext(Dispatchers.IO) { pendingFavoritesToSend(applicationContext) }
            .take(AUTO_DOWNLOAD_MAX_PER_RUN)
        if (pending.isEmpty()) {
            SmartMessage(getString(R.string.auto_download_nothing_pending), PopupType.Info, context = applicationContext)
            finish()
            return
        }
        total = pending.size

        for ((index, song) in pending.withIndex()) {
            // Only an activity that is on screen may open another app's.
            onScreen.first { it }
            if (cancelled) return
            // A later launch took over while this one sat stopped; that run sends from here on.
            if (!isRunOwner(runToken)) {
                finish()
                return
            }
            // The user asked for Wi-Fi only; a switch to mobile data mid-run stops it.
            if (isConnectionMetered()) {
                stoppedByMeteredNetwork = true
                break
            }

            current = index + 1
            val resumesBefore = resumeCount.value
            if (!sendToYtdlnisInBackground(this, song.id)) {
                Timber.w("AutoDownloadActivity could not reach YTDLnis, stopping at ${song.id}")
                break
            }
            markSentToYtdlnis(applicationContext, song.id)
            sentCount++
            reportProgress(runToken)
            Timber.d("AutoDownloadActivity sent ${song.id} to YTDLnis ($current of $total)")

            val returned = withTimeoutOrNull(RETURN_TIMEOUT_MS) { resumeCount.first { it > resumesBefore } }
            if (returned == null) {
                // What was sent is already recorded; finishRun() reports it.
                Timber.w("AutoDownloadActivity not resumed $RETURN_TIMEOUT_MS ms after ${song.id}, stopping")
                break
            }
            reportProgress(runToken)
            delay(SETTLE_AFTER_RETURN_MS)
        }

        finishRun()
    }

    private fun finishRun() {
        if (isFinishing) return
        when {
            stoppedByMeteredNetwork -> SmartMessage(
                getString(R.string.auto_download_stopped_metered),
                PopupType.Warning,
                context = applicationContext
            )
            sentCount > 0 -> SmartMessage(
                resources.getQuantityString(R.plurals.auto_download_done, sentCount, sentCount),
                PopupType.Success,
                context = applicationContext
            )
        }
        finish()
    }
}

@Composable
private fun AutoDownloadCard(
    current: Int,
    total: Int,
    onCancel: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBlack)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = if (total > 0) stringResource(R.string.auto_download_progress, current, total)
                else stringResource(R.string.auto_download_preparing),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}
