package it.fast4x.riplay.extensions.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import it.fast4x.riplay.extensions.ads.promo.YamboPromoBanner
import timber.log.Timber

/**
 * Banner slot rendered above the mini player.
 *
 * External ad networks (AdMob) were removed: for free users this slot always renders the Yambo
 * self-promo banner (a persistent Premium upsell), and nothing for premium users. It used to
 * rotate in only 1 of every 4 compositions, which made it appear to flicker in and out.
 */
@Composable
fun BannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (!YammboAdManager.shouldShowAds(context)) {
        Timber.d("BannerAd: premium user, hiding self-promo")
        return
    }

    YamboPromoBanner(modifier = modifier)
}
