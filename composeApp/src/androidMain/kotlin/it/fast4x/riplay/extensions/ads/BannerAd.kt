package it.fast4x.riplay.extensions.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import timber.log.Timber

@Composable
fun BannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Timber.d("BannerAd composable entered")

    if (!YammboAdManager.shouldShowAds(context)) {
        Timber.d("BannerAd: shouldShowAds=false, skipping ad")
        return
    }

    Timber.d("BannerAd: shouldShowAds=true, loading ad with unitId=${YammboAdManager.BANNER_AD_UNIT_ID}")

    var adLoaded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (adLoaded) 50.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                Timber.d("BannerAd: creating AdView")
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = YammboAdManager.BANNER_AD_UNIT_ID
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Timber.d("AdMob banner loaded successfully!")
                            adLoaded = true
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Timber.e("AdMob banner failed: code=${error.code}, message=${error.message}, domain=${error.domain}")
                            adLoaded = false
                        }
                        override fun onAdImpression() {
                            Timber.d("AdMob banner impression recorded")
                        }
                        override fun onAdClicked() {
                            Timber.d("AdMob banner clicked")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                    Timber.d("BannerAd: loadAd() called")
                }
            }
        )
    }
}
