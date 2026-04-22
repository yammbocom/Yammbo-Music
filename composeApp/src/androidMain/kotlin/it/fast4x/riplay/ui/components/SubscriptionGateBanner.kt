package it.fast4x.riplay.ui.components

import it.fast4x.riplay.extensions.customtabs.YammboCustomTabs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yambo.music.R
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.utils.colorPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SubscriptionGateOverlay(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { YammboAuthManager(context) }
    val colors = colorPalette()

    var isSubscribed by remember { mutableStateOf(authManager.isSubscriptionActive()) }

    // Re-check subscription on every resume (e.g. after returning from pricing page)
    LifecycleResumeEffect(Unit) {
        val userId = authManager.getUserId()
        if (userId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                YammboApiService.checkSubscription(userId).onSuccess { response ->
                    authManager.saveSubscriptionStatus(response)
                    isSubscribed = response.subscribed
                }
            }
        } else {
            isSubscribed = authManager.isSubscriptionActive()
        }
        onPauseOrDispose { }
    }

    // If user is subscribed, just show content without overlay
    if (isSubscribed) {
        content()
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content behind with blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(12.dp)
        ) {
            content()
        }

        // Overlay that blocks all touch
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background0.copy(alpha = 0.7f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume all clicks */ },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.background1)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.shield_checkmark),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Contenido Premium",
                    color = colors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Suscríbete para desbloquear todas las opciones de configuración",
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        val userId = authManager.getUserId()
                        val pricingUrl = if (userId > 0)
                            "https://music.yammbo.com/app-music/pricing?user_id=$userId"
                        else
                            "https://music.yammbo.com/app-music/pricing"
                        YammboCustomTabs.open(context, pricingUrl)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Ver Planes",
                        color = colors.onAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
