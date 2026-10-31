package it.fast4x.riplay.ui.screens.ondevice

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.commonutils.EXPLICIT_PREFIX
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import it.fast4x.riplay.commonutils.MODIFIED_PREFIX
import com.yambo.music.R
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Info
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.data.models.SongPlaylistMap
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.SwipeablePlaylistItem
import it.fast4x.riplay.ui.components.themed.AlbumsItemMenu
import it.fast4x.riplay.ui.components.themed.AutoResizeText
import it.fast4x.riplay.ui.components.themed.FontSizeRange
import it.fast4x.riplay.ui.components.themed.HeaderIconButton
import it.fast4x.riplay.ui.components.themed.InputTextDialog
import it.fast4x.riplay.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.components.themed.NowPlayingSongIndicator
import it.fast4x.riplay.ui.components.themed.SelectorDialog
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.items.AlbumItemPlaceholder
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.items.SongItemPlaceholder
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.ui.styling.align
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.utils.applyIf
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.commonutils.durationTextToMillis
import it.fast4x.riplay.utils.enqueue
import it.fast4x.riplay.utils.fadingEdge
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.forcePlayFromBeginning
import it.fast4x.riplay.utils.formatAsTime
import it.fast4x.riplay.utils.isLandscape
import it.fast4x.riplay.ui.styling.medium
import it.fast4x.riplay.extensions.preferences.parentalControlEnabledKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.utils.resize
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.extensions.preferences.showFloatingIconKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.ui.components.themed.QueuesDialog
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.DisposableListener
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.screens.album.AlbumNoirHeader
import it.fast4x.riplay.ui.screens.album.AlbumPlayAction
import it.fast4x.riplay.ui.screens.album.AlbumSquareAction
import it.fast4x.riplay.ui.screens.album.AlbumTrackRow
import it.fast4x.riplay.ui.screens.album.rememberCoverTint
import it.fast4x.riplay.utils.addToYtLikedSongs
import it.fast4x.riplay.utils.isNetworkConnected
import it.fast4x.riplay.utils.mediaItemSetLiked

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun OnDeviceAlbumDetails(
    navController: NavController,
    albumId: String,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {

    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalGlobalSheetState.current
    val context = LocalContext.current
    val selectedQueue = LocalSelectedQueue.current
    var songs by persistList<Song>("album/$albumId/songs")
    var album by persist<Album?>("album/$albumId")
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)

    LaunchedEffect(Unit) {
        Database.albumSongs(albumId).collect {
            songs = if (parentalControlEnabled)
                it.filter { !it.title.startsWith(EXPLICIT_PREFIX) } else it
        }
    }

    LaunchedEffect(Unit) {
        Database.album(albumId).collect { album = it }
    }

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailAlbumSizeDp = Dimensions.thumbnails.album

    val lazyListState = rememberLazyListState()

    var listMediaItems = remember {
        mutableListOf<MediaItem>()
    }

    var selectItems by remember {
        mutableStateOf(false)
    }

    var showSelectDialog by remember {
        mutableStateOf(false)
    }

    var showDialogChangeAlbumTitle by remember {
        mutableStateOf(false)
    }
    var showDialogChangeAlbumAuthors by remember {
        mutableStateOf(false)
    }
    var showDialogChangeAlbumCover by remember {
        mutableStateOf(false)
    }
    var isCreatingNewPlaylist by rememberSaveable {
        mutableStateOf(false)
    }
    var totalPlayTimes = 0L
    songs.forEach {
        totalPlayTimes += it.durationText?.let { it1 ->
            durationTextToMillis(it1)
        }?.toLong() ?: 0
    }
    var position by remember {
        mutableIntStateOf(0)
    }

    var scrollToNowPlaying by remember {
        mutableStateOf(false)
    }

    var nowPlayingItem by remember {
        mutableStateOf(-1)
    }
    val hapticFeedback = LocalHapticFeedback.current

    if (showDialogChangeAlbumTitle)
        InputTextDialog(
            onDismiss = { showDialogChangeAlbumTitle = false },
            title = stringResource(R.string.update_title),
            value = album?.title.toString(),
            placeholder = stringResource(R.string.title),
            setValue = {
                if (it.isNotEmpty()) {
                    Database.asyncTransaction {
                        updateAlbumTitle(albumId, it)
                    }
                }
            },
            prefix = MODIFIED_PREFIX
        )
    if (showDialogChangeAlbumAuthors)
        InputTextDialog(
            onDismiss = { showDialogChangeAlbumAuthors = false },
            title = stringResource(R.string.update_authors),
            value = album?.authorsText.toString(),
            placeholder = stringResource(R.string.authors),
            setValue = {
                if (it.isNotEmpty()) {
                    Database.asyncTransaction {
                        updateAlbumAuthors(albumId, it)
                    }
                    //context.toast("Album Saved $it")
                }
            },
            prefix = MODIFIED_PREFIX
        )

    if (showDialogChangeAlbumCover)
        InputTextDialog(
            onDismiss = { showDialogChangeAlbumCover = false },
            title = stringResource(R.string.update_cover),
            value = album?.thumbnailUrl.toString(),
            placeholder = stringResource(R.string.cover),
            setValue = {
                if (it.isNotEmpty()) {
                    Database.asyncTransaction {
                        updateAlbumCover(albumId, it)
                    }
                    //context.toast("Album Saved $it")
                }
            },
            prefix = MODIFIED_PREFIX
        )

    if (isCreatingNewPlaylist)
        InputTextDialog(
            onDismiss = { isCreatingNewPlaylist = false },
            title = stringResource(R.string.new_playlist),
            value = "",
            placeholder = stringResource(R.string.new_playlist),
            setValue = {
                if (it.isNotEmpty()) {
                    Database.asyncTransaction {
                        insert(Playlist(name = it))
                    }
                }
            }
        )

    var isViewingQueues by remember { mutableStateOf(false) }
    if (isViewingQueues) {
        QueuesDialog(
            onSelect = {
                binder?.player?.enqueue(songs.map(Song::asMediaItem))
            },
            onDismiss = { isViewingQueues = false }
        )
    }

    if (showSelectDialog)
        SelectorDialog(
            title = stringResource(R.string.enqueue),
            onDismiss = { showSelectDialog = false },
            values = listOf(
                Info("a", stringResource(R.string.enqueue_all)),
                Info("s", stringResource(R.string.enqueue_selected))
            ),
            onValueSelected = {
                if (it == "a") {
                    isViewingQueues = true
                } else selectItems = true

                showSelectDialog = false
            }
        )

    LaunchedEffect(scrollToNowPlaying) {
        if (scrollToNowPlaying)
            // +1: the header is the first item, the tracks follow it.
            lazyListState.scrollToItem(nowPlayingItem + 1)
        scrollToNowPlaying = false
    }

    val playable = songs.any { it.likedAt != -1L }

    // Downloaded copies, for the small mark on each track.
    val downloadedIds by remember { Database.downloadedMediaIds() }
        .collectAsState(initial = emptyList(), context = Dispatchers.IO)
    val downloadedSet = remember(downloadedIds) { downloadedIds.toHashSet() }

    // The track that is playing now shows bars instead of its number.
    var currentMediaId by remember { mutableStateOf(binder?.player?.currentMediaItem?.mediaId) }
    binder?.player?.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaId = mediaItem?.mediaId
            }
        }
    }

    // The only colour of the page: the one the cover brings.
    val coverTint = rememberCoverTint(album?.thumbnailUrl)

    // The full album menu (enqueue, play next, rename, cover, add to playlist, ...).
    val openAlbumMenu: () -> Unit = {
        menuState.display {
            album?.let { it ->
                AlbumsItemMenu(
                    navController = navController,
                    onDismiss = menuState::hide,
                    onSelectUnselect = {
                        selectItems = !selectItems
                        if (!selectItems) {
                            listMediaItems.clear()
                        }
                    },
                    onChangeAlbumTitle = {
                        if (album?.isYoutubeAlbum == true) {
                            SmartMessage(
                                context.resources.getString(R.string.cant_rename_Saved_albums),
                                type = PopupType.Error,
                                context = context
                            )
                        } else
                            showDialogChangeAlbumTitle = true
                    },
                    onChangeAlbumAuthors = {
                        showDialogChangeAlbumAuthors = true
                    },
                    onChangeAlbumCover = {
                        showDialogChangeAlbumCover = true
                    },
                    album = it,
                    onPlayNext = {
                        if (listMediaItems.isEmpty()) {
                            if (songs.any { it.likedAt != -1L }) {
                                binder?.player?.addNext(
                                    songs.filter { it.likedAt != -1L }
                                        .map(Song::asMediaItem),
                                    context,
                                    selectedQueue ?: defaultQueue()
                                )
                            } else {
                                SmartMessage(
                                    context.resources.getString(
                                        R.string.disliked_this_collection
                                    ),
                                    type = PopupType.Error,
                                    context = context
                                )
                            }
                        } else {
                            binder?.player?.addNext(
                                listMediaItems,
                                context,
                                selectedQueue ?: defaultQueue()
                            )
                            listMediaItems.clear()
                            selectItems = false
                        }
                    },
                    onEnqueue = {
                        if (listMediaItems.isEmpty()) {
                            if (songs.any { it.likedAt != -1L }) {
                                binder?.player?.enqueue(
                                    songs.filter { it.likedAt != -1L }
                                        .map(Song::asMediaItem),
                                    context
                                )
                            } else {
                                SmartMessage(
                                    context.resources.getString(
                                        R.string.disliked_this_collection
                                    ),
                                    type = PopupType.Error,
                                    context = context
                                )
                            }
                        } else {
                            binder?.player?.enqueue(
                                listMediaItems,
                                context
                            )
                            listMediaItems.clear()
                            selectItems = false
                        }
                    },
                    onAddToPlaylist = { playlistPreview ->
                        position =
                            playlistPreview.songCount.minus(1) ?: 0
                        //Log.d("mediaItem", " maxPos in Playlist $it ${position}")
                        if (position > 0) position++ else position =
                            0
                        //Log.d("mediaItem", "next initial pos ${position}")
                        if (listMediaItems.isEmpty()) {
                            if (!isYtSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                songs.forEachIndexed { index, song ->
                                    Database.asyncTransaction {
                                        insert(song.asMediaItem)
                                        insert(
                                            SongPlaylistMap(
                                                songId = song.asMediaItem.mediaId,
                                                playlistId = playlistPreview.playlist.id,
                                                position = position + index
                                            ).default()
                                        )
                                    }
                                }
                            }
                        } else {
                            if (!isYtSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                listMediaItems.forEachIndexed { index, song ->
                                    //Log.d("mediaItemMaxPos", position.toString())
                                    Database.asyncTransaction {
                                        insert(song)
                                        insert(
                                            SongPlaylistMap(
                                                songId = song.mediaId,
                                                playlistId = playlistPreview.playlist.id,
                                                position = position + index
                                            ).default()
                                        )
                                    }
                                }
                            }
                            listMediaItems.clear()
                            selectItems = false
                        }
                    },
                    onGoToPlaylist = {
                        navController.navigate("${NavRoutes.localPlaylist.name}/$it")
                    },
                    onAddToFavourites = {
                        if (!isNetworkConnected(appContext()) && isYtSyncEnabled()) {
                            SmartMessage(
                                appContext().resources.getString(
                                    R.string.no_connection
                                ),
                                context = appContext(),
                                type = PopupType.Error
                            )
                        } else if (!isYtSyncEnabled()) {
                            songs.forEach { song ->
                                mediaItemSetLiked(song.asMediaItem)
                            }
                        } else {
                            val totalSongsToLike = songs.filter {
                                it.likedAt in listOf(-1L, null)
                            }
                            CoroutineScope(Dispatchers.IO).launch {
                                addToYtLikedSongs(totalSongsToLike.map { it.asMediaItem })
                            }
                        }
                    },
                    disableScrollingText = disableScrollingText,
                )
            }
        }
    }

    // The "..." of the action bar: radio and locate, then the full album menu.
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
                        binder?.player?.forcePlayFromBeginning(songs.filter { it.likedAt != -1L }
                            .map(Song::asMediaItem))
                        binder?.setupRadio(
                            NavigationEndpoint.Endpoint.Watch(
                                videoId = songs.first { it.likedAt != -1L }.id
                            )
                        )
                    }
                )
                MenuEntry(
                    icon = R.drawable.locate,
                    text = stringResource(R.string.info_find_the_song_that_is_playing),
                    enabled = songs.isNotEmpty(),
                    onClick = {
                        menuState.hide()
                        nowPlayingItem = -1
                        scrollToNowPlaying = false
                        songs.forEachIndexed { index, song ->
                            if (song.asMediaItem.mediaId == binder?.player?.currentMediaItem?.mediaId)
                                nowPlayingItem = index
                        }
                        if (nowPlayingItem > -1)
                            scrollToNowPlaying = true
                    }
                )
                MenuEntry(
                    icon = R.drawable.ellipsis_horizontal,
                    text = stringResource(R.string.noir_album_options),
                    enabled = album != null,
                    onClick = { openAlbumMenu() }
                )
            }
        }
    }

            Box(
                modifier = Modifier
                    .background(
                        colorPalette().background0
                    )
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
                        state = lazyListState,
                        modifier = Modifier
                            .background(
                                colorPalette().background0
                            )
                            .fillMaxSize()
                    ) {
                        item(
                            key = "header"
                        ) {
                            // "year · N songs · N min", skipping what is missing.
                            val albumMeta = listOfNotNull(
                                album?.year?.takeIf { it.isNotBlank() },
                                if (songs.isNotEmpty())
                                    stringResource(R.string.noir_album_songs_count, songs.size)
                                else null,
                                (totalPlayTimes / 60_000L).toInt().takeIf { it > 0 }
                                    ?.let { stringResource(R.string.noir_album_minutes, it) }
                            ).joinToString(" · ")

                            // The page sits under the Noir top bar (back is there): embedded header.
                            AlbumNoirHeader(
                                coverUrl = album?.thumbnailUrl,
                                previewUrl = null,
                                sharedKey = null,
                                showCover = true,
                                title = cleanPrefix(album?.title ?: ""),
                                artist = cleanPrefix(album?.authorsText ?: ""),
                                meta = albumMeta,
                                tint = coverTint,
                                onBack = { navController.popBackStack() },
                                onMore = null,
                                embedded = true,
                                actions = {
                                    AlbumSquareAction(
                                        icon = R.drawable.shuffle,
                                        enabled = songs.isNotEmpty(),
                                        onClick = {
                                            if (playable) {
                                                binder?.stopRadio()
                                                binder?.player?.forcePlayFromBeginning(
                                                    songs.filter { it.likedAt != -1L }
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
                                        enabled = songs.isNotEmpty(),
                                        onClick = {
                                            if (playable) {
                                                binder?.stopRadio()
                                                binder?.player?.forcePlayFromBeginning(
                                                    songs.filter { it.likedAt != -1L }
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
                                        enabled = songs.isNotEmpty(),
                                        onClick = openMoreMenu
                                    )
                                }
                            )
                        }

                        itemsIndexed(
                            items = songs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            // Tracks centred at list width on tablets (no-op on phones).
                            Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(
                                        song.asMediaItem,
                                        queue = selectedQueue ?: defaultQueue()
                                    )
                                },
                                onEnqueue = {
                                    binder?.player?.enqueue(song.asMediaItem, queue = it)
                                }
                            ) {
                                val checkedState = rememberSaveable { mutableStateOf(false) }
                                if (!selectItems && checkedState.value) checkedState.value = false
                                AlbumTrackRow(
                                    index = index,
                                    song = song,
                                    isCurrent = currentMediaId == song.id,
                                    isDownloaded = downloadedSet.contains(song.id),
                                    selectMode = selectItems,
                                    checked = checkedState.value,
                                    onCheckedChange = {
                                        checkedState.value = it
                                        if (it) listMediaItems.add(song.asMediaItem) else
                                            listMediaItems.remove(song.asMediaItem)
                                    },
                                    modifier = Modifier
                                        .combinedClickable(
                                            onLongClick = {
                                                menuState.display {
                                                    NonQueuedMediaItemMenu(
                                                        navController = navController,
                                                        onDismiss = {
                                                            menuState.hide()
                                                        },
                                                        mediaItem = song.asMediaItem,
                                                        onInfo = {
                                                            navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.id}")
                                                        },
                                                        disableScrollingText = disableScrollingText,
                                                    )
                                                }
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                            },
                                            onClick = {
                                                if (!selectItems) {
                                                    if (song.likedAt != -1L) {
                                                        binder?.stopRadio()
                                                        binder?.player?.forcePlayAtIndex(
                                                            songs.filter { it.likedAt != -1L }
                                                                .map(Song::asMediaItem),
                                                            songs.filter { it.likedAt != -1L }
                                                                .map(Song::asMediaItem)
                                                                .indexOf(song.asMediaItem)
                                                        )
                                                    } else {
                                                        SmartMessage(
                                                            context.resources.getString(R.string.disliked_this_song),
                                                            type = PopupType.Error,
                                                            context = context
                                                        )
                                                    }
                                                } else checkedState.value = !checkedState.value
                                            }
                                        )
                                )
                            }
                            }
                        }

                        item(key = "bottom") {
                            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                        }

                        if (songs.isEmpty()) {
                            item(key = "loading") {
                                ShimmerHost(
                                    modifier = Modifier
                                        .fillParentMaxSize()
                                ) {
                                    repeat(1) {
                                        AlbumItemPlaceholder(thumbnailSizeDp = Dimensions.thumbnails.album)
                                    }
                                    repeat(4) {
                                        SongItemPlaceholder(thumbnailSizeDp = Dimensions.thumbnails.song)
                                    }
                                }
                            }
                        }

                    }
                }


                val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
                if (UiType.ViMusic.isCurrent() && showFloatingIcon)
                    MultiFloatingActionsContainer(
                        iconId = R.drawable.shuffle,
                        onClick = {
                            if (songs.any { it.likedAt != -1L }) {
                                binder?.stopRadio()
                                binder?.player?.forcePlayFromBeginning(
                                    songs.filter { it.likedAt != -1L }
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
                        onClickSettings = onSettingsClick,
                        onClickSearch = onSearchClick
                    )

                /*
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                onClick = {
                    if (songs.isNotEmpty()) {
                        binder?.stopRadio()
                        binder?.player?.forcePlayFromBeginning(
                            songs.shuffled().map(Song::asMediaItem)
                        )
                    }
                }
            )

             */


            }


}
