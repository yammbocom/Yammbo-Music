package it.fast4x.riplay.extensions.customtabs

import android.content.Context

/**
 * Opens Yammbo URLs (billing, pricing, account) inside an in-app WebView
 * styled to match the Yammbo Music dark theme — no URL bar, no browser chrome,
 * just a back button and the "Yammbo Music" title.
 *
 * Was previously a Chrome Custom Tab wrapper; reimplemented as a thin shim
 * around [YammboWebViewActivity] so the existing call sites
 * (MyAccountTab, SubscriptionGateBanner, PremiumFeature) keep working
 * without modification.
 */
object YammboCustomTabs {

    fun open(context: Context, url: String) {
        YammboWebViewActivity.open(context, url)
    }

    fun open(context: Context, url: String, title: String?) {
        YammboWebViewActivity.open(context, url, title)
    }
}
