package it.fast4x.riplay.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.ui.styling.medium
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

@Composable
inline fun Menu(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            //.padding(top = 12.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            // Same surface as the global sheet (so it reads as one card there), kept
            // for menus hosted outside the sheet.
            .background(colorPalette().background1)
            //.padding(top = 2.dp)
            // No top padding: the sheet's drag-handle pill already spaces the top.
            .padding(bottom = 8.dp)
            .navigationBarsPadding(),
        content = content
    )
}

/**
 * Hairline between a menu header (artwork + title) and its entries.
 */
@Composable
fun MenuHeaderDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().background2)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuEntry(
    painter: Painter,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 20.dp)
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier
                .size(22.dp)
        )

        Column(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .weight(1f)
        ) {
            BasicText(
                text = text,
                style = typography().s.medium.copy(color = colorPalette().text)
            )

            secondaryText?.let { secondaryText ->
                BasicText(
                    text = secondaryText,
                    style = typography().xxs.medium.secondary
                )
            }
        }

        trailingContent?.invoke()

    }
}

@Composable
fun MenuEntry(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    MenuEntry(
        painterResource( icon ),
        text,
        onClick,
        onLongClick,
        secondaryText,
        enabled,
        trailingContent
    )
}

@Composable
fun SubMenuEntry(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
) {
    MenuEntry(
        painterResource( icon ),
        text,
        onClick,
        onLongClick,
        secondaryText,
        enabled,
        trailingContent = {
            Image(
                painter = painterResource(R.drawable.chevron_forward),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    colorPalette().textSecondary
                ),
                modifier = Modifier
                    .size(16.dp)
            )
        }
    )
}
