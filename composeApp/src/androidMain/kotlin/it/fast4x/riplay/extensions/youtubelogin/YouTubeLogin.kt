package it.fast4x.riplay.extensions.youtubelogin

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import it.fast4x.environment.Environment
import it.fast4x.riplay.LocalPlayerAwareWindowInsets
import com.yambo.music.R
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.ytAccountChannelHandleKey
import it.fast4x.riplay.extensions.preferences.ytAccountEmailKey
import it.fast4x.riplay.extensions.preferences.ytAccountNameKey
import it.fast4x.riplay.extensions.preferences.ytAccountThumbnailKey
import it.fast4x.riplay.extensions.preferences.ytCookieKey
import it.fast4x.riplay.extensions.preferences.ytDataSyncIdKey
import it.fast4x.riplay.extensions.preferences.ytVisitorDataKey
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.thumbnailShape
import it.fast4x.riplay.utils.restartApp
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONTokener
import timber.log.Timber

private const val VISITOR_DATA_SCRIPT =
    "(function() { return window.yt && window.yt.config_ ? window.yt.config_.VISITOR_DATA : null; })()"
private const val DATA_SYNC_ID_SCRIPT =
    "(function() { return window.yt && window.yt.config_ ? window.yt.config_.DATASYNC_ID : null; })()"

private fun String?.fromJavascriptString(): String? {
    val value = this?.takeIf {
        it.isNotBlank() && it != "null" && it != "undefined"
    } ?: return null

    val parsedValue = runCatching {
        JSONTokener(value).nextValue() as? String
    }.getOrNull() ?: value

    return parsedValue.takeIf {
        it.isNotBlank() && it != "null" && it != "undefined"
    }
}

@Composable
fun YouTubeLogin(
    onLogin: (String) -> Unit
) {

    val scope = rememberCoroutineScope()
    var webView: WebView? = null

    var showConfirmButton by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    Box(modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize(),
            factory = { context ->
                var cookie = ""
                var dataSyncId = ""
                var visitorData = ""

                WebView(context).apply {
                    fun refreshYouTubeConfig(onComplete: ((String, String) -> Unit)? = null) {
                        var refreshedVisitorData = visitorData
                        var refreshedDataSyncId = dataSyncId
                        var pendingCallbacks = 2

                        fun completeRefresh() {
                            pendingCallbacks -= 1
                            if (pendingCallbacks == 0) {
                                onComplete?.invoke(refreshedVisitorData, refreshedDataSyncId)
                            }
                        }

                        evaluateJavascript(VISITOR_DATA_SCRIPT) { result ->
                            result.fromJavascriptString()?.let {
                                visitorData = it
                                refreshedVisitorData = it
                            }
                            completeRefresh()
                        }
                        evaluateJavascript(DATA_SYNC_ID_SCRIPT) { result ->
                            result.fromJavascriptString()?.substringBefore("||")?.takeIf { it.isNotBlank() }?.let {
                                dataSyncId = it
                                refreshedDataSyncId = it
                            }
                            completeRefresh()
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            refreshYouTubeConfig()

                            showConfirmButton = url?.startsWith("https://music.youtube.com") == true
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView,
                            url: String?,
                            isReload: Boolean
                        ) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            refreshYouTubeConfig()
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false

                        val userAgent = settings.userAgentString
                        settings.userAgentString = userAgent.replace("; wv", "")
                    }
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webView = this

                    val url = if (cookie.isNotEmpty()) {
                        "https://music.youtube.com"
                    } else {
                        "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
                    }

                    loadUrl(url)

                    confirmAction = {
                        val currentUrl = this.url
                        val freshCookie = CookieManager.getInstance().getCookie(currentUrl)

                        Timber.d("YouTubeLogin: User confirmed login.")

                        refreshYouTubeConfig { refreshedVisitorData, refreshedDataSyncId ->
                            scope.launch {
                                delay(200)

                                Timber.d("YouTubeLogin: save login preferences")
                                context.preferences.edit { putString(ytVisitorDataKey, refreshedVisitorData) }
                                context.preferences.edit { putString(ytDataSyncIdKey, refreshedDataSyncId) }
                                context.preferences.edit { putString(ytCookieKey, freshCookie) }
                                delay(200)

                                Timber.d("YouTubeLogin: Initialize Environment")
                                Timber.d("YouTubeLogin: freshCookie $freshCookie")

                                Environment.cookie = freshCookie
                                Environment.dataSyncId = refreshedDataSyncId
                                Environment.visitorData = refreshedVisitorData

                                Timber.d("YouTubeLogin: Initialized, get account info")

                                Environment.accountInfo().onSuccess {
                                    context.preferences.edit { putString(ytAccountNameKey, it?.name.orEmpty()) }
                                    context.preferences.edit { putString(ytAccountEmailKey, it?.email.orEmpty()) }
                                    context.preferences.edit { putString(ytAccountChannelHandleKey, it?.channelHandle.orEmpty()) }
                                    context.preferences.edit { putString(ytAccountThumbnailKey, it?.thumbnailUrl.orEmpty()) }
                                    delay(200)

                                    Timber.d("YouTubeLogin: Logged in as ${it?.name}, restarting app...")

                                }.onFailure {
                                    Timber.e(it, "YouTubeLogin: Authentication error")
                                }

                                webView.apply {
                                    stopLoading()
                                    clearHistory()
                                    clearCache(true)
                                    clearFormData()
                                }

                                Timber.d("YouTubeLogin: Restart app")
                                restartApp(context)

                            }
                        }
                    }
                }
            }
        )

        if (showConfirmButton && confirmAction != null) {
            Button(
                shape = thumbnailShape(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorPalette().accent,
                    contentColor = colorPalette().onAccent
                ),
                onClick = { confirmAction?.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                Text(
                    stringResource(R.string.login_select_your_preferred_account_or_profile_and_click_here_to_confirm_access),
                    fontSize = typography().l.fontSize
                )
            }
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
