package it.fast4x.riplay.extensions.ads

import android.app.Activity
import android.content.Context
import it.fast4x.riplay.extensions.ads.promo.YamboPromoManager
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import timber.log.Timber

/**
 * Monetization gate for Yambo Music.
 *
 * External ad networks (AdMob) were removed: the app monetizes exclusively through its
 * own self-promotion (see [YamboPromoManager]), which drives free users to the Premium
 * subscription. This object now only tracks premium state, the free-tier skip limiter,
 * and the "show a full-screen promo every N songs" cadence.
 */
object YammboAdManager {

    private const val SONGS_BETWEEN_ADS = 4
    private const val MAX_SKIPS_PER_HOUR = 6

    private var songsSinceLastAd = 0
    var pendingInterstitial = false
        private set

    // Skip tracking
    private val skipTimestamps = mutableListOf<Long>()

    fun isPremium(context: Context): Boolean {
        val result = YammboAuthManager(context).isSubscriptionActive()
        Timber.d("Monetization isPremium check: $result")
        return result
    }

    fun shouldShowAds(context: Context): Boolean {
        val result = !isPremium(context)
        Timber.d("Monetization shouldShowAds: $result")
        return result
    }

    // --- Full-screen self-promo cadence (replaces the old AdMob interstitial) ---

    fun onSongChanged(context: Context) {
        if (isPremium(context)) return

        songsSinceLastAd++
        Timber.d("Monetization songsSinceLastAd: $songsSinceLastAd")

        if (songsSinceLastAd >= SONGS_BETWEEN_ADS) {
            songsSinceLastAd = 0
            pendingInterstitial = true
        }
    }

    fun showInterstitialIfPending(activity: Activity) {
        if (!pendingInterstitial) return
        pendingInterstitial = false

        if (YamboPromoManager.shouldShowYamboFullScreen(activity)) {
            Timber.d("Monetization: showing Yambo full-screen self-promo")
            YamboPromoManager.launchFullScreenActivity(activity)
        }
    }

    // --- Skip limiter (free tier) ---

    fun canSkip(context: Context): Boolean {
        if (isPremium(context)) return true

        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600_000

        // Remove timestamps older than 1 hour
        skipTimestamps.removeAll { it < oneHourAgo }

        return skipTimestamps.size < MAX_SKIPS_PER_HOUR
    }

    fun recordSkip() {
        skipTimestamps.add(System.currentTimeMillis())
    }

    fun remainingSkips(context: Context): Int {
        if (isPremium(context)) return Int.MAX_VALUE

        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600_000
        skipTimestamps.removeAll { it < oneHourAgo }

        return (MAX_SKIPS_PER_HOUR - skipTimestamps.size).coerceAtLeast(0)
    }
}
