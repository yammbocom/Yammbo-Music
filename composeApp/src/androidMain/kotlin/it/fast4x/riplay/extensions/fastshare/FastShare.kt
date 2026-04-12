package it.fast4x.riplay.extensions.fastshare

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
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
import it.fast4x.riplay.enums.LinkType
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.extensions.preferences.rememberObservedPreference
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.components.themed.TitleMiniSection
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.copyTextToClipboard
import it.fast4x.riplay.utils.globalContext
import it.fast4x.riplay.utils.thumbnailShape
import it.fast4x.riplay.utils.typography

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
    var typeOfUrl by remember { mutableStateOf(LinkType.Yammbo) }
    var urlToShare by remember { mutableStateOf("") }
    var shareTitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit, typeOfUrl) {
        shareTitle = when (content) {
            is MediaItem -> content.asSong.let { "${it.title} - ${it.artistsText ?: ""}" }
            is Playlist -> content.name
            is Album -> "${content.title ?: ""} - ${content.authorsText ?: ""}"
            is Artist -> content.name ?: ""
            else -> ""
        }
        urlToShare = when (typeOfUrl) {
            LinkType.Yammbo -> {
                when (content) {
                    is MediaItem -> content.asSong.shareYamboUrl
                    is Playlist -> content.shareYamboUrl
                    is Album -> content.shareYamboUrl
                    is Artist -> content.shareYamboUrl
                    else -> ""
                }
            }
            LinkType.Main -> {
                when (content) {
                    is MediaItem -> content.asSong.shareYTUrl
                    is Playlist -> {
                        if (!content.isPodcast) content.shareYTUrl
                        else content.shareYTUrlAsPodcast
                    }
                    is Album -> content.shareYTUrl
                    is Artist -> content.shareYTUrl
                    else -> ""
                }
            }
            LinkType.Alternative -> {
                when (content) {
                    is MediaItem -> content.asSong.shareYTMUrl
                    is Playlist -> {
                        if (!content.isPodcast) content.shareYTMUrl
                        else content.shareYTMUrlAsPodcast
                    }
                    is Album -> content.shareYTMUrl
                    is Artist -> content.shareYTMUrl
                    else -> ""
                }
            }
        }.toString()
    }

    if (urlToShare == "") return

    val thumbnailRoundness by rememberObservedPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    val context = globalContext()

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
        Column(
            modifier = Modifier
                .padding(all = 20.dp)
                .background(colorPalette().background0)
                .fillMaxWidth()
        ) {
            // === Link type selector + Copy ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(1.dp, colorPalette().accent),
                        shape = thumbnailShape()
                    )
            ) {
                TitleMiniSection(title = "Copiar enlace")
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    LinkType.entries.forEach { linkType ->
                        Text(
                            linkType.textName,
                            fontSize = typography().xxs.fontSize,
                            fontFamily = typography().xxs.fontFamily,
                            fontWeight = typography().xxs.fontWeight,
                            color = if (typeOfUrl == linkType) colorPalette().accent else colorPalette().textSecondary,
                            modifier = Modifier
                                .clickable { typeOfUrl = linkType }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .requiredHeight(60.dp)
                        .background(colorPalette().background2, shape = thumbnailShape())
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = urlToShare,
                        fontSize = typography().xs.fontSize,
                        fontFamily = typography().xs.fontFamily,
                        fontWeight = typography().xs.fontWeight,
                        fontStyle = typography().xs.fontStyle,
                        color = colorPalette().text,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clickable { copyTextToClipboard(urlToShare, context) }
                    )
                    Image(
                        painter = painterResource(R.drawable.copy),
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        contentDescription = "Copy link",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { copyTextToClipboard(urlToShare, context) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === Compartir ===
            Text(
                text = "Compartir",
                color = colorPalette().text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Social media story buttons row
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Instagram Stories
                SocialShareButton(
                    icon = R.drawable.share_social,
                    label = "Instagram",
                    onClick = {
                        shareToInstagramStory(context, shareTitle, urlToShare)
                    }
                )
                // WhatsApp Status
                SocialShareButton(
                    icon = R.drawable.share_social,
                    label = "WhatsApp",
                    onClick = {
                        shareToWhatsApp(context, shareTitle, urlToShare)
                    }
                )
                // Facebook Stories
                SocialShareButton(
                    icon = R.drawable.share_social,
                    label = "Facebook",
                    onClick = {
                        shareToFacebookStory(context, shareTitle, urlToShare)
                    }
                )
                // TikTok
                SocialShareButton(
                    icon = R.drawable.share_social,
                    label = "TikTok",
                    onClick = {
                        shareToTikTok(context, shareTitle, urlToShare)
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // General share button
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorPalette().accent)
                    .clickable { classicShare(urlToShare, context, shareTitle) }
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
                    text = "Compartir con otras apps",
                    color = colorPalette().onAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
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

private fun shareToInstagramStory(context: Context, title: String, url: String) {
    try {
        // Try Instagram Stories share
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setPackage("com.instagram.android")
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback: share to Instagram feed
            val fallback = Intent(Intent.ACTION_SEND).apply {
                setPackage("com.instagram.android")
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    } catch (e: ActivityNotFoundException) {
        SmartMessage("Instagram no está instalado", PopupType.Error, context = context)
    }
}

private fun shareToWhatsApp(context: Context, title: String, url: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.whatsapp")
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        SmartMessage("WhatsApp no está instalado", PopupType.Error, context = context)
    }
}

private fun shareToFacebookStory(context: Context, title: String, url: String) {
    try {
        val intent = Intent("com.facebook.stories.ADD_TO_STORY").apply {
            setPackage("com.facebook.katana")
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                setPackage("com.facebook.katana")
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    } catch (e: ActivityNotFoundException) {
        SmartMessage("Facebook no está instalado", PopupType.Error, context = context)
    }
}

private fun shareToTikTok(context: Context, title: String, url: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage("com.zhiliaoapp.musically")
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        SmartMessage("TikTok no está instalado", PopupType.Error, context = context)
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
