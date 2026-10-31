package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

/**
 * Yammbo Noir ghost pill: transparent with a hairline border and text-coloured label.
 * [alternative] drops the border for a soft tinted fill instead.
 */
@Composable
fun SecondaryTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alternative: Boolean = false,
    /** Set false when the caller already supplies a surface (e.g. a glass pill); otherwise
     *  the button paints its own fill inside it and you see two stacked backgrounds. */
    showBackground: Boolean = true
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(percent = 50)
    val surface = when {
        !showBackground -> Modifier
        alternative -> Modifier.background(colors.text.copy(alpha = 0.08f))
        else -> Modifier
            .background(Color.Transparent)
            .border(1.dp, colors.text.copy(alpha = 0.16f), shape)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .then(surface)
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = text,
            style = typography().xs.semiBold.copy(
                color = colors.text,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}
