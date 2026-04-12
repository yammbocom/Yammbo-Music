package it.fast4x.riplay.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.yambo.music.BuildConfig
import com.yambo.music.R
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.getUpdateDownloadUrl
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
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.about),
            iconId = R.drawable.information,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
        ) {
            BasicText(
                text = "Yammbo Music v${BuildConfig.VERSION_NAME}",
                style = typography().s.secondary,
            )
        }

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = stringResource(R.string.social))

        SettingsEntry(
            title = "Facebook",
            text = "",
            onClick = {
                uriHandler.openUri("https://www.facebook.com/yammbo")
            }
        )

        SettingsEntry(
            title = "Instagram",
            text = "",
            onClick = {
                uriHandler.openUri("https://instagram.com/yammbo_com")
            }
        )

        SettingsEntry(
            title = "X (Twitter)",
            text = "",
            onClick = {
                uriHandler.openUri("https://x.com/yammbo_com")
            }
        )

        SettingsEntry(
            title = "TikTok",
            text = "",
            onClick = {
                uriHandler.openUri("https://www.tiktok.com/@yammbo_com")
            }
        )

        SettingsEntry(
            title = "WhatsApp",
            text = "",
            onClick = {
                uriHandler.openUri("https://api.whatsapp.com/send?phone=5623464876")
            }
        )

        SettingsEntry(
            title = "GitHub",
            text = "",
            onClick = {
                uriHandler.openUri("https://github.com/yammbocom")
            }
        )

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = stringResource(R.string.troubleshooting))

        SettingsEntry(
            title = "Yammbo Music",
            text = "",
            onClick = {
                uriHandler.openUri("https://music.yammbo.com")
            }
        )

        SettingsEntry(
            title = "Centro de Ayuda",
            text = "",
            onClick = {
                uriHandler.openUri("https://yammbo.zohodesk.com/portal/en/kb/yammbo-llc")
            }
        )

        SettingsEntry(
            title = "Chat en Vivo",
            text = "",
            onClick = {
                uriHandler.openUri("https://tawk.to/yammbo")
            }
        )

        SettingsEntry(
            title = stringResource(R.string.report_an_issue),
            text = "",
            onClick = {
                uriHandler.openUri("https://music.yammbo.com/support")
            }
        )

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}
