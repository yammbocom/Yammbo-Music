package it.fast4x.riplay.ui.screens.podcast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.screens.liveradio.LiveRadio
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

private const val SEGMENT_RADIO = 0
private const val SEGMENT_PODCASTS = 1

/**
 * Content of the Radio bottom tab: a two-segment switch ("Radio" | "Podcasts") over either the
 * existing live radio screen or the podcast discovery screen.
 *
 * @param onSeeAllMyPodcasts optional; when set, "Ver todo" in "Mis podcasts" calls it (for
 * example to jump to the library tab) instead of expanding the row in place.
 */
@OptIn(UnstableApi::class)
@Composable
fun RadioPodcastsTab(
    navController: NavController,
    onSeeAllMyPodcasts: (() -> Unit)? = null,
) {
    // Home tabs are destroyed when switching, so the segment must survive via saved state
    var segment by rememberSaveable { mutableIntStateOf(SEGMENT_RADIO) }

    Column(modifier = Modifier.fillMaxSize()) {
        PodcastSegmentedControl(
            labels = listOf(
                stringResource(R.string.podcasts_tab_radio),
                stringResource(R.string.podcasts_tab_podcasts)
            ),
            selected = segment,
            onSelect = { segment = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            when (segment) {
                SEGMENT_PODCASTS -> PodcastsDiscover(
                    navController = navController,
                    onSeeAllMyPodcasts = onSeeAllMyPodcasts
                )
                else -> LiveRadio()
            }
        }
    }
}

@Composable
private fun PodcastSegmentedControl(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .glassSurface(shape, elevation = 4.dp)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val segmentShape = RoundedCornerShape(18.dp)
            BasicText(
                text = label,
                style = typography().xs.semiBold
                    .color(if (isSelected) colorPalette().background0 else colorPalette().text)
                    .copy(textAlign = TextAlign.Center),
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .then(if (isSelected) Modifier.background(colorPalette().text) else Modifier)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}
