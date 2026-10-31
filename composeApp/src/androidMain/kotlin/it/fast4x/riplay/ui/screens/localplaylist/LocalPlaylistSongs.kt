package it.fast4x.riplay.ui.screens.localplaylist


import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.models.bodies.BrowseBody
import it.fast4x.environment.models.bodies.NextBody
import it.fast4x.environment.requests.PlaylistPage
import it.fast4x.environment.requests.podcastPage
import it.fast4x.environment.requests.relatedSongs
import it.fast4x.environment.utils.completed
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.Database.Companion.songAlbumInfo
import it.fast4x.riplay.data.Database.Companion.songArtistInfo
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import com.yambo.music.R
import it.fast4x.riplay.enums.MaxSongs
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PlaylistSongSortBy
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.RecommendationsNumber
import it.fast4x.riplay.enums.SortOrder
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.data.models.SongPlaylistMap
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.SwipeableQueueItem
import it.fast4x.riplay.ui.components.themed.ConfirmationDialog
import it.fast4x.riplay.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.riplay.ui.components.themed.HeaderIconButton
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.components.themed.IconButton
import it.fast4x.riplay.ui.components.themed.IconInfo
import it.fast4x.riplay.ui.components.themed.InPlaylistMediaItemMenu
import it.fast4x.riplay.ui.components.themed.InputTextDialog
import it.fast4x.riplay.ui.components.themed.Playlist
import it.fast4x.riplay.ui.components.themed.PlaylistsItemMenu
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.components.themed.SortMenu
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.LocalAppearance
import it.fast4x.riplay.ui.styling.favoritesIcon
import it.fast4x.riplay.ui.styling.onOverlay
import it.fast4x.riplay.ui.styling.overlay
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.extensions.preferences.UiTypeKey
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.isSpotifyTrack
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.commonutils.durationTextToMillis
import it.fast4x.riplay.utils.enqueue
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.forcePlayFromBeginning
import it.fast4x.riplay.extensions.preferences.isRecommendationEnabledKey
import it.fast4x.riplay.extensions.preferences.maxSongsInQueueKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.playlistSongSortByKey
import it.fast4x.riplay.extensions.preferences.recommendationsNumberKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.reorderInQueueEnabledKey
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.extensions.preferences.showFloatingIconKey
import it.fast4x.riplay.extensions.preferences.songSortOrderKey
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.MONTHLY_PREFIX
import it.fast4x.riplay.commonutils.PINNED_PREFIX
import it.fast4x.riplay.commonutils.PIPED_PREFIX
import it.fast4x.riplay.commonutils.YTP_PREFIX
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.enums.PlaylistSongsTypeFilter
import it.fast4x.riplay.extensions.download.downloadSongs
import it.fast4x.riplay.ui.components.themed.downloadIconFor
import it.fast4x.riplay.extensions.fastshare.FastShare
import it.fast4x.riplay.ui.components.themed.NowPlayingSongIndicator
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import it.fast4x.riplay.utils.checkFileExists
import it.fast4x.riplay.utils.deleteFileIfExists
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.utils.saveImageToInternalStorage
import kotlinx.coroutines.CoroutineScope
import it.fast4x.riplay.data.models.SongEntity
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.utils.LOCAL_KEY_PREFIX
import it.fast4x.riplay.ui.components.PullToRefreshBox
import it.fast4x.riplay.ui.components.themed.FilterMenu
import it.fast4x.riplay.ui.components.themed.InProgressDialog
import it.fast4x.riplay.utils.addToYtLikedSongs
import it.fast4x.riplay.utils.addToYtPlaylist
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.formatAsDuration
import it.fast4x.riplay.utils.getAlbumVersionFromVideo
import it.fast4x.riplay.utils.isExplicit
import it.fast4x.riplay.utils.isNetworkConnected
import it.fast4x.riplay.utils.mediaItemToggleLike
import it.fast4x.riplay.utils.move
import it.fast4x.riplay.extensions.preferences.playlistSongsTypeFilterKey
import it.fast4x.riplay.ui.components.themed.FastPlayActionsBar
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.removeYTSongFromPlaylist
import it.fast4x.riplay.utils.removeFromOnlineLikedSong
import it.fast4x.riplay.utils.updateLocalPlaylist
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import it.fast4x.riplay.extensions.persist.persistList
import timber.log.Timber
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.components.themed.MenuToggleEntry
import it.fast4x.riplay.ui.components.themed.NoirChip
import it.fast4x.riplay.ui.screens.album.AlbumNoirHeader
import it.fast4x.riplay.ui.screens.album.AlbumPlayAction
import it.fast4x.riplay.ui.screens.album.AlbumSquareAction
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
fun LocalPlaylistSongs(
    navController: NavController,
    playlistId: Long,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val (colorPalette, typography, thumbnailShape) = LocalAppearance.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalGlobalSheetState.current
    val selectedQueue = LocalSelectedQueue.current
    val uiType by rememberPreference(UiTypeKey, UiType.RiPlay)

    var playlistAllSongs by persistList<SongEntity>("localPlaylist/$playlistId/songs")
    var songsInTheToPlaylist by persistList<SongEntity>("")
    var downloadedPlaylistSongs by persistList<SongEntity>("localPlaylist/$playlistId/songs")
    var cachedPlaylistSongs by persistList<SongEntity>("localPlaylist/$playlistId/songs")
    var playlistSongs by persistList<SongEntity>("localPlaylist/$playlistId/songs")
    var playlistSongsSortByPosition by persistList<SongEntity>("localPlaylist/$playlistId/songs")
    var playlistPreview by persist<PlaylistPreview?>("localPlaylist/playlist")
    val thumbnailUrl = remember { mutableStateOf("") }


    var sortBy by rememberPreference(playlistSongSortByKey, PlaylistSongSortBy.Title)
    var sortOrder by rememberPreference(songSortOrderKey, SortOrder.Descending)

    var filter: String? by rememberSaveable { mutableStateOf(null) }

    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)
    var playlistSongsTypeFilter by rememberPreference(playlistSongsTypeFilterKey, PlaylistSongsTypeFilter.All)

    LaunchedEffect(Unit, filter, sortOrder, sortBy) {
        Database.songsPlaylist(playlistId, sortBy, sortOrder).filterNotNull()
            .collect { playlistAllSongs = it }


    }

    LaunchedEffect(Unit, playlistAllSongs, filter, playlistSongsTypeFilter) {
        when (playlistSongsTypeFilter) {
            PlaylistSongsTypeFilter.All -> {playlistSongs = playlistAllSongs}
            PlaylistSongsTypeFilter.Local -> {
                playlistSongs = playlistAllSongs.filter { it.asMediaItem.isLocal }
            }

            PlaylistSongsTypeFilter.OnlineSongs -> {
                playlistSongs =
                    playlistAllSongs.filter { it.song.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com") == true }
            }

            PlaylistSongsTypeFilter.Videos -> {
                playlistSongs =
                    playlistAllSongs.filter { it.song.thumbnailUrl?.startsWith("https://i.ytimg.com/") == true }
            }

            PlaylistSongsTypeFilter.Unmatched -> {
                playlistSongs =
                    playlistAllSongs.filter { (it.song.thumbnailUrl == "" && !it.asMediaItem.isLocal) || it.song.isSpotifyTrack }
            }

            PlaylistSongsTypeFilter.Favorites -> {
                playlistSongs =
                    playlistAllSongs.filter { it.song.likedAt !in listOf(-1L,null) }
            }

            PlaylistSongsTypeFilter.Explicit -> {
                playlistSongs =
                    playlistAllSongs.filter { it.asMediaItem.isExplicit }
            }

        }
    }

    LaunchedEffect(Unit) {
        Database.songsPlaylist(playlistId, PlaylistSongSortBy.Position, SortOrder.Ascending).filterNotNull()
            .collect { playlistSongsSortByPosition = it }
    }

    LaunchedEffect(Unit) {
        Database.singlePlaylistPreview(playlistId).collect { playlistPreview = it }
    }

    LaunchedEffect( playlistPreview?.playlist?.name ) {
        val thumbnailName = "thumbnail/playlist_${playlistId}"
        val presentThumbnailUrl: String? = checkFileExists(context, thumbnailName)
        if (presentThumbnailUrl != null) {
            thumbnailUrl.value = presentThumbnailUrl
        }
    }

    //**** SMART RECOMMENDATION
    val recommendationsNumber by rememberPreference(
        recommendationsNumberKey,
        RecommendationsNumber.`20`
    )
    var isRecommendationEnabled by rememberPreference(isRecommendationEnabledKey, false)
    var relatedSongsRecommendationResult by persist<Result<Environment.RelatedSongs?>?>(tag = "home/relatedSongsResult")
    var songBaseRecommendation by persist<SongEntity?>("home/songBaseRecommendation")
    var positionsRecommendationList = arrayListOf<Int>()
    var songMatchingDialogEnable by remember { mutableStateOf(false) }
    var matchingSongEntity by remember { mutableStateOf(SongEntity(
        Song(
        id = "",
        title = "",
        durationText = null,
        thumbnailUrl = null
                )
            )
        )
    }

    if (isRecommendationEnabled) {
        LaunchedEffect(Unit, isRecommendationEnabled) {
            Database.songsPlaylist(playlistId, sortBy, sortOrder).distinctUntilChanged()
                .collect { songs ->
                    val song = songs.firstOrNull()
                    if (relatedSongsRecommendationResult == null || songBaseRecommendation?.song?.id != song?.song?.id) {
                        relatedSongsRecommendationResult =
                            Environment.relatedSongs(NextBody(videoId = (song?.song?.id ?: "HZnNt9nnEhw")))
                    }
                    songBaseRecommendation = song
                }
        }
        //relatedSongsRecommendationResult?.getOrNull()?.songs?.toString()?.let { Log.d("mediaItem", "related  $it") }
        //Log.d("mediaItem","related size "+relatedSongsRecommendationResult?.getOrNull()?.songs?.size.toString())
        //val numRelated = relatedSongsResult?.getOrNull()?.songs?.size ?: 0
        //val relatedMax = playlistSongs.size
        if (relatedSongsRecommendationResult != null) {
            for (index in 0..recommendationsNumber.number) {
                positionsRecommendationList.add((0..playlistSongs.size).random())
            }
        }
        //Log.d("mediaItem","positionsList "+positionsRecommendationList.toString())
        //**** SMART RECOMMENDATION
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
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        //scrollThresholdPadding = WindowInsets.systemBars.asPaddingValues(),
    ) { from, to ->
        if (to.key != binder?.player?.currentMediaItem?.mediaId) {
            playlistSongs = playlistSongs.toMutableList().apply {
                // can't use .index because there are other items in the list (headers, footers, etc)
                val fromIndex = indexOfFirst { it.song.id == from.key }
                val toIndex = indexOfFirst { it.song.id == to.key }

                val currentDragInfo = dragInfo
                dragInfo = if (currentDragInfo == null)
                    fromIndex to toIndex
                else currentDragInfo.first to toIndex

                move(fromIndex, toIndex)
                println("reorderableLazyListState dragInfo from ${fromIndex} to ${toIndex}")
            }
        } else dragInfo = null

    }

    LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
        if (!reorderableLazyListState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                val fromIndex = from
                val toIndex = to
               Database.asyncTransaction {
                    move(playlistId, fromIndex, toIndex)
                }
                println("reorderableLazyListState.isAnyItemDragging moved from ${fromIndex} to ${toIndex}")
                dragInfo = null
            }
        }
    }


    var isDeleting by rememberSaveable {
        mutableStateOf(false)
    }

    val coroutineScope = rememberCoroutineScope()




    if (isDeleting) {
        ConfirmationDialog(
            text = stringResource(R.string.delete_playlist),
            onDismiss = { isDeleting = false },
            onConfirm = {
                CoroutineScope(Dispatchers.IO).launch {
                    if (isYtSyncEnabled() && playlistPreview?.playlist?.isYoutubePlaylist == true) {
                        if (playlistPreview?.playlist?.isEditable == true) {
                            playlistPreview?.playlist?.browseId?.let {EnvironmentExt.deletePlaylist(cleanPrefix(it))
                            }
                        } else {
                            playlistPreview?.playlist?.browseId?.let {EnvironmentExt.removelikePlaylistOrAlbum(cleanPrefix(it))}
                        }
                    }
                    Database.asyncTransaction {
                        playlistPreview?.playlist?.let(Database::delete)
                    }
                }

                navController.popBackStack()
            }
        )
    }

    var isRenumbering by rememberSaveable {
        mutableStateOf(false)
    }
    if (isRenumbering) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_renumbering_positions_in_this_playlist),
            onDismiss = { isRenumbering = false },
            onConfirm = {
                Database.asyncTransaction {
                    playlistSongs.forEachIndexed { index, song ->
                        playlistPreview?.playlist?.let {
                            Database.updateSongPosition(it.id, song.song.id, index)
                        }
                    }
                }

            }
        )
    }
    fun sync() {
        if (playlistPreview?.playlist?.name?.startsWith( MONTHLY_PREFIX, 0, true ) == true)
            return

        SmartMessage(
            message = context.resources.getString(R.string.syncing),
            durationLong = true,
            context = context,
        )
        playlistPreview?.let { playlistPreview ->

                Database.asyncTransaction {
                    runBlocking(Dispatchers.IO) {
                        withContext(Dispatchers.IO) {
                            playlistPreview.playlist.browseId?.let {
                                if (playlistPreview.playlist.isPodcast) {
                                    Environment.podcastPage(BrowseBody(browseId = it))?.getOrNull()
                                } else {
                                    EnvironmentExt.getPlaylist(
                                        playlistId = cleanPrefix(it)
                                    ).completed()?.getOrNull()
                                }
                            }
                        }
                    }?.let { remotePlaylist ->
                        when (remotePlaylist) {
                            is Environment.Podcast -> remotePlaylist.listEpisode.map(Environment.Podcast.EpisodeItem::asMediaItem)
                            is PlaylistPage -> remotePlaylist.songs.map(Environment.SongItem::asMediaItem)
                            else -> emptyList<MediaItem>()
                        }.let { songs ->
                            songs
                            .onEach(Database::insert)
                            .mapIndexed { position, mediaItem ->
                                SongPlaylistMap(
                                    songId = mediaItem.mediaId,
                                    playlistId = playlistId,
                                    position = position,
                                    setVideoId = mediaItem.mediaMetadata.extras?.getString("setVideoId"),
                                ).default()
                            }
                            .onEach {
                                Timber.d("LocalPlaylistSongs synced list of setvideoid ${it.setVideoId}")
                                Database.upsert(it)
                            }
                            .also {
                                //Timber.d("LocalPlaylistSongs synced list of setvideoid ${remotePlaylist.songs.map { it.setVideoId }}")
                                SmartMessage(context.resources.getString(R.string.done), context = context)
                            }
                        }



                    }
                }

        }
    }

    val shouldSync = remember(playlistPreview?.playlist?.name) {
        playlistPreview?.playlist?.name?.startsWith(YTP_PREFIX) == true
    }


    LaunchedEffect(shouldSync) {
        if (!shouldSync || !isNetworkConnected(context)) {
            return@LaunchedEffect
        }
        coroutineScope.launch {
            sync()
            Database.asyncTransaction {
                updatePlaylistName(cleanPrefix(playlistPreview?.playlist?.name ?: ""), playlistId)
            }
        }
    }

    var isReorderDisabled by rememberPreference(reorderInQueueEnabledKey, defaultValue = true)

    val playlistThumbnailSizeDp = Dimensions.thumbnails.playlist
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px

    val rippleIndication = ripple(bounded = false)

    val uriHandler = LocalUriHandler.current

    var showConfirmMatchAllDialog by remember {
        mutableStateOf(false)
    }

    var showYoutubeLikeConfirmDialog by remember {
        mutableStateOf(false)
    }

    var totalMinutesToLike by remember { mutableStateOf("") }

    var songItemsToLike = remember { mutableStateListOf<MediaItem>() }

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
                                    val artistInfos = Database.songArtistInfo(it.asMediaItem.mediaId)
                                    val albumInfo = Database.songAlbumInfo(it.asMediaItem.mediaId)
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
                                    val artistInfos = Database.songArtistInfo(it.mediaId)
                                    val albumInfo = Database.songAlbumInfo(it.mediaId)
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

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            context.applicationContext.contentResolver.openInputStream(uri)
                ?.use { inputStream ->
                    csvReader().open(inputStream) {
                        readAllWithHeaderAsSequence().forEachIndexed { index, row: Map<String, String> ->

                            Database.asyncTransaction {
                                plistId = row["PlaylistName"]?.let {
                                    Database.playlistExistByName(
                                        it
                                    )
                                } ?: 0L

                                if (plistId == 0L) {
                                    plistId = row["PlaylistName"]?.let {
                                        Database.insert(
                                            Playlist(
                                                name = it,
                                                browseId = row["PlaylistBrowseId"]
                                            )
                                        )
                                    } ?: 0L
                                } else {
                                    /**/
                                    if (row["MediaId"] != null && row["Title"] != null) {
                                        val song =
                                            row["MediaId"]?.let {
                                                row["Title"]?.let { it1 ->
                                                    Song(
                                                        id = it,
                                                        title = it1,
                                                        artistsText = row["Artists"],
                                                        durationText = row["Duration"],
                                                        thumbnailUrl = row["ThumbnailUrl"]
                                                    )
                                                }
                                            }
                                        Database.asyncTransaction {
                                            if (song != null) {
                                                Database.insert(song)
                                                Database.insert(
                                                    SongPlaylistMap(
                                                        songId = song.id,
                                                        playlistId = plistId,
                                                        position = index
                                                    ).default()
                                                )
                                            }
                                        }


                                    }
                                    /**/
                                }
                            }

                        }
                    }

                }
        }

    var isRenaming by rememberSaveable {
        mutableStateOf(false)
    }
    var isExporting by rememberSaveable {
        mutableStateOf(false)
    }

    if (isRenaming || isExporting) {
        InputTextDialog(
            onDismiss = {
                isRenaming = false
                isExporting = false
            },
            title = stringResource(R.string.enter_the_playlist_name),
            value = playlistPreview?.playlist?.name?.let { cleanPrefix(it) } ?: "",
            placeholder = stringResource(R.string.enter_the_playlist_name),
            setValue = { text ->
                if (isRenaming) {
                    CoroutineScope(Dispatchers.IO).launch {
                        if (isYtSyncEnabled() && (playlistPreview?.playlist?.isEditable == true)) {
                            println("Innertube YtMusic try to rename Playlist with browseId: ${playlistPreview?.playlist?.browseId}, name: $text")
                            playlistPreview?.playlist?.browseId?.let {
                                println("Innertube YtMusic renamePlaylist with id: $it, name: $text")
                                EnvironmentExt.renamePlaylist(cleanPrefix(it), text)
                            }
                        }
                        Database.asyncTransaction {
                            playlistPreview?.playlist?.copy(name = text)?.let(Database::update)
                        }
                    }

                }
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

    val playlistNotMonthlyType =
        playlistPreview?.playlist?.name?.startsWith(MONTHLY_PREFIX, 0, true) == false
    val playlistNotPipedType =
        playlistPreview?.playlist?.name?.startsWith(PIPED_PREFIX, 0, true) == false
    val hapticFeedback = LocalHapticFeedback.current
    val unmatchedSongsCount = playlistSongs.filter { (it.song.thumbnailUrl == "" && !it.asMediaItem.isLocal) || it.song.isSpotifyTrack }.size

    val editThumbnailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val thumbnailName = "playlist_${playlistPreview?.playlist?.id}"
            val permaUri = saveImageToInternalStorage(context, uri, "thumbnail", thumbnailName)
            thumbnailUrl.value = permaUri.toString()
        } else {
            SmartMessage(context.resources.getString(R.string.thumbnail_not_selected), context = context)
        }
    }
    fun openEditThumbnailPicker() {
        editThumbnailLauncher.launch("image/*")
    }

    fun resetThumbnail() {
        if(thumbnailUrl.value == ""){
            SmartMessage(context.resources.getString(R.string.no_thumbnail_present), context = context)
            return
        }
        val thumbnailName = "thumbnail/playlist_${playlistPreview?.playlist?.id}"
        val retVal = deleteFileIfExists(context, thumbnailName)
        if(retVal == true){
            SmartMessage(context.resources.getString(R.string.removed_thumbnail), context = context)
            thumbnailUrl.value = ""
        } else {
            SmartMessage(context.resources.getString(R.string.failed_to_remove_thumbnail), context = context)
        }
    }

    var getAlbumVersion by remember { mutableStateOf(false) }
    var showGetAlbumVersionDialogue by remember { mutableStateOf(false) }
    var showGetAlbumVersionDialogueExt by remember { mutableStateOf(false) }
    var totalSongsToMatch by remember { mutableIntStateOf(0) }
    var songsMatched by remember { mutableIntStateOf(0) }

    if (showGetAlbumVersionDialogue){
        InProgressDialog(
            total = totalSongsToMatch,
            done = songsMatched,
            text = stringResource(R.string.matching_songs)
        )
    }

    if (showGetAlbumVersionDialogueExt){
        InProgressDialog(
            total = totalSongsToMatch,
            done = songsMatched,
            text = stringResource(R.string.matching_songs),
            onDismiss = {showGetAlbumVersionDialogueExt = false}
        )
    }

    if (showYoutubeLikeConfirmDialog) {
        songItemsToLike.clear()
        if (listMediaItems.isEmpty()) {
            playlistSongs.forEachIndexed { index, song ->
                if (song.song.likedAt in listOf(-1L,null)) {
                    songItemsToLike.add(song.asMediaItem)
                }
            }
        } else {
            Database.asyncTransaction {
                listMediaItems.forEachIndexed { index, song ->
                    if (Database.getLikedAt(song.mediaId) in listOf(-1L,null)) {
                        songItemsToLike.add(song)
                    }
                }
            }
        }
        totalMinutesToLike = formatAsDuration(((songItemsToLike).size*1000).toLong())
        ConfirmationDialog(
            text = "$totalMinutesToLike "+stringResource(R.string.do_you_really_want_to_like_all),
            onDismiss = { showYoutubeLikeConfirmDialog = false },
            onConfirm = {
                showYoutubeLikeConfirmDialog = false
                CoroutineScope(Dispatchers.IO).launch {
                    addToYtLikedSongs(songItemsToLike)
                }
            }
        )
    }


    if (playlistSongsSortByPosition.any{songEntity -> songEntity.song.id == (cleanPrefix(songEntity.song.title)+songEntity.song.artistsText).filter{it.isLetterOrDigit()}}){
        showGetAlbumVersionDialogueExt = true
            LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                totalSongsToMatch = playlistSongsSortByPosition
                    .filter{songEntity -> songEntity.song.id == (cleanPrefix(songEntity.song.title)+songEntity.song.artistsText).filter{it.isLetterOrDigit()}}.size
                songsMatched = 0

                val jobs = mutableListOf<Job>()
                playlistSongsSortByPosition.forEachIndexed { index, video ->
                    if (video.song.id == (cleanPrefix(video.song.title)+video.song.artistsText).filter{it.isLetterOrDigit()}){
                        jobs.add(coroutineScope.launch(Dispatchers.IO) {
                            getAlbumVersionFromVideo(
                                song = video.song,
                                playlistId = playlistId,
                                position = index,
                                playlist = playlistPreview?.playlist
                            )
                          }
                        )
                    }
                }
                while(jobs.isNotEmpty()){
                    val oldSize = jobs.size
                    jobs.removeIf{it.isCompleted}
                    songsMatched += oldSize - jobs.size
                    delay(10)
                }
                showGetAlbumVersionDialogueExt = false
                getAlbumVersion = false
            }
        }
    }

    LaunchedEffect(getAlbumVersion) {
        withContext(Dispatchers.IO) {
            totalSongsToMatch = playlistSongsSortByPosition
                .filter {(it.song.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com") == false) && !(it.song.id.startsWith(LOCAL_KEY_PREFIX))}.size
            songsMatched = 0

            val jobs = mutableListOf<Job>()
            playlistSongsSortByPosition.forEachIndexed { index, video ->
                if ((video.song.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com") == false) && !(video.song.id.startsWith(LOCAL_KEY_PREFIX))) {
                    jobs.add(coroutineScope.launch(Dispatchers.IO) {
                        getAlbumVersionFromVideo(
                            song = video.song,
                            playlistId = playlistId,
                            position = index,
                            playlist = playlistPreview?.playlist
                        )
                      }
                    )
                }
            }
            while(jobs.isNotEmpty()){
                val oldSize = jobs.size
                jobs.removeIf{it.isCompleted}
                songsMatched += oldSize - jobs.size
                delay(10)
            }

            showGetAlbumVersionDialogue = false
            getAlbumVersion = false
        }
    }

    var playlistUpdateDialog by remember { mutableStateOf(false) }
    var songsUpdated by remember { mutableIntStateOf(0) }
    var totalSongsToUpdate by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit,playlistUpdateDialog){
        Database.asyncTransaction {
            totalSongsToUpdate = playlistAllSongs.filter { it.song.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com/") == true
                    && !((songAlbumInfo(it.asMediaItem.mediaId)?.id != null)
                    && songArtistInfo(it.asMediaItem.mediaId).isNotEmpty()
                    && !it.song.artistsText.isNullOrBlank()) }.size
        }
    }

    if (playlistUpdateDialog){
        InProgressDialog(
            total = totalSongsToUpdate,
            done = songsUpdated,
            text = stringResource(R.string.updating_playlist)
        )
    }

    LaunchedEffect(playlistUpdateDialog) {
        withContext(Dispatchers.IO) {
            songsUpdated = 0
            val jobs = mutableListOf<Job>()
            playlistAllSongs.filter { it.song.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com/") == true
                    && !((songAlbumInfo(it.asMediaItem.mediaId)?.id != null) && songArtistInfo(it.asMediaItem.mediaId).isNotEmpty() && !it.song.artistsText.isNullOrBlank()) }.forEach { song ->
                jobs.add(coroutineScope.launch(Dispatchers.IO) {
                    updateLocalPlaylist(song.song)
                }
                )
            }
            while(jobs.isNotEmpty()){
                val oldSize = jobs.size
                jobs.removeIf{it.isCompleted}
                songsUpdated += oldSize - jobs.size
                delay(10)
            }
            playlistUpdateDialog = false
        }
    }

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

    println("LocalPlaylistSongs playlist browseId ${playlistPreview?.playlist?.browseId}")

    // ---- Yammbo Noir playlist page ----
    LaunchedEffect(scrollToNowPlaying) {
        if (scrollToNowPlaying)
            // Songs follow the header, the tools row and (when shown) the empty state.
            lazyListState.scrollToItem(
                nowPlayingItem + 1 +
                        (if (playlistAllSongs.isNotEmpty()) 1 else 0) +
                        (if (playlistSongs.isEmpty() && !refreshing) 1 else 0),
                1
            )
        scrollToNowPlaying = false
    }

    if (showConfirmMatchAllDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_match_all),
            onDismiss = { showConfirmMatchAllDialog = false },
            onConfirm = {
                getAlbumVersion = true
                showGetAlbumVersionDialogue = true
                showConfirmMatchAllDialog = false
            }
        )
    }

    val isYtmPlaylist = playlistPreview?.playlist?.isYoutubePlaylist == true ||
            playlistPreview?.playlist?.name?.startsWith(YTP_PREFIX) == true
    val isPinned = playlistPreview?.playlist?.name?.startsWith(PINNED_PREFIX, 0, true) == true
    val hasBrowseId = !playlistPreview?.playlist?.browseId.isNullOrBlank()
    val canMatchAlbumVersion = playlistSongs.any {
        (it.song.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com") == false) &&
                !(it.song.id.startsWith(LOCAL_KEY_PREFIX))
    }

    // Cover: the custom thumbnail, the Liked Music art, or the first song's cover.
    val playlistCoverUrl: String? = thumbnailUrl.value.takeIf { it.isNotBlank() }
        ?: if (playlistPreview?.playlist?.browseId == "LM")
            "https://www.gstatic.com/youtube/media/ytm/images/pbg/liked-music-@1200.png"
        else playlistSongsSortByPosition
            .firstOrNull { !it.song.thumbnailUrl.isNullOrBlank() }?.song?.thumbnailUrl
    val coverTint = rememberCoverTint(playlistCoverUrl)

    val sortLabel = when (sortBy) {
        PlaylistSongSortBy.Album -> stringResource(R.string.sort_album)
        PlaylistSongSortBy.AlbumYear -> stringResource(R.string.sort_album_year)
        PlaylistSongSortBy.Position -> stringResource(R.string.sort_position)
        PlaylistSongSortBy.Title -> stringResource(R.string.sort_title)
        PlaylistSongSortBy.DatePlayed -> stringResource(R.string.sort_date_played)
        PlaylistSongSortBy.DateLiked -> stringResource(R.string.sort_date_liked)
        PlaylistSongSortBy.Artist -> stringResource(R.string.sort_artist)
        PlaylistSongSortBy.ArtistAndAlbum -> "${stringResource(R.string.sort_artist)}, ${stringResource(R.string.sort_album)}"
        PlaylistSongSortBy.PlayTime -> stringResource(R.string.sort_listening_time)
        PlaylistSongSortBy.Duration -> stringResource(R.string.sort_duration)
        PlaylistSongSortBy.DateAdded -> stringResource(R.string.sort_date_added)
        PlaylistSongSortBy.RelativePlayTime -> stringResource(R.string.sort_relative_listening_time)
    }
    val filterLabel = when (playlistSongsTypeFilter) {
        PlaylistSongsTypeFilter.All -> stringResource(R.string.all)
        PlaylistSongsTypeFilter.OnlineSongs -> stringResource(R.string.online_songs)
        PlaylistSongsTypeFilter.Videos -> stringResource(R.string.videos)
        PlaylistSongsTypeFilter.Local -> stringResource(R.string.on_device)
        PlaylistSongsTypeFilter.Favorites -> stringResource(R.string.favorites)
        PlaylistSongsTypeFilter.Unmatched -> stringResource(R.string.unmatched)
        PlaylistSongsTypeFilter.Explicit -> stringResource(R.string.explicit)
    }

    // "LISTA · N canciones · X min", skipping what is missing.
    val playlistMeta = listOfNotNull(
        stringResource(R.string.noir_kind_playlist),
        if (playlistSongs.isNotEmpty())
            stringResource(R.string.noir_album_songs_count, playlistSongs.size)
        else null,
        (totalPlayTimes / 60_000L).toInt().takeIf { it > 0 }
            ?.let { stringResource(R.string.noir_album_minutes, it) }
    ).joinToString(" · ")

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

    val enqueueAll: () -> Unit = {
        val playable = playlistSongs.filter { it.song.thumbnailUrl != "" && it.song.likedAt != -1L }
        if (playable.isNotEmpty()) {
            binder?.player?.enqueue(playable.map(SongEntity::asMediaItem), context)
        } else {
            SmartMessage(
                context.resources.getString(R.string.disliked_this_collection),
                type = PopupType.Error,
                context = context
            )
        }
    }

    // The full playlist menu (enqueue, play next, rename, delete, export, thumbnail, ...).
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
                                                /*
                                        onSelect = { selectItems = true },
                                        onUncheck = {
                                            selectItems = false
                                            listMediaItems.clear()
                                        },
                                         */
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
                                                showOnSyncronize = !playlistPreview.playlist.browseId.isNullOrBlank(),
                                                showLinkUnlink = isNetworkConnected(context) && (!playlistPreview.playlist.browseId.isNullOrBlank()),
                                                onSyncronize = { sync() },
                                                onLinkUnlink = {
                                                    if (!isNetworkConnected(context) && playlistPreview.playlist.isYoutubePlaylist && playlistPreview.playlist.isEditable && isYtSyncEnabled()) {
                                                        SmartMessage(
                                                            context.resources.getString(R.string.no_connection),
                                                            context = context,
                                                            type = PopupType.Error
                                                        )
                                                    } else if (playlistPreview.playlist.isYoutubePlaylist) {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            if (playlistPreview.playlist.isEditable) {
                                                                playlistPreview.playlist.browseId.let {
                                                                    EnvironmentExt.deletePlaylist(
                                                                        it ?: ""
                                                                    )
                                                                }
                                                            } else {
                                                                playlistPreview.playlist.browseId.let {
                                                                    EnvironmentExt.removelikePlaylistOrAlbum(
                                                                        it ?: ""
                                                                    )
                                                                }
                                                            }
                                                            Database.update(
                                                                playlistPreview.playlist.copy(
                                                                    browseId = null,
                                                                    isYoutubePlaylist = false,
                                                                    isEditable = false
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            Database.update(
                                                                playlistPreview.playlist.copy(
                                                                    browseId = null
                                                                )
                                                            )
                                                        }
                                                    }
                                                },
                                                onRename = {
                                                    if (!isNetworkConnected(context) && playlistPreview.playlist.isYoutubePlaylist && (playlistPreview.playlist.isEditable) && isYtSyncEnabled()) {
                                                        SmartMessage(
                                                            context.resources.getString(R.string.no_connection),
                                                            context = context,
                                                            type = PopupType.Error
                                                        )
                                                    } else if (playlistPreview.playlist.isEditable && playlistNotMonthlyType && playlistPreview.playlist.browseId != "LM") {
                                                        isRenaming = true
                                                    } else SmartMessage(
                                                        context.resources.getString(R.string.info_cannot_rename_a_monthly_or_piped_playlist),
                                                        context = context
                                                    )
                                                },
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
                                                    if (!isNetworkConnected(appContext()) && isYtSyncEnabled()) {
                                                        SmartMessage(
                                                            appContext().resources.getString(R.string.no_connection),
                                                            context = appContext(),
                                                            type = PopupType.Error
                                                        )
                                                    } else if (!isYtSyncEnabled()) {
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
                                                    } else {
                                                        showYoutubeLikeConfirmDialog = true
                                                    }
                                                },
                                                onRenumberPositions = {
                                                    if (playlistNotMonthlyType)
                                                        isRenumbering = true
                                                    else
                                                    /*
                                            SmartToast(context.resources.getString(R.string.info_cannot_renumbering_a_monthly_playlist))
                                             */
                                                        SmartMessage(
                                                            context.resources.getString(R.string.info_cannot_renumbering_a_monthly_playlist),
                                                            context = context
                                                        )
                                                },
                                                onDelete = {
                                                    if (!isNetworkConnected(context) && playlistPreview.playlist.isYoutubePlaylist && isYtSyncEnabled()) {
                                                        SmartMessage(
                                                            context.resources.getString(R.string.no_connection),
                                                            context = context,
                                                            type = PopupType.Error
                                                        )
                                                    } else isDeleting = true
                                                },
                                                showonListenToYT = !playlistPreview.playlist.browseId.isNullOrBlank(),
                                                onListenToYT = {
                                                    binder?.player?.pause()
                                                    uriHandler.openUri(
                                                        "https://youtube.com/playlist?list=${
                                                            playlistPreview.playlist.browseId?.let {
                                                                cleanPrefix(it).removePrefix("VL")
                                                            }
                                                        }"
                                                    )
                                                },
                                                onExport = {
                                                    isExporting = true
                                                },
                                                onEditThumbnail = {
                                                    openEditThumbnailPicker()
                                                },
                                                onResetThumbnail = {
                                                    resetThumbnail()
                                                },
                                                onGoToPlaylist = {
                                                    navController.navigate("${NavRoutes.localPlaylist.name}/$it")
                                                },
                                                disableScrollingText = disableScrollingText,
                                                /*
                                        onImport = {
                                            try {
                                                importLauncher.launch(
                                                    arrayOf(
                                                        "text/csv",
                                                        "text/txt"
                                                    )
                                                )
                                            } catch (e: ActivityNotFoundException) {
                                                context.toast("Couldn't find an application to open documents")
                                            }
                                        }
                                        */
                                            )
                                        }

                                    }
    }

    val openSortMenu: () -> Unit = {
                                        menuState.display {
                                            SortMenu(
                                                title = stringResource(R.string.sorting_order),
                                                onDismiss = menuState::hide,
                                                onTitle = {
                                                    sortBy =
                                                        PlaylistSongSortBy.Title; isReorderDisabled =
                                                    true
                                                },
                                                onAlbum = {
                                                    sortBy =
                                                        PlaylistSongSortBy.Album; isReorderDisabled =
                                                    true
                                                },
                                                onAlbumYear = {
                                                    sortBy =
                                                        PlaylistSongSortBy.AlbumYear; isReorderDisabled =
                                                    true
                                                },
                                                onDatePlayed = {
                                                    sortBy =
                                                        PlaylistSongSortBy.DatePlayed; isReorderDisabled =
                                                    true
                                                },
                                                onDateLiked = {
                                                    sortBy =
                                                        PlaylistSongSortBy.DateLiked; isReorderDisabled =
                                                    true
                                                },
                                                onPosition = {
                                                    sortBy = PlaylistSongSortBy.Position
                                                    if (sortOrder == SortOrder.Ascending) isReorderDisabled =
                                                        false else isReorderDisabled = true
                                                },
                                                onArtist = {
                                                    sortBy =
                                                        PlaylistSongSortBy.Artist; isReorderDisabled =
                                                    true
                                                },
                                                onArtistAndAlbum = {
                                                    sortBy =
                                                        PlaylistSongSortBy.ArtistAndAlbum; isReorderDisabled =
                                                    true
                                                },
                                                onPlayTime = {
                                                    sortBy =
                                                        PlaylistSongSortBy.PlayTime; isReorderDisabled =
                                                    true
                                                },
                                                onRelativePlayTime = {
                                                    sortBy =
                                                        PlaylistSongSortBy.RelativePlayTime; isReorderDisabled =
                                                    true
                                                },
                                                onDuration = {
                                                    sortBy =
                                                        PlaylistSongSortBy.Duration; isReorderDisabled =
                                                    true
                                                },
                                                onDateAdded = {
                                                    sortBy =
                                                        PlaylistSongSortBy.DateAdded; isReorderDisabled =
                                                    true
                                                },
                                            )
                                        }
    }

    val openFilterMenu: () -> Unit = {
                                        menuState.display {
                                            FilterMenu(
                                                title = stringResource(R.string.filter_by),
                                                onDismiss = menuState::hide,
                                                onAll = {
                                                    playlistSongsTypeFilter =
                                                        PlaylistSongsTypeFilter.All
                                                },
                                                onOnlineSongs = {
                                                    playlistSongsTypeFilter =
                                                        PlaylistSongsTypeFilter.OnlineSongs
                                                },
                                                onFavorites = {
                                                    playlistSongsTypeFilter =
                                                        PlaylistSongsTypeFilter.Favorites
                                                },
                                                onVideos = {
                                                    playlistSongsTypeFilter =
                                                        PlaylistSongsTypeFilter.Videos
                                                },
                                                onLocal = {
                                                    playlistSongsTypeFilter =
                                                        PlaylistSongsTypeFilter.Local
                                                },
                                                onUnmatched = {
                                                    playlistSongsTypeFilter =
                                                        PlaylistSongsTypeFilter.Unmatched
                                                },
                                                onExplicit = {
                                                    playlistSongsTypeFilter =
                                                        PlaylistSongsTypeFilter.Explicit
                                                },
                                            )
                                        }
    }

    // The top-right "...": every control the old action row and sort bar had, then the full menu.
    val openMoreMenu: () -> Unit = {
        menuState.display {
            Menu {
                MenuToggleEntry(
                    icon = R.drawable.search,
                    text = stringResource(R.string.search),
                    checked = searching,
                    onClick = {
                        menuState.hide()
                        searching = !searching
                        if (!searching) filter = null
                    }
                )
                MenuEntry(
                    icon = R.drawable.sort,
                    text = stringResource(R.string.sorting_order),
                    secondaryText = sortLabel,
                    onClick = { openSortMenu() }
                )
                MenuEntry(
                    icon = R.drawable.refresh,
                    text = stringResource(R.string.noir_playlist_reverse_order),
                    onClick = {
                        menuState.hide()
                        sortOrder = !sortOrder
                        isReorderDisabled =
                            !(sortBy == PlaylistSongSortBy.Position && sortOrder == SortOrder.Ascending)
                    }
                )
                MenuEntry(
                    icon = R.drawable.list_view,
                    text = stringResource(R.string.filter_by),
                    secondaryText = filterLabel,
                    onClick = { openFilterMenu() }
                )
                MenuToggleEntry(
                    icon = if (isReorderDisabled) R.drawable.locked else R.drawable.unlocked,
                    text = stringResource(R.string.info_lock_unlock_reorder_songs),
                    checked = !isReorderDisabled,
                    enabled = playlistSongs.isNotEmpty(),
                    onClick = {
                        isReorderDisabled = !isReorderDisabled
                        if (!isReorderDisabled) {
                            sortBy = PlaylistSongSortBy.Position
                            sortOrder = SortOrder.Ascending
                        }
                    }
                )
                MenuToggleEntry(
                    icon = R.drawable.pin,
                    text = stringResource(R.string.info_pin_unpin_playlist),
                    checked = isPinned,
                    enabled = playlistSongs.isNotEmpty(),
                    onClick = {
                        Database.asyncTransaction {
                            if (playlistPreview?.playlist?.name?.startsWith(
                                    PINNED_PREFIX,
                                    0,
                                    true
                                ) == true
                            )
                                Database.unPinPlaylist(playlistId) else
                                Database.pinPlaylist(playlistId)
                        }
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
                MenuToggleEntry(
                    icon = R.drawable.smart_shuffle,
                    text = stringResource(R.string.smart_recommendations),
                    checked = isRecommendationEnabled,
                    secondaryText = if (isRecommendationEnabled)
                        positionsRecommendationList.distinct().size.toString() else null,
                    onClick = { isRecommendationEnabled = !isRecommendationEnabled }
                )
                if (playlistPreview?.playlist?.isYoutubePlaylist == false && unmatchedSongsCount > 0) {
                    MenuEntry(
                        icon = R.drawable.alert,
                        text = stringResource(R.string.get_album_version),
                        secondaryText = "($unmatchedSongsCount)",
                        enabled = canMatchAlbumVersion,
                        onClick = {
                            menuState.hide()
                            if (!isNetworkConnected(context) && playlistPreview?.playlist?.isYoutubePlaylist == true && (playlistPreview?.playlist?.isEditable == true) && isYtSyncEnabled()) {
                                SmartMessage(
                                    context.resources.getString(R.string.no_connection),
                                    context = context,
                                    type = PopupType.Error
                                )
                            } else if (canMatchAlbumVersion) {
                                showConfirmMatchAllDialog = true
                            } else {
                                SmartMessage(
                                    context.resources.getString(R.string.no_videos_found),
                                    context = context
                                )
                            }
                        }
                    )
                }
                MenuEntry(
                    icon = R.drawable.update,
                    text = stringResource(R.string.updating_playlist_message),
                    onClick = {
                        menuState.hide()
                        playlistUpdateDialog = true
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
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth(
                    if (navigationBarPosition == NavigationBarPosition.Left ||
                        navigationBarPosition == NavigationBarPosition.Top ||
                        navigationBarPosition == NavigationBarPosition.Bottom
                    ) 1f
                    else Dimensions.contentWidthRightBar
                )
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
                        AlbumNoirHeader(
                            coverUrl = playlistCoverUrl,
                            previewUrl = null,
                            sharedKey = null,
                            showCover = true,
                            title = playlistPreview?.playlist?.name?.let { name ->
                                cleanPrefix(name)
                            } ?: "Unknown",
                            artist = if (isYtmPlaylist) "YouTube Music" else "",
                            meta = playlistMeta,
                            tint = coverTint,
                            onBack = { navController.popBackStack() },
                            onMore = openMoreMenu,
                            actions = {
                                // The songs on screen queued for in-app download.
                                AlbumSquareAction(
                                    icon = downloadIconFor(playlistSongs.map { it.song }.filterNot { it.isLocal }.map { it.id }),
                                    enabled = playlistSongs.isNotEmpty(),
                                    onLongClick = {
                                        SmartMessage(
                                            context.resources.getString(R.string.download_all_with_ytdlnis),
                                            context = context
                                        )
                                    },
                                    onClick = {
                                        downloadSongs(context = context, songs = playlistSongs.map { it.song })
                                    }
                                )

                                AlbumSquareAction(
                                    icon = R.drawable.shuffle,
                                    enabled = playlistSongs.isNotEmpty(),
                                    onClick = shuffleAll
                                )

                                AlbumPlayAction(
                                    enabled = playlistSongs.isNotEmpty(),
                                    onClick = playAll
                                )

                                // Synced with YouTube when it has a browse id, otherwise enqueue.
                                if (hasBrowseId)
                                    AlbumSquareAction(
                                        icon = R.drawable.sync,
                                        onClick = { refresh() },
                                        onLongClick = {
                                            SmartMessage(
                                                context.resources.getString(R.string.sync),
                                                context = context
                                            )
                                        }
                                    )
                                else
                                    AlbumSquareAction(
                                        icon = R.drawable.addqueue,
                                        enabled = playlistSongs.isNotEmpty(),
                                        onClick = enqueueAll,
                                        onLongClick = {
                                            SmartMessage(
                                                context.resources.getString(R.string.enqueue),
                                                context = context
                                            )
                                        }
                                    )

                                // Share needs a YouTube link; a local list can be exported to CSV instead.
                                if (playlistPreview?.playlist?.browseId != null)
                                    AlbumSquareAction(
                                        icon = R.drawable.share_social,
                                        onClick = { showFastShare = true }
                                    )
                                else
                                    AlbumSquareAction(
                                        icon = R.drawable.export,
                                        onClick = { isExporting = true },
                                        onLongClick = {
                                            SmartMessage(
                                                context.resources.getString(R.string.export_playlist),
                                                context = context
                                            )
                                        }
                                    )
                            }
                        )
                    }

                    if (playlistAllSongs.isNotEmpty())
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
                                        selected = false,
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
                                    NoirChip(
                                        text = filterLabel,
                                        selected = false,
                                        icon = R.drawable.list_view,
                                        onClick = openFilterMenu
                                    )
                                }
                                if (searching)
                                    PlaylistSearchField(
                                        value = filter ?: "",
                                        onValueChange = { filter = it },
                                        hint = stringResource(R.string.noir_playlist_search_hint),
                                        onClear = {
                                            filter = null
                                            searching = false
                                        }
                                    )
                            }
                        }

                    if (playlistSongs.isEmpty() && !refreshing)
                        item(
                            key = "empty",
                            contentType = 2
                        ) {
                            val noMatch = playlistAllSongs.isNotEmpty()
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
                                        else R.string.noir_playlist_empty_title
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
                                        else R.string.noir_playlist_empty_body
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
                        ReorderableItem(
                            reorderableLazyListState,
                            key = song.song.id
                        ) { isDragging ->

                            val interactionSource = remember { MutableInteractionSource() }

                            if (index in positionsRecommendationList.distinct()) {
                                val songRecommended =
                                    relatedSongsRecommendationResult?.getOrNull()?.songs?.shuffled()
                                        ?.lastOrNull()
                                songRecommended?.asMediaItem?.let {
                                    SongItem(
                                        song = it,
                                        isRecommended = true,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        thumbnailSizePx = thumbnailSizePx,
                                        trailingContent = {},
                                        onThumbnailContent = {},
                                        modifier = Modifier
                                            .noirContentWidth(NoirContentMax.List)
                                            .clickable {
                                                binder?.stopRadio()
                                                binder?.player?.forcePlay(it)
                                                //fastPlay(it, binder)
                                            },
                                        //disableScrollingText = disableScrollingText,
                                        //isNowPlaying = binder?.player?.isNowPlaying(it.mediaId) ?: false

                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .noirContentWidth(NoirContentMax.List)
                                    .animateItem()
                                    .padding(horizontal = 4.dp)

                            ) {
                                //val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }
                                val checkedState = rememberSaveable { mutableStateOf(false) }
                                val positionInPlaylist: Int = index
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .zIndex(10f)
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-5).dp)
                                        .draggableHandle(
                                            enabled = !isReorderDisabled,
                                            interactionSource = interactionSource,
                                            onDragStarted = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                            },
                                            onDragStopped = {
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.LongPress
                                                )
                                            }
                                        )

                                ) {
                                    if (!isReorderDisabled && sortBy == PlaylistSongSortBy.Position && sortOrder == SortOrder.Ascending) {
                                        IconButton(
                                            icon = R.drawable.reorder,
                                            color = colorPalette.accent,
                                            indication = rippleIndication,
                                            onClick = {},
                                        )
                                    }
                                }

                                SwipeableQueueItem(
                                    mediaItem = song.asMediaItem,
                                    onRemoveFromQueue = {
                                        if (!isNetworkConnected(context) && playlistPreview?.playlist?.isYoutubePlaylist == true && (playlistPreview?.playlist?.isEditable == true) && isYtSyncEnabled()) {
                                            SmartMessage(
                                                context.resources.getString(R.string.no_connection),
                                                context = context,
                                                type = PopupType.Error
                                            )
                                        } else if (playlistPreview?.playlist?.isEditable == true) {
                                            if (isYtSyncEnabled() && playlistPreview?.playlist?.isYoutubePlaylist == true) {
                                                Database.asyncTransaction {
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        if (removeYTSongFromPlaylist(
                                                                song.asMediaItem.mediaId,
                                                                playlistPreview?.playlist?.browseId
                                                                    ?: "",
                                                                playlistId
                                                            )
                                                        ) {
                                                            deleteSongFromPlaylist(
                                                                song.asMediaItem.mediaId,
                                                                playlistId
                                                            )
                                                        }
                                                        if (playlistPreview?.playlist?.browseId == "LM") {
                                                            removeFromOnlineLikedSong(song.asMediaItem)
                                                        }
                                                    }
                                                }
                                            } else {
                                                Database.asyncTransaction {
                                                    deleteSongFromPlaylist(
                                                        song.asMediaItem.mediaId,
                                                        playlistId
                                                    )
                                                }
                                            }

                                            coroutineScope.launch {
                                                SmartMessage(
                                                    context.resources.getString(R.string.deleted) + " \"" + song.asMediaItem.mediaMetadata.title.toString() + " - " + song.asMediaItem.mediaMetadata.artist.toString() + "\" ",
                                                    type = PopupType.Warning,
                                                    context = context,
                                                    durationLong = true
                                                )
                                            }
                                        } else {
                                            SmartMessage(
                                                context.resources.getString(R.string.cannot_delete_from_online_playlists),
                                                type = PopupType.Warning,
                                                context = context
                                            )
                                        }
                                    },
                                    onPlayNext = {
                                        binder?.player?.addNext(
                                            song.asMediaItem,
                                            queue = selectedQueue ?: defaultQueue()
                                        )
                                    }
                                ) {
                                    //var forceRecompose by remember { mutableStateOf(false) }
                                    SongItem(
                                        song = song.song,
                                        thumbnailSizePx = thumbnailSizePx,
                                        thumbnailSizeDp = thumbnailSizeDp,
                                        trailingContent = {
                                            if (selectItems)
                                                Checkbox(
                                                    checked = checkedState.value,
                                                    onCheckedChange = {
                                                        checkedState.value = it
                                                        if (it) listMediaItems.add(song.asMediaItem) else
                                                            listMediaItems.remove(song.asMediaItem)
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = colorPalette.accent,
                                                        uncheckedColor = colorPalette.text
                                                    ),
                                                    modifier = Modifier
                                                        .scale(0.7f)
                                                )
                                            else checkedState.value = false

                                        },
                                        onThumbnailContent = {
                                            if (sortBy == PlaylistSongSortBy.PlayTime) {
                                                BasicText(
                                                    text = song.song.formattedTotalPlayTime,
                                                    style = typography.xxs.semiBold.center.color(
                                                        colorPalette.onOverlay
                                                    ),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            brush = Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    colorPalette.overlay
                                                                )
                                                            ),
                                                            shape = thumbnailShape
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .align(Alignment.BottomCenter)
                                                )
                                            }
                                            if (sortBy == PlaylistSongSortBy.RelativePlayTime) {
                                                BasicText(
                                                    text = "${song.relativePlayTime().toLong()}",
                                                    style = typography.xxs.semiBold.center.color(
                                                        colorPalette.onOverlay
                                                    ),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            brush = Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    colorPalette.overlay
                                                                )
                                                            ),
                                                            shape = thumbnailShape
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .align(Alignment.BottomCenter)
                                                )
                                            }


                                            if (sortBy == PlaylistSongSortBy.Position && sortOrder == SortOrder.Ascending)
                                                BasicText(
                                                    text = (index + 1).toString(),
                                                    style = typography.m.semiBold.center.color(
                                                        colorPalette.onOverlay
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            brush = Brush.verticalGradient(
                                                                colors = listOf(
                                                                    Color.Transparent,
                                                                    colorPalette.overlay
                                                                )
                                                            ),
                                                            shape = thumbnailShape
                                                        )
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .align(Alignment.Center)
                                                )


                                            if (nowPlayingItem > -1)
                                                NowPlayingSongIndicator(
                                                    song.asMediaItem.mediaId,
                                                    binder?.player
                                                )
                                        },
                                        modifier = Modifier
                                            .combinedClickable(
                                                //enabled = isReorderDisabled,
                                                onLongClick = {
                                                    menuState.display {
                                                        InPlaylistMediaItemMenu(
                                                            onMatchingSong = {
                                                                if (!isNetworkConnected(context) && playlistPreview?.playlist?.isYoutubePlaylist == true && (playlistPreview?.playlist?.isEditable == true) && isYtSyncEnabled()) {
                                                                    SmartMessage(
                                                                        context.resources.getString(
                                                                            R.string.no_connection
                                                                        ),
                                                                        context = context,
                                                                        type = PopupType.Error
                                                                    )
                                                                } else if ((playlistPreview?.playlist?.isYoutubePlaylist) == false) {
                                                                    songMatchingDialogEnable = true
                                                                    matchingSongEntity = song
                                                                } else {
                                                                    SmartMessage(
                                                                        context.resources.getString(
                                                                            R.string.cannot_delete_from_online_playlists
                                                                        ),
                                                                        type = PopupType.Warning,
                                                                        context = context
                                                                    )
                                                                }
                                                            },
                                                            onInfo = {
                                                                navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.song.id}")
                                                            },
                                                            navController = navController,
                                                            playlist = playlistPreview,
                                                            playlistId = playlistId,
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
                                                        if (song.song.thumbnailUrl == "") {
                                                            songMatchingDialogEnable = true
                                                            matchingSongEntity = song
                                                        } else if (song.song.likedAt != -1L) {
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

                                            .background(color = colorPalette.background0),
                                        //disableScrollingText = disableScrollingText,
                                        //isNowPlaying = binder?.player?.isNowPlaying(song.song.id) ?: false
                                    )
                                }
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
                    visible = !reorderableLazyListState.isAnyItemDragging,
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


/** Pill search field of the playlist page, same look as the history one. */
@Composable
private fun PlaylistSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    onClear: () -> Unit,
) {
    val (colors, typo) = LocalAppearance.current
    val shape = RoundedCornerShape(percent = 50)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(colors.background1)
            .border(1.dp, colors.text.copy(alpha = 0.10f), shape)
            .padding(horizontal = 16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier.size(18.dp)
        )
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            if (value.isEmpty()) {
                BasicText(
                    text = hint,
                    style = typo.s.copy(color = colors.textSecondary, fontSize = 15.sp),
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = typo.s.copy(color = colors.text, fontSize = 15.sp),
                singleLine = true,
                cursorBrush = SolidColor(colors.text),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
        Image(
            painter = painterResource(R.drawable.close),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier
                .size(16.dp)
                .clickable { onClear() }
        )
    }
}
