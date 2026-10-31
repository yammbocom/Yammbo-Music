package it.fast4x.riplay.extensions.fastshare

import it.fast4x.riplay.extensions.yammboapi.AppEvents
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.utils.isRadio
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.net.Uri
import it.fast4x.riplay.extensions.ads.PremiumFeature
import it.fast4x.riplay.extensions.ads.PremiumGuard
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.download.downloadAlbum
import it.fast4x.riplay.extensions.download.downloadArtist
import it.fast4x.riplay.extensions.download.downloadPlaylist
import it.fast4x.riplay.extensions.download.downloadSong
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.GlobalSheetState
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

/**
 * Opens the share sheet from inside a menu shown by [GlobalSheetState]. The menu
 * sheet is replaced rather than left open underneath, so the two never stack.
 */
fun GlobalSheetState.displayFastShare(
    content: Any,
    showLinks: Boolean? = true,
    showShareWith: Boolean? = true,
) {
    // Stations and device files have no link to share; closing the menu onto nothing
    // looked like the tap was swallowed, so the menu simply stays.
    if (content is MediaItem && (content.isRadio || content.isLocal)) return
    displayDetached {
        FastShare(
            showFastShare = true,
            showLinks = showLinks,
            showShareWith = showShareWith,
            onDismissRequest = { hide() },
            content = content
        )
    }
}

/**
 * Yammbo Noir share sheet: a preview of the story card with a choice of style, the link with a
 * copy button, round targets for the apps that are installed, and "share with other apps"
 * (the system chooser) as the main action. The image sent is the one shown in the preview.
 */
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
    var ytUrlToShare by remember { mutableStateOf("") }
    var shareTitle by remember { mutableStateOf("") }
    var shareArtist by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf<String?>(null) }
    // Caption of the story card; null keeps the default "now playing" used for songs.
    var storyLabel by remember { mutableStateOf<String?>(null) }
    // "3:42" for songs: lets the story card print times on its progress bar. Null leaves them out.
    var durationText by remember { mutableStateOf<String?>(null) }
    // The story image is made as soon as the sheet opens: it is shown as a preview and the
    // share buttons reuse it instead of rendering it again.
    var storyUri by remember { mutableStateOf<Uri?>(null) }
    // The last style picked is remembered between openings.
    var savedStyle by rememberPreference(SHARE_STYLE_KEY, ShareCardStyle.Cover.name)
    val cardStyle = ShareCardStyle.entries.firstOrNull { it.name == savedStyle } ?: ShareCardStyle.Cover

    val appContext = LocalContext.current
    // Keyed on the content: screens compose this sheet before their page has loaded (an
    // artist without its photo yet), so it must pick up the later, complete value.
    LaunchedEffect(content) {
        storyUri = null
        durationText = null
        when (content) {
            is MediaItem -> content.asSong.let {
                durationText = it.durationText?.takeIf { d -> d.isNotBlank() && d != "0:00" }
                shareTitle = cleanPrefix(it.title)
                shareArtist = it.artistsText ?: ""
                thumbnailUrl = it.thumbnailUrl
                urlToShare = it.shareYamboUrl ?: ""
                ytUrlToShare = it.shareYTUrl ?: it.shareYTMUrl ?: ""
            }
            is Playlist -> {
                shareTitle = cleanPrefix(content.name)
                shareArtist = ""
                thumbnailUrl = content.thumbnailUrl
                urlToShare = content.shareYamboUrl ?: ""
                ytUrlToShare = content.shareYTUrl ?: content.shareYTMUrl ?: ""
                storyLabel = appContext.getString(
                    if (content.isPodcastShow || content.isPodcast) R.string.share_label_podcast
                    else R.string.share_label_playlist
                )
            }
            is Album -> {
                shareTitle = cleanPrefix(content.title ?: "")
                shareArtist = content.authorsText ?: ""
                thumbnailUrl = content.thumbnailUrl
                urlToShare = content.shareYamboUrl ?: ""
                ytUrlToShare = content.shareYTUrl ?: content.shareYTMUrl ?: ""
                storyLabel = appContext.getString(R.string.share_label_album)
            }
            is Artist -> {
                shareTitle = cleanPrefix(content.name ?: "")
                shareArtist = ""
                thumbnailUrl = content.thumbnailUrl
                urlToShare = content.shareYamboUrl ?: ""
                ytUrlToShare = content.shareYTUrl ?: content.shareYTMUrl ?: ""
                storyLabel = appContext.getString(R.string.share_label_artist)
            }
        }
    }

    if (urlToShare.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGeneratingImage by remember { mutableStateOf(false) }

    // Also keyed on the cover (a photo that arrives later redraws the preview) and on the
    // style, which renders the card again.
    LaunchedEffect(showFastShare, urlToShare, thumbnailUrl, cardStyle, durationText) {
        if (showFastShare && urlToShare.isNotEmpty()) {
            storyUri = null
            storyUri = ShareImageGenerator.generateShareImage(
                context, shareTitle, shareArtist, thumbnailUrl, urlToShare, storyLabel, cardStyle, durationText
            )
        }
    }

    CustomModalBottomSheet(
        showSheet = showFastShare,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        tonalElevation = 0.dp
    ) {
        // Runs [action] with the story image, rendering it first if the preview is not ready.
        val withStoryImage = { event: String, action: (Uri?) -> Unit ->
            if (!isGeneratingImage) {
                isGeneratingImage = true
                AppEvents.log(AppEvents.SHARE_SONG, detail = event)
                scope.launch {
                    val imageUri = try {
                        storyUri ?: ShareImageGenerator.generateShareImage(
                            context, shareTitle, shareArtist, thumbnailUrl, urlToShare, storyLabel, cardStyle, durationText
                        )
                    } finally {
                        // Never leave every target dead if rendering throws.
                        isGeneratingImage = false
                    }
                    if (storyUri == null) storyUri = imageUri
                    action(imageUri)
                }
            }
        }

        // A target that is not installed falls back to the system chooser.
        val shareToApp = { packageName: String? ->
            val target = packageName?.takeIf { isPackageInstalled(context, it) }
            withStoryImage(packageName ?: "chooser") { imageUri ->
                val intro = buildShareIntro(context, shareTitle, shareArtist)
                if (imageUri != null) {
                    shareWithImage(context, target, imageUri, intro, urlToShare)
                } else {
                    classicShare(urlToShare, context, intro)
                }
            }
        }

        val copyLink = {
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Yammbo Music", urlToShare))
            }
            SmartMessage(context.getString(R.string.share_link_copied), PopupType.Info, context = context)
        }

        val instagramInstalled = remember { isPackageInstalled(context, INSTAGRAM_PACKAGE) }
        val whatsappInstalled = remember { isPackageInstalled(context, WHATSAPP_PACKAGE) }
        val xInstalled = remember { isPackageInstalled(context, X_PACKAGE) }
        val facebookInstalled = remember { isPackageInstalled(context, FACEBOOK_PACKAGE) }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 2.dp, bottom = 16.dp)
                .fillMaxWidth()
        ) {
            // === Title + close ===
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    text = stringResource(R.string.share_sheet_title),
                    style = typography().xl.tight.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismissRequest)
                ) {
                    Image(
                        painter = painterResource(R.drawable.close),
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        contentDescription = stringResource(R.string.share_close),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // === Preview + card style ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // What gets sent, 9:16. Grey until the image is ready.
                val previewFrame = Modifier
                    .width(150.dp)
                    .height(266.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colorPalette().background1)
                if (storyUri != null)
                    AsyncImage(
                        model = storyUri,
                        contentDescription = stringResource(R.string.share_story_preview_label),
                        contentScale = ContentScale.Crop,
                        modifier = previewFrame
                    )
                else
                    Box(modifier = previewFrame)

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    NoirEyebrow(stringResource(R.string.share_card_style_label))
                    BasicText(
                        text = stringResource(shareStyleName(cardStyle)),
                        style = typography().xl.tight.copy(
                            color = colorPalette().text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    )
                }
            }

            // Scrolls sideways: a mini layout of every look, the chosen one outlined in white.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                ShareCardStyle.entries.forEach { option ->
                    StyleThumb(
                        label = stringResource(shareStyleName(option)),
                        selected = option == cardStyle,
                        onClick = { savedStyle = option.name }
                    ) { ShareStyleMini(option, thumbnailUrl) }
                }
            }

            // === Link + copy ===
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xff1A1A1C))
                    .padding(start = 14.dp, end = 6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.link),
                    colorFilter = ColorFilter.tint(colorPalette().textSecondary),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicText(
                    text = urlToShare.removePrefix("https://").removePrefix("http://"),
                    style = typography().xs.mono.copy(
                        color = colorPalette().text.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        letterSpacing = 0.em
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorPalette().text)
                        .clickable { copyLink() }
                        .padding(horizontal = 14.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.share_copy_action),
                        style = typography().xs.copy(
                            color = colorPalette().background0,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            // === Targets: round glass tiles; the row scrolls sideways on a narrow screen ===
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                // Only offered when the app is installed, so a tile never leads to an error.
                if (instagramInstalled)
                    ShareTarget(
                        icon = R.drawable.brand_instagram,
                        label = stringResource(R.string.share_target_ig_story)
                    ) {
                        withStoryImage("instagram_story") { imageUri ->
                            val intro = buildShareIntro(context, shareTitle, shareArtist)
                            if (imageUri != null) shareToInstagramStory(context, imageUri, intro, urlToShare)
                            else classicShare(urlToShare, context, intro)
                        }
                    }
                if (whatsappInstalled)
                    ShareTarget(
                        icon = R.drawable.brand_whatsapp,
                        label = "WhatsApp"
                    ) { shareToApp(WHATSAPP_PACKAGE) }
                if (xInstalled)
                    ShareTarget(
                        icon = R.drawable.brand_x,
                        label = "X"
                    ) { shareToApp(X_PACKAGE) }
                if (facebookInstalled)
                    ShareTarget(
                        icon = R.drawable.brand_facebook,
                        label = "Facebook"
                    ) { shareToApp(FACEBOOK_PACKAGE) }
                ShareTarget(
                    icon = R.drawable.download,
                    label = stringResource(R.string.share_target_save_image)
                ) {
                    withStoryImage("save_image") { imageUri ->
                        scope.launch {
                            val saved = imageUri != null && withContext(Dispatchers.IO) {
                                saveImageToGallery(context, imageUri)
                            }
                            SmartMessage(
                                context.getString(
                                    if (saved) R.string.share_image_saved else R.string.share_image_save_failed
                                ),
                                if (saved) PopupType.Success else PopupType.Error,
                                context = context
                            )
                        }
                    }
                }
                // Local files and stations have nothing to download.
                val downloadAction: (() -> Unit)? = when {
                    content is MediaItem && !content.isLocal && !content.isRadio ->
                        { { downloadSong(context, content.asSong) } }
                    content is Album -> { { downloadAlbum(context, content.id, content.title) } }
                    content is Playlist -> { { downloadPlaylist(context, content) } }
                    content is Artist -> { { downloadArtist(context, content.id) } }
                    else -> null
                }
                downloadAction?.let { action ->
                    ShareTarget(
                        icon = R.drawable.download,
                        label = stringResource(R.string.download_action)
                    ) {
                        // Free users cannot download tracks: gate behind Premium.
                        if (PremiumGuard.checkFeature(context, PremiumFeature.Download)) action()
                    }
                }
            }

            // === Main action: the system chooser, with the card attached ===
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorPalette().text)
                    .clickable { shareToApp(null) }
            ) {
                BasicText(
                    text = if (isGeneratingImage)
                        stringResource(R.string.share_generating_image)
                    else
                        stringResource(R.string.share_with_other_apps),
                    style = typography().s.tight.copy(
                        color = colorPalette().background0,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                )
            }
        }
    }
}

private const val SHARE_STYLE_KEY = "shareCardStyle"

private fun shareStyleName(style: ShareCardStyle): Int = when (style) {
    ShareCardStyle.Cover -> R.string.share_style_cover
    ShareCardStyle.Vinyl -> R.string.share_style_vinyl
    ShareCardStyle.Poster -> R.string.share_style_poster
    ShareCardStyle.Polaroid -> R.string.share_style_polaroid
    ShareCardStyle.Minimal -> R.string.share_style_minimal
    ShareCardStyle.Receipt -> R.string.share_style_receipt
    ShareCardStyle.Night -> R.string.share_style_night
    ShareCardStyle.Paper -> R.string.share_style_paper
    ShareCardStyle.Grain -> R.string.share_style_grain
    ShareCardStyle.Glass -> R.string.share_style_glass
}

/** A 9:16 mini card with its name underneath; the chosen one gets a white ring. */
@Composable
internal fun StyleThumb(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 86.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) colorPalette().text else Color.White.copy(alpha = 0.14f),
                    RoundedCornerShape(10.dp)
                )
                .padding(if (selected) 2.dp else 1.dp)
                .clip(RoundedCornerShape(8.dp)),
            content = content
        )
        BasicText(
            text = label,
            style = typography().xxs.copy(
                color = if (selected) colorPalette().text else colorPalette().textSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A few horizontal bars standing in for lines of text. */
@Composable
internal fun BoxScope.MiniBars(
    color: Color,
    modifier: Modifier = Modifier,
    widths: List<Float> = listOf(0.8f, 0.55f)
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        widths.forEach {
            Box(
                modifier = Modifier
                    .fillMaxWidth(it)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
internal fun BoxScope.MiniCover(url: String?, modifier: Modifier) {
    if (!url.isNullOrEmpty())
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    else Box(modifier = modifier.background(colorPalette().background3))
}

/** Tiny drawing of each story layout, built from boxes so it needs no rendering. */
@Composable
private fun BoxScope.ShareStyleMini(style: ShareCardStyle, coverUrl: String?) {
    val white = Color.White
    when (style) {
        ShareCardStyle.Cover -> {
            MiniCover(coverUrl, Modifier.fillMaxSize().alpha(0.55f))
            MiniCover(coverUrl, Modifier.align(Alignment.Center).size(30.dp).clip(RoundedCornerShape(4.dp)))
        }
        ShareCardStyle.Vinyl -> {
            Box(Modifier.fillMaxSize().background(Color(0xffDCDCD6)))
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 18.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.dp, white.copy(alpha = 0.35f), CircleShape)
            )
            MiniCover(coverUrl, Modifier.align(Alignment.CenterStart).offset(x = 3.dp).size(30.dp).clip(RoundedCornerShape(2.dp)))
        }
        ShareCardStyle.Glass -> {
            MiniCover(coverUrl, Modifier.fillMaxSize().alpha(0.6f))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 40.dp, height = 52.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(white.copy(alpha = 0.14f))
                    .border(1.dp, white.copy(alpha = 0.28f), RoundedCornerShape(5.dp))
            )
            MiniCover(coverUrl, Modifier.align(Alignment.Center).offset(y = (-6).dp).size(28.dp).clip(RoundedCornerShape(3.dp)))
            MiniBars(white, Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = 22.dp), listOf(0.6f))
        }
        ShareCardStyle.Poster -> {
            Box(Modifier.fillMaxSize().background(Color.Black))
            MiniCover(coverUrl, Modifier.fillMaxWidth().height(58.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .offset(y = 34.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            )
            MiniBars(white, Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 8.dp), listOf(0.8f, 0.5f))
        }
        ShareCardStyle.Polaroid -> {
            MiniCover(coverUrl, Modifier.fillMaxSize().alpha(0.5f))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .rotate(-5f)
                    .size(width = 34.dp, height = 44.dp)
                    .background(Color(0xffFAFAF7))
                    .padding(3.dp)
            ) {
                MiniCover(coverUrl, Modifier.fillMaxWidth().height(28.dp))
            }
        }
        ShareCardStyle.Minimal -> {
            Box(Modifier.fillMaxSize().background(Color(0xff0A0A0A)))
            MiniBars(white, Modifier.align(Alignment.CenterStart).padding(start = 5.dp), listOf(0.9f, 0.7f, 0.8f))
            MiniCover(coverUrl, Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 6.dp).size(10.dp).clip(CircleShape))
        }
        ShareCardStyle.Receipt -> {
            Box(Modifier.fillMaxSize().background(Color(0xff0E0E0E)))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 32.dp, height = 56.dp)
                    .background(Color(0xffF7F7F3))
                    .padding(4.dp)
            ) {
                MiniBars(Color(0xff555555), Modifier.align(Alignment.TopStart), listOf(0.9f, 0.6f, 0.8f, 0.5f, 0.9f, 0.7f))
            }
        }
        ShareCardStyle.Night ->
            Box(Modifier.fillMaxSize().background(Color.Black))
        ShareCardStyle.Paper -> Box(Modifier.fillMaxSize().background(Color(0xffF2F2EE)))
        ShareCardStyle.Grain ->
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        0f to Color(0xff1B1B1B),
                        0.5f to Color(0xff1B1B1B),
                        0.5f to Color(0xff262626),
                        1f to Color(0xff262626),
                        start = Offset.Zero,
                        end = Offset(12f, 12f),
                        tileMode = TileMode.Repeated
                    )
                )
            )
    }
    // Night, Paper and Grain keep the shared cover layout in miniature.
    if (style == ShareCardStyle.Night || style == ShareCardStyle.Paper || style == ShareCardStyle.Grain) {
        MiniCover(coverUrl, Modifier.align(Alignment.Center).size(34.dp).clip(RoundedCornerShape(4.dp)))
        MiniBars(
            if (style == ShareCardStyle.Paper) Color(0xff222222) else white,
            Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 6.dp),
            listOf(0.8f)
        )
    }
}

/** Round glass target: a 56 dp tile with a line icon and its name underneath. */
@Composable
internal fun ShareTarget(
    icon: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xff1A1A1C))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
        ) {
            Image(
                painter = painterResource(icon),
                colorFilter = ColorFilter.tint(colorPalette().text),
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
        }
        BasicText(
            text = label,
            style = typography().xxs.copy(
                color = colorPalette().text,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal const val INSTAGRAM_PACKAGE = "com.instagram.android"
internal const val WHATSAPP_PACKAGE = "com.whatsapp"
internal const val X_PACKAGE = "com.twitter.android"
internal const val FACEBOOK_PACKAGE = "com.facebook.katana"

internal fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

/** "Escucha «Title» de Artist en Yammbo Music"; the link goes on the next line. */
private fun buildShareIntro(context: Context, title: String, artist: String): String =
    if (artist.isNotBlank()) context.getString(R.string.share_msg_with_artist, title, artist)
    else context.getString(R.string.share_msg_no_artist, title)

private fun startShareIntent(context: Context, intent: Intent, packageName: String?, imageUri: Uri?) {
    if (imageUri != null) {
        // ClipData + explicit grant: the flag alone is ignored by some targets and, with a
        // chooser, the grant has to reach every app the user can pick.
        intent.clipData = ClipData.newRawUri("", imageUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (packageName != null) {
            runCatching {
                context.grantUriPermission(packageName, imageUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (packageName != null) {
        context.startActivity(intent)
    } else {
        val chooser = Intent.createChooser(intent, null)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }
}

internal fun shareWithImage(
    context: Context,
    packageName: String?,
    imageUri: Uri?,
    intro: String,
    url: String
) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (imageUri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, if (url.isBlank()) intro else "$intro\n$url")
            putExtra(Intent.EXTRA_SUBJECT, intro)
            if (packageName != null) setPackage(packageName)
        }
        startShareIntent(context, intent, packageName, imageUri)
    } catch (e: ActivityNotFoundException) {
        val appName = when (packageName) {
            INSTAGRAM_PACKAGE -> "Instagram"
            WHATSAPP_PACKAGE -> "WhatsApp"
            X_PACKAGE -> "X"
            FACEBOOK_PACKAGE -> "Facebook"
            else -> context.getString(R.string.share_app_fallback_label)
        }
        SmartMessage(
            context.getString(R.string.share_app_not_installed_short, appName),
            PopupType.Error,
            context = context,
        )
    } catch (e: Exception) {
        // A refused grant or an odd OEM restriction must never crash the share sheet.
        classicShare(url, context, intro)
    }
}

/**
 * Sends the card to the Instagram story editor. If the story action is refused it falls back
 * to a plain send to the app, which still lets the user pick feed, story or message.
 */
private fun shareToInstagramStory(context: Context, imageUri: Uri, intro: String, url: String) {
    // ADD_TO_STORY requires a registered Facebook App ID ("source_application"); without one
    // Instagram opens and silently drops the content. A plain image share to Instagram opens its
    // own picker, which offers Story, Feed and Messages.
    shareWithImage(context, INSTAGRAM_PACKAGE, imageUri, intro, url)
}

/** Copies the card into Pictures/Yammbo Music. Returns false when it could not be saved. */
private fun saveImageToGallery(context: Context, source: Uri): Boolean = runCatching {
    val name = "YammboMusic_" + System.currentTimeMillis() + ".png"
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Yammbo Music")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        val copied = resolver.openInputStream(source)?.use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
        } != null
        if (!copied) {
            resolver.delete(target, null, null)
            return@runCatching false
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        true
    } else {
        // Below Android 10 there is no scoped storage; this needs WRITE_EXTERNAL_STORAGE, so on
        // a device without it the copy throws and the caller shows the failure message.
        @Suppress("DEPRECATION")
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Yammbo Music"
        ).apply { mkdirs() }
        val file = File(folder, name)
        val copied = resolver.openInputStream(source)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } != null
        if (copied) MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png"), null)
        copied
    }
}.getOrDefault(false)

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
