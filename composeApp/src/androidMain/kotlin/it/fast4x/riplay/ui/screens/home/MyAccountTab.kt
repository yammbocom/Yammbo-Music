package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.ColorPaletteMode
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.preferences.colorPaletteModeKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import android.content.Intent
import it.fast4x.riplay.service.PlayerService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.themed.ConfirmationDialog
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.components.themed.Switch
import it.fast4x.riplay.ui.screens.settings.SettingsEntry
import it.fast4x.riplay.ui.screens.settings.SwitchSettingEntry
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.ui.styling.semiBold


@Composable
fun MyAccountTab(
    navController: NavController
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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

    val colors = colorPalette()
    val typo = typography()

    if (showLogoutDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.logout_confirmation),
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                context.stopService(Intent(context, PlayerService::class.java))
                authManager.logout()
                showLogoutDialog = false
                navController.navigate(NavRoutes.login.name) {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
    }

    Box(
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HeaderWithIcon(
                title = stringResource(R.string.my_account),
                iconId = R.drawable.person,
                enabled = true,
                showIcon = true,
                modifier = Modifier,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(8.dp))

            // User info section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background4)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar placeholder
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.background0),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (userName.isNotEmpty()) {
                    BasicText(
                        text = userName,
                        style = typo.l.semiBold.copy(color = colors.text)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (userEmail.isNotEmpty()) {
                    BasicText(
                        text = userEmail,
                        style = typo.s.copy(color = colors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Subscription badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.textDisabled.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.subscription_free),
                        style = typo.xs.semiBold.copy(
                            color = colors.textSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Settings button
            SettingsEntry(
                title = stringResource(R.string.settings),
                text = "",
                onClick = {
                    navController.navigate(NavRoutes.settings.name)
                },
                trailingContent = {
                    Image(
                        painter = painterResource(R.drawable.chevron_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textSecondary),
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            // Pricing button
            SettingsEntry(
                title = stringResource(R.string.pricing),
                text = "",
                onClick = {
                    val userId = authManager.getUserId()
                    val pricingUrl = if (userId > 0)
                        "https://music.yammbo.com/app-pricing?user_id=$userId"
                    else
                        "https://music.yammbo.com/app-pricing"
                    uriHandler.openUri(pricingUrl)
                },
                trailingContent = {
                    Image(
                        painter = painterResource(R.drawable.globe),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textSecondary),
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            // Theme toggle
            SwitchSettingEntry(
                title = stringResource(R.string.theme),
                text = when (colorPaletteMode) {
                    ColorPaletteMode.Light -> stringResource(R.string.light)
                    ColorPaletteMode.Dark -> stringResource(R.string.dark)
                    ColorPaletteMode.System -> stringResource(R.string.system)
                    else -> stringResource(R.string.dark)
                },
                isChecked = colorPaletteMode == ColorPaletteMode.Dark,
                onCheckedChange = {
                    colorPaletteMode = if (it) ColorPaletteMode.Dark else ColorPaletteMode.Light
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Logout button
            SettingsEntry(
                title = stringResource(R.string.logout),
                text = "",
                onClick = { showLogoutDialog = true },
                trailingContent = {
                    Image(
                        painter = painterResource(R.drawable.chevron_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textSecondary),
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
