package it.fast4x.riplay.ui.screens.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yambo.music.R
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.screens.auth.AuthHeadline
import it.fast4x.riplay.ui.screens.auth.AuthLogo
import it.fast4x.riplay.ui.screens.auth.AuthPrimaryButton
import it.fast4x.riplay.ui.screens.auth.AuthSubtitle
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onAllPermissionsGranted: () -> Unit, // Callback quando l'utente clicca "Inizia"
) {
    val context = LocalContext.current
    val colors = colorPalette()

    // Stato per forzare la ricomposizione quando lo stato dei permessi cambia
    // In un'app reale useresti un ViewModel, qui usiamo remember per semplicità
    var refreshTrigger by remember { mutableStateOf(0) }

    // Helper per controllare lo stato
    fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Definizione degli elementi (La lista viene ricalcolata quando refreshTrigger cambia)
    val items by remember(refreshTrigger) {
        derivedStateOf {
            buildList {

                add(
                    OnboardingSection(
                        title = "Permessi"
                    )
                )

                // 1. Notifiche (Richiesto su Android 13+)
                val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkPermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    true // Su Android < 13 è concesso di default col manifest
                }

                add(
                    OnboardingItem(
                        id = "notifications",
                        title = "Notifiche",
                        description = "Ricevi notifiche quando cambi canzone o controlli la musica in background.",
                        icon = R.drawable.notifications,
                        status = if (notificationGranted) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED,
                        onRequest = {
                            // Qui inietteresti il launcher: notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            // Per ora simuliamo il cambio stato
                            refreshTrigger++
                        }
                    )
                )

                // 2. Memorizzazione (Storage / Lettura File)
                // Nota: Su Android 13+ READ_MEDIA_AUDIO è preferito a READ_EXTERNAL_STORAGE
                val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkPermission(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                add(
                    OnboardingItem(
                        id = "storage",
                        title = "Accesso File Musicali",
                        description = "Permetti all'app di leggere la libreria musicale dal tuo dispositivo.",
                        icon = R.drawable.folder,
                        status = if (storageGranted) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED,
                        onRequest = {
                            // storagePermissionLauncher.launch(...)
                            refreshTrigger++
                        }
                    )
                )

                // 3. Bluetooth (Per la logica delle cuffie)
                val btGranted = checkPermission(Manifest.permission.BLUETOOTH_CONNECT)

                add(
                    OnboardingItem(
                        id = "bluetooth",
                        title = "Bluetooth",
                        description = "Permetti all'app di rilevare le cuffie per mettere in pausa/riprendere la musica.",
                        icon = R.drawable.ui,
                        status = if (btGranted) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED,
                        onRequest = {
                            // btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            refreshTrigger++
                        }
                    )
                )

                // 4. Ottimizzazione Batteria (Non è un permesso, ma un intent)
                // Verifichiamo se siamo nella lista delle eccezioni (semplificato)
                val isIgnoringBattery = true // Qui servirebbe un PowerManager check reale

                add(
                    OnboardingItem(
                        id = "battery",
                        title = "Ottimizzazione Batteria",
                        description = "Evita che il sistema arresti la musica quando lo schermo è spento.",
                        icon = R.drawable.chevron_back,
                        status = if (isIgnoringBattery) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            // Nota: non possiamo sapere subito se l'utente ha cliccato "Permetti",
                            // quindi in una app reale useresti un ActivityResultLauncher per controllare il risultato.
                        }
                    )
                )

                add(
                    OnboardingItem(
                        id = "battery",
                        title = "Ottimizzazione Batteria",
                        description = "Evita che il sistema arresti la musica quando lo schermo è spento.",
                        icon = R.drawable.chevron_back,
                        status = if (isIgnoringBattery) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            // Nota: non possiamo sapere subito se l'utente ha cliccato "Permetti",
                            // quindi in una app reale useresti un ActivityResultLauncher per controllare il risultato.
                        }
                    )
                )

                add(
                    OnboardingItem(
                        id = "battery",
                        title = "Ottimizzazione Batteria",
                        description = "Evita che il sistema arresti la musica quando lo schermo è spento.",
                        icon = R.drawable.chevron_back,
                        status = if (isIgnoringBattery) PermissionStatus.GRANTED else PermissionStatus.NOT_REQUESTED,
                        onRequest = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            // Nota: non possiamo sapere subito se l'utente ha cliccato "Permetti",
                            // quindi in una app reale useresti un ActivityResultLauncher per controllare il risultato.
                        }
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background0)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = rememberNoirLayout().sidePadding(NoirContentMax.Reading, 24.dp))
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        AuthLogo(modifier = Modifier.align(Alignment.Start))

        Spacer(modifier = Modifier.height(18.dp))

        AuthHeadline(text = "Configura la tua esperienza musicale perfetta")

        Spacer(modifier = Modifier.height(6.dp))

        AuthSubtitle(
            text = "Consenti queste opzioni per sfruttare al meglio tutte le funzionalità dell'app."
        )

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items) { item ->
                when (item) {
                    is OnboardingItem -> PermissionCard(item = item)
                    is OnboardingSection -> PermissionSection(item = item)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pulsante finale
        AuthPrimaryButton(
            text = "Inizia",
            onClick = { onAllPermissionsGranted() },
            enabled = true //items.all { it.status == PermissionStatus.GRANTED }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PermissionSection(item: OnboardingSection) {
    NoirEyebrow(
        text = item.title,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun PermissionCard(item: OnboardingItem) {
    val colors = colorPalette()
    val typo = typography()
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.background1)
            .border(1.dp, colors.text.copy(alpha = 0.10f), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icona a sinistra
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colors.background2),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(item.icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Testo centrale
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BasicText(
                text = item.title,
                style = typo.s.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text
                )
            )
            BasicText(
                text = item.description,
                style = typo.xxs.copy(fontSize = 12.sp, color = colors.textSecondary)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Pulsante Azione a destra
        ActionButton(status = item.status, onClick = item.onRequest)
    }
}

@Composable
fun ActionButton(status: PermissionStatus, onClick: () -> Unit) {
    val colors = colorPalette()
    when (status) {
        PermissionStatus.GRANTED -> {
            Image(
                painter = painterResource(id = R.drawable.checkmark),
                contentDescription = "Concesso",
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(24.dp)
            )
        }
        PermissionStatus.NOT_REQUESTED, PermissionStatus.DENIED,
        PermissionStatus.PERMANENTLY_DENIED -> {
            val shape = RoundedCornerShape(12.dp)
            val toSettings = status == PermissionStatus.PERMANENTLY_DENIED
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(shape)
                    .border(1.dp, colors.text.copy(alpha = 0.22f), shape)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = if (toSettings) "Impostazioni" else "Concedi",
                    style = typography().xs.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
