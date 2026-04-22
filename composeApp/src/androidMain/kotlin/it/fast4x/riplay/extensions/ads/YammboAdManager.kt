package it.fast4x.riplay.extensions.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import timber.log.Timber

object YammboAdManager {

    // Production uses PROD ad units. Real users see real ads (no "Test Ad" label).
    // For safe debugging on your own device, add its hashed ID to TEST_DEVICE_IDS
    // below — that device alone will serve Google test ads while prod IDs remain active.
    // The hashed ID appears in logcat on first ad load:
    //   "Use RequestConfiguration.Builder.setTestDeviceIds(Arrays.asList(\"ABC...\"))"
    private const val USE_TEST_ADS = false
    private val TEST_DEVICE_IDS: List<String> = emptyList()

    private const val PROD_BANNER_AD_UNIT_ID = "ca-app-pub-1890269745181275/1516165794"
    private const val PROD_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1890269745181275/1778843493"
    private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    val BANNER_AD_UNIT_ID: String
        get() = if (USE_TEST_ADS) TEST_BANNER_AD_UNIT_ID else PROD_BANNER_AD_UNIT_ID
    val INTERSTITIAL_AD_UNIT_ID: String
        get() = if (USE_TEST_ADS) TEST_INTERSTITIAL_AD_UNIT_ID else PROD_INTERSTITIAL_AD_UNIT_ID

    private const val SONGS_BETWEEN_ADS = 4
    private const val MAX_SKIPS_PER_HOUR = 6

    private var interstitialAd: InterstitialAd? = null
    private var songsSinceLastAd = 0
    private var isInitialized = false
    var pendingInterstitial = false
        private set

    // Skip tracking
    private var skipTimestamps = mutableListOf<Long>()

    fun initialize(context: Context) {
        Timber.d("AdMob initialize called, isInitialized=$isInitialized, USE_TEST_ADS=$USE_TEST_ADS, testDevices=${TEST_DEVICE_IDS.size}")
        if (isInitialized) return

        if (TEST_DEVICE_IDS.isNotEmpty()) {
            val config = com.google.android.gms.ads.RequestConfiguration.Builder()
                .setTestDeviceIds(TEST_DEVICE_IDS)
                .build()
            MobileAds.setRequestConfiguration(config)
        }

        MobileAds.initialize(context) { initStatus ->
            Timber.d("AdMob SDK initialized, status: ${initStatus.adapterStatusMap}")
            isInitialized = true
            preloadInterstitial(context)
        }
    }

    fun isPremium(context: Context): Boolean {
        val result = YammboAuthManager(context).isSubscriptionActive()
        Timber.d("AdMob isPremium check: $result")
        return result
    }

    fun shouldShowAds(context: Context): Boolean {
        val result = !isPremium(context)
        Timber.d("AdMob shouldShowAds: $result")
        return result
    }

    // --- Interstitial ---

    fun onSongChanged(context: Context) {
        if (isPremium(context)) return

        songsSinceLastAd++
        Timber.d("AdMob songsSinceLastAd: $songsSinceLastAd")

        if (songsSinceLastAd >= SONGS_BETWEEN_ADS) {
            songsSinceLastAd = 0
            pendingInterstitial = true
        }
    }

    fun showInterstitialIfPending(activity: Activity) {
        if (!pendingInterstitial) return
        pendingInterstitial = false
        showInterstitial(activity)
    }

    private fun preloadInterstitial(context: Context) {
        if (isPremium(context)) return

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Timber.d("AdMob interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Timber.e("AdMob interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    private fun showInterstitial(activity: Activity) {
        val ad = interstitialAd ?: run {
            preloadInterstitial(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                preloadInterstitial(activity)
            }
        }

        ad.show(activity)
    }

    // --- Skip limiter ---

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
