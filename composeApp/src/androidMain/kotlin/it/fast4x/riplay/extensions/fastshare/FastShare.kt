package it.fast4x.riplay.extensions.fastshare

import it.fast4x.riplay.extensions.yammboapi.AppEvents
import androidx.compose.foundation.layout.Box
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
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File
import android.net.Uri
import it.fast4x.riplay.extensions.ads.PremiumFeature
import it.fast4x.riplay.extensions.ads.PremiumGuard
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yambo.music.R
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.extensions.preferences.rememberObservedPreference
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.GlobalSheetState
import it.fast4x.riplay.ui.components.SheetDragHandle
import it.fast4x.riplay.ui.components.SheetShape
import it.fast4x.riplay.ui.components.themed.ConfirmationDialog
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.thumbnailShape
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.launch

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
    var pendingInstallApp by remember { mutableStateOf<DownloaderApp?>(null) }
    // The story image is made as soon as the sheet opens: it is shown as a preview and the
    // share buttons reuse it instead of rendering it again.
    var storyUri by remember { mutableStateOf<Uri?>(null) }

    val appContext = LocalContext.current
    // Keyed on the content: screens compose this sheet before their page has loaded (an
    // artist without its photo yet), so it must pick up the later, complete value.
    LaunchedEffect(content) {
        storyUri = null
        when (content) {
            is MediaItem -> content.asSong.let {
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

    val thumbnailRoundness by rememberObservedPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isGeneratingImage by remember { mutableStateOf(false) }

    // Also keyed on the cover, so a photo that arrives later redraws the preview.
    LaunchedEffect(showFastShare, urlToShare, thumbnailUrl) {
        if (showFastShare && urlToShare.isNotEmpty() && storyUri == null)
            storyUri = ShareImageGenerator.generateShareImage(
                context, shareTitle, shareArtist, thumbnailUrl, urlToShare, storyLabel
            )
    }

    CustomModalBottomSheet(
        showSheet = showFastShare,
        onDismissRequest = onDismissRequest,
        containerColor = colorPalette().background0,
        contentColor = colorPalette().background0,
        modifier = Modifier.fillMaxWidth(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        tonalElevation = 0.dp,
        dragHandle = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SheetDragHandle()
                Text(
                    text = stringResource(R.string.share_sheet_title),
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        },
        shape = SheetShape
    ) {
        // Runs [action] with the story image, rendering it first if the preview is not ready.
        val withStoryImage = { event: String, action: (Uri?) -> Unit ->
            if (!isGeneratingImage) {
                isGeneratingImage = true
                AppEvents.log(AppEvents.SHARE_SONG, detail = event)
                scope.launch {
                    val imageUri = storyUri ?: ShareImageGenerator.generateShareImage(
                        context, shareTitle, shareArtist, thumbnailUrl, urlToShare, storyLabel
                    )
                    if (storyUri == null) storyUri = imageUri
                    isGeneratingImage = false
                    action(imageUri)
                }
            }
        }

        val shareToApp = { packageName: String? ->
            withStoryImage(packageName ?: "chooser") { imageUri ->
                val intro = buildShareIntro(context, shareTitle, shareArtist)
                if (imageUri != null) {
                    shareWithImage(context, packageName, imageUri, intro, urlToShare)
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

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .background(colorPalette().background0)
                .fillMaxWidth()
        ) {
            // === Hero card: story preview + title + artist ===
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorPalette().background2)
                    .padding(12.dp)
            ) {
                // What Instagram will get, 9:16. Grey until the image is ready.
                val previewFrame = Modifier
                    .width(96.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colorPalette().background1)
                if (storyUri != null)
                    AsyncImage(
                        model = storyUri,
                        contentDescription = stringResource(R.string.share_story_preview_label),
                        modifier = previewFrame
                    )
                else
                    Box(modifier = previewFrame)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_story_preview_label),
                        color = colorPalette().textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = shareTitle,
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (shareArtist.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = shareArtist,
                            color = colorPalette().textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
                    .clickable { copyLink() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = urlToShare,
                    fontSize = 12.sp,
                    color = colorPalette().textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                text = stringResource(R.string.share_section_label),
                color = colorPalette().textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Monochrome tiles; the row scrolls sideways when the screen is narrow.
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                // Only offered when Instagram is installed, so the tile never leads to an error.
                if (instagramInstalled)
                    SocialShareButton(
                        icon = R.drawable.brand_instagram,
                        label = stringResource(R.string.share_target_ig_story)
                    ) {
                        withStoryImage("instagram_story") { imageUri ->
                            val intro = buildShareIntro(context, shareTitle, shareArtist)
                            if (imageUri != null) shareToInstagramStory(context, imageUri, intro, urlToShare)
                            else classicShare(urlToShare, context, intro)
                        }
                    }
                SocialShareButton(
                    icon = R.drawable.brand_whatsapp,
                    label = "WhatsApp"
                ) { shareToApp("com.whatsapp") }
                SocialShareButton(
                    icon = R.drawable.brand_facebook,
                    label = "Facebook"
                ) { shareToApp("com.facebook.katana") }
                SocialShareButton(
                    icon = R.drawable.link,
                    label = stringResource(R.string.share_target_copy_link)
                ) { copyLink() }
                SocialShareButton(
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
                SocialShareButton(
                    icon = R.drawable.share_social,
                    label = "YTDLnis"
                ) {
                    // Free users cannot download tracks via YTDLnis — gate behind Premium.
                    if (PremiumGuard.checkFeature(context, PremiumFeature.Download)) {
                        val url = ytUrlToShare.ifEmpty { urlToShare }
                        if (url.isNotEmpty()) {
                            shareUrlToDownloader(context, YTDLNIS_APP, url) {
                                pendingInstallApp = YTDLNIS_APP
                            }
                        }
                    }
                }
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
                    text = if (isGeneratingImage)
                        stringResource(R.string.share_generating_image)
                    else
                        stringResource(R.string.share_with_other_apps),
                    color = colorPalette().onAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    pendingInstallApp?.let { app ->
        ConfirmationDialog(
            text = stringResource(R.string.share_app_not_installed, app.name, app.description),
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.share_open_github),
            onDismiss = { pendingInstallApp = null },
            onConfirm = { openExternalUrl(context, app.githubUrl) }
        )
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
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colorPalette().background2)
        ) {
            Image(
                painter = painterResource(icon),
                colorFilter = ColorFilter.tint(colorPalette().text),
                contentDescription = label,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = colorPalette().textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val INSTAGRAM_PACKAGE = "com.instagram.android"

private fun isPackageInstalled(context: Context, packageName: String): Boolean =
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

private fun shareWithImage(
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
            putExtra(Intent.EXTRA_TEXT, "$intro\n$url")
            putExtra(Intent.EXTRA_SUBJECT, intro)
            if (packageName != null) setPackage(packageName)
        }
        startShareIntent(context, intent, packageName, imageUri)
    } catch (e: ActivityNotFoundException) {
        val appName = when (packageName) {
            INSTAGRAM_PACKAGE -> "Instagram"
            "com.whatsapp" -> "WhatsApp"
            "com.facebook.katana" -> "Facebook"
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

internal data class DownloaderApp(
    val name: String,
    val packageName: String,
    val githubUrl: String,
    val description: String
)

internal val YTDLNIS_APP = DownloaderApp(
    name = "YTDLnis",
    packageName = "com.deniscerri.ytdl",
    githubUrl = "https://github.com/deniscerri/ytdlnis",
    description = "YTDLnis es un descargador open-source basado en yt-dlp para audio y video de YouTube y cientos de sitios."
)

internal fun shareUrlToDownloader(
    context: Context,
    app: DownloaderApp,
    url: String,
    onAppMissing: () -> Unit
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        // YTDLnis opens its card on whichever tab this names. This is a music app, so
        // audio, every time; it used to land on whatever the last download had been.
        putExtra("TYPE", "audio")
        setPackage(app.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
        AppEvents.log(AppEvents.DOWNLOAD_SEND, detail = app.packageName)
    } catch (e: ActivityNotFoundException) {
        onAppMissing()
    }
}

/**
 * Hands a whole list to YTDLnis in one go.
 *
 * Not as shared text: YTDLnis runs extractURL() on it, a regex that returns the FIRST match and
 * nothing else, so a playlist sent that way downloaded exactly one song. What it does read in
 * full is a text file of links, one per line, which is what the application/txt entry in its
 * manifest is for. So the list is written to a file in the cache and handed over as a content
 * uri. Local files and radio stations have nothing to download and are left out.
 */
private const val MAX_BULK_DOWNLOAD_URLS = 1000

/**
 * A whole playlist, album or artist, by its own url.
 *
 * One link is worth far more than a file of many: yt-dlp expands a playlist or a channel
 * by itself, so YTDLnis opens its quick download card with every track already in it,
 * instead of the app opening on a list of links. The song list is only the fallback for
 * collections with no url of their own, like a playlist made here.
 */
fun shareCollectionToDownloader(
    context: Context,
    url: String?,
    songs: List<Song>,
    title: String = "",
    onEmpty: () -> Unit = {},
    onAppMissing: () -> Unit = {},
) {
    if (!url.isNullOrBlank()) {
        shareUrlToDownloader(context, YTDLNIS_APP, url, onAppMissing)
        return
    }
    shareSongsToDownloader(context, songs, title, onEmpty, onAppMissing)
}

/** YouTube's own limit for a temporary playlist built out of ids. */
private const val MAX_TEMP_PLAYLIST_IDS = 50

/**
 * Turns a handful of video ids into a playlist link.
 *
 * youtube.com/watch_videos?video_ids=... answers 303 with a list=TLGG... id, a real
 * playlist holding exactly those videos. That matters because YTDLnis reads ONE link per
 * share: with this, a playlist of your own, or the songs on screen, arrive as a single
 * link and its download card opens with every track, instead of the app opening on a
 * file of links.
 */
private fun temporaryPlaylistUrl(videoIds: List<String>): String? {
    if (videoIds.isEmpty()) return null
    val ids = videoIds.take(MAX_TEMP_PLAYLIST_IDS).joinToString(",")
    return runCatching {
        val connection = (URL("https://www.youtube.com/watch_videos?video_ids=$ids")
            .openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val location = connection.getHeaderField("Location")
        connection.disconnect()
        location?.substringAfter("list=", "")?.substringBefore('&')
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://www.youtube.com/playlist?list=$it" }
    }.getOrNull()
}

fun shareSongsToDownloader(
    context: Context,
    songs: List<Song>,
    title: String = "",
    onEmpty: () -> Unit = {},
    onAppMissing: () -> Unit = {},
) {
    val urls = songs
        .mapNotNull { it.shareYTMUrl ?: it.shareYTUrl }
        .distinct()
        // An intent extra travels through Binder, which refuses transactions around 500 KB.
        // A thousand links is roughly 90 KB, comfortably inside it and more than any real list.
        .take(MAX_BULK_DOWNLOAD_URLS)

    if (urls.isEmpty()) {
        onEmpty()
        return
    }

    // A local file or a station has no video id, so they never travel.
    val videoIds = songs.map { it.id }.filterNot { it.startsWith("local:") || it.startsWith("radio:") }
    CoroutineScope(Dispatchers.IO).launch {
        val playlistUrl = temporaryPlaylistUrl(videoIds)
        withContext(Dispatchers.Main) {
            if (playlistUrl != null) shareUrlToDownloader(context, YTDLNIS_APP, playlistUrl, onAppMissing)
            else shareLinksFileToDownloader(context, urls, title, onEmpty, onAppMissing)
        }
    }
}

/** Last resort when the temporary playlist cannot be built: the app opens on the links. */
private fun shareLinksFileToDownloader(
    context: Context,
    urls: List<String>,
    title: String,
    onEmpty: () -> Unit,
    onAppMissing: () -> Unit,
) {
    val uri = runCatching {
        val folder = File(context.cacheDir, "downloads-share").apply { mkdirs() }
        val safeTitle = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "yammbo" }
        val file = File(folder, safeTitle + "-links.txt")
        file.writeText(urls.joinToString("\n"))
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }.getOrNull()

    if (uri == null) {
        onEmpty()
        return
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        // The mime type its manifest listens on for a file of links; text/plain goes to the
        // single-link share screen, which is where the whole list was being thrown away.
        type = "application/txt"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra("TYPE", "audio")
        if (title.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, title)
        setPackage(YTDLNIS_APP.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onAppMissing()
    } catch (e: Exception) {
        // A list far past the cap, or an odd OEM restriction: never crash on a share
        onEmpty()
    }
}

internal fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
    }
}
