package it.fast4x.riplay.ui.styling

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import it.fast4x.riplay.enums.ColorPaletteMode
import it.fast4x.riplay.enums.ColorPaletteName


@Immutable
data class ColorPalette(
    val background0: Color,
    val background1: Color,
    val background2: Color,
    val background3: Color,
    val background4: Color,
    val accent: Color,
    val onAccent: Color,
    val red: Color = Color(0xffbf4040),
    val blue: Color = Color(0xff4472cf),
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val isDark: Boolean,
    val iconButtonPlayer: Color,
) {
    companion object : Saver<ColorPalette, List<Any>> {
        override fun restore(value: List<Any>) = when (value[0] as Int) {
            0 -> DefaultDarkColorPalette
            1 -> DefaultLightColorPalette
            else -> if (value[1] as Boolean) DefaultDarkColorPalette else DefaultLightColorPalette
        }

        override fun SaverScope.save(value: ColorPalette) =
            listOf(
                when {
                    value === DefaultDarkColorPalette -> 0
                    value === DefaultLightColorPalette -> 1
                    else -> value.accent.toArgb()
                },
                value.isDark
            )
    }
}



val DefaultDarkColorPalette = ColorPalette(
    background0 = Color(0xff121212),
    background1 = Color(0xff1E1E1E),
    background2 = Color(0xff2A2A2A),
    background3 = Color(0xff3D3D3D),
    background4 = Color(0xff333333),
    text = Color(0xffFFFFFF),
    textSecondary = Color(0xffB0B0B0),
    textDisabled = Color(0xff6f6f73),
    iconButtonPlayer = Color(0xffFFFFFF),
    accent = Color(0xffFFFFFF),
    onAccent = Color(0xff121212),
    isDark = true
)

val DefaultLightColorPalette = ColorPalette(
    background0 = Color(0xffFFFFFF),
    background1 = Color(0xffF5F5F5),
    background2 = Color(0xffE8E8E8),
    background3 = Color(0xffD6D6D6),
    background4 = Color(0xffD6D6D6),
    text = Color(0xff000000),
    textSecondary = Color(0xff555555),
    textDisabled = Color(0xff9d9d9d),
    iconButtonPlayer = Color(0xff000000),
    accent = Color(0xff000000),
    onAccent = Color(0xffFFFFFF),
    isDark = false
)

val PureBlackColorPalette = DefaultDarkColorPalette.copy(
    background0 = Color.Black,
    background1 = Color.Black,
    background2 = Color.Black,
    accent = Color.White,
    onAccent = Color.DarkGray
    )

val ModernBlackColorPalette = DefaultDarkColorPalette.copy(
    background0 = Color.Black,
    background1 = Color.Black,
    //background2 = DefaultDarkColorPalette.background2, // Color.Black,
    background2 = Color.Black,
    background3 = DefaultDarkColorPalette.accent
)

fun colorPaletteOf(
    colorPaletteName: ColorPaletteName,
    colorPaletteMode: ColorPaletteMode,
    isSystemInDarkMode: Boolean
): ColorPalette {
    return when (colorPaletteMode) {
        ColorPaletteMode.Light -> DefaultLightColorPalette
        ColorPaletteMode.Dark, ColorPaletteMode.PitchBlack -> DefaultDarkColorPalette
        ColorPaletteMode.System -> if (isSystemInDarkMode) DefaultDarkColorPalette else DefaultLightColorPalette
    }
}

fun dynamicColorPaletteOf(bitmap: Bitmap, isDark: Boolean): ColorPalette? {
    val palette = Palette
        .from(bitmap)
        .maximumColorCount(8)
        //.addFilter(if (isDark) ({ _, hsl -> hsl[0] !in 36f..100f }) else null)
        .generate()



    val hsl = if (isDark) {
        palette.dominantSwatch ?: Palette
            .from(bitmap)
            .maximumColorCount(8)
            .generate()
            .dominantSwatch
    } else {
        palette.dominantSwatch
    }?.hsl ?: return null

    return if (hsl[1] < 0.08) {
        val newHsl = palette.swatches
            .map(Palette.Swatch::getHsl)
            .sortedByDescending(FloatArray::component2)
            .find { it[1] != 0f }
            ?: hsl
        dynamicColorPaletteOf(newHsl, isDark)

    } else {
        dynamicColorPaletteOf(hsl, isDark)
    }
}

fun dynamicColorPaletteOf(hsl: FloatArray, isDark: Boolean): ColorPalette {
    return colorPaletteOf(ColorPaletteName.Dynamic, if (isDark) ColorPaletteMode.Dark else ColorPaletteMode.Light, false).copy(

        background0 = Color.hsl(hsl[0], hsl[1].coerceAtMost(0.1f), if (isDark) 0.10f else 0.925f),
        background1 = Color.hsl(hsl[0], hsl[1].coerceAtMost(0.3f), if (isDark) 0.15f else 0.90f),
        background2 = Color.hsl(hsl[0], hsl[1].coerceAtMost(0.4f), if (isDark) 0.2f else 0.85f),

        accent = Color.hsl(hsl[0], hsl[1].coerceAtMost(0.5f), 0.5f),

        text = Color.hsl(hsl[0], hsl[1].coerceAtMost(0.02f), if (isDark) 0.88f else 0.12f),
        textSecondary = Color.hsl(hsl[0], hsl[1].coerceAtMost(0.1f), if (isDark) 0.65f else 0.40f),
        textDisabled = Color.hsl(hsl[0], hsl[1].coerceAtMost(0.2f), if (isDark) 0.40f else 0.65f),

    )
}


fun dynamicColorPaletteOf(hsl: Hsl, isDark: Boolean) = hsl.let { (hue, saturation) ->
    val accentColor = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(if (isDark) 0.4f else 0.5f),
        lightness = 0.5f
    )

    colorPaletteOf(
        ColorPaletteName.Dynamic,
        if (isDark) ColorPaletteMode.Dark else ColorPaletteMode.Light,
        isDark
    ).copy(
        background0 = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.1f),
            lightness = if (isDark) 0.10f else 0.925f
        ),
        background1 = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.3f),
            lightness = if (isDark) 0.15f else 0.90f
        ),
        background2 = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.4f),
            lightness = if (isDark) 0.2f else 0.85f
        ),
        accent = accentColor,
        text = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.02f),
            lightness = if (isDark) 0.88f else 0.12f
        ),
        textSecondary = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.1f),
            lightness = if (isDark) 0.65f else 0.40f
        ),
        textDisabled = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.2f),
            lightness = if (isDark) 0.40f else 0.65f
        )
    )
}

fun dynamicColorPaletteOf(
    accentColor: Color,
    isDark: Boolean
) = dynamicColorPaletteOf(
    hsl = accentColor.hsl,
    isDark = isDark
)

inline val ColorPalette.collapsedPlayerProgressBar: Color
    get() = accent



inline val ColorPalette.favoritesIcon: Color
    get() = accent

inline val ColorPalette.shimmer: Color
    get() = Color(0xff838383)

inline val ColorPalette.primaryButton: Color
    get() = background2


inline val ColorPalette.favoritesOverlay: Color
    get() = accent.copy(alpha = 0.4f)

inline val ColorPalette.overlay: Color
    get() = Color.Black.copy(alpha = 0.5f)

inline val ColorPalette.onOverlay: Color
    get() = Color.White

inline val ColorPalette.onOverlayShimmer: Color
    get() = Color(0xff838383)

inline val ColorPalette.applyPitchBlack: ColorPalette
    get() = this.copy(
        isDark = true,
        background0 = Color.Black,
        background1 = Color.Black,
        background2 = Color.Black,
        background3 = Color.Black,
        background4 = Color.Black,
        text = Color.White,
    )

inline val ColorPalette.applyTransparency: ColorPalette
    get() = this.copy(
        isDark = false,
        background0 = Color.Transparent,
        background1 = Color.Transparent,
        background2 = Color.Transparent,
        background3 = Color.Transparent,
        background4 = Color.Transparent,
        text = Color.White,
    )