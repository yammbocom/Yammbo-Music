package it.fast4x.riplay.ui.screens.ondevice


import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.EnvironmentExt
import it.fast4x.riplay.LocalOnDeviceViewModel
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.Database.Companion.songAlbumInfo
import it.fast4x.riplay.data.Database.Companion.songArtistInfo
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import com.yambo.music.R
import it.fast4x.riplay.enums.MaxSongs
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.SortOrder
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.data.models.SongPlaylistMap
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.SwipeableQueueItem
import it.fast4x.riplay.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.riplay.ui.components.themed.HeaderIconButton
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.components.themed.IconButton
import it.fast4x.riplay.ui.components.themed.IconInfo
import it.fast4x.riplay.ui.components.themed.InPlaylistMediaItemMenu
import it.fast4x.riplay.ui.components.themed.InputTextDialog
import it.fast4x.riplay.ui.components.themed.PlaylistsItemMenu
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.components.themed.SortMenu
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.LocalAppearance
import it.fast4x.riplay.ui.styling.favoritesIcon
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.extensions.preferences.UiTypeKey
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.commonutils.durationTextToMillis
import it.fast4x.riplay.utils.enqueue
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.forcePlayFromBeginning
import it.fast4x.riplay.utils.formatAsTime
import it.fast4x.riplay.extensions.preferences.maxSongsInQueueKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.extensions.preferences.showFloatingIconKey
import it.fast4x.riplay.extensions.preferences.songSortOrderKey
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.extensions.fastshare.FastShare
import it.fast4x.riplay.ui.components.themed.NowPlayingSongIndicator
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import kotlinx.coroutines.CoroutineScope
import it.fast4x.riplay.data.models.SongEntity
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.enums.OnDeviceSongSortBy
import it.fast4x.riplay.utils.LOCAL_KEY_PREFIX
import it.fast4x.riplay.ui.components.PullToRefreshBox
import it.fast4x.riplay.utils.addToYtPlaylist
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.mediaItemToggleLike
import it.fast4x.riplay.ui.components.themed.FastPlayActionsBar
import it.fast4x.riplay.utils.LazyListContainer
import kotlinx.coroutines.delay
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.riplay.extensions.preferences.onDeviceSongSortByKey
import it.fast4x.riplay.ui.items.PlaylistItem
import it.fast4x.riplay.utils.cleanOnDeviceName
import timber.log.Timber
import it.fast4x.riplay.utils.DisposableListener
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.components.themed.NoirChip
import it.fast4x.riplay.ui.screens.album.AlbumNoirHeader
import it.fast4x.riplay.ui.screens.album.AlbumPlayAction
import it.fast4x.riplay.ui.screens.album.AlbumSquareAction
import it.fast4x.riplay.ui.screens.album.AlbumTrackRow
import it.fast4x.riplay.ui.screens.album.NoirListSearchField
import it.fast4x.riplay.ui.screens.album.rememberCoverTint

@KotlinCsvExperimental
@ExperimentalMaterialApi
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun OnDevicePlaylist(
    navController: NavController,
    folder: String,
) {
    val context = LocalContext.current
    val (colorPalette, typography, thumbnailShape) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalGlobalSheetState.current
    val selectedQueue = LocalSelectedQueue.current
    val uiType by rememberPreference(UiTypeKey, UiType.RiPlay)

    val folder by remember(folder) { mutableStateOf(folder.replace("$","/")) }

    var playlistAllSongs by persistList<SongEntity>("localPlaylist/$folder/songs")
    var songsInTheToPlaylist by persistList<SongEntity>("")
    var playlistSongs by persistList<SongEntity>("localPlaylist/$folder/songs")
    var playlistPreview by persist<PlaylistPreview?>("localPlaylist/playlist")
    val thumbnailUrl = remember { mutableStateOf("") }


    var sortBy by rememberPreference(onDeviceSongSortByKey, OnDeviceSongSortBy.DateAdded)
    var sortOrder by rememberPreference(songSortOrderKey, SortOrder.Descending)

    var filter: String? by rememberSaveable { mutableStateOf(null) }

    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)

    val onDeviceViewModel = LocalOnDeviceViewModel.current
    LaunchedEffect(Unit, filter, sortOrder, sortBy) {
        onDeviceViewModel.sortBy = sortBy
        onDeviceViewModel.sortOrder = sortOrder
        onDeviceViewModel.loadAudioFiles()
        onDeviceViewModel.audioFilesFromFolder(folder).collect {
            playlistAllSongs = it
            playlistSongs = it
            playlistPreview = PlaylistPreview(
                playlist = Playlist(name = folder, isEditable = false, ),
                songCount = it.size,
                isOnDevice = true,
                folder = folder
            )
        }

        Timber.d("OnDevicePlaylist playlistAllSongs ${playlistAllSongs.size}")
    }

    var filterCharSequence: CharSequence
    filterCharSequence = filter.toString()

    if (!filter.isNullOrBlank())
        playlistSongs =
            playlistSongs.filter { songItem ->
                songItem.song.title.contains(
                    filterCharSequence,
                    true
                ) ?: false
                        || songItem.song.artistsText?.contains(
                    filterCharSequence,
                    true
                ) ?: false
                        || songItem.albumTitle?.contains(
                    filterCharSequence,
                    true
                ) ?: false
            }

    var searching by rememberSaveable { mutableStateOf(false) }

    var totalPlayTimes = 0L
    playlistSongs.forEach {
        totalPlayTimes += it.song.durationText?.let { it1 ->
            durationTextToMillis(it1)
        }?.toLong() ?: 0
    }


    val thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    val sortOrderIconRotation by animateFloatAsState(
        targetValue = if (sortOrder == SortOrder.Ascending) 0f else 180f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing), label = ""
    )

    val lazyListState = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()

    fun sync() {

        playlistPreview?.let { playlistPreview ->

        }
    }


    val playlistThumbnailSizeDp = Dimensions.thumbnails.playlist
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px

    val rippleIndication = ripple(bounded = false)

    val uriHandler = LocalUriHandler.current

    var scrollToNowPlaying by remember {
        mutableStateOf(false)
    }

    var nowPlayingItem by remember {
        mutableStateOf(-1)
    }

    var listMediaItems = remember {
        mutableListOf<MediaItem>()
    }

    var selectItems by remember {
        mutableStateOf(false)
    }

    var plistId by remember {
        mutableStateOf(0L)
    }
    var plistName by remember {
        mutableStateOf(playlistPreview?.playlist?.name)
    }


    var position by remember {
        mutableIntStateOf(0)
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            coroutineScope.launch (Dispatchers.IO){
                context.applicationContext.contentResolver.openOutputStream(uri)
                    ?.use { outputStream ->
                        csvWriter().open(outputStream) {
                            writeRow(
                                "PlaylistBrowseId",
                                "PlaylistName",
                                "MediaId",
                                "Title",
                                "Artists",
                                "Duration",
                                "ThumbnailUrl",
                                "AlbumId",
                                "AlbumTitle",
                                "ArtistIds"
                            )
                            if (listMediaItems.isEmpty()) {
                                playlistSongs.forEach {
                                    val artistInfos = songArtistInfo(it.asMediaItem.mediaId)
                                    val albumInfo = songAlbumInfo(it.asMediaItem.mediaId)
                                    writeRow(
                                        playlistPreview?.playlist?.browseId,
                                        plistName,
                                        it.song.id,
                                        it.song.title,
                                        artistInfos.joinToString(",") { it.name ?: "" },
                                        it.song.durationText,
                                        it.song.thumbnailUrl,
                                        albumInfo?.id,
                                        albumInfo?.name,
                                        artistInfos.joinToString(",") { it.id }
                                    )
                                }
                            } else {
                                listMediaItems.forEach {
                                    val artistInfos = songArtistInfo(it.mediaId)
                                    val albumInfo = songAlbumInfo(it.mediaId)
                                    writeRow(
                                        playlistPreview?.playlist?.browseId,
                                        plistName,
                                        it.mediaId,
                                        it.mediaMetadata.title,
                                        artistInfos.joinToString(",") { it.name ?: "" },
                                        it.asSong.durationText,
                                        it.mediaMetadata.artworkUri,
                                        albumInfo?.id,
                                        albumInfo?.name,
                                        artistInfos.joinToString(",") { it.id }
                                    )
                                }
                            }
                        }
                    }
                }
        }

    var isExporting by rememberSaveable {
        mutableStateOf(false)
    }

    if (isExporting) {
        InputTextDialog(
            onDismiss = {
                isExporting = false
            },
            title = stringResource(R.string.enter_the_playlist_name),
            value = playlistPreview?.playlist?.name?.let { cleanPrefix(it) } ?: "",
            placeholder = stringResource(R.string.enter_the_playlist_name),
            setValue = { text ->
                if (isExporting) {
                    plistName = text
                    try {
                        @SuppressLint("SimpleDateFormat")
                        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss")
                        exportLauncher.launch("RMPlaylist_${text.take(20)}_${dateFormat.format(Date())}")
                    } catch (e: ActivityNotFoundException) {
                        SmartMessage(
                            context.resources.getString(R.string.info_not_find_app_create_doc),
                            type = PopupType.Warning, context = context
                        )
                    }
                }

            }
        )
    }

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )
    val maxSongsInQueue by rememberPreference(maxSongsInQueueKey, MaxSongs.`500`)

    val thumbnails = playlistSongs.map { it.song }
        .takeWhile { it.thumbnailUrl?.isNotEmpty() ?: false }
        .take(4)
        .map { it.thumbnailUrl.thumbnail(thumbnailSizePx / 2) }

    val hapticFeedback = LocalHapticFeedback.current

    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    fun refresh() {
        if (refreshing) return
        refreshScope.launch(Dispatchers.IO) {
            refreshing = true
            sync()
            delay(500)
            refreshing = false
        }
    }

    var showFastShare by remember { mutableStateOf(false) }

    FastShare(
        showFastShare,
        onDismissRequest = { showFastShare = false},
        content = playlistPreview?.playlist ?: return
    )

    // ---- Yammbo Noir folder page ----
    // Title = last folder of the path, the full path small under it.
    val folderPath = playlistPreview?.playlist?.name?.let { cleanPrefix(it).trimEnd('/') }.orEmpty()
    val folderTitle = folderPath.substringAfterLast('/').ifBlank { "Unknown" }

    // Up to four distinct covers of the folder, for the mosaic; the first one tints the page.
    val mosaicUrls = playlistAllSongs
        .mapNotNull { it.song.thumbnailUrl?.takeIf { url -> url.isNotBlank() } }
        .distinct()
        .take(4)
    val coverTint = rememberCoverTint(mosaicUrls.firstOrNull())

    // The track that is playing now shows bars instead of its number.
    var currentMediaId by remember { mutableStateOf(binder?.player?.currentMediaItem?.mediaId) }
    binder?.player?.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentMediaId = mediaItem?.mediaId
            }
        }
    }

    // "N songs · X min", skipping what is missing.
    val folderMeta = listOfNotNull(
        if (playlistSongs.isNotEmpty())
            stringResource(R.string.noir_album_songs_count, playlistSongs.size)
        else null,
        (totalPlayTimes / 60_000L).toInt().takeIf { it > 0 }
            ?.let { stringResource(R.string.noir_album_minutes, it) }
    ).joinToString(" · ")

    val sortLabel = when (sortBy) {
        OnDeviceSongSortBy.DateAdded -> stringResource(R.string.sort_date_added)
        OnDeviceSongSortBy.Title -> stringResource(R.string.sort_title)
        OnDeviceSongSortBy.Album -> stringResource(R.string.sort_album)
        OnDeviceSongSortBy.Artist -> stringResource(R.string.sort_artist)
        OnDeviceSongSortBy.Duration -> stringResource(R.string.sort_duration)
    }

    // Songs follow the header, the tools row and (when shown) the empty state.
    LaunchedEffect(scrollToNowPlaying) {
        if (scrollToNowPlaying)
            lazyListState.scrollToItem(
                nowPlayingItem + 1 +
                        (if (playlistAllSongs.isNotEmpty() || searching) 1 else 0) +
                        (if (playlistSongs.isEmpty() && !refreshing) 1 else 0),
                1
            )
        scrollToNowPlaying = false
    }

    val playAll: () -> Unit = {
        val playable = playlistSongs.filter { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }
        if (playable.isNotEmpty()) {
            val itemsLimited =
                if (playable.size > maxSongsInQueue.number) playable
                    .take(maxSongsInQueue.number.toInt()) else playable
            binder?.stopRadio()
            binder?.player?.forcePlayFromBeginning(itemsLimited.map(SongEntity::asMediaItem))
        } else {
            SmartMessage(
                context.resources.getString(R.string.disliked_this_collection),
                type = PopupType.Error,
                context = context
            )
        }
    }

    val shuffleAll: () -> Unit = {
        val playable = playlistSongs.filter { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }
        if (playable.isNotEmpty()) {
            val itemsLimited =
                if (playable.size > maxSongsInQueue.number) playable.shuffled()
                    .take(maxSongsInQueue.number.toInt()) else playable
            binder?.stopRadio()
            binder?.player?.forcePlayFromBeginning(
                itemsLimited.shuffled().map(SongEntity::asMediaItem)
            )
        } else {
            SmartMessage(
                context.resources.getString(R.string.disliked_this_collection),
                type = PopupType.Error,
                context = context
            )
        }
    }

    // The sort options (same ones the old sort bar had).
    val openSortMenu: () -> Unit = {
        menuState.display {
            SortMenu(
                title = stringResource(R.string.sorting_order),
                onDismiss = menuState::hide,
                onTitle = {
                    sortBy =
                        OnDeviceSongSortBy.Title
                },
                onDateAdded = {
                    sortBy =
                        OnDeviceSongSortBy.DateAdded
                },
                onArtist = {
                    sortBy =
                        OnDeviceSongSortBy.Artist
                },
                onAlbum = {
                    sortBy =
                        OnDeviceSongSortBy.Album
                },
            )
        }
    }

    // The full folder menu (enqueue, play next, add to playlist, export, ...).
    val openPlaylistOptions: () -> Unit = {
        menuState.display {
            playlistPreview?.let { playlistPreview ->
                PlaylistsItemMenu(
                    navController = navController,
                    onDismiss = menuState::hide,
                    onSelectUnselect = {
                        selectItems = !selectItems
                        if (!selectItems) {
                            listMediaItems.clear()
                        }
                    },
                    playlist = playlistPreview,
                    onEnqueue = {
                        if (listMediaItems.isEmpty()) {
                            if (playlistSongs.any { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }) {
                                binder?.player?.enqueue(playlistSongs.filter { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }
                                    .map(SongEntity::asMediaItem),
                                    context)
                            } else {
                                SmartMessage(
                                    context.resources.getString(R.string.disliked_this_collection),
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
                    onPlayNext = {
                        if (listMediaItems.isEmpty()) {
                            if (playlistSongs.any { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }) {
                                binder?.player?.addNext(playlistSongs.filter { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }
                                    .map(SongEntity::asMediaItem),
                                    context,
                                    selectedQueue ?: defaultQueue()
                                )
                            } else {
                                SmartMessage(
                                    context.resources.getString(R.string.disliked_this_collection),
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
                    showOnSyncronize = false,
                    showLinkUnlink = false,
                    onAddToPlaylist = { toPlaylistPreview ->
                        position = toPlaylistPreview.songCount.minus(1)
                        //Log.d("mediaItem", " maxPos in Playlist $it ${position}")
                        if (position > 0) position++ else position = 0
                        //Log.d("mediaItem", "next initial pos ${position}")
                        if (listMediaItems.isEmpty()) {
                            val filteredPLSongs =
                                playlistSongs.filterNot {
                                    it.asMediaItem.mediaId.startsWith(
                                        LOCAL_KEY_PREFIX
                                    ) || it.song.thumbnailUrl == ""
                                }
                            CoroutineScope(Dispatchers.IO).launch {
                                songsInTheToPlaylist =
                                    withContext(Dispatchers.IO) {
                                        Database.sortSongsPlaylistByPositionNoFlow(
                                            toPlaylistPreview.playlist.id
                                        )
                                    }
                                var distinctSongs =
                                    filteredPLSongs.filterNot { it in songsInTheToPlaylist }

                                if ((distinctSongs.size + toPlaylistPreview.songCount) > 5000 && toPlaylistPreview.playlist.isYoutubePlaylist && isYtSyncEnabled()) {
                                    SmartMessage(
                                        context.resources.getString(
                                            R.string.yt_playlist_limited
                                        ),
                                        context = context,
                                        type = PopupType.Error
                                    )
                                } else if (!isYtSyncEnabled() || !toPlaylistPreview.playlist.isYoutubePlaylist) {
                                    playlistSongs.forEachIndexed { index, song ->
                                        Database.asyncTransaction {
                                            Database.insert(song.asMediaItem)
                                            Database.insert(
                                                SongPlaylistMap(
                                                    songId = song.asMediaItem.mediaId,
                                                    playlistId = toPlaylistPreview.playlist.id,
                                                    position = position + index
                                                ).default()
                                            )
                                        }
                                    }
                                } else {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        if (playlistPreview.playlist.isYoutubePlaylist) {
                                            EnvironmentExt.addPlaylistToPlaylist(
                                                cleanPrefix(
                                                    toPlaylistPreview.playlist.browseId
                                                        ?: ""
                                                ),
                                                cleanPrefix(
                                                    playlistPreview.playlist.browseId
                                                        ?: ""
                                                )
                                            ).onSuccess {
                                                playlistSongs.forEachIndexed { index, song ->
                                                    Database.asyncTransaction {
                                                        Database.insert(
                                                            song.asMediaItem
                                                        )
                                                        Database.insert(
                                                            SongPlaylistMap(
                                                                songId = song.asMediaItem.mediaId,
                                                                playlistId = toPlaylistPreview.playlist.id,
                                                                position = position + index
                                                            ).default()
                                                        )
                                                    }
                                                }
                                            }
                                        } else if (distinctSongs.isNotEmpty()) {
                                            addToYtPlaylist(
                                                toPlaylistPreview.playlist.id,
                                                position,
                                                toPlaylistPreview.playlist.browseId
                                                    ?: "",
                                                distinctSongs.map { it.asMediaItem })
                                        }
                                    }
                                }

                            }
                        } else {
                            CoroutineScope(Dispatchers.IO).launch {
                                val filteredListMediaItems =
                                    listMediaItems.filterNot {
                                        it.mediaId.startsWith(
                                            LOCAL_KEY_PREFIX
                                        ) || it.mediaMetadata.artworkUri.toString() == ""
                                    }
                                songsInTheToPlaylist =
                                    withContext(Dispatchers.IO) {
                                        Database.sortSongsPlaylistByPositionNoFlow(
                                            toPlaylistPreview.playlist.id
                                        )
                                    }

                                val distinctSongs =
                                    filteredListMediaItems.filter { item -> item !in songsInTheToPlaylist.map { it.asMediaItem } }
                                if ((distinctSongs.size + toPlaylistPreview.songCount) > 5000 && toPlaylistPreview.playlist.isYoutubePlaylist && isYtSyncEnabled()) {
                                    SmartMessage(
                                        context.resources.getString(
                                            R.string.yt_playlist_limited
                                        ),
                                        context = context,
                                        type = PopupType.Error
                                    )
                                } else if (!isYtSyncEnabled() || !toPlaylistPreview.playlist.isYoutubePlaylist) {
                                    listMediaItems.forEachIndexed { index, song ->
                                        Database.asyncTransaction {
                                            Database.insert(song)
                                            Database.insert(
                                                SongPlaylistMap(
                                                    songId = song.mediaId,
                                                    playlistId = toPlaylistPreview.playlist.id,
                                                    position = position + index
                                                ).default()
                                            )
                                        }
                                    }
                                } else if (distinctSongs.isNotEmpty()) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        addToYtPlaylist(
                                            toPlaylistPreview.playlist.id,
                                            position,
                                            toPlaylistPreview.playlist.browseId
                                                ?: "",
                                            distinctSongs
                                        )
                                    }
                                }
                                println("pipedInfo mediaitemmenu uuid ${playlistPreview.playlist.browseId}")


                                listMediaItems.clear()
                                selectItems = false
                            }
                        }
                    },
                    onAddToPreferites = {
                            if (listMediaItems.isEmpty()) {
                                playlistSongs.forEachIndexed { index, song ->
                                    if (song.song.likedAt in listOf(
                                            -1L,
                                            null
                                        )
                                    ) {
                                        mediaItemToggleLike(song.asMediaItem)
                                    }
                                }
                            } else {
                                Database.asyncTransaction {
                                    listMediaItems.forEachIndexed { index, song ->
                                        if (Database.getLikedAt(song.mediaId) !in listOf(
                                                -1L,
                                                null
                                            )
                                        ) {
                                            mediaItemToggleLike(song)
                                        }
                                    }
                                }
                            }

                    },
                    showonListenToYT = false,
                    onExport = {
                        isExporting = true
                    },
                    disableScrollingText = disableScrollingText,
                )
            }

        }
    }

    // The "..." of the action bar: reverse order and locate, then the full folder menu.
    val openMoreMenu: () -> Unit = {
        menuState.display {
            Menu {
                MenuEntry(
                    icon = R.drawable.refresh,
                    text = stringResource(R.string.noir_playlist_reverse_order),
                    onClick = {
                        menuState.hide()
                        sortOrder = !sortOrder
                    }
                )
                MenuEntry(
                    icon = R.drawable.locate,
                    text = stringResource(R.string.info_find_the_song_that_is_playing),
                    enabled = playlistSongs.isNotEmpty(),
                    onClick = {
                        menuState.hide()
                        nowPlayingItem = -1
                        scrollToNowPlaying = false
                        playlistSongs
                            .forEachIndexed { index, song ->
                                if (song.asMediaItem.mediaId == binder?.player?.currentMediaItem?.mediaId)
                                    nowPlayingItem = index
                            }

                        if (nowPlayingItem > -1)
                            scrollToNowPlaying = true
                    }
                )
                MenuEntry(
                    icon = R.drawable.ellipsis_horizontal,
                    text = stringResource(R.string.noir_playlist_options),
                    onClick = { openPlaylistOptions() }
                )
            }
        }
    }

    PullToRefreshBox(
        refreshing = refreshing,
        onRefresh = { refresh() }
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette.background0)
                .fillMaxHeight()
                .fillMaxWidth(
                    if (navigationBarPosition == NavigationBarPosition.Left ||
                        navigationBarPosition == NavigationBarPosition.Top ||
                        navigationBarPosition == NavigationBarPosition.Bottom
                    ) 1f
                    else Dimensions.contentWidthRightBar
                ),

        ) {
            LazyListContainer(
                state = lazyListState
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .background(colorPalette.background0)
                        .fillMaxSize()
                ) {
                    item(
                        key = "header",
                        contentType = 0
                    ) {
                        // The page sits under the Noir top bar (back is there): embedded header.
                        AlbumNoirHeader(
                            coverUrl = null,
                            previewUrl = null,
                            sharedKey = null,
                            showCover = true,
                            title = folderTitle,
                            artist = if (folderPath != folderTitle) folderPath else "",
                            meta = folderMeta,
                            tint = coverTint,
                            onBack = { navController.popBackStack() },
                            onMore = null,
                            embedded = true,
                            coverContent = { FolderMosaic(mosaicUrls) },
                            actions = {
                                AlbumSquareAction(
                                    icon = R.drawable.shuffle,
                                    enabled = playlistSongs.isNotEmpty(),
                                    onClick = shuffleAll
                                )

                                AlbumPlayAction(
                                    enabled = playlistSongs.isNotEmpty(),
                                    onClick = playAll
                                )

                                AlbumSquareAction(
                                    icon = R.drawable.ellipsis_horizontal,
                                    onClick = openMoreMenu
                                )
                            }
                        )
                    }

                    if (playlistAllSongs.isNotEmpty() || searching)
                        item(
                            key = "tools",
                            contentType = 1
                        ) {
                            Column(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 20.dp, vertical = 6.dp)
                                ) {
                                    NoirChip(
                                        text = stringResource(R.string.search),
                                        selected = searching,
                                        icon = R.drawable.search,
                                        onClick = {
                                            searching = !searching
                                            if (!searching) filter = null
                                        }
                                    )
                                    NoirChip(
                                        text = sortLabel,
                                        selected = false,
                                        icon = R.drawable.sort,
                                        onClick = openSortMenu
                                    )
                                }
                                if (searching) {
                                    val focusRequester = remember { FocusRequester() }
                                    val focusManager = LocalFocusManager.current
                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                                    NoirListSearchField(
                                        value = filter ?: "",
                                        onValueChange = { filter = it },
                                        hint = stringResource(R.string.ondevice_folder_search_hint),
                                        onDone = { focusManager.clearFocus() },
                                        fieldModifier = Modifier.focusRequester(focusRequester)
                                    )
                                }
                            }
                        }

                    if (playlistSongs.isEmpty() && !refreshing)
                        item(
                            key = "empty",
                            contentType = 2
                        ) {
                            val noMatch = !filter.isNullOrBlank()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 40.dp, vertical = 56.dp)
                            ) {
                                BasicText(
                                    text = stringResource(
                                        if (noMatch) R.string.noir_playlist_no_match_title
                                        else R.string.ondevice_folder_empty_title
                                    ),
                                    style = typography.m.semiBold.copy(
                                        color = colorPalette.text,
                                        fontSize = 20.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = stringResource(
                                        if (noMatch) R.string.noir_playlist_no_match_body
                                        else R.string.ondevice_folder_empty_body
                                    ),
                                    style = typography.xs.copy(
                                        color = colorPalette.textSecondary,
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }

                    itemsIndexed(
                        items = playlistSongs.distinctBy { it.song.id },
                        key = { _, song -> song.song.id },
                        contentType = { _, song -> song },
                    ) { index, song ->

                            val interactionSource = remember { MutableInteractionSource() }

                            Box(
                                modifier = Modifier
                                    .noirContentWidth(NoirContentMax.List)
                                    .animateItem()

                            ) {
                                val checkedState = rememberSaveable { mutableStateOf(false) }
                                if (!selectItems && checkedState.value) checkedState.value = false

                                SwipeableQueueItem(
                                    mediaItem = song.asMediaItem,
                                    onPlayNext = {
                                        binder?.player?.addNext(
                                            song.asMediaItem,
                                            queue = selectedQueue ?: defaultQueue()
                                        )
                                    }
                                ) {
                                    AlbumTrackRow(
                                        index = index,
                                        song = song.song,
                                        isCurrent = currentMediaId == song.asMediaItem.mediaId,
                                        isDownloaded = false,
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
                                                        InPlaylistMediaItemMenu(
                                                            onInfo = {
                                                                navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.song.id}")
                                                            },
                                                            navController = navController,
                                                            playlist = playlistPreview,
                                                            playlistId = 0,
                                                            positionInPlaylist = index,
                                                            song = song.song,
                                                            onDismiss = menuState::hide,
                                                            disableScrollingText = disableScrollingText,
                                                        )
                                                    }
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                },
                                                onClick = {
                                                    if (!selectItems) {
                                                        if (song.song.likedAt != -1L) {
                                                            searching = false
                                                            filter = null
                                                            playlistSongs.filter { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }
                                                                .map(SongEntity::asMediaItem)
                                                                .let { mediaItems ->
                                                                    binder?.stopRadio()
                                                                    binder?.player?.forcePlayAtIndex(
                                                                        mediaItems,
                                                                        mediaItems.indexOf(song.asMediaItem)
                                                                    )
                                                                }
                                                        } else {
                                                            CoroutineScope(Dispatchers.Main).launch {
                                                                SmartMessage(
                                                                    context.resources.getString(R.string.disliked_this_song),
                                                                    type = PopupType.Error,
                                                                    context = context
                                                                )
                                                            }
                                                        }
                                                    } else checkedState.value = !checkedState.value
                                                }
                                            )

                                            .background(color = colorPalette.background0)
                                    )
                                }
                            }

                    }

                    item(
                        key = "footer",
                        contentType = 0,
                    ) {
                        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if (uiType == UiType.ViMusic || showFloatingIcon)
                FloatingActionsContainerWithScrollToTop(
                    lazyListState = lazyListState,
                    iconId = R.drawable.shuffle,
                    onClick = {
                        if (playlistSongs.any { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }) {
                            playlistSongs.filter { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }
                                .let { songs ->
                                    if (songs.isNotEmpty()) {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayFromBeginning(
                                            songs.shuffled().map(SongEntity::asMediaItem)
                                        )
                                    }
                                }
                        } else {
                            SmartMessage(
                                context.resources.getString(R.string.disliked_this_collection),
                                type = PopupType.Error,
                                context = context
                            )
                        }
                }
            )


        }

    }
}

/** The folder cover: one cover, or a 2x2 mosaic of up to four (repeating when there are fewer). */
@Composable
private fun FolderMosaic(urls: List<String>) {
    if (urls.isEmpty()) return
    if (urls.size == 1) {
        RevealImage(
            model = urls.first().thumbnail(600),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    val cells = List(4) { urls[it % urls.size] }
    Column(modifier = Modifier.fillMaxSize()) {
        for (row in 0..1) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                for (col in 0..1) {
                    RevealImage(
                        model = cells[row * 2 + col].thumbnail(300),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}
