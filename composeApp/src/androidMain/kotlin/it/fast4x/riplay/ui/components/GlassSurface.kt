package it.fast4x.riplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.riplay.utils.colorPalette

/**
 * "Liquid glass" surface.
 *
 * Compose has no backdrop blur: `Modifier.blur` blurs the composable itself, not what sits
 * behind it, and even that is Android 12+ only (this app's minSdk is 24). So the glass look
 * here is built from what works everywhere and degrades to nothing:
 *
 *  - a translucent fill, so the content underneath tints the panel,
 *  - a brighter hairline along the top edge, which is what actually reads as "glass" — light
 *    catching a bevel,
 *  - a soft drop shadow that lifts the panel off the page.
 *
 * Strictly monochrome: the tint comes from the palette's own surfaces, so it works in the
 * light and dark themes without introducing a colour.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float = 0.82f,
    elevation: Dp = 10.dp,
    borderAlpha: Float = 0.22f
): Modifier {
    val colors = colorPalette()

    // Derive the theme from the surface itself instead of taking a flag: the palette can be
    // dark, light, pure-black or a custom accent, and luminance is right in all of them.
    val isDark = colors.background1.luminance() < 0.5f

    // The sheen is *light* in both themes — it stands for a highlight catching the bevel.
    // Using colors.text here was wrong: in the light theme text is black, so the "highlight"
    // darkened the top edge and every panel looked dirty.
    //
    // It also has to stay subtle on light surfaces: at 0.60 it read as a white block inside
    // the panel rather than a highlight, which is what made the nav bar look two-tone.
    val sheen = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.18f)

    // The border, on the other hand, has to invert: a white hairline is invisible on a light
    // surface, so there it becomes a soft dark edge instead.
    val edge = if (isDark) Color.White else Color.Black
    val edgeAlpha = if (isDark) borderAlpha else borderAlpha * 0.45f

    // Elevation shadows are near-invisible on dark backgrounds but ring a light one with a
    // pale halo, which is what haloed every circular button in the light theme.
    val effectiveElevation = if (isDark) elevation else elevation * 0.3f

    // Translucency is the point of the effect, but it also means the panel reproduces
    // whatever sits behind it — and in the light theme the jump between "content behind"
    // and "empty background behind" reads as a bright band across the bar. Dark themes hide
    // it because everything behind is near-black, so only the light one needs the opacity.
    val effectiveAlpha = if (isDark) alpha else (alpha + 0.14f).coerceAtMost(0.96f)

    return this
        .shadow(elevation = effectiveElevation, shape = shape, clip = false)
        .clip(shape)
        .background(colors.background1.copy(alpha = effectiveAlpha))
        .background(
            Brush.verticalGradient(
                colors = listOf(sheen, Color.Transparent)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    edge.copy(alpha = edgeAlpha),
                    edge.copy(alpha = edgeAlpha * 0.25f)
                )
            ),
            shape = shape
        )
}

/** Container flavour of [glassSurface] for when a Box is needed anyway. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float = 0.82f,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.glassSurface(shape = shape, alpha = alpha, elevation = elevation),
        content = content
    )
}
