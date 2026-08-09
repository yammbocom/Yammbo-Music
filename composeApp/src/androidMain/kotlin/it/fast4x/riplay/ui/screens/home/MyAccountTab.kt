package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.enums.ColorPaletteMode
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.customtabs.YammboCustomTabs
import it.fast4x.riplay.extensions.preferences.colorPaletteModeKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.service.PlayerService
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.themed.ConfirmationDialog
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale


@Composable
fun MyAccountTab(
    navController: NavController
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val authManager = remember { YammboAuthManager(context) }

    val userName = authManager.getUserName()
    val userEmail = authManager.getUserEmail()
    val userAvatar = authManager.getUserAvatar()

    var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)
    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    var showLogoutDialog by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(authManager.isSubscriptionActive()) }
    var subscriptionPlan by remember { mutableStateOf(authManager.getSubscriptionPlan()) }
    var renewsAt by remember { mutableStateOf(authManager.getSubscriptionRenewsAt()) }

    // Summary counters for the stats strip. Single-row aggregates, so collecting
    // them on the tab is cheap; they update live as the user listens.
    val listenedMs by remember { Database.totalListeningTimeMs() }
        .collectAsState(initial = 0L, context = Dispatchers.IO)
    val artistsCount by remember { Database.listenedArtistsCount() }
        .collectAsState(initial = 0, context = Dispatchers.IO)
    val playlistsCount by remember { Database.playlistsCount() }
        .collectAsState(initial = 0, context = Dispatchers.IO)

    LifecycleResumeEffect(Unit) {
        val userId = authManager.getUserId()
        if (userId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                YammboApiService.checkSubscription(userId).onSuccess { response ->
                    authManager.saveSubscriptionStatus(response)
                    isSubscribed = response.subscribed
                    subscriptionPlan = response.plan.orEmpty()
                    renewsAt = response.renewsAt.orEmpty()
                }
            }
        }
        onPauseOrDispose { }
    }

    val colors = colorPalette()

    if (showLogoutDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.logout_confirmation),
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                binder?.player?.stop()
                binder?.player?.clearMediaItems()
                context.stopService(Intent(context, PlayerService::class.java))
                authManager.logout()
                showLogoutDialog = false
                navController.navigate(NavRoutes.login.name) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .background(colors.background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Stagger entry: header leads, then plan, stats and the section cards
        // land 40ms apart. Indexes match visual order.
        StaggeredEntry(index = 0) {
            ProfileHeader(
                userName = userName,
                userEmail = userEmail,
                avatarUrl = userAvatar,
                onEdit = { navController.navigate(NavRoutes.settings.name) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        StaggeredEntry(index = 1) {
            PlanCard(
                isSubscribed = isSubscribed,
                subscriptionPlan = subscriptionPlan,
                renewsAt = renewsAt,
                onAction = { openSubscriptionPage(context, authManager, isSubscribed) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        StaggeredEntry(index = 2) {
            StatsStrip(
                listenedMs = listenedMs,
                artistsCount = artistsCount,
                playlistsCount = playlistsCount,
                onClick = { navController.navigate(NavRoutes.statistics.name) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        StaggeredEntry(index = 3) {
            // GENERAL
            AccountSectionCard(title = stringResource(R.string.general).uppercase()) {
                AccountLinkRow(
                    title = stringResource(R.string.settings),
                    subtitle = stringResource(R.string.account_hint_settings),
                    iconId = R.drawable.settings
                ) {
                    navController.navigate(NavRoutes.settings.name)
                }
                AccountRowDivider()
                AccountLinkRow(
                    title = stringResource(R.string.statistics),
                    subtitle = stringResource(R.string.account_hint_statistics),
                    iconId = R.drawable.trending
                ) {
                    navController.navigate(NavRoutes.statistics.name)
                }
                AccountRowDivider()
                AccountLinkRow(
                    title = if (isSubscribed) stringResource(R.string.account_manage_sub)
                    else stringResource(R.string.pricing),
                    subtitle = if (isSubscribed) stringResource(R.string.account_hint_manage_sub)
                    else stringResource(R.string.account_hint_pricing),
                    iconId = if (isSubscribed) R.drawable.sparkles else R.drawable.globe
                ) {
                    openSubscriptionPage(context, authManager, isSubscribed)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        StaggeredEntry(index = 4) {
            // APARIENCIA / TEMA
            AccountSectionCard(title = stringResource(R.string.theme).uppercase()) {
                ThemeSegmentedControl(
                    current = colorPaletteMode,
                    onSelect = { colorPaletteMode = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        StaggeredEntry(index = 5) {
            // CUENTA
            AccountSectionCard(title = stringResource(R.string.account).uppercase()) {
                AccountLinkRow(
                    title = stringResource(R.string.logout),
                    subtitle = stringResource(R.string.account_hint_logout),
                    iconId = R.drawable.close
                ) {
                    showLogoutDialog = true
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

/**
 * Banner + overlapping avatar, mirroring the profile header pattern from the
 * reference design. The banner reads as a gradient of the theme's own greys, so
 * it works in both light and dark without introducing a brand colour.
 */
@Composable
private fun ProfileHeader(
    userName: String,
    userEmail: String,
    avatarUrl: String,
    onEdit: () -> Unit
) {
    val colors = colorPalette()
    val typo = typography()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.background1)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.background2,
                            colors.background1
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.background0.copy(alpha = 0.55f))
                    .pressable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.pencil),
                    contentDescription = stringResource(R.string.settings),
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-28).dp)
                .padding(horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.background2)
                    .border(
                        width = 3.dp,
                        color = colors.background1,
                        shape = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                    )
                } else {
                    BasicText(
                        text = userName.take(1).uppercase().ifEmpty { "?" },
                        style = typo.l.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            color = colors.text,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (userName.isNotEmpty()) {
                    BasicText(
                        text = userName,
                        style = typo.l.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                            color = colors.text
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (userEmail.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    BasicText(
                        text = userEmail,
                        style = typo.xs.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * "TU PLAN" card. The reference fills it with a lime gradient; here the emphasis
 * comes from the bordered surface plus the solid accent button, which is the one
 * high-contrast element in the card.
 */
@Composable
private fun PlanCard(
    isSubscribed: Boolean,
    subscriptionPlan: String,
    renewsAt: String,
    onAction: () -> Unit
) {
    val colors = colorPalette()
    val typo = typography()
    val renewalLabel = formatRenewalDate(renewsAt)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.background1)
            .border(
                width = 1.dp,
                color = colors.textDisabled.copy(alpha = 0.35f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = stringResource(R.string.profile_your_plan).uppercase(),
                style = typo.xxs.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = if (isSubscribed) subscriptionPlan.ifEmpty { "Premium" }
                else stringResource(R.string.subscription_free),
                style = typo.m.copy(
                    fontWeight = FontWeight.Bold,
                    color = colors.text
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Only shown when the backend actually gave us a date — an unknown
            // renewal must not be rendered as a made-up one.
            if (isSubscribed && renewalLabel != null) {
                Spacer(modifier = Modifier.height(3.dp))
                BasicText(
                    text = stringResource(R.string.profile_renews_on, renewalLabel),
                    style = typo.xxs.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors.accent)
                .pressable(onClick = onAction)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            BasicText(
                text = if (isSubscribed) stringResource(R.string.profile_manage)
                else stringResource(R.string.account_get_premium_title),
                style = typo.xs.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onAccent
                ),
                maxLines = 1
            )
        }
    }
}

/** Three equal tiles: listening minutes, artists heard, playlists owned. */
@Composable
private fun StatsStrip(
    listenedMs: Long,
    artistsCount: Int,
    playlistsCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatTile(
            value = formatCompact(listenedMs / 60_000L),
            label = stringResource(R.string.profile_stat_minutes).uppercase(),
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
        // Short, tile-specific labels: the generic R.string.playlists reads
        // "Listas de reproducción" in Spanish and gets ellipsised in a third of a row.
        StatTile(
            value = formatCompact(artistsCount.toLong()),
            label = stringResource(R.string.profile_stat_artists).uppercase(),
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            value = formatCompact(playlistsCount.toLong()),
            label = stringResource(R.string.profile_stat_playlists).uppercase(),
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    val typo = typography()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background1)
            .pressable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 6.dp)
    ) {
        BasicText(
            text = value,
            style = typo.l.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.text,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        BasicText(
            text = label,
            style = typo.xxs.copy(
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AccountSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = colorPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.background1)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        BasicText(
            text = title,
            style = typography().xxs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                letterSpacing = 1.sp
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun AccountLinkRow(
    title: String,
    subtitle: String,
    iconId: Int,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    ) {
        // Monochrome badge: the reference tints one per row, but the brand is
        // strictly black and white, so separation comes from the surface step.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background2),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography().s.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typography().xxs.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Image(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDisabled),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AccountRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().background0.copy(alpha = 0.4f))
    )
}

@Composable
private fun ThemeSegmentedControl(
    current: ColorPaletteMode,
    onSelect: (ColorPaletteMode) -> Unit
) {
    val colors = colorPalette()
    val typo = typography()
    val options = listOf(
        stringResource(R.string.light) to ColorPaletteMode.Light,
        stringResource(R.string.dark) to ColorPaletteMode.Dark,
        stringResource(R.string.auto) to ColorPaletteMode.System
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background0.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, mode) ->
            val isActive = current == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (isActive) colors.accent
                        else Color.Transparent
                    )
                    .pressable(onClick = { onSelect(mode) })
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = label,
                    style = typo.xs.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) colors.onAccent else colors.textSecondary
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** 940 -> "940", 1_200 -> "1.2k", 1_000_000 -> "1M". */
private fun formatCompact(value: Long): String {
    fun trim(text: String) = text.removeSuffix(".0")
    return when {
        value >= 1_000_000 -> trim(String.format(Locale.US, "%.1f", value / 1_000_000.0)) + "M"
        value >= 1_000 -> trim(String.format(Locale.US, "%.1f", value / 1_000.0)) + "k"
        else -> value.toString()
    }
}

/**
 * The backend sends the renewal date as an ISO timestamp. Returns null when the
 * field is empty so the caller can hide the line instead of showing a placeholder.
 */
private fun formatRenewalDate(raw: String): String? {
    if (raw.isBlank()) return null
    return runCatching {
        LocalDate.parse(raw.take(10))
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrElse { raw }
}

private fun openSubscriptionPage(
    context: android.content.Context,
    authManager: YammboAuthManager,
    isSubscribed: Boolean,
) {
    val userId = authManager.getUserId()
    val token = authManager.getAccessToken().orEmpty()
    val base = if (isSubscribed) "/app-music/billing" else "/app-music/pricing"

    val params = buildList {
        if (userId > 0) add("user_id=$userId")
        if (token.isNotEmpty()) add("token=" + Uri.encode(token))
        add("lang=es")
    }
    val qs = if (params.isEmpty()) "" else "?" + params.joinToString("&")
    val url = "https://music.yammbo.com$base$qs"

    YammboCustomTabs.open(context, url)
}
