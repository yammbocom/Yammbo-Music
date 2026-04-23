package it.fast4x.riplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.extensions.yammboapi.YammboSession
import java.awt.Desktop
import java.net.URI

// Top-bar Yammbo account widget for desktop.
// - Not logged in: "Iniciar sesión" pill that opens YammboLoginDialog.
// - Logged in: avatar-circle-with-initial + name, click → dropdown with
//   subscription status, billing/pricing, logout.
//
// On first mount (and on login) it triggers a background subscription refresh
// so the plan badge stays accurate across app launches.
@Composable
fun YammboAccountWidget() {
    val state by YammboSession.state.collectAsState()
    var showLogin by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    // Refresh subscription when we learn we're logged in.
    LaunchedEffect(state.loggedIn, state.userId) {
        if (state.loggedIn && state.userId > 0) {
            YammboSession.refreshSubscriptionStatus()
        }
    }

    val accent = Color(0xFFFF6B6B)
    val pillBg = Color(0xFF1E1E1E)

    if (!state.loggedIn) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accent, RoundedCornerShape(20.dp))
                .clickable { showLogin = true }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Iniciar sesión",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    } else {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(pillBg, RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .clickable { menuExpanded = true }
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Avatar(name = state.name.ifEmpty { state.email }, accent = accent)
                Spacer(Modifier.width(8.dp))
                val label = state.name.ifEmpty { state.email }.take(22)
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (state.subscriptionActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "PREMIUM",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Color(0xFF1A1A1A))
            ) {
                if (state.subscriptionActive) {
                    DropdownMenuItem(
                        text = { Text("Gestionar suscripción", color = Color.White) },
                        onClick = {
                            menuExpanded = false
                            openBilling(state.userId, YammboSession.getAccessToken())
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Actualizar a Premium", color = accent) },
                        onClick = {
                            menuExpanded = false
                            openPricing(state.userId)
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (state.subscriptionActive)
                                "Plan: ${state.plan.ifEmpty { "activo" }}"
                            else "Sin suscripción",
                            color = Color(0xFFB0B0B0),
                            fontSize = 12.sp
                        )
                    },
                    onClick = { /* info only */ },
                    enabled = false
                )
                DropdownMenuItem(
                    text = { Text("Refrescar estado", color = Color.White) },
                    onClick = {
                        menuExpanded = false
                        YammboSession.refreshSubscriptionStatus()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Cerrar sesión", color = Color(0xFFFF8A8A)) },
                    onClick = {
                        menuExpanded = false
                        YammboSession.logout()
                    }
                )
            }
        }
    }

    if (showLogin) {
        YammboLoginDialog(onDismiss = { showLogin = false })
    }
}

@Composable
private fun Avatar(name: String, accent: Color) {
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .background(accent.copy(alpha = 0.25f), CircleShape)
            .border(1.dp, accent, CircleShape)
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun openBilling(userId: Int, token: String?) {
    val url = buildString {
        append("https://music.yammbo.com/app-music/billing?user_id=")
        append(userId)
        if (!token.isNullOrEmpty()) {
            append("&token=")
            append(token)
        }
        append("&lang=es")
    }
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

private fun openPricing(userId: Int) {
    val url = if (userId > 0)
        "https://music.yammbo.com/app-music/pricing?user_id=$userId"
    else
        "https://music.yammbo.com/app-music/pricing"
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
