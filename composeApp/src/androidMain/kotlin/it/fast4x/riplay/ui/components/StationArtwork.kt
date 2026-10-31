package it.fast4x.riplay.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.yambo.music.R
import it.fast4x.riplay.utils.colorPalette
import kotlin.math.max
import kotlin.math.min

// A logo smaller than this share of the square it has to fill is drawn at its own size instead.
private const val SmallLogoShare = 0.7f

// A small logo is shown at most this many times its natural size, so it stays crisp.
private const val MaxLogoUpscale = 2f

// The logo never touches the edges of the cover when it is drawn on its own.
private const val LogoFillShare = 0.8f

/**
 * Cover for a radio station. Station logos from the directory are often tiny favicons (64 px);
 * stretched over a ~330 dp cover they are a blur. This loads the logo at its natural size, and
 * when it is much smaller than the area it has to fill draws it crisp (at most 2x its size,
 * centred) over a backdrop made of the same logo heavily blurred. Large logos fill the area
 * normally. The shape of the cover is kept either way.
 *
 * [model] is the logo URL (or anything coil accepts). [modifier] carries the size, and any clip
 * the caller already applies; [shape] is applied on top.
 */
@Composable
fun StationArtwork(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = RectangleShape,
) {
    val context = LocalContext.current
    val colors = colorPalette()

    // Natural size in pixels of the decoded logo; null until it is known.
    var natural by remember(model) { mutableStateOf<Size?>(null) }
    var failed by remember(model) { mutableStateOf(false) }

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(model)
            // Never upscaled by coil (automatic precision): a favicon stays a favicon here.
            .size(1200, 1200)
            .build(),
        filterQuality = FilterQuality.High,
        onSuccess = { state: AsyncImagePainter.State.Success -> natural = state.painter.intrinsicSize },
        onError = { failed = true }
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(colors.background2, colors.background1.copy(alpha = 1f))
                )
            )
    ) {
        val density = LocalDensity.current
        val widthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f
        val heightPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 0f
        val targetPx = when {
            widthPx > 0f && heightPx > 0f -> min(widthPx, heightPx)
            else -> max(widthPx, heightPx)
        }

        val size = natural
        val isSmall = size != null &&
            size.isSpecified && size.width > 0f && size.height > 0f &&
            targetPx > 0f &&
            min(size.width, size.height) < SmallLogoShare * targetPx

        when {
            failed -> Image(
                painter = painterResource(R.drawable.musical_notes),
                contentDescription = contentDescription,
                colorFilter = ColorFilter.tint(colors.textDisabled),
                modifier = Modifier.size(48.dp)
            )

            size == null -> Unit // Nothing until the size is known: no blurry first frame.

            !isSmall -> Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            else -> {
                val logo = size ?: return@BoxWithConstraints
                // Backdrop: the same logo, scaled to fill and blurred. Below API 31 there is no
                // blur, so the plain gradient behind stays and a dark veil keeps the logo legible.
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val radius = 36.dp.toPx()
                                renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                                // Grow past the edges so the blur has nothing transparent to pull in.
                                scaleX = 1.25f
                                scaleY = 1.25f
                            } else {
                                alpha = 0f
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )

                // Crisp logo: at most 2x its natural size and never closer than 10 % to the edges.
                val scale = min(
                    MaxLogoUpscale,
                    LogoFillShare * targetPx / max(logo.width, logo.height)
                )
                val logoWidth = with(density) { (logo.width * scale).toDp() }
                val logoHeight = with(density) { (logo.height * scale).toDp() }
                Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(logoWidth, logoHeight)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}
