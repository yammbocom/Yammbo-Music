package it.fast4x.riplay.extensions.customtabs

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.yambo.music.BuildConfig
import com.yambo.music.R

class YammboWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.yammbo_webview)

        window.statusBarColor = Color.parseColor("#FF0A0A0A")
        window.navigationBarColor = Color.parseColor("#FF0A0A0A")

        webView = findViewById(R.id.yambo_webview)
        progress = findViewById(R.id.yambo_webview_progress)

        findViewById<ImageView>(R.id.yambo_webview_back).setOnClickListener {
            handleBack()
        }

        intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let {
            findViewById<TextView>(R.id.yambo_webview_title).text = it
        }

        configureWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
        webView.loadUrl(url)
    }

    private fun configureWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString YammboMusic/${BuildConfig.VERSION_NAME}"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                return when (scheme) {
                    "http", "https" -> false
                    else -> {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }.onFailure { if (it !is ActivityNotFoundException) throw it }
                        true
                    }
                }
            }

            // Stamp the host's localStorage so the TWA migration modal knows
            // a sideloaded APK is present. `navigator.getInstalledRelatedApps()`
            // misses sideload installs; this is the bridge.
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && url.contains("music.yammbo.com")) {
                    view.evaluateJavascript(
                        "try { localStorage.setItem('yambo_apk_installed', String(Date.now())); } catch(_) {}",
                        null
                    )
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress in 1..99) {
                    progress.visibility = View.VISIBLE
                    progress.progress = newProgress
                } else {
                    progress.visibility = View.GONE
                }
            }
        }
    }

    private fun handleBack() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "yambo_url"
        const val EXTRA_TITLE = "yambo_title"
        private const val DEFAULT_URL = "https://music.yammbo.com/app-music/pricing"

        fun open(context: Context, url: String, title: String? = null) {
            val intent = Intent(context, YammboWebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                if (!title.isNullOrBlank()) putExtra(EXTRA_TITLE, title)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
