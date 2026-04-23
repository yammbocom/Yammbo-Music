package it.fast4x.riplay.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yambo.music.BuildConfig
import com.yambo.music.R
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


@ExperimentalAnimationApi
@Composable
fun About() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Logo + App name + Version
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.yambo_icon),
                contentDescription = "Yammbo Music",
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            BasicText(
                text = "Yammbo Music",
                style = typography().l.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = "v${BuildConfig.VERSION_NAME}",
                style = typography().s.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = "Tu musica, tu mundo",
                style = typography().xs.secondary.copy(
                    textAlign = TextAlign.Center
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Social section
        SectionCard(title = "Redes Sociales") {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                SocialIcon("Fb", "Facebook") {
                    uriHandler.openUri("https://www.facebook.com/yammbo")
                }
                SocialIcon("Ig", "Instagram") {
                    uriHandler.openUri("https://instagram.com/yammbo_com")
                }
                SocialIcon("X", "X") {
                    uriHandler.openUri("https://x.com/yammbo_com")
                }
                SocialIcon("Tk", "TikTok") {
                    uriHandler.openUri("https://www.tiktok.com/@yammbo_com")
                }
                SocialIcon("Wa", "WhatsApp") {
                    uriHandler.openUri("https://api.whatsapp.com/send?phone=5623464876")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Support section
        SectionCard(title = "Soporte") {
            LinkRow(
                title = "Yammbo Music",
                subtitle = "music.yammbo.com",
                iconId = R.drawable.link
            ) {
                uriHandler.openUri("https://music.yammbo.com")
            }
            LinkRow(
                title = "Centro de Ayuda",
                subtitle = "Base de conocimiento",
                iconId = R.drawable.information
            ) {
                uriHandler.openUri("https://yammbo.zohodesk.com/portal/en/kb/yammbo-llc")
            }
            LinkRow(
                title = "Chat en Vivo",
                subtitle = "Habla con nosotros",
                iconId = R.drawable.help_circle
            ) {
                uriHandler.openUri("https://tawk.to/yammbo")
            }
            LinkRow(
                title = stringResource(R.string.report_an_issue),
                subtitle = "Reportar un problema",
                iconId = R.drawable.alert_circle
            ) {
                uriHandler.openUri("https://music.yammbo.com/support")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legal section
        SectionCard(title = "Legal") {
            LinkRow(
                title = "Politica de Privacidad",
                subtitle = "Como protegemos tus datos",
                iconId = R.drawable.shield_checkmark
            ) {
                uriHandler.openUri("https://music.yammbo.com/pages/privacy-policy")
            }
            LinkRow(
                title = "Terminos de Servicio",
                subtitle = "Condiciones de uso",
                iconId = R.drawable.singlepage
            ) {
                uriHandler.openUri("https://music.yammbo.com/pages/terms-of-service")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Footer
        BasicText(
            text = "Hecho con amor por Yammbo LLC",
            style = typography().xxs.secondary.copy(
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}

@Composable
private fun SectionCard(
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
            text = title,
            style = typography().xs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().textSecondary
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SocialIcon(
    label: String,
    name: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colorPalette().background0)
        ) {
            BasicText(
                text = label,
                style = typography().xs.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        BasicText(
            text = name,
            style = typography().xxs.secondary
        )
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    iconId: Int,
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
                colorFilter = ColorFilter.tint(colorPalette().text),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typography().xs.copy(
                    fontWeight = FontWeight.Medium
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
