package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.extensions.download.AutoDownloads
import it.fast4x.riplay.extensions.download.toggleAutoDownload
import it.fast4x.riplay.utils.colorPalette

/** "Download automatically on Wi-Fi" for one playlist, album or the favorites; a check when on. */
@Composable
fun AutoDownloadMenuEntry(key: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Read once: tapping the entry closes the menu, so it never has to change while shown.
    val enabled = remember(key) { AutoDownloads.isEnabled(context, key) }
    MenuEntry(
        icon = R.drawable.download,
        text = stringResource(R.string.auto_download_collection),
        onClick = {
            onDismiss()
            toggleAutoDownload(context, key)
        },
        trailingContent = if (enabled) {
            {
                Image(
                    painter = painterResource(R.drawable.checkmark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().text),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null
    )
}
