package it.fast4x.riplay.ui.screens.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yambo.music.R
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


/**
 * A browse tile. The grid used to separate categories by hue; the brand is black
 * and white, so each tile is told apart by its oversized watermark icon and by
 * the layout of its section instead — genres read as corner-marked cards,
 * moods as centred badges.
 */
private data class BrowseCategory(
    val labelRes: Int,
    val queryRes: Int,
    val iconRes: Int
)

private val GenreCategories = listOf(
    BrowseCategory(R.string.browse_cat_top_hits, R.string.browse_q_top_hits, R.drawable.trending),
    BrowseCategory(R.string.browse_cat_pop, R.string.browse_q_pop, R.drawable.musical_notes),
    BrowseCategory(R.string.browse_cat_reggaeton, R.string.browse_q_reggaeton, R.drawable.radio),
    BrowseCategory(R.string.browse_cat_latin, R.string.browse_q_latin, R.drawable.disc),
    BrowseCategory(R.string.browse_cat_hiphop, R.string.browse_q_hiphop, R.drawable.mic),
    BrowseCategory(R.string.browse_cat_rock, R.string.browse_q_rock, R.drawable.equalizer),
    BrowseCategory(R.string.browse_cat_electronic, R.string.browse_q_electronic, R.drawable.headset),
    BrowseCategory(R.string.browse_cat_classical, R.string.browse_q_classical, R.drawable.musical_note)
)

private val MoodCategories = listOf(
    BrowseCategory(R.string.browse_cat_chill, R.string.browse_q_chill, R.drawable.moon),
    BrowseCategory(R.string.browse_cat_workout, R.string.browse_q_workout, R.drawable.equalizer),
    BrowseCategory(R.string.browse_cat_party, R.string.browse_q_party, R.drawable.sparkles),
    BrowseCategory(R.string.browse_cat_focus, R.string.browse_q_focus, R.drawable.headset),
    BrowseCategory(R.string.browse_cat_sleep, R.string.browse_q_sleep, R.drawable.star),
    BrowseCategory(R.string.browse_cat_romantic, R.string.browse_q_romantic, R.drawable.heart)
)

@Composable
internal fun BrowseCategoriesGrid(
    onCategoryClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        BrowseSectionLabel(stringResource(R.string.browse_section_genres))

        TwoColumnGrid(
            items = GenreCategories,
            staggerOffset = 0
        ) { cat, index ->
            val query = stringResource(cat.queryRes)
            GenreTile(
                label = stringResource(cat.labelRes),
                iconRes = cat.iconRes,
                onClick = { onCategoryClick(query) }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        BrowseSectionLabel(stringResource(R.string.browse_section_moods))

        TwoColumnGrid(
            items = MoodCategories,
            staggerOffset = GenreCategories.size
        ) { cat, index ->
            val query = stringResource(cat.queryRes)
            MoodTile(
                label = stringResource(cat.labelRes),
                iconRes = cat.iconRes,
                onClick = { onCategoryClick(query) }
            )
        }
    }
}

/**
 * Two equal columns, each tile fading + sliding up 40 ms after the previous one
 * (same entry spec as the rest of the app). `staggerOffset` keeps the second
 * section continuing the first section's sequence instead of restarting it.
 */
@Composable
private fun TwoColumnGrid(
    items: List<BrowseCategory>,
    staggerOffset: Int,
    tile: @Composable (BrowseCategory, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEachIndexed { colIndex, cat ->
                    val tileIndex = rowIndex * 2 + colIndex
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredEntry(index = staggerOffset + tileIndex) {
                            tile(cat, tileIndex)
                        }
                    }
                }
                // Pad the final row if it ended up with a single item
                if (rowItems.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BrowseSectionLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = typography().xxs.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().textSecondary,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 10.dp)
    )
}

/** Label top-left, oversized icon bleeding out of the bottom-right corner. */
@Composable
private fun GenreTile(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background1)
            .border(
                width = 1.dp,
                color = colors.textDisabled.copy(alpha = 0.28f),
                shape = RoundedCornerShape(16.dp)
            )
            .pressable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.text.copy(alpha = 0.10f)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 12.dp)
                .size(72.dp)
        )
        BasicText(
            text = label,
            style = typography().m.copy(
                fontWeight = FontWeight.Bold,
                color = colors.text
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

/** Centred icon over a centred label, on a slightly raised surface. */
@Composable
private fun MoodTile(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background2)
            .pressable(onClick = onClick)
            .padding(horizontal = 10.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.text.copy(alpha = 0.55f)),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicText(
            text = label,
            style = typography().xs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                textAlign = TextAlign.Center
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
