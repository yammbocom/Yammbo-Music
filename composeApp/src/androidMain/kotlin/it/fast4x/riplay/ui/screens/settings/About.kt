package it.fast4x.riplay.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


// Brand colors — official palette for each social platform.
private val FacebookColor = Color(0xFF1877F2)
private val InstagramTop = Color(0xFFFEDA77)
private val InstagramMid = Color(0xFFF58529)
private val InstagramBot = Color(0xFFDD2A7B)
private val XColor = Color(0xFF000000)
private val TikTokBg = Color(0xFF010101)
private val WhatsAppColor = Color(0xFF25D366)


@ExperimentalAnimationApi
@Composable
fun About() {
    val uriHandler = LocalUriHandler.current
    val colors = colorPalette()
    val typo = typography()

    Column(
        modifier = Modifier
            .background(colors.background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // === Hero ===
        StaggeredEntry(index = 0) {
            AboutHeroCard()
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Social ===
        StaggeredEntry(index = 1) {
            SectionLabel("Conecta con nosotros")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.background1)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                SocialChip(
                    iconId = R.drawable.brand_facebook,
                    label = "Facebook",
                    brandBg = Brush.linearGradient(listOf(FacebookColor, FacebookColor)),
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://www.facebook.com/yammbo") }
                SocialChip(
                    iconId = R.drawable.brand_instagram,
                    label = "Instagram",
                    brandBg = Brush.linearGradient(
                        colors = listOf(InstagramTop, InstagramMid, InstagramBot),
                    ),
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://instagram.com/yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_x,
                    label = "X",
                    brandBg = Brush.linearGradient(listOf(XColor, XColor)),
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://x.com/yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_tiktok,
                    label = "TikTok",
                    brandBg = Brush.linearGradient(listOf(TikTokBg, TikTokBg)),
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://www.tiktok.com/@yammbo_com") }
                SocialChip(
                    iconId = R.drawable.brand_whatsapp,
                    label = "WhatsApp",
                    brandBg = Brush.linearGradient(listOf(WhatsAppColor, WhatsAppColor)),
                    modifier = Modifier.weight(1f),
                ) { uriHandler.openUri("https://api.whatsapp.com/send?phone=5623464876") }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Support ===
        StaggeredEntry(index = 2) {
            SectionLabel("Soporte")
            Spacer(modifier = Modifier.height(10.dp))
            LinkGroup {
                AboutLinkRow(
                    title = "Sitio web",
                    subtitle = "music.yammbo.com",
                    iconId = R.drawable.globe,
                    tint = Color(0xFF26C6DA),
                ) { uriHandler.openUri("https://music.yammbo.com") }
                AboutRowDivider()
                AboutLinkRow(
                    title = "Centro de ayuda",
                    subtitle = "Base de conocimiento y tutoriales",
                    iconId = R.drawable.information,
                    tint = Color(0xFF5C6BC0),
                ) { uriHandler.openUri("https://yammbo.zohodesk.com/portal/en/kb/yammbo-llc") }
                AboutRowDivider()
                AboutLinkRow(
                    title = "Chat en vivo",
                    subtitle = "Habla con nuestro equipo",
                    iconId = R.drawable.help_circle,
                    tint = Color(0xFF66BB6A),
                ) { uriHandler.openUri("https://tawk.to/yammbo") }
                AboutRowDivider()
                AboutLinkRow(
                    title = stringResource(R.string.report_an_issue),
                    subtitle = "Reportar un problema o sugerencia",
                    iconId = R.drawable.alert_circle,
                    tint = Color(0xFFEF5350),
                ) { uriHandler.openUri("https://music.yammbo.com/support") }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // === Legal ===
        StaggeredEntry(index = 3) {
            SectionLabel("Legal")
            Spacer(modifier = Modifier.height(10.dp))
            LinkGroup {
                AboutLinkRow(
                    title = "Política de privacidad",
                    subtitle = "Cómo protegemos tus datos",
                    iconId = R.drawable.shield_checkmark,
                    tint = Color(0xFF5C6BC0),
                ) { uriHandler.openUri("https://music.yammbo.com/pages/privacy-policy") }
                AboutRowDivider()
                AboutLinkRow(
                    title = "Términos de servicio",
                    subtitle = "Condiciones de uso",
                    iconId = R.drawable.singlepage,
                    tint = Color(0xFFAB47BC),
                ) { uriHandler.openUri("https://music.yammbo.com/pages/terms-of-service") }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // === Footer ===
        StaggeredEntry(index = 4) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    text = "Hecho con ♥ por Yammbo LLC",
                    style = typo.xs.copy(
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = "© 2026 Yammbo LLC · Todos los derechos reservados",
                    style = typo.xxs.copy(
                        color = colors.textDisabled,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

@Composable
private fun AboutHeroCard() {
    val colors = colorPalette()
    val typo = typography()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = 0.28f),
                        colors.background2.copy(alpha = 0.92f),
                    )
                )
            )
            .padding(vertical = 32.dp, horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon — circle backdrop with subtle border for depth.
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colors.background0)
                    .border(
                        width = 2.dp,
                        color = colors.accent.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.yambo_icon),
                    contentDescription = "Yammbo Music",
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = Modifier.size(48.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            BasicText(
                text = "Yammbo Music",
                style = typo.l.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(modifier = Modifier.height(2.dp))

            BasicText(
                text = "Tu música, tu mundo",
                style = typo.s.copy(
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Version pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.background0.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicText(
                    text = "Versión ${BuildConfig.VERSION_NAME}",
                    style = typo.xs.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(
        text = text.uppercase(),
        style = typography().xxs.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().textSecondary,
            letterSpacing = 1.sp,
        ),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun LinkGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colorPalette().background1)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        content()
    }
}

@Composable
private fun SocialChip(
    iconId: Int,
    label: String,
    brandBg: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val typo = typography()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.pressable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(brandBg),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = label,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        BasicText(
            text = label,
            style = typo.xxs.copy(
                color = colorPalette().textSecondary,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    subtitle: String,
    iconId: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    val colors = colorPalette()
    val typo = typography()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = typo.s.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                ),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typo.xxs.secondary,
                maxLines = 1,
            )
        }
        Image(
            painter = painterResource(R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textDisabled),
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AboutRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colorPalette().background0.copy(alpha = 0.5f)),
    )
}
