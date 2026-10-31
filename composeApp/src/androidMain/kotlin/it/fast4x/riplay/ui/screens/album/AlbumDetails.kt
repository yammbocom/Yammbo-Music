package it.fast4x.riplay.ui.screens.album

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
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
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil.compose.AsyncImage
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.environment.requests.AlbumPage
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
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.SwipeablePlaylistItem
import it.fast4x.riplay.ui.components.themed.AlbumsItemMenu
import it.fast4x.riplay.ui.components.themed.AutoResizeText
import it.fast4x.riplay.ui.components.themed.FontSizeRange
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.components.themed.HeaderIconButton
import it.fast4x.riplay.ui.components.themed.InputTextDialog
import it.fast4x.riplay.ui.components.themed.ItemsList
import it.fast4x.riplay.ui.components.themed.LayoutWithAdaptiveThumbnail
import it.fast4x.riplay.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.components.themed.NowPlayingSongIndicator
import it.fast4x.riplay.ui.components.themed.SelectorDialog
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.items.AlbumItem
import it.fast4x.riplay.ui.items.AlbumItemPlaceholder
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.items.SongItemPlaceholder
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px
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
import it.fast4x.riplay.utils.languageDestination
import it.fast4x.riplay.ui.styling.medium
import it.fast4x.riplay.extensions.preferences.parentalControlEnabledKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.utils.resize
import it.fast4x.riplay.utils.resizeNoCrop
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.extensions.preferences.showFloatingIconKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.download.downloadSongs
import it.fast4x.riplay.ui.components.themed.downloadIconFor
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.extensions.fastshare.FastShare
import it.fast4x.riplay.data.models.SongAlbumMap
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.ui.components.PullToRefreshBox
import it.fast4x.riplay.ui.components.themed.LoadFailed
import it.fast4x.riplay.ui.components.themed.MediaActionButton
import it.fast4x.riplay.ui.components.themed.MediaHeader
import it.fast4x.riplay.ui.components.themed.Loader
import it.fast4x.riplay.ui.components.themed.LoaderScreen
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.components.themed.NoirSectionHeader
import it.fast4x.riplay.ui.screens.artist.ArtistAboutCard
import it.fast4x.riplay.utils.DisposableListener
import it.fast4x.riplay.ui.components.themed.QueuesDialog
import it.fast4x.riplay.ui.components.themed.Title
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.addToYtLikedSongs
import it.fast4x.riplay.utils.addToYtPlaylist
import it.fast4x.riplay.utils.globalContext
import it.fast4x.riplay.utils.httpClient
import it.fast4x.riplay.utils.isNetworkConnected
import it.fast4x.riplay.utils.mediaItemSetLiked
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun AlbumDetails(
    navController: NavController,
    browseId: String,
    albumPage: AlbumPage?,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    // The fetch lives in AlbumScreen: it reports a failed load and owns the retry.
    loadFailed: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {

    // Resolved before anything else: the shared cover's target has to exist from the first
    // frame, while the page is still loading.
    val handoff = remember(browseId) { CoverHandoff.forPage(browseId) }

    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalGlobalSheetState.current
    val context = LocalContext.current
    val selectedQueue = LocalSelectedQueue.current
    var songs by persistList<Song>("album/$browseId/songs")
    var album by persist<Album?>("album/$browseId")
    //val albumPage by persist<Innertube.PlaylistOrAlbumPage?>("album/$browseId/albumPage")
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)
    var songPlaylist by remember {
        mutableIntStateOf(0)
    }
    var playlistsList by remember { mutableStateOf<List<Database.PlayListIdPosition>?>(null) }
    var songExists by remember { mutableStateOf(false) }
    var likedAt by remember {
        mutableStateOf<Long?>(null)
    }
    var playTime by remember {
        mutableStateOf<Long?>(null)
    }


    data class AlbumSongsState(
        val song : Song,
        val likedAt : Long? = null,
        val playtime : Long? = null,
        val songExists : Boolean = false,
        val playlistsList : List<Database.PlayListIdPosition>? = emptyList(),
    )

    fun update() {
        if(!isNetworkConnected(context)) {
            return
        }
        runBlocking(Dispatchers.IO) {
            withContext(Dispatchers.IO) {
                Database.asyncTransaction {
                    //todo maybe not needed
//                    val albumSongsStateList = mutableListOf<AlbumSongsState>()
//                    songs.forEach { song ->
//                        CoroutineScope(Dispatchers.IO).launch {
//                            Database.songUsedInPlaylistsAsFlow(song.id)
//                                .collect { songPlaylist = it }
//                        }
//                        if (songPlaylist > 0) songExists = true
//                        playlistsList = Database.playlistsUsedForSong(song.id)
//                        likedAt = song.likedAt
//                        playTime = song.totalPlayTimeMs
//                        binder?.cache?.removeResource(song.id)
//                        val songState =
//                            AlbumSongsState(song, likedAt, playTime, songExists, playlistsList)
//                        albumSongsStateList.add(songState)
//                        Database.delete(song)
//                    }

                    Database.upsert(
                        Album(
                            id = browseId,
                            title = if (album?.title?.startsWith(MODIFIED_PREFIX) == true) album?.title else albumPage?.album?.title,
                            thumbnailUrl = if (album?.thumbnailUrl?.startsWith(MODIFIED_PREFIX) == true) album?.thumbnailUrl else albumPage?.album?.thumbnail?.url,
                            year = albumPage?.album?.year,
                            authorsText = if (album?.authorsText?.startsWith(MODIFIED_PREFIX) == true) album?.authorsText else albumPage?.album?.authors
                                ?.joinToString("") { it.name ?: "" },
                            shareUrl = albumPage?.url,
                            timestamp = System.currentTimeMillis(),
                            bookmarkedAt = album?.bookmarkedAt,
                            isYoutubeAlbum = album?.isYoutubeAlbum == true
                        ),
                        albumPage
                            ?.songs?.distinct()
                            ?.map(Environment.SongItem::asMediaItem)
                            ?.onEach(Database::insert)
                            ?.mapIndexed { position, mediaItem ->
                                SongAlbumMap(
                                    songId = mediaItem.mediaId,
                                    albumId = browseId,
                                    position = position
                                )
                            } ?: emptyList()
                    )

                    //todo maybe not needed
                    /*
                    albumSongsStateList.forEach { albumSongsState ->
                        if ((albumSongsState.songExists || albumSongsState.likedAt != null || albumSongsState.playtime != null)
                            && songExist(albumSongsState.song.id) == 0
                        ) {
                            insert(albumSongsState.song)
                        }
                        if (albumSongsState.songExists) {
                            albumSongsState.playlistsList?.forEach { item ->
                                insert(
                                    SongPlaylistMap(
                                        songId = albumSongsState.song.id,
                                        playlistId = item.playlistId,
                                        position = item.position
                                    ).default()
                                )
                            }
                        }
                        if (albumSongsState.likedAt != null) {
                            Database.like(albumSongsState.song.id, albumSongsState.likedAt)
                        }
                        Database.incrementTotalPlayTimeMs(
                            albumSongsState.song.id,
                            albumSongsState.playtime ?: 0
                        )
                    }
                    */
                }
            }
        }
    }

    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    fun refresh() {
        if (refreshing) return
        refreshScope.launch(Dispatchers.IO) {
            refreshing = true
            update()
            delay(500)
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        Database.albumSongs(browseId).collect {
            songs = if (parentalControlEnabled)
                it.filter { s -> !s.title.startsWith(EXPLICIT_PREFIX) || s.mediaId?.isNotEmpty() == true } else it
        }
    }

    LaunchedEffect(Unit) {
        Database.album(browseId).collect { album = it }
    }

    /*
    val playlistPreviews by remember {
        Database.playlistPreviews(PlaylistSortBy.Name, SortOrder.Ascending)
    }.collectAsState(initial = emptyList(), context = Dispatchers.IO)

    var showPlaylistSelectDialog by remember {
        mutableStateOf(false)
    }
     */

    var showConfirmDeleteDownloadDialog by remember {
        mutableStateOf(false)
    }

    var showConfirmDownloadAllDialog by remember {
        mutableStateOf(false)
    }

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailAlbumSizeDp = Dimensions.thumbnails.album

    val thumbnailAlbumSizePx = thumbnailAlbumSizeDp.px

    val lazyListState = rememberLazyListState()

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    var listMediaItems = remember {
        mutableListOf<MediaItem>()
    }

    var selectItems by remember {
        mutableStateOf(false)
    }

    var showSelectDialog by remember {
        mutableStateOf(false)
    }

    /*
    var showAddPlaylistSelectDialog by remember {
        mutableStateOf(false)
    }
     */

    var showSelectCustomizeAlbumDialog by remember {
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
                        updateAlbumTitle(browseId, it)
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
                        updateAlbumAuthors(browseId, it)
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
                        updateAlbumCover(browseId, it)
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

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    var translateEnabled by remember {
        mutableStateOf(false)
    }

    val translator = Translator(httpClient())
    val languageDestination = languageDestination()

    var readMore by remember { mutableStateOf(false) }

    var showFastShare by remember { mutableStateOf(false) }
    var showDirectFastShare by remember { mutableStateOf(false) }
    // Needs the album row, which only exists once the page has loaded.
    album?.let { shareAlbum ->
        FastShare(
            showFastShare,
            showLinks = !showDirectFastShare,
            showShareWith = !showDirectFastShare,
            onDismissRequest = {
                showFastShare = false
                showDirectFastShare = false
            },
            // Fall back to the freshly-fetched page thumbnail when the DB album's
            // thumbnailUrl isn't populated yet (avoids the placeholder on the first share).
            content = if (shareAlbum.thumbnailUrl.isNullOrEmpty())
                shareAlbum.copy(thumbnailUrl = albumPage?.album?.thumbnail?.url)
            else shareAlbum
        )
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
    val coverTint = rememberCoverTint(album?.thumbnailUrl ?: handoff?.url)

    // The full album menu (select, enqueue, add to playlist, rename...).
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
                                    /*
                        onSelect = { selectItems = true },
                        onUncheck = {
                            selectItems = false
                            listMediaItems.clear()
                        },
                         */
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
                                            } else {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    EnvironmentExt.addPlaylistToPlaylist(
                                                        cleanPrefix(
                                                            playlistPreview.playlist.browseId
                                                                ?: ""
                                                        ),
                                                        cleanPrefix(
                                                            albumPage?.album?.playlistId
                                                                ?: ""
                                                        )
                                                    ).onSuccess {
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
                                            } else {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    addToYtPlaylist(
                                                        playlistPreview.playlist.id,
                                                        position,
                                                        cleanPrefix(
                                                            playlistPreview.playlist.browseId
                                                                ?: ""
                                                        ),
                                                        listMediaItems
                                                    )
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

    // The top-right "...": what the action row has no room for, then the album menu.
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
                    onClick = { openAlbumMenu() }
                )
            }
        }
    }

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        PullToRefreshBox(
            refreshing = refreshing,
            onRefresh = { refresh() }
        ) {
            Box(
                modifier = Modifier
                    .background(
                        colorPalette().background0
                    )
                    //.fillMaxSize()
                    .fillMaxHeight()
                    //.fillMaxWidth(if (navigationBarPosition == NavigationBarPosition.Left) 1f else contentWidth)
                    .fillMaxWidth()
            ) {

                LazyListContainer(
                    state = lazyListState,
                ) {
                    LazyColumn(
                        state = lazyListState,
                        //contentPadding = LocalPlayerAwareWindowInsets.current
                        //    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                        modifier = Modifier
                            .background(
                                colorPalette().background0
                            )
                            .fillMaxSize()
                    ) {
                        item(
                            key = "header"
                        ) {
                            // "ALBUM · year · N songs · N min", skipping what is missing.
                            val albumMeta = listOfNotNull(
                                stringResource(R.string.noir_kind_album),
                                album?.year?.takeIf { it.isNotBlank() },
                                if (songs.isNotEmpty())
                                    stringResource(R.string.noir_album_songs_count, songs.size)
                                else null,
                                (totalPlayTimes / 60_000L).toInt().takeIf { it > 0 }
                                    ?.let { stringResource(R.string.noir_album_minutes, it) }
                            ).joinToString(" · ")

                            AlbumNoirHeader(
                                // Landscape shows the cover in the side panel: no cover here.
                                coverUrl = if (isLandscape) null else album?.thumbnailUrl,
                                previewUrl = if (isLandscape) null else handoff?.url,
                                sharedKey = if (isLandscape) null else handoff?.key,
                                showCover = !isLandscape,
                                title = cleanPrefix(album?.title ?: handoff?.title ?: ""),
                                artist = cleanPrefix(album?.authorsText ?: ""),
                                meta = albumMeta,
                                tint = coverTint,
                                onBack = { navController.popBackStack() },
                                onMore = if (songs.isNotEmpty()) openMoreMenu else null,
                                actions = {
                                    // The whole album, queued for in-app download.
                                    AlbumSquareAction(
                                        icon = downloadIconFor(songs.filterNot { it.isLocal }.map { it.id }),
                                        enabled = songs.isNotEmpty(),
                                        onLongClick = {
                                            SmartMessage(
                                                context.resources.getString(R.string.download_all_with_ytdlnis),
                                                context = context
                                            )
                                        },
                                        onClick = {
                                            downloadSongs(
                                                context = context,
                                                songs = songs,
                                                albumTitle = album?.title,
                                            )
                                        }
                                    )

                                    // Bookmark = save the album to the library.
                                    AlbumSquareAction(
                                        icon = if (album?.bookmarkedAt == null) {
                                            R.drawable.add
                                        } else {
                                            R.drawable.bookmark
                                        },
                                        enabled = album != null,
                                        onClick = {
                                            if (isYtSyncEnabled() && !isNetworkConnected(
                                                    context
                                                )
                                            ) {
                                                SmartMessage(
                                                    context.resources.getString(R.string.no_connection),
                                                    context = context,
                                                    type = PopupType.Error
                                                )
                                            } else {
                                                val bookmarkedAt =
                                                    if (album?.bookmarkedAt == null) System.currentTimeMillis() else null

                                                Database.asyncTransaction {
                                                    album
                                                        ?.copy(bookmarkedAt = bookmarkedAt)
                                                        ?.let(::update)
                                                }



                                                if (isYtSyncEnabled())
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        if (bookmarkedAt == null)
                                                            albumPage?.album?.playlistId.let {
                                                                if (it != null) {
                                                                    EnvironmentExt.removelikePlaylistOrAlbum(
                                                                        it
                                                                    )
                                                                    Database.asyncTransaction {
                                                                        album?.let { update(it.copy(isYoutubeAlbum = false)) }
                                                                    }
                                                                }
                                                            }
                                                        else
                                                            albumPage?.album?.playlistId.let {
                                                                if (it != null) {
                                                                    EnvironmentExt.likePlaylistOrAlbum(
                                                                        it
                                                                    )
                                                                    if (album != null) {
                                                                        Database.asyncTransaction {
                                                                            album?.let { update(it.copy(isYoutubeAlbum = false)) }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                    }
                                            }
                                        },
                                        onLongClick = {
                                            SmartMessage(
                                                context.resources.getString(R.string.info_bookmark_album),
                                                context = context
                                            )
                                        }
                                    )

                                    AlbumPlayAction(
                                        enabled = songs.isNotEmpty(),
                                        onClick = {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayFromBeginning(
                                                songs.filter { it.likedAt != -1L }
                                                    .map(Song::asMediaItem)
                                            )
                                        }
                                    )

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

                                    AlbumSquareAction(
                                        icon = R.drawable.share_social,
                                        enabled = album != null,
                                        onClick = { showFastShare = true }
                                    )
                                }
                            )
                        }

                        // Under the header, not over it: the cover keeps flying while this shows.
                        if (songs.isEmpty() && albumPage == null && !loadFailed) {
                            item(key = "loading") {
                                Loader(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                )
                            }
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
                                                        val mediaItems = songs.filter { it.likedAt != -1L }
                                                            .map(Song::asMediaItem)
                                                        val mediaItemIndex = mediaItems.indexOfFirst {
                                                            it.mediaId == song.id
                                                        }
                                                        binder?.stopRadio()
                                                        binder?.player?.forcePlayAtIndex(
                                                            mediaItems,
                                                            mediaItemIndex
                                                        )
                                                    } else {
                                                        SmartMessage(
                                                            globalContext().resources.getString(R.string.disliked_this_song),
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

                        albumPage?.description?.let { description ->
                            item(
                                key = "albumInfo"
                            ) {
                                ArtistAboutCard(
                                    description = description,
                                    imageUrl = null,
                                    subscribers = null
                                )
                            }
                        }

                        if (albumPage != null) {
                            item(key = "alternateVersionsTitle") {
                                Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                                NoirSectionHeader(title = stringResource(R.string.album_alternative_versions))
                                }
                            }
                            item(key = "alternateVersions") {
                                Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                                ItemsList(
                                    tag = "album/$browseId/alternatives",
                                    headerContent = {},
                                    initialPlaceholderCount = 1,
                                    continuationPlaceholderCount = 1,
                                    emptyItemsText = stringResource(R.string.album_no_alternative_version),
                                    itemsPageProvider = albumPage?.let {
                                        ({
                                            Result.success(
                                                Environment.ItemsPage(
                                                    items = albumPage.otherVersions,
                                                    continuation = null
                                                )
                                            )
                                        })
                                    } ?: {
                                        Result.success(
                                            Environment.ItemsPage(
                                                items = emptyList(),
                                                continuation = null
                                            )
                                        )
                                    },
                                    itemContent = { album ->
                                        AlbumItem(
                                            alternative = true,
                                            album = album,
                                            thumbnailSizePx = thumbnailAlbumSizePx,
                                            thumbnailSizeDp = thumbnailAlbumSizeDp,
                                            modifier = Modifier
                                                .clickable {
                                                    //albumRoute(album.key)
                                                    navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                                },
                                            disableScrollingText = disableScrollingText
                                        )
                                    },
                                    itemPlaceholderContent = {
                                        AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                                    }
                                )

                                /**********/

                                /**********/
                                }
                            }
                        }

                        item(key = "bottom") {
                            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                        }

                    }
                }

                // Gave up waiting: opaque retry screen over the page (it has its own back button).
                if (albumPage == null && loadFailed && onRetry != null)
                    LoadFailed(onRetry = onRetry, onBack = { navController.popBackStack() })


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


    }


}
