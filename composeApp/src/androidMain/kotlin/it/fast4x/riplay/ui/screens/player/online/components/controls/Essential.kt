package it.fast4x.riplay.ui.screens.player.online.components.controls

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import it.fast4x.riplay.ui.styling.tight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.LocalPlayerServiceBinder
import com.yambo.music.R
import androidx.compose.ui.focus.onFocusChanged
import it.fast4x.riplay.utils.artistRouteFor
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.utils.isTVDevice
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.enums.ButtonState
import it.fast4x.riplay.enums.ColorPaletteMode
import it.fast4x.riplay.enums.ColorPaletteName
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.PlayerBackgroundColors
import it.fast4x.riplay.enums.PlayerControlsType
import it.fast4x.riplay.enums.PlayerPlayButtonType
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.QueueLoopType
import it.fast4x.riplay.data.models.Info
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.data.models.UiMedia
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.ui.components.likeBounce
import it.fast4x.riplay.ui.components.themed.IconButton
import it.fast4x.riplay.ui.components.themed.SelectorArtistsDialog
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.bounceClick
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import it.fast4x.riplay.ui.styling.collapsedPlayerProgressBar
import it.fast4x.riplay.ui.styling.favoritesIcon
import it.fast4x.riplay.utils.horizontalfadingEdge2
import it.fast4x.riplay.utils.addToOnlineLikedSong
import it.fast4x.riplay.ui.styling.bold
import it.fast4x.riplay.extensions.preferences.buttonStateKey
import it.fast4x.riplay.extensions.preferences.colorPaletteModeKey
import it.fast4x.riplay.extensions.preferences.colorPaletteNameKey
import it.fast4x.riplay.utils.applyIf
import it.fast4x.riplay.extensions.preferences.effectRotationKey
import it.fast4x.riplay.utils.getIconQueueLoopState
import it.fast4x.riplay.utils.getLikeState
import it.fast4x.riplay.utils.getUnlikedIcon
import it.fast4x.riplay.utils.isNetworkConnected
import it.fast4x.riplay.extensions.preferences.jumpPreviousKey
import it.fast4x.riplay.utils.mediaItemToggleLike
import it.fast4x.riplay.extensions.preferences.playerBackgroundColorsKey
import it.fast4x.riplay.extensions.preferences.playerControlsTypeKey
import it.fast4x.riplay.extensions.preferences.queueLoopTypeKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.commonutils.setDisLikeState
import it.fast4x.riplay.utils.setQueueLoopState
import it.fast4x.riplay.extensions.preferences.showthumbnailKey
import it.fast4x.riplay.utils.copyTextToClipboard
import it.fast4x.riplay.extensions.preferences.textoutlineKey
import it.fast4x.riplay.utils.DisposableListener
import it.fast4x.riplay.utils.removeFromOnlineLikedSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@SuppressLint("UnusedBoxWithConstraintsScope")
@UnstableApi
@ExperimentalFoundationApi
@Composable
fun InfoAlbumAndArtistEssential(
    navController: NavController,
    albumId: String?,
    media: UiMedia,
    title: String?,
    likedAt: Long?,
    artistIds: List<Info>?,
    artist: String?,
    isExplicit: Boolean,
    onCollapse: () -> Unit,
    disableScrollingText: Boolean = false,
    mediaItem: MediaItem
) {
    var effectRotationEnabled by rememberPreference(effectRotationKey, false)
    var isRotated by rememberSaveable { mutableStateOf(false) }
    var showSelectDialog by remember { mutableStateOf(false) }

    if (showSelectDialog)
        SelectorArtistsDialog(
            title = stringResource(R.string.artists),
            onDismiss = { showSelectDialog = false },
            values = artistIds,
            onValueSelected = {
                CoroutineScope(Dispatchers.Main).launch {
                    navController.navigate(route = artistRouteFor(mediaItem.mediaId, it, artistIds?.firstOrNull { a -> a.id == it }?.name))
                }
                showSelectDialog = false
                onCollapse()
            }
        )

    // Yammbo Noir: title and artist flush left, the like button in a ring on the right.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            if (albumId != null) {
                                navController.navigate(route = "${NavRoutes.album.name}/${albumId}")
                                onCollapse()
                            }
                        },
                        onLongClick = {
                            copyTextToClipboard(cleanPrefix(title ?: ""), context = appContext())
                        }
                    )
                    .applyIf(!disableScrollingText) { basicMarquee() }
            ) {
                if (isExplicit) {
                    Image(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().textSecondary),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                BasicText(
                    text = cleanPrefix(title ?: ""),
                    style = typography().xxl.bold.tight.copy(color = colorPalette().text, fontSize = 26.sp),
                    maxLines = 1,
                )
            }
            BasicText(
                text = artist ?: "",
                style = typography().m.copy(color = colorPalette().textSecondary, fontSize = 16.sp),
                maxLines = 1,
                modifier = Modifier
                    .combinedClickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {
                            if (artistIds?.isNotEmpty() == true && artistIds.size > 1)
                                showSelectDialog = true
                            if (artistIds?.isNotEmpty() == true && artistIds.size == 1 && artistIds.firstOrNull()?.id != "") {
                                CoroutineScope(Dispatchers.Main).launch {
                                    navController.navigate(route = artistRouteFor(mediaItem.mediaId, artistIds[0].id, artistIds[0].name))
                                }
                                onCollapse()
                            }
                        },
                        onLongClick = {
                            copyTextToClipboard(artist ?: "", context = appContext())
                        }
                    )
                    .applyIf(!disableScrollingText) { basicMarquee() }
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                // Before the clip so the ring can grow past the circle
                .likeBounce((likedAt ?: 0L) > 0L, colorPalette().text, key = mediaItem.mediaId)
                .clip(CircleShape)
                .border(1.dp, colorPalette().text.copy(alpha = 0.22f), CircleShape)
        ) {
            IconButton(
                color = colorPalette().text,
                icon = getLikeState(mediaItem.mediaId),
                onClick = {
                    if (!it.fast4x.riplay.extensions.ads.PremiumGuard.checkFeature(
                        it.fast4x.riplay.utils.globalContext(),
                        it.fast4x.riplay.extensions.ads.PremiumFeature.Like
                    )) return@IconButton
                    if (!isNetworkConnected(appContext()) && isYtSyncEnabled()) {
                        SmartMessage(appContext().resources.getString(R.string.no_connection), context = appContext(), type = PopupType.Error)
                    } else if (!isYtSyncEnabled()){
                        Database.asyncTransaction {
                            CoroutineScope(Dispatchers.IO).launch {
                                mediaItemToggleLike(mediaItem)
                            }
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            addToOnlineLikedSong(mediaItem)
                        }
                    }
                    if (effectRotationEnabled) isRotated = !isRotated
                },
                onLongClick = {
                    if (!isNetworkConnected(appContext()) && isYtSyncEnabled()) {
                        SmartMessage(appContext().resources.getString(R.string.no_connection), context = appContext(), type = PopupType.Error)
                    } else if (!isYtSyncEnabled()){
                        Database.asyncTransaction {
                            CoroutineScope(Dispatchers.IO).launch {
                                if (like(mediaItem.mediaId, setDisLikeState(likedAt)) == 0) {
                                    insert(mediaItem, Song::toggleDislike)
                                }
                            }
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            // currently can not implement disliking for sync, so only unliking the song
                            removeFromOnlineLikedSong(mediaItem)
                        }
                    }
                    if (effectRotationEnabled) isRotated = !isRotated
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsEssential(
    position: Long,
    playbackSpeed: Float,
    shouldBePlaying: Boolean,
    likedAt: Long?,
    mediaItem: MediaItem,
    playerPlayButtonType: PlayerPlayButtonType,
    isGradientBackgroundEnabled: Boolean,
    onShowSpeedPlayerDialog: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffleMode: () -> Unit,
    playerState: PlayerConstants.PlayerState,
) {

    val colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.System)
    var effectRotationEnabled by rememberPreference(effectRotationKey, false)
    var isRotated by rememberSaveable { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )
    val shouldBePlayingTransition = updateTransition(shouldBePlaying, label = "shouldBePlaying")
    val playPauseRoundness by shouldBePlayingTransition.animateDp(
        transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
        label = "playPauseRoundness",
        targetValueByState = { if (it) 32.dp else 16.dp }
    )

    var queueLoopType by rememberPreference(queueLoopTypeKey, defaultValue = QueueLoopType.Default)
    val playerBackgroundColors by rememberPreference(playerBackgroundColorsKey,PlayerBackgroundColors.CoverColorGradient)
    var jumpPrevious by rememberPreference(jumpPreviousKey,"3")
    val currentMediaItem = mediaItem
    var lightTheme = colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))

    // TV D-pad focus highlight state for main transport controls.
    // Only used when running on a TV — on phones/tablets these remain false.
    val isTvCtx = isTVDevice()
    var prevFocused by remember { mutableStateOf(false) }
    var playFocused by remember { mutableStateOf(false) }
    var nextFocused by remember { mutableStateOf(false) }
    // Capture accent from the @Composable context so non-composable Modifier
    // blocks (applyIf) can use it without invoking colorPalette() inside them.
    val accentColor = colorPalette().accent
    // Yammbo Noir: the plain button types become the solid white rounded square.
    val noirPlay = playerPlayButtonType == PlayerPlayButtonType.Disabled || playerPlayButtonType == PlayerPlayButtonType.Default
    val playShape = if (noirPlay) RoundedCornerShape(24.dp) else RoundedCornerShape(playPauseRoundness)
    val focusBorderColor = if (noirPlay) colorPalette().textSecondary else accentColor

    val binder = LocalPlayerServiceBinder.current
    binder?.player?.DisposableListener {
        object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                queueLoopType = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> QueueLoopType.RepeatOne
                    Player.REPEAT_MODE_ALL -> QueueLoopType.RepeatAll
                    else -> QueueLoopType.Default
                }
                super.onRepeatModeChanged(repeatMode)
            }
        }
    }

    // Yammbo Noir: the like button lives next to the title now; the panel opens with shuffle.
    IconButton(
        icon = R.drawable.shuffle,
        color = colorPalette().textSecondary,
        enabled = true,
        onClick = {
            onToggleShuffleMode()
        },
        modifier = Modifier
            .size(24.dp),
    )

    Image(
        painter = painterResource(R.drawable.play_skip_back),
        contentDescription = null,
        colorFilter = ColorFilter.tint(colorPalette().text),
        modifier = Modifier
            .then(if (isTvCtx) Modifier.onFocusChanged { prevFocused = it.isFocused } else Modifier)
            .then(if (isTvCtx) Modifier.focusable() else Modifier)
            .clip(CircleShape)
            .background(
                if (isTvCtx && prevFocused) colorPalette().accent.copy(alpha = 0.28f)
                else Color.Transparent
            )
            .border(
                width = if (isTvCtx && prevFocused) 2.dp else 0.dp,
                color = if (isTvCtx && prevFocused) colorPalette().accent else Color.Transparent,
                shape = CircleShape
            )
            .combinedClickable(
                indication = ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    //if (jumpPrevious == "") jumpPrevious = "0"
                    onPrevious()

                    if (effectRotationEnabled) isRotated = !isRotated
                },
                onLongClick = {}
            )
            .rotate(rotationAngle)
            .padding(11.dp)
            .size(28.dp)

    )



    Box(
        modifier = Modifier
            .then(if (isTvCtx) Modifier.onFocusChanged { playFocused = it.isFocused } else Modifier)
            .then(if (isTvCtx) Modifier.focusable() else Modifier)
            .combinedClickable(
                indication = ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    if (shouldBePlaying) {
                        onPause()
                    } else {
                        onPlay()
                    }
                    if (effectRotationEnabled) isRotated = !isRotated
                },
                onLongClick = onShowSpeedPlayerDialog
            )
            .bounceClick()
            .applyIf(playerPlayButtonType != PlayerPlayButtonType.Circle) {
                clip(playShape)
            }
            .applyIf(playerPlayButtonType == PlayerPlayButtonType.Circle) { clip(CircleShape) }
            .applyIf(isTvCtx && playFocused) {
                border(3.dp, focusBorderColor, playShape)
            }
            .background(
                when (playerPlayButtonType) {
                    PlayerPlayButtonType.Disabled -> colorPalette().accent
                    PlayerPlayButtonType.CircularRibbed -> Color.Transparent
                    else -> {
                        if (playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient && !noirPlay)
                            if (lightTheme) Color.White else Color.Black
                        else colorPalette().accent
                    }
                }
            )
            .width(if (noirPlay) 68.dp else playerPlayButtonType.width.dp)
            .height(if (noirPlay) 68.dp else playerPlayButtonType.height.dp)

    ) {

        if (playerPlayButtonType == PlayerPlayButtonType.CircularRibbed)
            Image(
                painter = painterResource(R.drawable.a13shape),
                colorFilter = ColorFilter.tint(
                    if (playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient)
                        if (lightTheme) Color.White else Color.Black
                    else colorPalette().accent
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationAngle)
                    .bounceClick(),
                contentDescription = "Background Image",
                contentScale = ContentScale.Fit
            )

        val imgSize = if (noirPlay) 28.dp else 30.dp
        val playIconColor = if (playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient && !noirPlay) colorPalette().accent
            else colorPalette().onAccent
        if (playerState != PlayerConstants.PlayerState.BUFFERING) {
            Image(
                painter = painterResource(if (shouldBePlaying) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                colorFilter = ColorFilter.tint(playIconColor),
                modifier = Modifier
                    .rotate(rotationAngle)
                    .align(Alignment.Center)
                    .size(imgSize)
                    .bounceClick()
            )
        } else CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center).size(imgSize),
            color = playIconColor
        )

        val fmtSpeed = "%.1fx".format(playbackSpeed).replace(",", ".")
        if (fmtSpeed != "1.0x")
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)

            ) {
                BasicText(
                    text = fmtSpeed,
                    style = TextStyle(
                        color = playIconColor,
                        fontStyle = typography().xxxs.semiBold.fontStyle,
                        fontSize = typography().xxxs.semiBold.fontSize
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .padding(bottom = if (playerPlayButtonType != PlayerPlayButtonType.CircularRibbed) 5.dp else 15.dp)
                )
            }
    }




    Image(
        painter = painterResource(R.drawable.play_skip_forward),
        contentDescription = null,
        colorFilter = ColorFilter.tint(colorPalette().text),
        modifier = Modifier
            .then(if (isTvCtx) Modifier.onFocusChanged { nextFocused = it.isFocused } else Modifier)
            .then(if (isTvCtx) Modifier.focusable() else Modifier)
            .clip(CircleShape)
            .background(
                if (isTvCtx && nextFocused) colorPalette().accent.copy(alpha = 0.28f)
                else Color.Transparent
            )
            .border(
                width = if (isTvCtx && nextFocused) 2.dp else 0.dp,
                color = if (isTvCtx && nextFocused) colorPalette().accent else Color.Transparent,
                shape = CircleShape
            )
            .combinedClickable(
                indication = ripple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    val ctx = it.fast4x.riplay.utils.globalContext()
                    if (it.fast4x.riplay.extensions.ads.YammboAdManager.canSkip(ctx)) {
                        it.fast4x.riplay.extensions.ads.YammboAdManager.recordSkip()
                        onNext()
                        if (effectRotationEnabled) isRotated = !isRotated
                    } else {
                        it.fast4x.riplay.extensions.ads.PremiumGuard.checkFeature(ctx, it.fast4x.riplay.extensions.ads.PremiumFeature.SkipSong)
                    }
                },
                onLongClick = {}
            )
            .rotate(rotationAngle)
            .padding(11.dp)
            .size(28.dp)

    )



    IconButton(
        icon = getIconQueueLoopState(queueLoopType),
        color = if (queueLoopType == QueueLoopType.Default) colorPalette().textSecondary else colorPalette().text,
        onClick = {
            queueLoopType = setQueueLoopState(queueLoopType)
        },
        modifier = Modifier
            //.padding(10.dp)
            .size(26.dp)
    )

}