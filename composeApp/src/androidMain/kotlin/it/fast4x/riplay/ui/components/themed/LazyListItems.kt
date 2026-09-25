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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import it.fast4x.riplay.utils.colorPalette
import java.util.WeakHashMap

/**
 * True while composing inside a settings card, so rows (SettingsEntry) draw the
 * hairline divider that separates them from the row above.
 */
val LocalSettingsCard = staticCompositionLocalOf { false }

private val SettingsCardRadius = 16.dp

// Consecutive non-header settingsItem calls (between two headers) form one visual card:
// each lazy item is a segment that only rounds the corners at the ends of its group.
// The DSL runs completely before any item composes, so the final group size is known
// by the time a segment decides whether it is the last one.
private class SettingsCardGroup {
    var size = 0
}

private class SettingsListCursor {
    var group: SettingsCardGroup? = null
}

// Keyed by the LazyListScope instance, which Compose recreates on every DSL pass.
private val settingsListCursors = WeakHashMap<LazyListScope, SettingsListCursor>()

private fun LazyListScope.settingsListCursor(): SettingsListCursor =
    synchronized(settingsListCursors) {
        settingsListCursors.getOrPut(this) { SettingsListCursor() }
    }

// Pulls the content up by one hairline so the divider of the first row in a card is
// clipped away by the card's rounded top edge.
private fun Modifier.hideLeadingHairline(): Modifier = layout { measurable, constraints ->
    val cut = 1.dp.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - cut).coerceAtLeast(0)) {
        placeable.place(0, -cut)
    }
}

fun LazyListScope.settingsItem(
    isHeader: Boolean = false,
    content: @Composable () -> Unit
) {
    val cursor = settingsListCursor()
    if (isHeader) {
        cursor.group = null
        item {
            // Section header (SettingsEntryGroupText) — sits above the card; the label
            // itself carries the start inset and top spacing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorPalette().background0)
                    .padding(end = 20.dp)
            ) {
                content()
            }
        }
    } else {
        val group = cursor.group ?: SettingsCardGroup().also { cursor.group = it }
        val index = group.size++
        item {
            val isFirst = index == 0
            val isLast = index == group.size - 1
            val shape = RoundedCornerShape(
                topStart = if (isFirst) SettingsCardRadius else 0.dp,
                topEnd = if (isFirst) SettingsCardRadius else 0.dp,
                bottomStart = if (isLast) SettingsCardRadius else 0.dp,
                bottomEnd = if (isLast) SettingsCardRadius else 0.dp
            )
            // Regular settings item — a segment of the group's single card
            CompositionLocalProvider(LocalSettingsCard provides true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(shape)
                        .background(colorPalette().background1)
                        .then(if (isFirst) Modifier.hideLeadingHairline() else Modifier)
                        .padding(horizontal = 4.dp)
                ) {
                    content()
                }
            }
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
