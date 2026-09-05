package it.fast4x.riplay.extensions.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import timber.log.Timber

/**
 * The system Cast button, which opens Google's own device picker.
 *
 * It hides itself while no Chromecast is visible on the network, exactly like every other app:
 * an always-visible button that opens an empty list is worse than no button.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!CastManager.isAvailable(context)) return

    DisposableEffect(Unit) {
        CastManager.initialize(context)
        onDispose { }
    }

    // The icon inside the button is painted by the Cast SDK, which sometimes still shows
    // the disconnected mark while a session is up. The dot is the app's own answer to
    // "am I casting", from the same flag that decides where the sound goes.
    val connected by CastManager.isConnected.collectAsStateWithLifecycle()

    Box(modifier = modifier.padding(end = 6.dp)) {
        AndroidView(
            modifier = Modifier.size(34.dp),
            factory = { ctx ->
                MediaRouteButton(ctx).apply {
                    // Always on screen. Left to itself the button disappears whenever discovery has
                    // not found a device yet, and discovery only runs while a button is attached, so
                    // it would flash once at launch and never come back.
                    runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, this) }
                        .onFailure { Timber.w("CastButton: setUp failed: ${it.message}") }
                    runCatching { setAlwaysVisible(true) }
                }
            }
        )
        if (connected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
