package it.fast4x.riplay.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
    accent = Color(0xFFFFFFFF),
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

inline val ColorPalette.collapsedPlayerProgressBar: Color
    get() = accent

inline val ColorPalette.favoritesIcon: Color
    get() = red

inline val ColorPalette.shimmer: Color
    get() = Color(0xff838383)

inline val ColorPalette.primaryButton: Color
    get() = background2

inline val ColorPalette.favoritesOverlay: Color
    get() = red.copy(alpha = 0.4f)

inline val ColorPalette.overlay: Color
    get() = Color.Black.copy(alpha = 0.5f)

inline val ColorPalette.onOverlay: Color
    get() = Color.White

inline val ColorPalette.onOverlayShimmer: Color
    get() = Color(0xff838383)
