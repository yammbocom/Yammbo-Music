package it.fast4x.riplay.ui.components.themed

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

data class NotificationPopupData(
    val id: String,
    val title: String,
    val message: String,
    val videoUrl: String? = null,
    val buttonText: String? = null,
    val buttonUrl: String? = null
)

@Composable
fun YammboNotificationPopup(
    notification: NotificationPopupData,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorPalette().background1,
        textContentColor = colorPalette().text,
        title = {
            Text(
                text = notification.title,
                style = typography().m.semiBold,
                color = colorPalette().text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = notification.message,
                    style = typography().s,
                    color = colorPalette().text
                )

                notification.videoUrl?.let { url ->
                    val videoId = extractYouTubeId(url)
                    if (videoId != null) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    webViewClient = WebViewClient()
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    loadData(
                                        """
                                        <html><body style="margin:0;padding:0;background:transparent;">
                                        <iframe width="100%" height="100%"
                                        src="https://www.youtube.com/embed/$videoId?rel=0"
                                        frameborder="0" allowfullscreen></iframe>
                                        </body></html>
                                        """.trimIndent(),
                                        "text/html", "utf-8"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }

                if (notification.buttonText != null && notification.buttonUrl != null) {
                    Button(
                        onClick = {
                            uriHandler.openUri(notification.buttonUrl)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(notification.buttonText)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = colorPalette().text)
            }
        }
    )
}

private fun extractYouTubeId(url: String): String? {
    val patterns = listOf(
        Regex("""youtu\.be/([a-zA-Z0-9_-]{11})"""),
        Regex("""youtube\.com/watch\?v=([a-zA-Z0-9_-]{11})"""),
        Regex("""youtube\.com/embed/([a-zA-Z0-9_-]{11})"""),
    )
    return patterns.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }
}
