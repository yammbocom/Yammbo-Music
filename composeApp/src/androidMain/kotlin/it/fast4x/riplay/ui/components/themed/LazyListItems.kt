package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.fast4x.riplay.utils.colorPalette


fun LazyListScope.settingsItem(
    isHeader: Boolean = false,
    content: @Composable () -> Unit
) {
    if (isHeader)
        item {
            // Section header (SettingsEntryGroupText) — sits above the card with extra top spacing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorPalette().background0)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
            ) {
                content()
            }
        }
    else
        item {
            // Regular settings item — rendered inside a card for the new design
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorPalette().background1)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                content()
            }
        }
}

fun LazyListScope.settingsSearchBarItem(
    content: @Composable (ColumnScope.() -> Unit)
) {
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            content()
        }
    }
}
