package it.fast4x.riplay.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.extensions.listenbrainz.ListenBrainzClient
import it.fast4x.riplay.extensions.listenbrainz.ListenBrainzValidation
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.components.themed.Switch
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.launch

/**
 * Building blocks of the Accounts screen: one glass card per service, with the same header
 * (avatar or icon, name, status line) and a body of rows and buttons. Strictly monochrome.
 */
@Composable
internal fun AccountsCard(
    title: String,
    statusText: String,
    connected: Boolean,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    @DrawableRes icon: Int? = null,
    monogram: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(20.dp), elevation = 4.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colorPalette().background2),
                contentAlignment = Alignment.Center,
            ) {
                // The logo or monogram is always drawn, and the photo goes on top: an avatar URL
                // that fails to load (or is empty on the server) used to leave a blank circle.
                when {
                    icon != null -> Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier.size(22.dp),
                    )

                    monogram != null -> BasicText(
                        text = monogram,
                        style = typography().m.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorPalette().text,
                        ),
                    )
                }
                if (!avatarUrl.isNullOrBlank()) AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style = typography().s.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colorPalette().text,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Filled dot when linked, dim when not: state reads without any colour.
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (connected) colorPalette().text else colorPalette().textDisabled
                            )
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    BasicText(
                        text = statusText,
                        style = typography().xxs.copy(color = colorPalette().textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        content()
    }
}

/** Main call to action of a card: 44 dp pill filled with the text colour. */
@Composable
internal fun AccountsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(colorPalette().text)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colorPalette().background0,
                strokeWidth = 2.dp,
            )
        } else {
            BasicText(
                text = text,
                style = typography().xs.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colorPalette().background0,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Quiet counterpart of the primary button: same pill, filled with a surface tone. */
@Composable
internal fun AccountsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(20.dp))
            .background(colorPalette().background2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = typography().xs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().text,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AccountsDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().background2)
    )
}

/** Tappable row with a title, an optional subtitle and an optional trailing slot. */
@Composable
internal fun AccountsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography().xs.copy(
                    fontWeight = FontWeight.Medium,
                    color = colorPalette().text,
                ),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = subtitle,
                    style = typography().xxs.copy(color = colorPalette().textSecondary),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun AccountsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    AccountsRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(isChecked = checked) },
    )
}

/** "hace 5 min" style text for the last-sync line. [now] is passed in so the caller can tick it. */
@Composable
internal fun accountsSyncAgeText(lastSyncAt: Long, now: Long): String {
    val minutes = ((now - lastSyncAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> stringResource(R.string.accounts_time_just_now)
        minutes < 60L -> stringResource(R.string.accounts_time_minutes_ago, minutes.toInt())
        minutes < 60L * 24L -> stringResource(R.string.accounts_time_hours_ago, (minutes / 60L).toInt())
        else -> stringResource(R.string.accounts_time_days_ago, (minutes / (60L * 24L)).toInt())
    }
}

private enum class TokenDialogError { None, Invalid, Network }

/**
 * Asks for the ListenBrainz user token, checks it against the server and only stores it when
 * valid. The token is held in plain `remember` (not saveable) so it never lands in saved state.
 */
@Composable
internal fun ListenBrainzTokenDialog(
    onDismiss: () -> Unit,
    onConnected: (userName: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var token by remember { mutableStateOf("") }
    var validating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(TokenDialogError.None) }

    Dialog(onDismissRequest = { if (!validating) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(shape = RoundedCornerShape(20.dp), alpha = 1f, elevation = 0.dp)
                .padding(20.dp)
        ) {
            BasicText(
                text = stringResource(R.string.accounts_listenbrainz_dialog_title),
                style = typography().m.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colorPalette().text,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = stringResource(R.string.accounts_listenbrainz_dialog_info),
                style = typography().xs.copy(color = colorPalette().textSecondary),
            )
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorPalette().background2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        error = TokenDialogError.None
                    },
                    singleLine = true,
                    enabled = !validating,
                    textStyle = typography().xs.copy(color = colorPalette().text),
                    cursorBrush = SolidColor(colorPalette().text),
                    // Masked: it is a credential, and a paste is all that is expected here.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (token.isEmpty()) {
                    BasicText(
                        text = stringResource(R.string.accounts_listenbrainz_token_hint),
                        style = typography().xs.copy(color = colorPalette().textDisabled),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // One status line: checking, error, or nothing. Never an endless spinner.
            val statusLine = when {
                validating -> stringResource(R.string.accounts_listenbrainz_validating)
                error == TokenDialogError.Invalid -> stringResource(R.string.accounts_listenbrainz_invalid_token)
                error == TokenDialogError.Network -> stringResource(R.string.accounts_listenbrainz_network_error)
                else -> null
            }
            if (statusLine != null) {
                BasicText(
                    text = statusLine,
                    style = typography().xxs.copy(
                        color = if (validating) colorPalette().textSecondary else colorPalette().text,
                        fontWeight = if (validating) FontWeight.Normal else FontWeight.SemiBold,
                    ),
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            AccountsRow(
                title = stringResource(R.string.accounts_listenbrainz_open_settings),
                onClick = {
                    runCatching { uriHandler.openUri("https://listenbrainz.org/settings/") }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountsSecondaryButton(
                    text = stringResource(R.string.accounts_action_cancel),
                    onClick = onDismiss,
                    enabled = !validating,
                    modifier = Modifier.weight(1f),
                )
                AccountsPrimaryButton(
                    text = if (error == TokenDialogError.Network)
                        stringResource(R.string.accounts_action_retry)
                    else stringResource(R.string.accounts_action_connect),
                    enabled = token.isNotBlank(),
                    loading = validating,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        validating = true
                        error = TokenDialogError.None
                        scope.launch {
                            when (val result = ListenBrainzClient.validateToken(token)) {
                                is ListenBrainzValidation.Valid -> {
                                    ListenBrainzClient.saveConnection(token, result.userName)
                                    validating = false
                                    onConnected(result.userName)
                                }

                                ListenBrainzValidation.Invalid -> {
                                    validating = false
                                    error = TokenDialogError.Invalid
                                }

                                ListenBrainzValidation.NetworkError -> {
                                    validating = false
                                    error = TokenDialogError.Network
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
