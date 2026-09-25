package it.fast4x.riplay.ui.screens.player.online.components.core

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import it.fast4x.riplay.enums.PlayerThumbnailSize
import it.fast4x.riplay.extensions.preferences.isKeepScreenOnEnabledKey
import it.fast4x.riplay.extensions.preferences.playerThumbnailSizeKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.isLandscape
import it.fast4x.riplay.utils.isVideo

/** Zoom that pushes YouTube's own title and "More videos" strips out of the clip. */
private const val OVERLAY_CROP_SCALE = 1.45f

/**
 * Page height over width in portrait. Taller than 16:9 (0.5625) so the letterbox bars, where
 * YouTube draws its strips, are tall enough to hold them once the picture fills the window.
 */
private const val PAGE_HEIGHT_RATIO = 1.25f

@Composable
fun OnlinePlayerView(
    onlinePlayerView: YouTubePlayerView? = null,
    mediaItem: MediaItem,
    actAsMini: Boolean = false,
){
    if (mediaItem.isLocal) return

    val enableKeepScreenOn by rememberPreference(isKeepScreenOnEnabledKey, false)
    val isLandscape = isLandscape
    val playerThumbnailSize by rememberPreference(
        playerThumbnailSizeKey,
        PlayerThumbnailSize.Biggest
    )

    if (mediaItem.isVideo) {
        // The page keeps the embed at 1x1 unless a video is on screen, so YouTube streams its
        // lowest picture (see the audio-only rule in ayp_youtube_player.html). With the app in
        // the background nobody sees it either, so a stopped activity counts as off screen.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(onlinePlayerView, lifecycleOwner) {
            var shown = false
            fun setShown(on: Boolean) {
                if (on == shown) return
                shown = on
                if (on) OnlineVideoOnScreen.show(onlinePlayerView) else OnlineVideoOnScreen.hide(onlinePlayerView)
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> setShown(true)
                    Lifecycle.Event.ON_STOP -> setShown(false)
                    else -> {}
                }
            }
            setShown(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                setShown(false)
            }
        }

        // YouTube draws its own title, share button and "More videos" strip over the top
        // and bottom of the player, and no player parameter removes them. The view is
        // blown up slightly inside a box that clips, so those strips fall outside it.
        //
        // Portrait: the box is the exact 16:9 window the caller sized. The page is laid out
        // at 1/1.45 of that width and taller than 16:9, so YouTube letterboxes the picture
        // and puts its strips on the bars; scaled back up, the picture fills the window edge
        // to edge and the bars with the strips land outside the clip. (Scaling a view of the
        // window's own size instead cut a third of the picture's width off.)
        val fitsWindow = !actAsMini && !isLandscape
        BoxWithConstraints(
            modifier = when {
                actAsMini -> Modifier
                fitsWindow -> Modifier.fillMaxSize().clipToBounds()
                else -> Modifier.clipToBounds()
            },
            contentAlignment = Alignment.Center
        ) {
        val pageWidth = maxWidth / OVERLAY_CROP_SCALE
        AndroidView(
            modifier = when {
                actAsMini -> Modifier
                fitsWindow -> Modifier
                    .requiredSize(pageWidth, pageWidth * PAGE_HEIGHT_RATIO)
                    .graphicsLayer {
                        scaleX = OVERLAY_CROP_SCALE
                        scaleY = OVERLAY_CROP_SCALE
                    }
                else -> Modifier.graphicsLayer {
                    // The same factor on both axes: scaling only the height stretched faces.
                    // 1.45 pushes the title strip and the share/YouTube row off the view.
                    scaleX = OVERLAY_CROP_SCALE
                    scaleY = OVERLAY_CROP_SCALE
                }
            },
            factory = { onlinePlayerView as View },
            update = {
                it.keepScreenOn = enableKeepScreenOn

                when (actAsMini) {
                    true -> {
                        it.layoutParams = ViewGroup.LayoutParams(
                            100,
                            100
                        )
                    }

                    false -> {
                        it.layoutParams = if (!isLandscape) {
                            // Sized by requiredSize above; the view just fills it.
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        } else {
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                    }
                }

            }
        )
        }
    } else {
        LocalView.current.keepScreenOn = enableKeepScreenOn
        onlinePlayerView?.keepScreenOn = enableKeepScreenOn
    }
}

/**
 * Whether a video is on screen, and telling the player page so. Counted, not a flag: the full
 * player and the mini player can overlap for a frame while one of them leaves.
 */
object OnlineVideoOnScreen {
    private var visible = 0

    fun show(view: View?) {
        visible++
        apply(view)
    }

    fun hide(view: View?) {
        visible = (visible - 1).coerceAtLeast(0)
        apply(view)
    }

    /**
     * The page starts audio only on every load, and a call made before it loads is lost, so the
     * service repeats this from onReady. Main thread only, like evaluateJavascript itself.
     */
    fun apply(view: View?) {
        runCatching { findWebView(view)?.evaluateJavascript("setAudioOnly(${visible == 0})", null) }
    }

    private fun findWebView(view: View?): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
