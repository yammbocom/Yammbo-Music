package it.fast4x.riplay.ui.screens.auth

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yambo.music.R
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV Login Screen with QR code device-pairing flow.
 * Polls the backend every 3 seconds. When linked, saves token and goes home.
 */
@Composable
fun LoginScreenTV(
    navController: NavController,
    authManager: YammboAuthManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf<String?>(null) }
    var confirmUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Generando código...") }

    suspend fun generateCode() {
        isGenerating = true
        error = null
        statusText = "Generando código..."
        YammboApiService.tvLinkGenerate()
            .onSuccess { resp ->
                if (resp.code != null && resp.confirmUrl != null) {
                    code = resp.code
                    confirmUrl = resp.confirmUrl
                    statusText = "Esperando confirmación..."
                } else {
                    error = "Respuesta inválida del servidor"
                }
            }
            .onFailure { error = "Error de conexión: ${it.message}" }
        isGenerating = false
    }

    // Generate initial code
    LaunchedEffect(Unit) { generateCode() }

    // Poll loop
    LaunchedEffect(code) {
        val current = code ?: return@LaunchedEffect
        while (true) {
            delay(3_000)
            val result = YammboApiService.tvLinkPoll(current)
            result.onSuccess { resp ->
                when (resp.status) {
                    "linked" -> {
                        if (resp.accessToken != null) {
                            authManager.saveTvLinkUser(resp.accessToken, resp.user)
                            navController.navigate(NavRoutes.home.name) {
                                popUpTo(0) { inclusive = true }
                            }
                            return@LaunchedEffect
                        }
                    }
                    "expired", "invalid" -> {
                        statusText = "Código expirado, generando uno nuevo..."
                        scope.launch { generateCode() }
                        return@LaunchedEffect
                    }
                    // "pending" → keep polling
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            .padding(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(64.dp)
        ) {
            // Left: instructions
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.yambo_icon),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                BasicText(
                    text = "Inicia sesión con tu teléfono",
                    style = typography().l.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                )
                StepRow(num = "1", text = "Escanea el código QR con tu teléfono")
                StepRow(num = "2", text = "O visita music.yammbo.com/tv-link")
                StepRow(
                    num = "3",
                    text = if (code != null)
                        "E ingresa este código: ${code}"
                    else
                        "Espera el código..."
                )

                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    text = statusText,
                    style = typography().s.copy(color = colorPalette().textSecondary)
                )

                if (error != null) {
                    BasicText(
                        text = error.orEmpty(),
                        style = typography().s.copy(color = colorPalette().red)
                    )
                }
            }

            // Right: QR code
            Box(
                modifier = Modifier
                    .size(380.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = colorPalette().accent)
                } else if (confirmUrl != null) {
                    val qrPainter = rememberQrCodePainter(data = confirmUrl.orEmpty())
                    Image(
                        painter = qrPainter,
                        contentDescription = "QR para vincular TV",
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(num: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorPalette().accent),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = num,
                style = typography().s.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        BasicText(
            text = text,
            style = typography().m.copy(
                color = colorPalette().text,
                fontSize = 18.sp
            )
        )
    }
}
