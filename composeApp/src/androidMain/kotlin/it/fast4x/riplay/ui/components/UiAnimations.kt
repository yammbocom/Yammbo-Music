package it.fast4x.riplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Press animation specs shared across Top 50 / Mi Música / Mi Cuenta / Búsqueda.
 *
 * Snappy spec: scale 0.96 on press, returns via spring with high stiffness so
 * the feel is closer to ~100ms — Apple-like response without a bouncy overshoot.
 */
private val PressableSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh,
)

/**
 * Drop-in replacement for `Modifier.clickable` that adds a subtle scale-down
 * on press. Keeps the system indication (ripple / focus highlight) intact so
 * accessibility and theming continue to work.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    indication: Indication? = null,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = PressableSpring,
        label = "pressable-scale",
    )
    val resolvedIndication = indication ?: LocalIndication.current
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = resolvedIndication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/**
 * Wraps `content` in a fade-in + slide-up entry that starts after
 * `index * delayPerItem` ms. Use within a Column/Row by feeding each child
 * its own index — gives the section a "cards land sequentially" feel without
 * a list-wide animation library.
 *
 * The entry plays once on mount. After that the wrapper is transparent.
 */
@Composable
fun StaggeredEntry(
    index: Int,
    delayPerItem: Int = 40,
    durationMillis: Int = 250,
    initialOffsetY: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val density = LocalDensity.current
    val offsetPx = with(density) { initialOffsetY.roundToPx() }
    val delay = index * delayPerItem
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis, delayMillis = delay)) +
                slideInVertically(
                    animationSpec = tween(durationMillis, delayMillis = delay),
                    initialOffsetY = { offsetPx },
                ),
        exit = fadeOut(tween(120)),
    ) {
        content()
    }
}
