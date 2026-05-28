package it.fast4x.riplay.extensions.ads.promo

import android.content.Context
import android.content.Intent
import it.fast4x.riplay.extensions.ads.YammboAdManager
import it.fast4x.riplay.extensions.customtabs.YammboCustomTabs
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

object YamboPromoManager {

    private const val PREFS_NAME = "yambo_promo_prefs"

    private const val KEY_BANNER_COUNTER = "yambo_banner_counter"
    private const val KEY_INTERSTITIAL_COUNTER = "yambo_interstitial_counter"
    private const val KEY_POPUP_LAST_TS = "yambo_popup_last_ts"
    private const val KEY_POPUP_DISMISSED_COUNT = "yambo_popup_dismissed_count"

    private const val BANNER_FREQUENCY = 4
    private const val INTERSTITIAL_FREQUENCY = 3
    private val POPUP_INTERVAL_MS = TimeUnit.DAYS.toMillis(3)
    private const val POPUP_MAX_DISMISSALS = 3

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldShowYamboBanner(context: Context): Boolean {
        if (YammboAdManager.isPremium(context)) return false
        val p = prefs(context)
        val counter = p.getInt(KEY_BANNER_COUNTER, 0) + 1
        p.edit().putInt(KEY_BANNER_COUNTER, counter).apply()
        val show = counter % BANNER_FREQUENCY == 0
        Timber.d("YamboPromo banner counter=$counter show=$show")
        return show
    }

    fun shouldShowYamboFullScreen(context: Context): Boolean {
        if (YammboAdManager.isPremium(context)) return false
        val p = prefs(context)
        val counter = p.getInt(KEY_INTERSTITIAL_COUNTER, 0) + 1
        p.edit().putInt(KEY_INTERSTITIAL_COUNTER, counter).apply()
        val show = counter % INTERSTITIAL_FREQUENCY == 0
        Timber.d("YamboPromo full-screen counter=$counter show=$show")
        return show
    }

    fun shouldShowYamboPopup(context: Context): Boolean {
        // Only show to logged-in free users — on the login screen we cannot tell
        // free from premium, and an unauthenticated user has no account to upgrade.
        if (!YammboAuthManager(context).isLoggedIn()) return false
        if (YammboAdManager.isPremium(context)) return false
        val p = prefs(context)
        val dismissed = p.getInt(KEY_POPUP_DISMISSED_COUNT, 0)
        if (dismissed >= POPUP_MAX_DISMISSALS) {
            Timber.d("YamboPromo popup suppressed: dismissed=$dismissed (max=$POPUP_MAX_DISMISSALS)")
            return false
        }
        val lastTs = p.getLong(KEY_POPUP_LAST_TS, 0L)
        val elapsed = System.currentTimeMillis() - lastTs
        val show = elapsed >= POPUP_INTERVAL_MS
        Timber.d("YamboPromo popup elapsed=${elapsed}ms interval=${POPUP_INTERVAL_MS}ms show=$show")
        return show
    }

    fun markPopupShown(context: Context) {
        prefs(context).edit().putLong(KEY_POPUP_LAST_TS, System.currentTimeMillis()).apply()
    }

    fun markPopupDismissed(context: Context) {
        val p = prefs(context)
        val count = p.getInt(KEY_POPUP_DISMISSED_COUNT, 0) + 1
        p.edit().putInt(KEY_POPUP_DISMISSED_COUNT, count).apply()
        Timber.d("YamboPromo popup dismissed total=$count")
    }

    fun markPopupConverted(context: Context) {
        prefs(context).edit().putInt(KEY_POPUP_DISMISSED_COUNT, 0).apply()
    }

    fun launchUpgradeFlow(context: Context) {
        val userId = runCatching { YammboAuthManager(context).getUserId() }.getOrDefault(-1)
        val url = if (userId > 0)
            "https://music.yammbo.com/app-music/pricing?user_id=$userId"
        else
            "https://music.yammbo.com/app-music/pricing"
        Timber.d("YamboPromo launching upgrade flow url=$url")
        YammboCustomTabs.open(context, url)
    }

    fun launchFullScreenActivity(context: Context) {
        val intent = Intent(context, YamboPromoFullScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
