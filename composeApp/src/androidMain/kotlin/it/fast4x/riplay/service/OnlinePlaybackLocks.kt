package it.fast4x.riplay.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import timber.log.Timber

/**
 * CPU and Wi-Fi locks for online playback.
 *
 * Online songs sound inside a WebView, not in ExoPlayer, so ExoPlayer's own wake mode never covers
 * them: with the screen off (phone in the car, in a pocket) the CPU could sleep between two songs
 * and the next one never started, and Wi-Fi could drop to power save mid-song.
 *
 * Held while online playback is wanted, released [RELEASE_GRACE_MS] after it stops being wanted
 * (so the gap between two songs never lets go), and dropped by a watchdog when nothing actually
 * played for a while, in case some path never reported the stop. Main thread only.
 */
class OnlinePlaybackLocks(
    context: Context,
    private val isWanted: () -> Boolean,
    private val isPlaying: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())

    private val wakeLock: PowerManager.WakeLock? = runCatching {
        (context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YammboMusic:OnlinePlayback")
            .apply { setReferenceCounted(false) }
    }.onFailure { Timber.e("OnlinePlaybackLocks wake lock unavailable ${it.message}") }.getOrNull()

    @Suppress("DEPRECATION") // what ExoPlayer's own WifiLockManager uses too
    private val wifiLock: WifiManager.WifiLock? = runCatching {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "YammboMusic:OnlinePlayback")
            .apply { setReferenceCounted(false) }
    }.onFailure { Timber.e("OnlinePlaybackLocks wifi lock unavailable ${it.message}") }.getOrNull()

    private var held = false
    private var releasePending = false
    private var idleChecks = 0

    private val delayedRelease = Runnable {
        releasePending = false
        if (!wanted()) release()
    }

    private val watchdog = object : Runnable {
        override fun run() {
            if (!held) return
            if (playing()) idleChecks = 0 else idleChecks++
            if (!wanted() || idleChecks >= MAX_IDLE_CHECKS) {
                Timber.d("OnlinePlaybackLocks watchdog releasing (idleChecks $idleChecks)")
                release()
                return
            }
            // Renews the timeout: the wake lock never outlives a dead service by more than that
            runCatching { wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS) }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun wanted() = runCatching(isWanted).getOrDefault(false)
    private fun playing() = runCatching(isPlaying).getOrDefault(false)

    /** Cheap: call it on every playback state change. */
    fun refresh() {
        if (wanted()) {
            if (releasePending) {
                handler.removeCallbacks(delayedRelease)
                releasePending = false
            }
            if (!held) acquire()
        } else if (held && !releasePending) {
            releasePending = true
            handler.postDelayed(delayedRelease, RELEASE_GRACE_MS)
        }
    }

    private fun acquire() {
        held = true
        idleChecks = 0
        runCatching { wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS) }
            .onFailure { Timber.e("OnlinePlaybackLocks wake lock acquire failed ${it.message}") }
        runCatching { wifiLock?.takeUnless { it.isHeld }?.acquire() }
            .onFailure { Timber.e("OnlinePlaybackLocks wifi lock acquire failed ${it.message}") }
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        Timber.d("OnlinePlaybackLocks acquired")
    }

    fun release() {
        handler.removeCallbacks(watchdog)
        handler.removeCallbacks(delayedRelease)
        releasePending = false
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        if (held) Timber.d("OnlinePlaybackLocks released")
        held = false
    }

    private companion object {
        const val RELEASE_GRACE_MS = 20_000L
        const val WATCHDOG_INTERVAL_MS = 3 * 60_000L
        const val WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L
        // Two checks in a row without sound (3 to 6 minutes) and the locks go
        const val MAX_IDLE_CHECKS = 2
    }
}
