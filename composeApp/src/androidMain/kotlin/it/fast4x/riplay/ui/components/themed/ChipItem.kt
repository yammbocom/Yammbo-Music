package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.environment.Environment
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlin.random.Random

@Composable
fun ChipItemColored(
    chip: Environment.Chip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Yammbo Noir: an outlined tile. No coloured stripe, the brand is B/N.
    NoirTile(
        text = chip.title,
        onClick = onClick,
        modifier = Modifier
            .padding(4.dp)
            .width(156.dp)
            .fillMaxHeight()
    )
}

@Composable
fun ChipGridItemColored(
    chip: Environment.Chip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSizeDp: Dp
) {
    NoirTile(
        text = chip.title,
        onClick = onClick,
        modifier = modifier
            .size(thumbnailSizeDp, thumbnailSizeDp)
            .padding(5.dp)
    )
}

