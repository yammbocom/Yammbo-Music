package it.fast4x.riplay.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yambo.music.R
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

// Yammbo Noir: the pieces the sign-in, sign-up, reset and verify screens share.

/** Hairline used on every border of the auth screens (white at ~12% on dark). */
@Composable
internal fun authHairline(): Color = colorPalette().text.copy(alpha = 0.12f)

/** Big bold screen headline: Geist, tight tracking. */
@Composable
internal fun AuthHeadline(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = typography().xxl.copy(
            color = colorPalette().text,
            fontSize = 32.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.04).em,
        ),
        modifier = modifier
    )
}

/** Grey supporting line under a headline. */
@Composable
internal fun AuthSubtitle(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = typography().xs.copy(
            color = colorPalette().textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        modifier = modifier
    )
}

/** Yammbo wordmark, left-aligned by its parent. */
@Composable
internal fun AuthLogo(modifier: Modifier = Modifier, height: Dp = 24.dp) {
    val colors = colorPalette()
    // The two artworks have different proportions (light 5116x805, dark 3559x589).
    val ratio = if (colors.isDark) 5116f / 805f else 3559f / 589f
    Image(
        painter = painterResource(
            id = if (colors.isDark) R.drawable.yambo_logo_light else R.drawable.yambo_logo_dark
        ),
        contentDescription = "Yammbo",
        modifier = modifier
            .height(height)
            .aspectRatio(ratio)
    )
}

/** Back chevron, 44dp target, optically aligned with the 24dp page margin. */
@Composable
internal fun AuthBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.chevron_back),
            contentDescription = stringResource(R.string.auth_back_to_login),
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Labelled rounded input: label above, 54dp field, hairline border (brighter when focused). */
@Composable
internal fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isPassword: Boolean = false,
) {
    val colors = colorPalette()
    val typo = typography()
    var visible by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(
            text = label,
            style = typo.xxs.copy(color = colors.textSecondary, fontSize = 12.sp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(shape)
                .background(colors.background1)
                .border(
                    1.dp,
                    if (focused) colors.text.copy(alpha = 0.5f) else authHairline(),
                    shape
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = typo.s.copy(
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(colors.text),
                visualTransformation = if (isPassword && !visible) PasswordVisualTransformation()
                else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = if (isPassword) 52.dp else 16.dp)
                    .onFocusChanged { focused = it.isFocused }
            )
            if (isPassword) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { visible = !visible },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(if (visible) R.drawable.eye_off else R.drawable.eye),
                        contentDescription = stringResource(
                            if (visible) R.string.auth_hide_password else R.string.auth_show_password
                        ),
                        colorFilter = ColorFilter.tint(colors.textSecondary),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** The one solid action of the screen: full-width pill, text colour on background colour. */
@Composable
internal fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled || loading) 1f else 0.4f)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.text)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = colors.background0,
                strokeWidth = 2.dp
            )
        } else {
            BasicText(
                text = text,
                style = typography().s.copy(
                    color = colors.background0,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * Error or info line in a hairline card with an icon. Never red: errors are told apart by
 * the icon and the brighter text, not by colour.
 */
@Composable
internal fun AuthMessage(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background1)
            .border(1.dp, authHairline(), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(
                if (isError) R.drawable.alert_circle_not_filled else R.drawable.checkmark
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.text),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicText(
            text = text,
            style = typography().xxs.copy(
                color = if (isError) colors.text else colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

internal val noirCoverBrushes = listOf(
    Brush.radialGradient(listOf(Color(0xFFF1D9A7), Color(0xFFB06A2C), Color(0xFF2B140A))),
    Brush.linearGradient(listOf(Color(0xFF1D2B3A), Color(0xFF5D7C8F), Color(0xFFE0C9A0))),
    Brush.linearGradient(listOf(Color(0xFFF5F5F0), Color(0xFFBDBDB6), Color(0xFF3A3A38))),
    Brush.sweepGradient(
        listOf(Color(0xFFF4B860), Color(0xFF5A3C8C), Color(0xFF14141F), Color(0xFFF4B860))
    ),
    Brush.radialGradient(listOf(Color(0xFF9FE2C9), Color(0xFF2F6F73), Color(0xFF0B1D22))),
    Brush.radialGradient(listOf(Color(0xFFFFE28A), Color(0xFFE08A2E), Color(0xFF3A1F0C))),
)

/**
 * Tilted grid of gradient tiles that fades into the page: the Login board's backdrop.
 * Dark theme only; in light theme the page stays plain.
 */
@Composable
internal fun BoxScope.AuthCoverBackdrop() {
    val colors = colorPalette()
    if (!colors.isDark) return
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(360.dp)
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationZ = -8f
                    scaleX = 1.2f
                    scaleY = 1.1f
                    translationY = -30.dp.toPx()
                }
                .alpha(0.55f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(2) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(4) { col ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(noirCoverBrushes[(row * 4 + col) % noirCoverBrushes.size])
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.background0.copy(alpha = 0.2f),
                        0.92f to colors.background0,
                        1f to colors.background0
                    )
                )
        )
    }
}
