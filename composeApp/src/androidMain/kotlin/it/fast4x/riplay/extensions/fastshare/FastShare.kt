package it.fast4x.riplay.extensions.fastshare

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import com.yambo.music.R
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.extensions.preferences.rememberObservedPreference
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.copyTextToClipboard
import it.fast4x.riplay.utils.thumbnailShape
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FastShare(
    showFastShare: Boolean,
    showLinks: Boolean? = true,
    showShareWith: Boolean? = true,
    showShareWithExternalApps: Boolean? = true,
    onDismissRequest: () -> Unit,
    content: Any,
) {
    var urlToShare by remember { mutableStateOf("") }
    var shareTitle by remember { mutableStateOf("") }
    var shareArtist by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (content) {
            is MediaItem -> content.asSong.let {
                shareTitle = it.title
                shareArtist = it.artistsText ?: ""
                thumbnailUrl = it.thumbnailUrl
                urlToShare = it.shareYamboUrl ?: ""
            }
            is Playlist -> {
                shareTitle = content.name
                shareArtist = ""
                thumbnailUrl = null
                urlToShare = content.shareYamboUrl ?: ""
            }
            is Album -> {
                shareTitle = content.title ?: ""
                shareArtist = content.authorsText ?: ""
                thumbnailUrl = content.thumbnailUrl
                urlToShare = content.shareYamboUrl ?: ""
            }
            is Artist -> {
                shareTitle = content.name ?: ""
                shareArtist = ""
                thumbnailUrl = content.thumbnailUrl
                urlToShare = content.shareYamboUrl ?: ""
            }
        }
    }

    if (urlToShare.isEmpty()) return

    val thumbnailRoundness by rememberObservedPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGeneratingImage by remember { mutableStateOf(false) }

    CustomModalBottomSheet(
        showSheet = showFastShare,
        onDismissRequest = onDismissRequest,
        containerColor = colorPalette().background0,
        contentColor = colorPalette().background0,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 0.dp),
                color = colorPalette().background0,
                shape = thumbnailShape()
            ) {
                Image(
                    painter = painterResource(R.drawable.share_social),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().text),
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        shape = thumbnailRoundness.shape()
    ) {
        val shareToApp = { packageName: String? ->
            if (!isGeneratingImage) {
                isGeneratingImage = true
                scope.launch {
                    val imageUri = ShareImageGenerator.generateShareImage(
                        context, shareTitle, shareArtist, thumbnailUrl, urlToShare
                    )
                    isGeneratingImage = false
                    if (imageUri != null) {
                        shareWithImage(context, packageName, imageUri, shareTitle, urlToShare)
                    } else {
                        classicShare(urlToShare, context, "$shareTitle - $shareArtist")
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .background(colorPalette().background0)
                .fillMaxWidth()
        ) {
            // === Song info header ===
            Text(
                text = shareTitle,
                color = colorPalette().text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1
            )
            if (shareArtist.isNotEmpty()) {
                Text(
                    text = shareArtist,
                    color = colorPalette().textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === Copy link ===
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorPalette().background2)
                    .clickable { copyTextToClipboard(urlToShare, context) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = urlToShare,
                    fontSize = 12.sp,
                    color = colorPalette().textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    painter = painterResource(R.drawable.copy),
                    colorFilter = ColorFilter.tint(colorPalette().accent),
                    contentDescription = "Copy",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === Social media buttons ===
            Text(
                text = "Compartir en",
                color = colorPalette().textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                SocialShareButton(R.drawable.share_social, "Instagram") { shareToApp("com.instagram.android") }
                SocialShareButton(R.drawable.share_social, "WhatsApp") { shareToApp("com.whatsapp") }
                SocialShareButton(R.drawable.share_social, "Facebook") { shareToApp("com.facebook.katana") }
                SocialShareButton(R.drawable.share_social, "TikTok") { shareToApp("com.zhiliaoapp.musically") }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === General share button ===
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorPalette().accent)
                    .clickable { shareToApp(null) }
                    .padding(vertical = 14.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.share_social),
                    colorFilter = ColorFilter.tint(colorPalette().onAccent),
                    contentDescription = "Share",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isGeneratingImage) "Generando imagen..." else "Compartir con otras apps",
                    color = colorPalette().onAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SocialShareButton(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Image(
            painter = painterResource(icon),
            colorFilter = ColorFilter.tint(colorPalette().text),
            contentDescription = label,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colorPalette().textSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun shareWithImage(
    context: Context,
    packageName: String?,
    imageUri: Uri?,
    title: String,
    url: String
) {
    try {
        if (imageUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (packageName != null) setPackage(packageName)
            }
            if (packageName != null) {
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, null)
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } else {
            // Fallback to text share
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (packageName != null) setPackage(packageName)
            }
            if (packageName != null) {
                context.startActivity(intent)
            } else {
                val chooser = Intent.createChooser(intent, null)
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        }
    } catch (e: ActivityNotFoundException) {
        val appName = when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.whatsapp" -> "WhatsApp"
            "com.facebook.katana" -> "Facebook"
            "com.zhiliaoapp.musically" -> "TikTok"
            else -> "La app"
        }
        SmartMessage("$appName no está instalado", PopupType.Error, context = context)
    }
}

fun classicShare(content: String, context: Context, title: String = "") {
    val shareText = if (title.isNotEmpty()) "$title\n$content" else content
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        if (title.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, title)
    }
    val shareIntent = Intent.createChooser(sendIntent, null)
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
}
