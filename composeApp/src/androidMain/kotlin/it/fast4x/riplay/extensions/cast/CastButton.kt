package it.fast4x.riplay.extensions.cast

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import it.fast4x.riplay.utils.colorPalette
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

    // The in-app theme is not the system night mode, so the SDK picks its own icon colour
    // (white on white in light mode). Repaint the whole view with the theme's text colour.
    val colors = colorPalette()
    val iconTint = colors.text.toArgb()

    Box(modifier = modifier.padding(end = 6.dp)) {
        AndroidView(
            modifier = Modifier.size(34.dp),
            update = { view ->
                val paint = Paint().apply {
                    colorFilter = PorterDuffColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
                }
                view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            },
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
                    .background(colors.text)
                    .border(1.dp, colors.background0, CircleShape)
            )
        }
    }
}
