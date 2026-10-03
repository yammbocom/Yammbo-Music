package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.yambo.music.R
import it.fast4x.riplay.extensions.preferences.enableYouTubeLoginKey
import it.fast4x.riplay.extensions.preferences.onboardingDismissedForeverKey
import it.fast4x.riplay.extensions.preferences.onboardingPendingKey
import it.fast4x.riplay.extensions.preferences.onboardingSeenExistingKey
import it.fast4x.riplay.extensions.preferences.onboardingSnoozeUntilKey
import it.fast4x.riplay.extensions.preferences.onboardingTimesShownKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.youtubelogin.YouTubeLogin
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.SheetDragHandle
import it.fast4x.riplay.ui.components.SheetShape
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.screens.settings.AccountsPrimaryButton
import it.fast4x.riplay.ui.screens.settings.isYtLoggedIn
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.spotify.SpotifyImport
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.launch

/**
 * When the "Trae tu música" sheet offers itself. All state lives in the app preferences: the
 * Home tabs are destroyed and recreated, and connecting YouTube restarts the whole app.
 *
 *  - A new Yammbo account marks it pending; it comes back (after "Ahora no", or after being
 *    ignored) until it has been shown [MAX_AUTO_SHOWS] times.
 *  - An existing user who never connected YouTube Music sees it once.
 *  - "No volver a mostrar" ends it for good; tapping Connect or Import counts as done.
 */
object OnboardingConnect {

    private const val SNOOZE_MS = 3L * 24 * 60 * 60 * 1000

    /** Shown and left without an answer (app closed): not again before this. */
    private const val IGNORED_GAP_MS = 24L * 60 * 60 * 1000

    /** Automatic showings in total, answered or not. */
    private const val MAX_AUTO_SHOWS = 3

    // Home re-enters composition on every return from another screen: one offer per process.
    @Volatile
    private var offeredThisProcess = false

    /** Called right after a new Yammbo account is created, before leaving the register screen. */
    fun markNewAccount() {
        // A logout and a new sign-up in the same run still gets the offer.
        offeredThisProcess = false
        appContext().preferences.edit {
            putBoolean(onboardingPendingKey, true)
            putLong(onboardingSnoozeUntilKey, 0L)
            putInt(onboardingTimesShownKey, 0)
        }
    }

    fun shouldAutoShow(now: Long = System.currentTimeMillis()): Boolean {
        if (offeredThisProcess) return false
        val prefs = appContext().preferences
        if (prefs.getBoolean(onboardingDismissedForeverKey, false)) return false
        if (now < prefs.getLong(onboardingSnoozeUntilKey, 0L)) return false
        if (prefs.getInt(onboardingTimesShownKey, 0) >= MAX_AUTO_SHOWS) return false

        val ytConnected = isYtLoggedIn()
        if (ytConnected && SpotifyImport.hasImports()) return false

        val pendingNewAccount = prefs.getBoolean(onboardingPendingKey, false)
        val existingNeverAsked = !prefs.getBoolean(onboardingSeenExistingKey, false) && !ytConnected
        return pendingNewAccount || existingNeverAsked
    }

    /** Another prompt (an update, a remote popup, an import) had the stage this run. */
    fun skipThisProcess() {
        offeredThisProcess = true
    }

    /**
     * The sheet is about to be shown automatically. Counted now, not on an answer: a sheet that
     * is simply ignored must not come back on every cold start. Existing users get it only once.
     */
    fun markAutoShown(now: Long = System.currentTimeMillis()) {
        offeredThisProcess = true
        val prefs = appContext().preferences
        prefs.edit {
            putBoolean(onboardingSeenExistingKey, true)
            putInt(onboardingTimesShownKey, prefs.getInt(onboardingTimesShownKey, 0) + 1)
            putLong(onboardingSnoozeUntilKey, now + IGNORED_GAP_MS)
        }
    }

    /** "Ahora no", the X, a swipe down or back. The showing itself was already counted. */
    fun snooze(now: Long = System.currentTimeMillis()) {
        appContext().preferences.edit { putLong(onboardingSnoozeUntilKey, now + SNOOZE_MS) }
    }

    fun dismissForever() {
        appContext().preferences.edit {
            putBoolean(onboardingDismissedForeverKey, true)
            putBoolean(onboardingPendingKey, false)
        }
    }

    /** Import tapped: the offer did its job. */
    fun markActed() {
        appContext().preferences.edit {
            putBoolean(onboardingPendingKey, false)
            putBoolean(onboardingSeenExistingKey, true)
        }
    }

    /**
     * Connect tapped. Written synchronously: a successful login ends in Runtime.exit(0), and an
     * apply() still queued at that point would be lost, bringing the sheet back after the restart.
     * The login screen only stores the cookie; turning the login switch on is ours, as in Accounts.
     */
    fun prepareYouTubeLogin() {
        appContext().preferences.edit(commit = true) {
            putBoolean(onboardingPendingKey, false)
            putBoolean(onboardingSeenExistingKey, true)
            putBoolean(enableYouTubeLoginKey, true)
        }
    }
}

/**
 * "Trae tu música a Yammbo": YouTube Music and Spotify, one card each. Keep it composed and
 * drive it with [show]: it also hosts the YouTube login sheet, which outlives this one.
 *
 * [manual] is the Accounts entry point: no rules, no snooze, no "don't show again".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingConnectSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    manual: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showYouTubeLogin by rememberSaveable { mutableStateOf(false) }
    // Set before a button hides the sheet, so the dismiss callback can tell it from a swipe.
    var closingByAction by remember { mutableStateOf(false) }

    // Read each time the sheet opens: both can change while it is closed.
    val ytConnected = remember(show) { isYtLoggedIn() }
    val spotifyImported = remember(show) { SpotifyImport.hasImports() }

    // One answer per showing: taps that land during the hide animation are ignored, so a
    // double tap cannot snooze twice or fire Connect and Import together.
    val closeThen: (before: () -> Unit, after: () -> Unit) -> Unit = { before, after ->
        if (!closingByAction) {
            closingByAction = true
            before()
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                closingByAction = false
                onDismiss()
                after()
            }
        }
    }
    val putOff: () -> Unit = {
        closeThen({ if (!manual) OnboardingConnect.snooze() }, {})
    }

    CustomModalBottomSheet(
        showSheet = show,
        onDismissRequest = {
            // Swipe down, back or a tap outside: same as "Ahora no".
            if (!closingByAction) {
                if (!manual) OnboardingConnect.snooze()
                onDismiss()
            }
        },
        containerColor = colorPalette().background1,
        contentColor = colorPalette().text,
        scrimColor = Color.Black.copy(
            alpha = if (colorPalette().background0.luminance() > 0.5f) 0.18f else 0.45f
        ),
        tonalElevation = 0.dp,
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() },
        shape = SheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.onboarding_connect_title),
                        style = typography().l.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorPalette().text,
                        ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicText(
                        text = stringResource(R.string.onboarding_connect_subtitle),
                        style = typography().xs.copy(color = colorPalette().textSecondary),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colorPalette().background2)
                        .clickable(onClick = putOff),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.onboarding_connect_close),
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            ConnectCard(
                title = "YouTube Music",
                subtitle = stringResource(R.string.onboarding_connect_yt_subtitle),
                doneLabel = stringResource(R.string.onboarding_connect_yt_done).takeIf { ytConnected },
                mark = {
                    BasicText(
                        text = "YT",
                        style = typography().m.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorPalette().text,
                        ),
                    )
                },
            ) {
                if (ytConnected) {
                    DonePill(text = stringResource(R.string.onboarding_connect_yt_done))
                } else {
                    AccountsPrimaryButton(
                        text = stringResource(R.string.onboarding_connect_yt_action),
                        onClick = {
                            // Persisted before the login opens: a successful login restarts the app.
                            closeThen(
                                { OnboardingConnect.prepareYouTubeLogin() },
                                { showYouTubeLogin = true },
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ConnectCard(
                title = "Spotify",
                subtitle = stringResource(R.string.onboarding_connect_spotify_subtitle),
                doneLabel = stringResource(R.string.onboarding_connect_spotify_done).takeIf { spotifyImported },
                mark = {
                    Image(
                        painter = painterResource(R.drawable.ic_spotify_mark),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier.size(24.dp),
                    )
                },
            ) {
                AccountsPrimaryButton(
                    text = if (spotifyImported) stringResource(R.string.onboarding_connect_spotify_action_more)
                    else stringResource(R.string.onboarding_connect_spotify_action),
                    onClick = {
                        // The import dialog is hosted app-wide (SpotifyImportHost in MainActivity).
                        closeThen(
                            { OnboardingConnect.markActed() },
                            { SpotifyImport.openDialog() },
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SheetTextButton(
                text = if (manual) stringResource(R.string.onboarding_connect_close)
                else stringResource(R.string.onboarding_connect_later),
                color = colorPalette().text,
                onClick = putOff,
            )
            if (!manual) {
                SheetTextButton(
                    text = stringResource(R.string.onboarding_connect_never),
                    color = colorPalette().textSecondary,
                    onClick = {
                        closeThen({ OnboardingConnect.dismissForever() }, {})
                    },
                )
            }
        }
    }

    // Same full-height login sheet as the Accounts screen. YouTubeLogin stores the cookie and
    // restarts the app on success, so there is nothing to do in its callback.
    CustomModalBottomSheet(
        showSheet = showYouTubeLogin,
        onDismissRequest = { showYouTubeLogin = false },
        containerColor = colorPalette().background0,
        contentColor = colorPalette().text,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {},
        shape = SheetShape,
    ) {
        YouTubeLogin(onLogin = {})
    }
}

@Composable
private fun ConnectCard(
    title: String,
    subtitle: String,
    doneLabel: String?,
    mark: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(20.dp), elevation = 4.dp)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colorPalette().background2),
                contentAlignment = Alignment.Center,
            ) {
                mark()
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
                Spacer(modifier = Modifier.height(3.dp))
                BasicText(
                    text = subtitle,
                    style = typography().xxs.copy(color = colorPalette().textSecondary),
                )
            }
            if (doneLabel != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.checkmark),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicText(
                        text = doneLabel,
                        style = typography().xxs.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colorPalette().text,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        action()
    }
}

/** Disabled stand-in for the primary button once the step is done: check plus a label. */
@Composable
private fun DonePill(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(0.7f)
            .clip(RoundedCornerShape(20.dp))
            .background(colorPalette().background2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.checkmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicText(
            text = text,
            style = typography().xs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().text,
            ),
        )
    }
}

@Composable
private fun SheetTextButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = typography().xs.copy(
                fontWeight = FontWeight.SemiBold,
                color = color,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
