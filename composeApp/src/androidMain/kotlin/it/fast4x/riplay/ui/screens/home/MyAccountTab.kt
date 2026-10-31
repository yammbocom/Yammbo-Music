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
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
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
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.styling.ColorPalette
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
    //
    // rememberSaveable rather than collectAsState(initial = 0): the tabs live inside a
    // SaveableStateProvider keyed by tab index feeding a `when`, so leaving this tab
    // disposes it outright. With a plain remember every counter fell back to its initial
    // 0 on return and only refilled once Room had re-run the aggregate — the reported
    // "48 artists, switch tab, come back, 0 artists". A saveable survives that disposal,
    // so the last known figure stays on screen while the query runs again underneath.
    var listenedMs by rememberSaveable { mutableLongStateOf(0L) }
    var artistsCount by rememberSaveable { mutableIntStateOf(0) }
    var playlistsCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            launch { Database.totalListeningTimeMs().collect { listenedMs = it } }
            launch { Database.listenedArtistsCount().collect { artistsCount = it } }
            launch { Database.playlistsCount().collect { playlistsCount = it } }
        }
    }

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
            .padding(horizontal = rememberNoirLayout().sidePadding(NoirContentMax.Reading, 20.dp))
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Stagger entry: header leads, then stats, plan and the cards land 40ms
        // apart. Indexes match visual order.
        StaggeredEntry(index = 0) {
            ProfileHeader(
                userName = userName,
                userEmail = userEmail,
                avatarUrl = userAvatar,
                planLabel = if (isSubscribed) subscriptionPlan.ifEmpty { "Premium" }
                else stringResource(R.string.subscription_free)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        StaggeredEntry(index = 1) {
            StatsStrip(
                listenedMs = listenedMs,
                artistsCount = artistsCount,
                playlistsCount = playlistsCount,
                onClick = { navController.navigate(NavRoutes.statistics.name) }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        StaggeredEntry(index = 2) {
            PlanCard(
                isSubscribed = isSubscribed,
                subscriptionPlan = subscriptionPlan,
                renewsAt = renewsAt,
                onAction = { openSubscriptionPage(context, authManager, isSubscribed) }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        StaggeredEntry(index = 3) {
            Column {
                NoirEyebrow(text = stringResource(R.string.theme))
                Spacer(modifier = Modifier.height(8.dp))
                ThemeSegmentedControl(
                    current = colorPaletteMode,
                    onSelect = { colorPaletteMode = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        StaggeredEntry(index = 4) {
            AccountSectionCard {
                AccountLinkRow(
                    title = stringResource(R.string.settings),
                    iconId = R.drawable.settings
                ) {
                    navController.navigate(NavRoutes.settings.name)
                }
                AccountRowDivider()
                AccountLinkRow(
                    title = stringResource(R.string.statistics),
                    iconId = R.drawable.trending
                ) {
                    navController.navigate(NavRoutes.statistics.name)
                }
                AccountRowDivider()
                AccountLinkRow(
                    title = if (isSubscribed) stringResource(R.string.account_manage_sub)
                    else stringResource(R.string.pricing),
                    iconId = if (isSubscribed) R.drawable.sparkles else R.drawable.globe
                ) {
                    openSubscriptionPage(context, authManager, isSubscribed)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        StaggeredEntry(index = 5) {
            // Outlined, never filled: the one solid-white action on this screen is the plan card.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, colors.text.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .pressable(onClick = { showLogoutDialog = true })
            ) {
                BasicText(
                    text = stringResource(R.string.logout),
                    style = typography().s.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text
                    ),
                    maxLines = 1
                )
            }
        }

        val versionName = remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty()
        }
        if (versionName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            NoirEyebrow(
                text = "Yammbo Music $versionName",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

private fun accountHairline(colors: ColorPalette) = colors.text.copy(alpha = 0.10f)

/** Avatar disc (white with a black initial), name, email and the plan tag. */
@Composable
private fun ProfileHeader(
    userName: String,
    userEmail: String,
    avatarUrl: String,
    planLabel: String
) {
    val colors = colorPalette()
    val typo = typography()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.text),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                BasicText(
                    text = userName.take(1).uppercase().ifEmpty { "?" },
                    style = typo.l.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        color = colors.background0,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (userName.isNotEmpty()) {
                BasicText(
                    text = userName,
                    style = typo.l.tight.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = colors.text
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (userEmail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = userEmail,
                    style = typo.xs.copy(color = colors.textSecondary, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = planLabel.uppercase(),
                style = typo.xxxs.mono.copy(color = colors.text, fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .border(1.dp, colors.text.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * Plan card. Free users get the solid-white "Hazte Premium" card (the one primary
 * action of the screen); subscribers get a hairline card showing plan and renewal.
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
    val shape = RoundedCornerShape(22.dp)

    // Free: white card with a black button; subscribed: dark card with a white button.
    val cardBg = if (isSubscribed) colors.background1 else colors.text
    val onCard = if (isSubscribed) colors.text else colors.background0
    val onCardSecondary =
        if (isSubscribed) colors.textSecondary else colors.background0.copy(alpha = 0.65f)
    val buttonBg = if (isSubscribed) colors.text else colors.background0
    val onButton = if (isSubscribed) colors.background0 else colors.text

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .then(
                if (isSubscribed) Modifier.border(1.dp, accountHairline(colors), shape)
                else Modifier
            )
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (isSubscribed) {
                NoirEyebrow(text = stringResource(R.string.profile_your_plan))
                Spacer(modifier = Modifier.height(6.dp))
            }
            BasicText(
                text = if (isSubscribed) subscriptionPlan.ifEmpty { "Premium" }
                else stringResource(R.string.account_get_premium_title),
                style = typo.m.tight.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = onCard
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isSubscribed) {
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.account_premium_pitch),
                    style = typo.xs.copy(color = onCardSecondary, fontSize = 13.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (renewalLabel != null) {
                // Only shown when the backend actually gave us a date: an unknown
                // renewal must not be rendered as a made-up one.
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.profile_renews_on, renewalLabel),
                    style = typo.xs.copy(color = onCardSecondary, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(buttonBg)
                .pressable(onClick = onAction)
                .padding(horizontal = 14.dp)
        ) {
            BasicText(
                text = if (isSubscribed) stringResource(R.string.profile_manage)
                else stringResource(R.string.account_view_plans),
                style = typo.xs.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = onButton
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatTile(
            value = formatCompact(listenedMs / 60_000L),
            label = stringResource(R.string.profile_stat_minutes),
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            value = formatCompact(artistsCount.toLong()),
            label = stringResource(R.string.profile_stat_artists),
            onClick = onClick,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            value = formatCompact(playlistsCount.toLong()),
            label = stringResource(R.string.profile_stat_playlists),
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
    val shape = RoundedCornerShape(18.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(shape)
            .background(colors.background1)
            .border(1.dp, accountHairline(colors), shape)
            .pressable(onClick = onClick)
            .padding(14.dp)
    ) {
        BasicText(
            text = value,
            style = typo.l.tight.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = colors.text
            ),
            maxLines = 1
        )
        BasicText(
            text = label,
            style = typo.xs.copy(color = colors.textSecondary, fontSize = 12.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AccountSectionCard(
    content: @Composable () -> Unit
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background1)
            .border(1.dp, accountHairline(colors), shape)
    ) {
        content()
    }
}

@Composable
private fun AccountLinkRow(
    title: String,
    iconId: Int,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.text),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        BasicText(
            text = title,
            style = typography().s.copy(fontSize = 15.sp, color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Image(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AccountRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(accountHairline(colorPalette()))
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
        stringResource(R.string.account_theme_system) to ColorPaletteMode.System
    )
    val outer = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(colors.background1)
            .border(1.dp, accountHairline(colors), outer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, mode) ->
            val isActive = current == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) colors.text else Color.Transparent)
                    .pressable(onClick = { onSelect(mode) }),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = label,
                    style = typo.xs.copy(
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isActive) colors.background0 else colors.text
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
