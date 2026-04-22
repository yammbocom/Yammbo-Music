package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.ColorPaletteMode
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.preferences.colorPaletteModeKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import android.content.Intent
import android.net.Uri
import it.fast4x.riplay.extensions.customtabs.YammboCustomTabs
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.service.PlayerService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.themed.ConfirmationDialog
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


@Composable
fun MyAccountTab(
    navController: NavController
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val authManager = remember { YammboAuthManager(context) }

    val userName = authManager.getUserName()
    val userEmail = authManager.getUserEmail()
    val isLoggedIn = authManager.isLoggedIn()

    var colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)
    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    var showLogoutDialog by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(authManager.isSubscriptionActive()) }
    var subscriptionPlan by remember { mutableStateOf(authManager.getSubscriptionPlan()) }

    // Re-check subscription on every resume
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
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Profile header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.background1)
                .padding(vertical = 32.dp, horizontal = 24.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = userName.take(1).uppercase().ifEmpty { "?" },
                    style = typo.l.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = colors.background0,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (userName.isNotEmpty()) {
                BasicText(
                    text = userName,
                    style = typo.l.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (userEmail.isNotEmpty()) {
                BasicText(
                    text = userEmail,
                    style = typo.s.secondary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Subscription badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSubscribed) colors.accent.copy(alpha = 0.15f)
                        else colors.background0
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = painterResource(
                            if (isSubscribed) R.drawable.star else R.drawable.shield_checkmark
                        ),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(
                            if (isSubscribed) colors.accent else colors.textSecondary
                        ),
                        modifier = Modifier.size(14.dp)
                    )
                    BasicText(
                        text = if (isSubscribed) subscriptionPlan.ifEmpty { "Premium" }
                        else stringResource(R.string.subscription_free),
                        style = typo.xs.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSubscribed) colors.accent else colors.textSecondary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subscription card
        if (!isSubscribed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent.copy(alpha = 0.1f))
                    .clickable {
                        openSubscriptionPage(context, authManager, isSubscribed = false)
                    }
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicText(
                            text = "Hazte Premium",
                            style = typo.xs.copy(
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicText(
                            text = "Musica sin limites, sin anuncios",
                            style = typo.xxs.copy(
                                color = colors.accent.copy(alpha = 0.8f)
                            )
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.chevron_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.accent),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // General section
        AccountSectionCard(title = "General") {
            AccountLinkRow(
                title = stringResource(R.string.settings),
                subtitle = "Personaliza tu experiencia",
                iconId = R.drawable.settings
            ) {
                navController.navigate(NavRoutes.settings.name)
            }
            AccountLinkRow(
                title = stringResource(R.string.pricing),
                subtitle = if (isSubscribed) "Gestionar suscripcion" else "Ver planes disponibles",
                iconId = R.drawable.globe
            ) {
                openSubscriptionPage(context, authManager, isSubscribed)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Appearance section
        AccountSectionCard(title = stringResource(R.string.theme)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                listOf(
                    "Claro" to ColorPaletteMode.Light,
                    "Oscuro" to ColorPaletteMode.Dark,
                    "Auto" to ColorPaletteMode.System
                ).forEach { (label, mode) ->
                    val isActive = colorPaletteMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) colors.accent
                                else colors.background0
                            )
                            .clickable { colorPaletteMode = mode }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = label,
                            style = typo.xs.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) colors.background0 else colors.textSecondary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Account actions
        AccountSectionCard(title = "Cuenta") {
            AccountLinkRow(
                title = stringResource(R.string.logout),
                subtitle = "Cerrar sesion",
                iconId = R.drawable.close,
                tintColor = colors.red
            ) {
                showLogoutDialog = true
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

@Composable
private fun AccountSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorPalette().background1)
            .padding(16.dp)
    ) {
        BasicText(
            text = title.uppercase(),
            style = typography().xxs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().textSecondary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun AccountLinkRow(
    title: String,
    subtitle: String,
    iconId: Int,
    tintColor: androidx.compose.ui.graphics.Color = colorPalette().text,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorPalette().background0)
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tintColor),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography().xs.copy(
                    fontWeight = FontWeight.Medium,
                    color = tintColor
                )
            )
            BasicText(
                text = subtitle,
                style = typography().xxs.secondary
            )
        }
        Image(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().textDisabled),
            modifier = Modifier.size(16.dp)
        )
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
