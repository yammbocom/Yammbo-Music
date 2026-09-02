package it.fast4x.riplay.ui.components.themed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One in-app message on its way to the screen.
 */
data class AppMessage(
    val text: String,
    val type: PopupType?,
    val durationLong: Boolean,
)

/**
 * The channel [SmartMessage] posts to.
 *
 * A flow rather than a direct call because messages come from everywhere — the
 * playback service, file utilities, view models — and almost none of those are
 * composables. This keeps the existing call sites untouched while the UI decides
 * how a message actually looks.
 *
 * extraBufferCapacity with DROP_OLDEST so a burst never suspends a caller: a
 * background thread reporting an error must not be made to wait on the UI, and
 * the newest message is the one worth showing.
 */
object AppMessages {
    private val _messages = MutableSharedFlow<AppMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = _messages.asSharedFlow()

    /** True once a host is on screen; until then messages have to fall back to a Toast. */
    @Volatile
    var hasHost: Boolean = false
        private set

    internal fun setHostPresent(present: Boolean) {
        hasHost = present
    }

    /** Returns false when nothing consumed the message, so the caller can fall back. */
    fun post(message: AppMessage): Boolean {
        if (!hasHost) return false
        return _messages.tryEmit(message)
    }
}

/**
 * Draws the current message above everything else.
 *
 * Sits at the bottom because that is where the eye already is during playback, and
 * it is lifted clear of the mini player: the old system Toast landed right on top
 * of it, hiding the controls for the length of the message.
 */
@Composable
fun BoxWithMessages(
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    var current by remember { mutableStateOf<AppMessage?>(null) }

    LaunchedEffect(Unit) {
        AppMessages.setHostPresent(true)
        AppMessages.messages.collect { message ->
            current = message
            // Long enough to read without lingering. Matches the two Toast lengths it
            // replaces so nothing that relied on the old timing feels different.
            delay(if (message.durationLong) 3500 else 2200)
            current = null
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        content()

        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(animationSpec = tween(260)) { it / 2 } +
                    fadeIn(animationSpec = tween(220)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(260)),
            exit = slideOutVertically(animationSpec = tween(200)) { it / 3 } +
                    fadeOut(animationSpec = tween(160)) +
                    scaleOut(targetScale = 0.96f, animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            current?.let { AppMessageBar(it, bottomInset) }
        }
    }
}

@Composable
private fun AppMessageBar(
    message: AppMessage,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val palette = colorPalette()

    // Monochrome on purpose. The library this replaces painted every message a
    // saturated blue, green or red, which read as someone else's product inside a
    // black-and-white one. The icon carries the meaning instead of the colour.
    val icon = when (message.type) {
        PopupType.Error -> R.drawable.alert_circle
        PopupType.Warning -> R.drawable.alert_circle
        PopupType.Success -> R.drawable.checkmark
        else -> R.drawable.information
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(bottom = bottomInset + 12.dp)
            .padding(horizontal = 16.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.background1)
            .border(1.dp, palette.background2, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.textSecondary),
            modifier = Modifier.size(18.dp),
        )
        BasicText(
            text = message.text,
            style = typography().xs.copy(color = palette.text),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
