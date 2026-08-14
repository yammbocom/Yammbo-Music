package com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.pierfrancescosoffritti.androidyoutubeplayer.R
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.BooleanProvider
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayerBridge
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayerCallbacks
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.toFloat
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader


private class YouTubePlayerImpl(
  private val webView: WebView,
  private val callbacks: YouTubePlayerCallbacks
) : YouTubePlayer {
  private val mainThread: Handler = Handler(Looper.getMainLooper())

  private val lock = Any()
  @GuardedBy("lock")
  private val listeners = mutableSetOf<YouTubePlayerListener>()

  override fun loadVideo(videoId: String, startSeconds: Float) = webView.invoke("loadVideo", videoId, startSeconds)
  override fun cueVideo(videoId: String, startSeconds: Float) = webView.invoke("cueVideo", videoId, startSeconds)
  override fun play() = webView.invoke("playVideo")
  override fun pause() = webView.invoke("pauseVideo")
  override fun nextVideo() = webView.invoke("nextVideo")
  override fun previousVideo() = webView.invoke("previousVideo")
  override fun playVideoAt(index: Int) = webView.invoke("playVideoAt", index)
  override fun setLoop(loop: Boolean) = webView.invoke("setLoop", loop)
  override fun setShuffle(shuffle: Boolean) = webView.invoke("setShuffle", shuffle)
  override fun mute() = webView.invoke("mute")
  override fun unMute() = webView.invoke("unMute")
  override fun isMutedAsync(callback: BooleanProvider) {
    val requestId = callbacks.registerBooleanCallback(callback)
    webView.invoke("getMuteValue", requestId)
  }
  override fun setVolume(volumePercent: Int) {
    require(volumePercent in 0..100) { "Volume must be between 0 and 100" }
    webView.invoke("setVolume", volumePercent)
  }
  override fun seekTo(time: Float) = webView.invoke("seekTo", time)
  override fun setPlaybackRate(playbackRate: PlayerConstants.PlaybackRate) = webView.invoke("setPlaybackRate", playbackRate.toFloat())
  override fun addListener(listener: YouTubePlayerListener) = synchronized(lock) { listeners.add(listener) }
  override fun removeListener(listener: YouTubePlayerListener) = synchronized(lock) { listeners.remove(listener) }

  fun getListeners(): Collection<YouTubePlayerListener> = synchronized(lock) { listeners.toList() }

  fun release() {
    synchronized(lock) { listeners.clear() }
    mainThread.removeCallbacksAndMessages(null)
  }

  private fun WebView.invoke(function: String, vararg args: Any) {
    val stringArgs = args.map {
      if (it is String) {
        "'$it'"
      }
      else {
        it.toString()
      }
    }
    // evaluateJavascript instead of loadUrl("javascript:"): loadUrl fails
    // silently while the WebView is navigating/reloading, which dropped
    // seekTo/play/pause commands (upstream fix 87af6d429, v0.7.81).
    mainThread.post { evaluateJavascript("$function(${stringArgs.joinToString(",")})", null) }
  }
}

internal object FakeWebViewYouTubeListener : FullscreenListener {
  override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {}
  override fun onExitFullscreen() {}
}

/**
 * WebView implementation of [YouTubePlayer]. The player runs inside the WebView, using the IFrame Player API.
 */
internal class WebViewYouTubePlayer constructor(
  context: Context,
  private val listener: FullscreenListener,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr), YouTubePlayerBridge.YouTubePlayerBridgeCallbacks {

  /** Constructor used by tools */
  constructor(context: Context) : this(context, FakeWebViewYouTubeListener)

  private val youTubePlayerCallbacks = YouTubePlayerCallbacks()
  private val _youTubePlayer = YouTubePlayerImpl(this, youTubePlayerCallbacks)
  internal val youtubePlayer: YouTubePlayer get() = _youTubePlayer

  private lateinit var youTubePlayerInitListener: (YouTubePlayer) -> Unit

  internal var isBackgroundPlaybackEnabled = false

  private val youTubePlayerBridge = YouTubePlayerBridge(this)

  internal fun initialize(initListener: (YouTubePlayer) -> Unit, playerOptions: IFramePlayerOptions?, videoId: String?) {
    youTubePlayerInitListener = initListener
    initWebView(playerOptions ?: IFramePlayerOptions.getDefault(context), videoId)
  }

  override val listeners: Collection<YouTubePlayerListener> get() = _youTubePlayer.getListeners()
  override fun getInstance(): YouTubePlayer = _youTubePlayer
  override fun onYouTubeIFrameAPIReady() = youTubePlayerInitListener(_youTubePlayer)
  fun addListener(listener: YouTubePlayerListener) = _youTubePlayer.addListener(listener)
  fun removeListener(listener: YouTubePlayerListener) = _youTubePlayer.removeListener(listener)

  override fun destroy() {
    _youTubePlayer.release()
    super.destroy()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun initWebView(playerOptions: IFramePlayerOptions, videoId: String?) {
    // Nothing here is a user interface, so the WebView's own click and touch feedback
    // is only ever noise on top of the music.
    isSoundEffectsEnabled = false
    isHapticFeedbackEnabled = false

    settings.apply {
      javaScriptEnabled = true
      mediaPlaybackRequiresUserGesture = false
      // domStorageEnabled stays OFF. The YouTube embed keeps its own volume in
      // localStorage under yt-player-volume and restores it whenever the player
      // re-initialises. With no DOM storage it cannot, so playback always starts at the
      // volume this app asks for — which is the behaviour we want, since the volume is
      // driven from PlayerService and not by whatever the embed last remembered.
      // Deliberately LOAD_NO_CACHE, decided in 0cea5dd0a: the WebView cache grows without
      // bound and nothing here can evict it. LOAD_DEFAULT does save the re-download of the
      // IFrame API script on every song, and upstream runs it today — but they had already
      // backed it out once (3bba8302e) for this same reason. Do not flip it without a plan
      // for capping the cache.
      cacheMode = WebSettings.LOAD_NO_CACHE
    }

    addJavascriptInterface(youTubePlayerBridge, "YouTubePlayerBridge")
    addJavascriptInterface(youTubePlayerCallbacks, "YouTubePlayerCallbacks")

    val htmlPage = readHTMLFromUTF8File(resources.openRawResource(R.raw.ayp_youtube_player))
      .replace("<<injectedVideoId>>", if (videoId != null) { "'$videoId'" } else { "undefined" })
      .replace("<<injectedPlayerVars>>", playerOptions.toString())

    loadDataWithBaseURL(playerOptions.getOrigin(), htmlPage, "text/html", "utf-8", null)

    webChromeClient = object : WebChromeClient() {

      override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        super.onShowCustomView(view, callback)
        listener.onEnterFullscreen(view) { callback.onCustomViewHidden() }
      }

      override fun onHideCustomView() {
        super.onHideCustomView()
        listener.onExitFullscreen()
      }

      override fun getDefaultVideoPoster(): Bitmap? {
        val result = super.getDefaultVideoPoster()
        // if the video's thumbnail is not in memory, show a black screen
        return result ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
      }
    }



  }

  override fun onWindowVisibilityChanged(visibility: Int) {
    var newVisibility = visibility
    if (isBackgroundPlaybackEnabled && (visibility == View.GONE || visibility == View.INVISIBLE)) {
      newVisibility = View.VISIBLE
    }
    super.onWindowVisibilityChanged(newVisibility)
  }
}

@VisibleForTesting
internal fun readHTMLFromUTF8File(inputStream: InputStream): String {
  inputStream.use { stream ->
    BufferedReader(InputStreamReader(stream, "utf-8")).use { bufferedReader ->
      try {
        return bufferedReader.readLines().joinToString("\n")
      } catch (_: Exception) {
        throw RuntimeException("Can't parse HTML file.")
      }
    }
  }
}
