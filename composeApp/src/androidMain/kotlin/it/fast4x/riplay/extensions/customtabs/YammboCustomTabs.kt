package it.fast4x.riplay.extensions.customtabs

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens a URL in a Chrome Custom Tab styled to match the Yammbo Music dark
 * theme. Hides the share button, hides the page title, and lets the URL bar
 * collapse on scroll so the Yammbo-branded page fills the screen.
 *
 * Chrome does not allow hiding the URL bar entirely (security decision) —
 * `setUrlBarHidingEnabled(true)` is the closest thing: the bar disappears
 * while scrolling and only reappears when the user scrolls back to the top.
 */
object YammboCustomTabs {

    private const val TOOLBAR_COLOR = 0xFF0A0A0A.toInt() // matches billing/pricing blade --bg

    fun open(context: Context, url: String) {
        val colors = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(TOOLBAR_COLOR)
            .setNavigationBarColor(TOOLBAR_COLOR)
            .setSecondaryToolbarColor(TOOLBAR_COLOR)
            .build()

        val intent = CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setDefaultColorSchemeParams(colors)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, colors)
            .build()

        intent.launchUrl(context, Uri.parse(url))
    }
}
