package it.fast4x.riplay.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.utils.typography


private data class BrowseCategory(
    val labelRes: Int,
    val queryRes: Int,
    val topColor: Color,
    val bottomColor: Color
)

private val BrowseCategoryGrid = listOf(
    BrowseCategory(
        R.string.browse_cat_top_hits, R.string.browse_q_top_hits,
        Color(0xFFEF5350), Color(0xFFB71C1C)
    ),
    BrowseCategory(
        R.string.browse_cat_pop, R.string.browse_q_pop,
        Color(0xFFEC407A), Color(0xFF880E4F)
    ),
    BrowseCategory(
        R.string.browse_cat_reggaeton, R.string.browse_q_reggaeton,
        Color(0xFFFF7043), Color(0xFFBF360C)
    ),
    BrowseCategory(
        R.string.browse_cat_latin, R.string.browse_q_latin,
        Color(0xFFFFA726), Color(0xFFE65100)
    ),
    BrowseCategory(
        R.string.browse_cat_hiphop, R.string.browse_q_hiphop,
        Color(0xFF8D6E63), Color(0xFF3E2723)
    ),
    BrowseCategory(
        R.string.browse_cat_rock, R.string.browse_q_rock,
        Color(0xFF66BB6A), Color(0xFF1B5E20)
    ),
    BrowseCategory(
        R.string.browse_cat_electronic, R.string.browse_q_electronic,
        Color(0xFF26C6DA), Color(0xFF006064)
    ),
    BrowseCategory(
        R.string.browse_cat_chill, R.string.browse_q_chill,
        Color(0xFF5C6BC0), Color(0xFF1A237E)
    ),
    BrowseCategory(
        R.string.browse_cat_workout, R.string.browse_q_workout,
        Color(0xFFAB47BC), Color(0xFF4A148C)
    ),
    BrowseCategory(
        R.string.browse_cat_classical, R.string.browse_q_classical,
        Color(0xFF78909C), Color(0xFF263238)
    )
)

@Composable
internal fun BrowseCategoriesGrid(
    onCategoryClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Render 2 columns × 5 rows (10 categories total). Each tile fades+
        // slides up in sequence (40 ms stagger) for a "cards land one after
        // the other" entry — same spec used across Mi Música / Top 50.
        BrowseCategoryGrid.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEachIndexed { colIndex, cat ->
                    val label = stringResource(cat.labelRes)
                    val query = stringResource(cat.queryRes)
                    val tileIndex = rowIndex * 2 + colIndex
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredEntry(index = tileIndex) {
                            CategoryTile(
                                label = label,
                                topColor = cat.topColor,
                                bottomColor = cat.bottomColor,
                                onClick = { onCategoryClick(query) },
                            )
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
private fun CategoryTile(
    label: String,
    topColor: Color,
    bottomColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(2.4f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(topColor, bottomColor)
                )
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicText(
            text = label,
            style = typography().m.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
