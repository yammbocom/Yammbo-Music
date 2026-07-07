package it.fast4x.riplay.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.yambo.music.R

// Strict Yammbo palette: black & white only, never colored. The widgets
// deliberately skip GlanceTheme — Material You would tint them with the
// wallpaper's colors, which breaks the brand.
internal object YammboWidgetPalette {
    val background = Color.Black
    val text = Color.White
    val textSecondary = Color(0xFFB0B0B0)
}

// Shared prev / play-pause / next row: white icons on the black card, with
// the play/pause action inside a white circle for contrast.
@Composable
internal fun WidgetPlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.play_skip_back),
            contentDescription = "previous",
            colorFilter = ColorFilter.tint(ColorProvider(YammboWidgetPalette.text)),
            modifier = GlanceModifier
                .size(26.dp)
                .clickable { onPrevious() }
        )
        Box(modifier = GlanceModifier.padding(horizontal = 14.dp)) {
            Box(
                modifier = GlanceModifier
                    .size(42.dp)
                    .cornerRadius(21.dp)
                    .background(YammboWidgetPalette.text)
                    .clickable { onPlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(
                        if (isPlaying) R.drawable.pause else R.drawable.play
                    ),
                    contentDescription = "play/pause",
                    colorFilter = ColorFilter.tint(ColorProvider(YammboWidgetPalette.background)),
                    modifier = GlanceModifier.size(22.dp)
                )
            }
        }
        Image(
            provider = ImageProvider(R.drawable.play_skip_forward),
            contentDescription = "next",
            colorFilter = ColorFilter.tint(ColorProvider(YammboWidgetPalette.text)),
            modifier = GlanceModifier
                .size(26.dp)
                .clickable { onNext() }
        )
    }
}
