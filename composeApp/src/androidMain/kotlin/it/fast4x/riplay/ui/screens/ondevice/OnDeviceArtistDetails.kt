package it.fast4x.riplay.ui.screens.ondevice

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.NoirWidth
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.components.themed.NoirSectionHeader
import it.fast4x.riplay.ui.components.themed.NoirTextAction
import it.fast4x.riplay.ui.screens.album.AlbumPlayAction
import it.fast4x.riplay.ui.screens.album.AlbumSquareAction
import it.fast4x.riplay.ui.screens.album.rememberCoverTint
import it.fast4x.riplay.ui.screens.artist.ArtistShelfCard
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import java.net.URLEncoder
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.LocalPlayerAwareWindowInsets
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import com.yambo.music.R
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.enums.ArtistItem
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.utils.thumbnailShape
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.ui.components.CustomModalBottomSheet
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.SwipeablePlaylistItem
import it.fast4x.riplay.ui.components.themed.AutoResizeText
import it.fast4x.riplay.ui.components.themed.FontSizeRange
import it.fast4x.riplay.ui.components.themed.HeaderIconButton
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.components.themed.Title2Actions
import it.fast4x.riplay.ui.items.AlbumItem
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.utils.applyIf
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.enqueue
import it.fast4x.riplay.utils.fadingEdge
import it.fast4x.riplay.utils.forcePlayFromBeginning
import it.fast4x.riplay.utils.isExplicit
import it.fast4x.riplay.utils.isLandscape
import it.fast4x.riplay.extensions.preferences.parentalControlEnabledKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.utils.resize
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.forcePlay
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@UnstableApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun OnDeviceArtistDetails(
    navController: NavController,
    artistId: String?,
    disableScrollingText: Boolean
) {

    if (artistId == null) return

    val binder = LocalPlayerServiceBinder.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val selectedQueue = LocalSelectedQueue.current

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val thumbnailRoundness by rememberPreference(thumbnailRoundnessKey, ThumbnailRoundness.Light)

    var artistItem by rememberSaveable {
        mutableStateOf(ArtistItem.Songs)
    }

    val context = LocalContext.current

    val artist by remember {
        Database.artist(artistId)
    }.collectAsState(initial = null, context = Dispatchers.IO)
    println("OnDeviceArtistDetails artistId: $artistId artist: $artist")
    val topSongs by remember {
        Database.artistTopSongs(artistId)
    }.collectAsState(initial = emptyList(), context = Dispatchers.IO)
    println("OnDeviceArtistDetails topSongs: $topSongs")
    val albums by remember {
        Database.artistAlbums(artistId)
    }.collectAsState(initial = emptyList(), context = Dispatchers.IO)
    println("OnDeviceArtistDetails albums: $albums")

    var showArtistItems by rememberSaveable { mutableStateOf(false) }

    val hapticFeedback = LocalHapticFeedback.current
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val menuState = LocalGlobalSheetState.current

    var scrollToNowPlaying by remember {
        mutableStateOf(false)
    }

    var nowPlayingItem by remember {
        mutableStateOf(-1)
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(scrollToNowPlaying) {
        if (scrollToNowPlaying)
            // +2: the header and the songs title come before the songs.
            lazyListState.scrollToItem(nowPlayingItem + 2, 1)
        scrollToNowPlaying = false
    }

    val playable = topSongs.any { it.likedAt != -1L }

    // The photo when there is one, else the cover of the first song.
    val artistImageUrl = artist?.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: topSongs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
    val coverTint = rememberCoverTint(artistImageUrl)

    // The "..." of the action bar: radio and locate.
    val openMoreMenu: () -> Unit = {
        menuState.display {
            Menu {
                MenuEntry(
                    icon = R.drawable.radio,
                    text = stringResource(R.string.start_radio),
                    enabled = playable,
                    onClick = {
                        menuState.hide()
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(topSongs.filter { it.likedAt != -1L }
                            .map(Song::asMediaItem))
                        binder?.setupRadio(
                            NavigationEndpoint.Endpoint.Watch(
                                videoId = topSongs.first { it.likedAt != -1L }.id
                            )
                        )
                    }
                )
                MenuEntry(
                    icon = R.drawable.locate,
                    text = stringResource(R.string.info_find_the_song_that_is_playing),
                    enabled = topSongs.isNotEmpty(),
                    onClick = {
                        menuState.hide()
                        nowPlayingItem = -1
                        scrollToNowPlaying = false
                        topSongs
                            .forEachIndexed { index, song ->
                                if (song.asMediaItem.mediaId == binder?.player?.currentMediaItem?.mediaId)
                                    nowPlayingItem = index
                            }

                        if (nowPlayingItem > -1)
                            scrollToNowPlaying = true
                    }
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
    ) {

        LazyListContainer(
            state = lazyListState,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
            ) {

                item(key = "header") {
                    val name = cleanPrefix(artist?.name ?: "")
                    OnDeviceArtistHeader(
                        imageUrl = artistImageUrl,
                        name = name,
                        count = topSongs.size,
                        tint = coverTint,
                        onProfile = if (name.isNotBlank()) {
                            {
                                // The real profile lives online: search the artist name.
                                navController.navigate(
                                    "${NavRoutes.search.name}?text=${URLEncoder.encode(name, "UTF-8")}"
                                )
                            }
                        } else null
                    ) {
                        AlbumSquareAction(
                            icon = R.drawable.shuffle,
                            enabled = topSongs.isNotEmpty(),
                            onClick = {
                                if (playable) {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayFromBeginning(
                                        topSongs.filter { it.likedAt != -1L }
                                            .shuffled()
                                            .map(Song::asMediaItem)
                                    )
                                } else {
                                    SmartMessage(
                                        context.resources.getString(R.string.disliked_this_collection),
                                        type = PopupType.Error,
                                        context = context
                                    )
                                }
                            },
                            onLongClick = {
                                SmartMessage(
                                    context.resources.getString(R.string.info_shuffle),
                                    context = context
                                )
                            }
                        )

                        AlbumPlayAction(
                            enabled = topSongs.isNotEmpty(),
                            onClick = {
                                if (playable) {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayFromBeginning(
                                        topSongs.filter { it.likedAt != -1L }
                                            .map(Song::asMediaItem)
                                    )
                                } else {
                                    SmartMessage(
                                        context.resources.getString(R.string.disliked_this_collection),
                                        type = PopupType.Error,
                                        context = context
                                    )
                                }
                            }
                        )

                        AlbumSquareAction(
                            icon = R.drawable.ellipsis_horizontal,
                            enabled = topSongs.isNotEmpty(),
                            onClick = openMoreMenu
                        )
                    }
                }

                item {
                    Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                        NoirSectionHeader(
                            title = stringResource(R.string.songs),
                            onSeeAll = if (topSongs.isNotEmpty()) {
                                {
                                    artistItem = ArtistItem.Songs
                                    showArtistItems = true
                                }
                            } else null,
                            trailing = {
                                OnDeviceRandomButton(enabled = topSongs.isNotEmpty()) {
                                    if (topSongs.isEmpty()) return@OnDeviceRandomButton
                                    val item = topSongs.get(
                                        if (topSongs.size > 1)
                                            Random(System.currentTimeMillis()).nextInt(0, topSongs.size - 1)
                                        else 0
                                    )
                                    binder?.player?.forcePlay(item.asMediaItem)
                                }
                            }
                        )
                    }
                }
                items(
                    count = topSongs.size
                ) { index ->
                    val item = topSongs[index]
                    if (parentalControlEnabled && item.asMediaItem.isExplicit) return@items
                    Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                    SwipeablePlaylistItem(
                        mediaItem = item.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(
                                item.asMediaItem,
                                queue = selectedQueue ?: defaultQueue()
                            )
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(item.asMediaItem, queue = it)
                        }
                    ) {
                        OnDeviceArtistSongRow(
                            song = item,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                navController = navController,
                                                onDismiss = {
                                                    menuState.hide()
                                                },
                                                mediaItem = item.asMediaItem,
                                                onInfo = {
                                                    navController.navigate("${NavRoutes.videoOrSongInfo.name}/${item.id}")
                                                },
                                                disableScrollingText = disableScrollingText,
                                            )
                                        };
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlay(item.asMediaItem)
                                    }
                                )
                        )
                    }
                    }
                }
                item {
                    Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                        NoirSectionHeader(
                            title = stringResource(R.string.albums),
                            onSeeAll = if (albums.isNotEmpty()) {
                                {
                                    artistItem = ArtistItem.Albums
                                    showArtistItems = true
                                }
                            } else null,
                            trailing = {
                                OnDeviceRandomButton(enabled = albums.isNotEmpty()) {
                                    if (albums.isEmpty()) return@OnDeviceRandomButton
                                    val idItem = albums.get(
                                        if (albums.size > 1)
                                            Random(System.currentTimeMillis()).nextInt(0, albums.size - 1)
                                        else 0
                                    ).id
                                    navController.navigate(route = "${NavRoutes.onDeviceAlbum.name}/${idItem}")
                                }
                            }
                        )
                    }
                }
                item {
                    // Square covers on a shelf, the same as the online artist page.
                    val shelfScale = rememberNoirLayout().shelfScale
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums.size) { index ->
                            val item = albums[index]
                            ArtistShelfCard(
                                image = item.thumbnailUrl,
                                title = cleanPrefix(item.title ?: ""),
                                subtitle = item.year?.takeIf { it.isNotBlank() },
                                width = 152.dp * shelfScale,
                                onClick = {
                                    navController.navigate(route = "${NavRoutes.onDeviceAlbum.name}/${item.id}")
                                }
                            )
                        }
                    }
                }



                item(key = "bottom") {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }

            }
        }

//        val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
//        if (UiType.ViMusic.isCurrent() && showFloatingIcon)
//            artistPage?.radioEndpoint?.let { endpoint ->
//
//                MultiFloatingActionsContainer(
//                    iconId = R.drawable.radio,
//                    onClick = {
//                        binder?.stopRadio()
//                        binder?.playRadio(endpoint)
//                    },
//                    onClickSettings = { navController.navigate(NavRoutes.search.name) },
//                    onClickSearch = { navController.navigate(NavRoutes.settings.name) }
//                )
//
//            }


        if (artist != null)
            CustomModalBottomSheet(
                showSheet = showArtistItems,
                onDismissRequest = { showArtistItems = false },
                containerColor = colorPalette().background2,
                contentColor = colorPalette().background2,
                modifier = Modifier
                    .fillMaxWidth(),
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = {
                    Surface(
                        modifier = Modifier.padding(vertical = 0.dp),
                        color = colorPalette().background0,
                        shape = thumbnailShape()
                    ) {}
                },
                shape = thumbnailRoundness.shape()
            ) {
                OnDeviceArtistItems (
                    navController,
                    artistId = artist?.id.toString() ,
                    artistName = cleanPrefix(artist?.name.toString()),
                    artistItem = artistItem,
                    disableScrollingText = false,
                    onDismiss = { showArtistItems = false }
                )
            }


    }

}

/** Top of the on-device artist page: cover tint, round picture, name and the song count. */
@Composable
private fun OnDeviceArtistHeader(
    imageUrl: String?,
    name: String,
    count: Int,
    tint: Color?,
    onProfile: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = colorPalette()
    val page = colors.background0
    val glow by animateColorAsState(
        targetValue = lerp(page, tint ?: page, 0.6f),
        animationSpec = tween(600),
        label = "onDeviceArtistTint"
    )
    val noirLayout = rememberNoirLayout()
    val side = when (noirLayout.width) {
        NoirWidth.Compact -> 168.dp
        NoirWidth.Medium -> 200.dp
        NoirWidth.Expanded -> 232.dp
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to glow,
                        0.55f to glow.copy(alpha = 0.4f),
                        1f to page
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(side)
                    .clip(CircleShape)
                    .background(colors.background2)
            ) {
                if (imageUrl != null)
                    RevealImage(
                        model = imageUrl.thumbnail(800),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                else
                    BasicText(
                        text = name.take(1).uppercase(),
                        style = typography().xxl.tight.copy(
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 56.sp
                        )
                    )
            }
            Spacer(Modifier.height(22.dp))
            if (name.isNotBlank())
                BasicText(
                    text = name,
                    style = typography().xxl.tight.copy(
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (name.length > 22) 28.sp else 34.sp,
                        lineHeight = if (name.length > 22) 32.sp else 38.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            BasicText(
                text = (
                        if (count == 1) stringResource(R.string.ondevice_artist_song_one)
                        else stringResource(R.string.ondevice_artist_songs_count, count)
                        ).uppercase(),
                style = typography().xxxs.mono.copy(
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
            )
            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
                content = actions
            )
            if (onProfile != null) {
                Spacer(Modifier.height(8.dp))
                NoirTextAction(
                    text = stringResource(R.string.ondevice_artist_view_profile),
                    onClick = onProfile
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** One song of the artist: its cover, title and duration. Clicks come in [modifier]. */
@Composable
private fun OnDeviceArtistSongRow(
    song: Song,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    // Disliked songs stay in the list but dimmed, as they cannot be played.
    val dimmed = song.likedAt == -1L
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        RevealImage(
            model = song.thumbnailUrl.thumbnail(160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.background2)
        )
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            BasicText(
                text = cleanPrefix(song.title),
                style = typography().s.copy(
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = song.durationText.orEmpty()
            if (subtitle.isNotBlank())
                BasicText(
                    text = subtitle,
                    style = typography().xs.mono.copy(
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        letterSpacing = 0.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
        }
    }
}

/** Small outlined dice of a section header: plays or opens one item at random. */
@Composable
private fun OnDeviceRandomButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Image(
            painter = painterResource(R.drawable.dice),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().text),
            modifier = Modifier.size(20.dp)
        )
    }
}
