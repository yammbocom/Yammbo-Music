package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import it.fast4x.riplay.ui.screens.auth.noirCoverBrushes
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

private val NoirSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

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
        modifier = Modifier.border(1.dp, colorPalette().text.copy(alpha = 0.10f), NoirSheetShape),
        sheetState = sheetState,
        dragHandle = { SheetDragHandle() },
        shape = NoirSheetShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectCollage()

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.onboarding_connect_title),
                    style = typography().xl.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.035).em,
                        color = colorPalette().text,
                        textAlign = TextAlign.Center,
                    ),
                )
                BasicText(
                    text = stringResource(R.string.onboarding_connect_subtitle),
                    style = typography().xs.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = colorPalette().textSecondary,
                        textAlign = TextAlign.Center,
                    ),
                )
            }

            ConnectCard(
                title = "YouTube Music",
                subtitle = stringResource(R.string.onboarding_connect_yt_subtitle),
                doneLabel = stringResource(R.string.onboarding_connect_yt_done).takeIf { ytConnected },
                mark = {
                    Image(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().background0),
                        modifier = Modifier.size(22.dp),
                    )
                },
                markSolid = true,
            ) {
                if (!ytConnected) {
                    ConnectButton(
                        text = stringResource(R.string.onboarding_connect_yt_action),
                        solid = true,
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
                markSolid = false,
            ) {
                ConnectButton(
                    text = if (spotifyImported) stringResource(R.string.onboarding_connect_spotify_action_more)
                    else stringResource(R.string.onboarding_connect_spotify_action),
                    solid = false,
                    onClick = {
                        // The import dialog is hosted app-wide (SpotifyImportHost in MainActivity).
                        closeThen(
                            { OnboardingConnect.markActed() },
                            { SpotifyImport.openDialog() },
                        )
                    },
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SheetTextButton(
                    text = if (manual) stringResource(R.string.onboarding_connect_close)
                    else stringResource(R.string.onboarding_connect_later),
                    outlined = true,
                    onClick = putOff,
                )
                if (!manual) {
                    SheetTextButton(
                        text = stringResource(R.string.onboarding_connect_never),
                        outlined = false,
                        onClick = {
                            closeThen({ OnboardingConnect.dismissForever() }, {})
                        },
                    )
                }
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

/** Three tilted gradient covers: the artwork of the board, decoration only. */
@Composable
private fun ConnectCollage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .offset(x = (-76).dp, y = 14.dp)
                .size(92.dp)
                .rotate(-12f)
                .clip(RoundedCornerShape(20.dp))
                .background(noirCoverBrushes[1])
        )
        Box(
            modifier = Modifier
                .offset(x = 76.dp, y = 14.dp)
                .size(92.dp)
                .rotate(12f)
                .clip(RoundedCornerShape(20.dp))
                .background(noirCoverBrushes[5])
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(noirCoverBrushes[0])
        )
    }
}

@Composable
private fun ConnectCard(
    title: String,
    subtitle: String,
    doneLabel: String?,
    mark: @Composable () -> Unit,
    markSolid: Boolean,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit,
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background2)
            .border(1.dp, colors.text.copy(alpha = 0.10f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (markSolid) colors.text else colors.background1),
            contentAlignment = Alignment.Center,
        ) {
            mark()
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = title,
                style = typography().s.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (doneLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.checkmark),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.text),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicText(
                        text = doneLabel,
                        style = typography().xxs.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                        ),
                        maxLines = 1,
                    )
                }
            } else {
                BasicText(
                    text = subtitle,
                    style = typography().xxs.copy(fontSize = 12.sp, color = colors.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        action()
    }
}

/** Compact action on a card: solid for the main source, hairline outline for the other. */
@Composable
private fun ConnectButton(text: String, solid: Boolean, onClick: () -> Unit) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .then(
                if (solid) Modifier.background(colors.text)
                else Modifier.border(1.dp, colors.text.copy(alpha = 0.22f), shape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = typography().xs.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (solid) colors.background0 else colors.text,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetTextButton(text: String, outlined: Boolean, onClick: () -> Unit) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (outlined) 54.dp else 40.dp)
            .clip(shape)
            .then(
                if (outlined) Modifier.border(1.dp, colors.text.copy(alpha = 0.18f), shape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = typography().xs.copy(
                fontSize = if (outlined) 15.sp else 13.sp,
                fontWeight = if (outlined) FontWeight.SemiBold else FontWeight.Normal,
                color = if (outlined) colors.text else colors.textSecondary,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
