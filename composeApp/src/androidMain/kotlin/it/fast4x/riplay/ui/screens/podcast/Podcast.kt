package it.fast4x.riplay.ui.screens.podcast

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil.compose.AsyncImage
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.Environment
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.environment.models.bodies.BrowseBody
import it.fast4x.environment.requests.podcastPage
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.enums.ThumbnailRoundness
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.SongPlaylistMap
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.SwipeablePlaylistItem
import it.fast4x.riplay.ui.components.themed.AutoResizeText
import it.fast4x.riplay.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.riplay.ui.components.themed.FontSizeRange
import it.fast4x.riplay.ui.components.themed.HeaderIconButton
import it.fast4x.riplay.ui.components.themed.IconButton
import it.fast4x.riplay.ui.components.themed.InputTextDialog
import it.fast4x.riplay.ui.components.themed.LayoutWithAdaptiveThumbnail
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.components.themed.PlaylistsItemMenu
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.components.themed.adaptiveThumbnailContent
import it.fast4x.riplay.ui.items.AlbumItemPlaceholder
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.items.SongItemPlaceholder
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.favoritesIcon
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.commonutils.durationTextToMillis
import it.fast4x.riplay.utils.enqueue
import it.fast4x.riplay.utils.fadingEdge
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.forcePlayFromBeginning
import it.fast4x.riplay.utils.formatAsTime
import it.fast4x.riplay.utils.isLandscape
import it.fast4x.riplay.ui.styling.medium
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.utils.resize
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.extensions.preferences.showFloatingIconKey
import it.fast4x.riplay.extensions.preferences.thumbnailRoundnessKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import it.fast4x.riplay.ui.components.themed.LoadFailed
import it.fast4x.riplay.ui.components.themed.MediaActionButton
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.screens.album.AlbumNoirHeader
import it.fast4x.riplay.ui.screens.album.AlbumPlayAction
import it.fast4x.riplay.ui.screens.album.AlbumSquareAction
import it.fast4x.riplay.ui.screens.album.NoirDescriptionBlock
import it.fast4x.riplay.ui.screens.album.NoirListSearchField
import it.fast4x.riplay.ui.screens.album.rememberCoverTint
import it.fast4x.riplay.utils.httpClient
import it.fast4x.riplay.utils.languageDestination
import me.bush.translator.Language
import me.bush.translator.Translator
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.extensions.fastshare.FastShare
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.ui.components.themed.LoaderScreen
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.addToYtPlaylist
import it.fast4x.riplay.utils.toPlaylist
import timber.log.Timber


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun Podcast(
    navController: NavController,
    browseId: String,
) {

    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalGlobalSheetState.current
    val selectedQueue = LocalSelectedQueue.current

    var podcastPage by persist<Environment.Podcast?>("podcast/$browseId/listEpisodes")

    var filter: String? by rememberSaveable { mutableStateOf(null) }
    val hapticFeedback = LocalHapticFeedback.current

    var loadFailed by remember(browseId) { mutableStateOf(false) }
    // Bumped by the retry button to run the fetch again.
    var reloadKey by remember(browseId) { mutableIntStateOf(0) }

    // Resolved before any early return: while the page loads, the header (which carries the
    // shared cover) is composed from this handoff so the tapped cover has somewhere to fly to.
    val handoff = remember(browseId) { CoverHandoff.forPage(browseId) }

    // With a handoff the header is already on screen and the shimmer below it shows the loading.
    LoaderScreen(show = podcastPage == null && !loadFailed && handoff == null)

    LaunchedEffect(browseId, reloadKey) {
        if (podcastPage != null) return@LaunchedEffect

        // A hung request or an error used to leave the spinner running forever.
        val page = withTimeoutOrNull(20_000L) {
            withContext(Dispatchers.IO) {
                Environment.podcastPage(BrowseBody(browseId = browseId)).getOrNull()
            }
        }
        if (page == null) loadFailed = true
        else {
            loadFailed = false
            podcastPage = page
        }
    }

    // Emitted before the early return further down, which would otherwise hide it.
    if (podcastPage == null && loadFailed) {
        LoadFailed(
            onRetry = {
                loadFailed = false
                reloadKey++
            },
            onBack = { navController.popBackStack() }
        )
        return
    }

    var filterCharSequence: CharSequence
    filterCharSequence = filter.toString()
    /*
    //Log.d("mediaItemFilter", "<${filter}>  <${filterCharSequence}>")
    if (!filter.isNullOrBlank())
        playlistPage?.songsPage?.items =
        playlistPage?.songsPage?.items?.filter {songItem ->
                songItem.asMediaItem.mediaMetadata.title?.contains(filterCharSequence,true) ?: false
                        || songItem.asMediaItem.mediaMetadata.artist?.contains(filterCharSequence,true) ?: false
            }
    */

    var searching by rememberSaveable { mutableStateOf(false) }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px

    var isImportingPlaylist by rememberSaveable {
        mutableStateOf(false)
    }

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    var thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)

    var totalPlayTimes = 0L
    podcastPage?.listEpisode?.forEach {
        totalPlayTimes += it.durationString?.let { it1 ->
            durationTextToMillis(it1) }?.toLong() ?: 0
    }

    if (isImportingPlaylist) {
        InputTextDialog(
            onDismiss = { isImportingPlaylist = false },
            title = stringResource(R.string.enter_the_playlist_name),
            value = podcastPage?.title ?: "",
            placeholder = "https://........",
            setValue = { text ->
                Database.asyncTransaction {
                    val playlistId = insert(
                        Playlist(
                            name = text,
                            browseId = browseId,
                            //isYoutubePlaylist = true,
                            //isEditable = false,
                            isPodcast = true
                        )
                    )

                    podcastPage?.listEpisode
                        ?.map(Environment.Podcast.EpisodeItem::asMediaItem)
                        ?.onEach( ::insert )
                        ?.mapIndexed { index, mediaItem ->
                            SongPlaylistMap(
                                songId = mediaItem.mediaId,
                                playlistId = playlistId,
                                position = index
                            ).default()
                        }
                        ?.onEach { Database.insert(it) }
                }
                SmartMessage(context.resources.getString(R.string.done), PopupType.Success, context = context)
            }
        )
    }

    var position by remember {
        mutableIntStateOf(0)
    }

    val thumbnailContent = adaptiveThumbnailContent(podcastPage == null, podcastPage?.thumbnail?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url)

    val lazyListState = rememberLazyListState()

    var showFastShare by remember { mutableStateOf(false) }

    // Nothing to draw yet and no cover to fly in: keep the full-screen loader alone.
    if (podcastPage == null && handoff == null) return

    podcastPage?.toPlaylist(browseId)?.let { sharePlaylist ->
        FastShare(
            showFastShare,
            onDismissRequest = { showFastShare = false},
            content = sharePlaylist
        )
    }

    val hasEpisodes = podcastPage?.listEpisode?.isNotEmpty() == true

    val podcastCoverUrl = podcastPage?.thumbnail?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
    val coverTint = rememberCoverTint(if (isLandscape) null else podcastCoverUrl ?: handoff?.url)

    // Add the episodes to one of the user's playlists (or import them as a new one).
    val openAddToPlaylist: () -> Unit = {
        menuState.display {
            PlaylistsItemMenu(
                navController = navController,
                modifier = Modifier.fillMaxHeight(0.4f),
                onDismiss = menuState::hide,
                onImportOnlinePlaylist = {
                    isImportingPlaylist = true
                },

                //NOT NECESSARY IN ONLINE PLAYLIST USE IMPORT
                onAddToPlaylist = { playlistPreview ->
                    position =
                        playlistPreview.songCount.minus(1)
                            ?: 0
                    if (position > 0) position++ else position =
                        0

                    if (!isYtSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                        podcastPage?.listEpisode?.forEachIndexed { index, song ->
                            runCatching {
                                Database.insert(song.asMediaItem)
                                Database.insert(
                                    SongPlaylistMap(
                                        songId = song.asMediaItem.mediaId,
                                        playlistId = playlistPreview.playlist.id,
                                        position = position + index
                                    ).default()
                                )
                            }.onFailure {
                                Timber.e("Failed onAddToPlaylist in PlaylistSongListModern  ${it.stackTraceToString()}")
                            }
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO).launch {
                            playlistPreview.playlist.browseId?.let { id ->
                                addToYtPlaylist(
                                    playlistPreview.playlist.id,
                                    position,
                                    id,
                                    podcastPage?.listEpisode?.map { it.asMediaItem }
                                        ?: emptyList())
                            }
                        }
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        SmartMessage(
                            context.resources.getString(
                                R.string.done
                            ),
                            type = PopupType.Success,
                            context = context
                        )
                    }
                },
                onGoToPlaylist = {
                    navController.navigate("${NavRoutes.localPlaylist.name}/$it")
                },
                disableScrollingText = disableScrollingText
            )
        }
    }

    // The top-right "...": what the action row has no room for.
    val openMoreMenu: () -> Unit = {
        menuState.display {
            Menu {
                MenuEntry(
                    icon = R.drawable.search_circle,
                    text = stringResource(R.string.noir_list_search_action),
                    onClick = {
                        menuState.hide()
                        searching = !searching
                    }
                )
                MenuEntry(
                    icon = R.drawable.enqueue,
                    text = stringResource(R.string.enqueue_all),
                    enabled = hasEpisodes,
                    onClick = {
                        menuState.hide()
                        podcastPage?.listEpisode?.map(Environment.Podcast.EpisodeItem::asMediaItem)
                            ?.let { mediaItems ->
                                binder?.player?.enqueue(mediaItems, context)
                            }
                    }
                )
                MenuEntry(
                    icon = R.drawable.radio,
                    text = stringResource(R.string.start_radio),
                    enabled = hasEpisodes,
                    onClick = {
                        menuState.hide()
                        if (binder != null) {
                            binder.stopRadio()
                            binder.playRadio(
                                NavigationEndpoint.Endpoint.Watch(
                                    videoId =
                                        if (binder.player.currentMediaItem?.mediaId != null)
                                            binder.player.currentMediaItem?.mediaId
                                        else podcastPage?.listEpisode?.first()?.asMediaItem?.mediaId
                                )
                            )
                        }
                    }
                )
                MenuEntry(
                    icon = R.drawable.add_in_playlist,
                    text = stringResource(R.string.add_to_playlist),
                    onClick = {
                        menuState.hide()
                        openAddToPlaylist()
                    }
                )
            }
        }
    }

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
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
                    //contentPadding = LocalPlayerAwareWindowInsets.current
                    //.only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize()
                ) {

                    item(
                        key = "header"
                    ) {
                        // "PODCAST · N episodios", skipping what is missing.
                        val podcastMeta = listOfNotNull(
                            stringResource(R.string.noir_kind_podcast),
                            podcastPage?.listEpisode?.size?.takeIf { it > 0 }
                                ?.let { stringResource(R.string.mediahdr_episodes_count, it) }
                        ).joinToString(" · ")

                        AlbumNoirHeader(
                            // Landscape shows the cover in the side panel: no cover here.
                            coverUrl = if (isLandscape) null else podcastCoverUrl,
                            previewUrl = if (isLandscape) null else handoff?.url,
                            sharedKey = if (isLandscape) null else handoff?.key,
                            showCover = !isLandscape,
                            title = podcastPage?.title ?: handoff?.title ?: "",
                            artist = podcastPage?.author ?: "",
                            meta = podcastMeta,
                            tint = coverTint,
                            onBack = { navController.popBackStack() },
                            onMore = if (podcastPage != null) openMoreMenu else null,
                            actions = {
                                // There is no podcast library to subscribe to: the left slot adds
                                // the episodes to one of the user's playlists.
                                AlbumSquareAction(
                                    icon = R.drawable.add_in_playlist,
                                    enabled = podcastPage != null,
                                    onClick = openAddToPlaylist,
                                    onLongClick = {
                                        SmartMessage(
                                            context.resources.getString(R.string.info_add_in_playlist),
                                            context = context
                                        )
                                    }
                                )

                                AlbumPlayAction(
                                    enabled = hasEpisodes,
                                    onClick = {
                                        binder?.stopRadio()
                                        podcastPage?.listEpisode
                                            ?.map(Environment.Podcast.EpisodeItem::asMediaItem)
                                            ?.let { binder?.player?.forcePlayFromBeginning(it) }
                                    }
                                )

                                AlbumSquareAction(
                                    icon = R.drawable.shuffle,
                                    enabled = hasEpisodes,
                                    onClick = {
                                        if (hasEpisodes) {
                                            binder?.stopRadio()
                                            podcastPage?.listEpisode?.shuffled()
                                                ?.map(Environment.Podcast.EpisodeItem::asMediaItem)
                                                ?.let {
                                                    binder?.player?.forcePlayFromBeginning(
                                                        it
                                                    )
                                                }
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
                                    enabled = podcastPage != null,
                                    onClick = { showFastShare = true }
                                )
                            }
                        )
                    }

                    // The in-list search ("..." > search in this list): a pill under the header.
                    item(
                        key = "actions",
                        contentType = 0
                    ) {
                        Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                        AnimatedVisibility(visible = searching) {
                            val focusRequester = remember { FocusRequester() }
                            val focusManager = LocalFocusManager.current
                            val keyboardController = LocalSoftwareKeyboardController.current

                            LaunchedEffect(searching) {
                                focusRequester.requestFocus()
                            }

                            NoirListSearchField(
                                value = filter ?: "",
                                onValueChange = { filter = it },
                                hint = stringResource(R.string.noir_list_search_hint),
                                onDone = {
                                    if (filter.isNullOrBlank()) filter = ""
                                    focusManager.clearFocus()
                                },
                                fieldModifier = Modifier
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (!it.hasFocus) {
                                            keyboardController?.hide()
                                            if (filter?.isBlank() == true) {
                                                filter = null
                                                searching = false
                                            }
                                        }
                                    }
                            )
                        }
                        }
                    }

                    podcastPage?.description?.takeIf { it.isNotBlank() }?.let { description ->
                        item(
                            key = "podcastInfo"
                        ) {
                            var translateEnabled by remember { mutableStateOf(false) }
                            val translator = remember { Translator(httpClient()) }
                            val languageDestination = languageDestination()

                            var translatedText by remember(description) { mutableStateOf(description) }

                            LaunchedEffect(translateEnabled, description) {
                                if (translateEnabled) {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            translator.translate(
                                                description,
                                                languageDestination,
                                                Language.AUTO
                                            ).translatedText
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            null
                                        }
                                    }
                                    translatedText = result?.takeIf { it.isNotBlank() } ?: description
                                } else translatedText = description
                            }

                            Box(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                            NoirDescriptionBlock(
                                eyebrow = stringResource(R.string.noir_podcast_about),
                                text = translatedText,
                                translated = translateEnabled,
                                translateLabel = stringResource(R.string.translate),
                                originalLabel = stringResource(R.string.noir_translate_original),
                                onToggleTranslate = { translateEnabled = !translateEnabled }
                            )
                            }
                        }
                    }

                    itemsIndexed(
                        items = podcastPage?.listEpisode?.filter { item ->
                            if (!filter.isNullOrBlank()) {
                                item.asMediaItem.mediaMetadata.title?.contains(
                                    filterCharSequence,
                                    true
                                ) ?: false
                                        || item.asMediaItem.mediaMetadata.artist?.contains(
                                    filterCharSequence,
                                    true
                                ) ?: false
                            } else true
                        } ?: emptyList())
                    { index, song ->

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
                            //var forceRecompose by remember { mutableStateOf(false) }
                            SongItem(
                                song = song.asMediaItem,
                                thumbnailSizePx = songThumbnailSizePx,
                                thumbnailSizeDp = songThumbnailSizeDp,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    navController = navController,
                                                    onDismiss = {
                                                        menuState.hide()
                                                        //forceRecompose = true
                                                    },
                                                    mediaItem = song.asMediaItem,
                                                    onInfo = {
                                                        navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.videoId}")
                                                    },
                                                    disableScrollingText = disableScrollingText,
                                                )
                                            };
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onClick = {
                                            searching = false
                                            filter = null
                                            podcastPage?.listEpisode?.map(Environment.Podcast.EpisodeItem::asMediaItem)
                                                ?.let { mediaItems ->
                                                    binder?.stopRadio()
                                                    // index is into the filtered list; find the episode in the full one.
                                                    binder?.player?.forcePlayAtIndex(
                                                        mediaItems,
                                                        mediaItems.indexOfFirst { it.mediaId == song.asMediaItem.mediaId }
                                                            .coerceAtLeast(0)
                                                    )
                                                }
                                        }
                                    ),
                                //disableScrollingText = disableScrollingText,
                                //isNowPlaying = binder?.player?.isNowPlaying(song.videoId) ?: false,
                                //forceRecompose = forceRecompose
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

                    if (podcastPage == null) {
                        item(key = "loading") {
                            ShimmerHost(
                                modifier = Modifier
                                    .fillParentMaxSize()
                            ) {
                                repeat(4) {
                                    SongItemPlaceholder(thumbnailSizeDp = songThumbnailSizeDp)
                                }
                            }
                        }
                    }
                }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( UiType.ViMusic.isCurrent() && showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                onClick = {
                    podcastPage?.listEpisode?.let { songs ->
                        if (songs.isNotEmpty()) {
                            binder?.stopRadio()
                            binder?.player?.forcePlayFromBeginning(
                                songs.shuffled().map(Environment.Podcast.EpisodeItem::asMediaItem)
                            )
                        }
                    }
                }
            )


        }
    }
}
