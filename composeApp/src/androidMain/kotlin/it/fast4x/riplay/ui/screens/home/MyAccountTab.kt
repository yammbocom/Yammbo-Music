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
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
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


// Section-coloured badge tints — reuse the same palette as Mi Música / OnDevice so
// the user gets consistent colour identity across the app.
private val SettingsTint = Color(0xFF5C6BC0)   // indigo
private val PricingTint = Color(0xFFFFA726)    // amber
private val PremiumTint = Color(0xFFAB47BC)    // purple
private val LogoutTint = Color(0xFFEF5350)     // red


@Composable
fun MyAccountTab(
    navController: NavController
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val authManager = remember { YammboAuthManager(context) }

    val userName = authManager.getUserName()
    val userEmail = authManager.getUserEmail()

    var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)
    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    var showLogoutDialog by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(authManager.isSubscriptionActive()) }
    var subscriptionPlan by remember { mutableStateOf(authManager.getSubscriptionPlan()) }

    LifecycleResumeEffect(Unit) {
        val userId = authManager.getUserId()
        if (userId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                YammboApiService.checkSubscription(userId).onSuccess { response ->
                    authManager.saveSubscriptionStatus(response)
                    isSubscribed = response.subscribed
                    subscriptionPlan = response.plan.orEmpty()
                }
            }
        }
        onPauseOrDispose { }
    }

    val colors = colorPalette()
    val typo = typography()

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
        Spacer(modifier = Modifier.height(32.dp))

        // Stagger entry: hero leads, then promo (if free), then the three
        // section cards land 40ms apart. Indexes match visual order.
        StaggeredEntry(index = 0) {
            ProfileHeroCard(
                userName = userName,
                userEmail = userEmail,
                isSubscribed = isSubscribed,
                subscriptionPlan = subscriptionPlan
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isSubscribed) {
            StaggeredEntry(index = 1) {
                PremiumPromoCard(
                    onClick = {
                        openSubscriptionPage(context, authManager, isSubscribed = false)
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        StaggeredEntry(index = 2) {
            // GENERAL
            AccountSectionCard(title = stringResource(R.string.general).uppercase()) {
                AccountLinkRow(
                    title = stringResource(R.string.settings),
                    subtitle = stringResource(R.string.account_hint_settings),
                    iconId = R.drawable.settings,
                    tint = SettingsTint
                ) {
                    navController.navigate(NavRoutes.settings.name)
                }
                AccountRowDivider()
                AccountLinkRow(
                    title = if (isSubscribed) stringResource(R.string.account_manage_sub)
                    else stringResource(R.string.pricing),
                    subtitle = if (isSubscribed) stringResource(R.string.account_hint_manage_sub)
                    else stringResource(R.string.account_hint_pricing),
                    iconId = if (isSubscribed) R.drawable.sparkles else R.drawable.globe,
                    tint = if (isSubscribed) PremiumTint else PricingTint
                ) {
                    openSubscriptionPage(context, authManager, isSubscribed)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        StaggeredEntry(index = 3) {
            // APARIENCIA / TEMA
            AccountSectionCard(title = stringResource(R.string.theme).uppercase()) {
                ThemeSegmentedControl(
                    current = colorPaletteMode,
                    onSelect = { colorPaletteMode = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        StaggeredEntry(index = 4) {
            // CUENTA
            AccountSectionCard(title = stringResource(R.string.account).uppercase()) {
                AccountLinkRow(
                    title = stringResource(R.string.logout),
                    subtitle = stringResource(R.string.account_hint_logout),
                    iconId = R.drawable.close,
                    tint = LogoutTint,
                    titleColor = LogoutTint
                ) {
                    showLogoutDialog = true
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

@Composable
private fun ProfileHeroCard(
    userName: String,
    userEmail: String,
    isSubscribed: Boolean,
    subscriptionPlan: String
) {
    val colors = colorPalette()
    val typo = typography()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = 0.22f),
                        colors.background2.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(vertical = 28.dp, horizontal = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar with subtle accent ring
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .border(width = 3.dp, color = Color.White.copy(alpha = 0.18f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = userName.take(1).uppercase().ifEmpty { "?" },
                    style = typo.l.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                        color = colors.onAccent,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (userName.isNotEmpty()) {
                BasicText(
                    text = userName,
                    style = typo.l.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.text,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (userEmail.isNotEmpty()) {
                BasicText(
                    text = userEmail,
                    style = typo.s.secondary.copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Plan badge
            val badgeBg = if (isSubscribed) PremiumTint.copy(alpha = 0.22f)
            else colors.background0.copy(alpha = 0.6f)
            val badgeFg = if (isSubscribed) PremiumTint else colors.textSecondary
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(badgeBg)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(
                        if (isSubscribed) R.drawable.sparkles else R.drawable.shield_checkmark
                    ),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(badgeFg),
                    modifier = Modifier.size(14.dp)
                )
                BasicText(
                    text = if (isSubscribed) subscriptionPlan.ifEmpty { "Premium" }
                    else stringResource(R.string.subscription_free),
                    style = typo.xs.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = badgeFg
                    )
                )
            }
        }
    }
}

@Composable
private fun PremiumPromoCard(onClick: () -> Unit) {
    val colors = colorPalette()
    val typo = typography()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        PremiumTint.copy(alpha = 0.85f),
                        colors.accent.copy(alpha = 0.78f)
                    )
                )
            )
            .pressable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.sparkles),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = stringResource(R.string.account_get_premium_title),
                    style = typo.m.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                BasicText(
                    text = stringResource(R.string.account_get_premium_subtitle),
                    style = typo.xs.copy(
                        color = Color.White.copy(alpha = 0.9f)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(R.drawable.chevron_forward),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(20.dp)
            )
        }
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
    tint: Color,
    titleColor: Color? = null,
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
        // Colored badge — gives each row its own identity (consistent with the rest
        // of the app's section colours).
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography().s.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor ?: colors.text
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
