package it.fast4x.riplay.ui.screens.search

import androidx.compose.ui.unit.sp
import it.fast4x.riplay.ui.styling.mono
import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.environment.Environment
import it.fast4x.environment.models.Context
import it.fast4x.environment.models.bodies.BrowseBody
import it.fast4x.environment.models.bodies.ContinuationBody
import it.fast4x.environment.models.bodies.SearchBody
import it.fast4x.environment.requests.albumPage
import it.fast4x.environment.requests.searchPage
import it.fast4x.environment.utils.from
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import com.yambo.music.R
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.SongAlbumMap
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.enums.ContentType
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.riplay.extensions.preferences.parentalControlEnabledKey
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.ui.screens.searchresult.ItemsPage
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.SwipeableAlbumItem
import it.fast4x.riplay.ui.components.SwipeablePlaylistItem
import it.fast4x.riplay.ui.components.themed.Loader
import it.fast4x.riplay.ui.components.themed.Menu
import it.fast4x.riplay.ui.components.themed.MenuEntry
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.components.themed.NowPlayingSongIndicator
import it.fast4x.riplay.ui.components.themed.NoirResultRow
import it.fast4x.riplay.ui.components.themed.NoirResultRowPlaceholder
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.utils.openPlaylistOrMix
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.enqueue
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.isPersonalMix
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import it.fast4x.riplay.extensions.yammboapi.AppEvents

// Searches already reported as empty in this run of the app.
private val reportedEmptySearches = java.util.Collections.synchronizedSet(HashSet<String>())

@ExperimentalSerializationApi
@ExperimentalMaterialApi
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SearchResultsContent(
    query: String,
    tabIndex: Int,
    filterContentType: ContentType,
    onFilterChanged: (ContentType) -> Unit,
    navController: NavController,
    onSaveHistory: () -> Unit,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?
) {
    val binder = LocalPlayerServiceBinder.current
    val selectedQueue = LocalSelectedQueue.current
    val hapticFeedback = LocalHapticFeedback.current
    val menuState = LocalGlobalSheetState.current
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)
    val emptyItemsText = stringResource(R.string.no_results_found)
    val kindAlbum = stringResource(R.string.noir_kind_album)
    val kindArtist = stringResource(R.string.noir_kind_artist)
    val kindVideo = stringResource(R.string.noir_kind_video)
    val kindPlaylist = stringResource(R.string.noir_kind_playlist)
    val kindPodcast = stringResource(R.string.noir_kind_podcast)
    val context = LocalContext.current


    val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {

        if (tabIndex == 0) SearchTopResult(query = query, navController = navController)

        // The content-type filter (All / Official / User-generated) is only applied on the
        // Songs (0) and Videos (3) tabs — on Albums/Artists/Playlists/Featured/Podcasts it was
        // a no-op header. Only render it where it does something, with a localized title.
        if (tabIndex == 0 || tabIndex == 3) {
            // Clean row: title on the left, the active filter as a monochrome chip on the
            // right (outlined for All, inverted once a narrower filter is picked).
            val colors = colorPalette()
            val isFiltered = filterContentType != ContentType.All
            val chipShape = RoundedCornerShape(50)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.filter_content_type).uppercase(),
                    style = typography().xxxs.mono.copy(color = colors.textSecondary, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(12.dp))
                BasicText(
                    text = filterContentType.textName,
                    style = typography().xs.semiBold.copy(
                        color = if (isFiltered) colors.background0 else colors.text
                    ),
                    maxLines = 1,
                    modifier = Modifier
                        .clip(chipShape)
                        .background(if (isFiltered) colors.text else colors.background0)
                        .border(width = 1.dp, color = colors.background2, shape = chipShape)
                        .clickable {
                            menuState.display {
                                Menu {
                                    ContentType.entries.forEach {
                                        MenuEntry(
                                            icon = it.icon,
                                            text = it.textName,
                                            onClick = {
                                                onFilterChanged(it)
                                                menuState.hide()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }

    // Salva nella cronologia la prima volta che vengono renderizzati i risultati per questa query
    LaunchedEffect(query) {
        onSaveHistory()
    }

    // INCOLLA QUI IL TUO WHEN(TABINDEX) CON TUTTE LE ITEMSPAGE (0 -> Canzoni, 1 -> Album, ecc)
    // Ricorda di usare la variabile 'query' passata come parametro, non più letta da navBackStackEntry!
    when ( tabIndex ) {
        0 -> {
            val localBinder = LocalPlayerServiceBinder.current
            val menuState = LocalGlobalSheetState.current
            val thumbnailSizeDp = Dimensions.thumbnails.song
            val thumbnailSizePx = thumbnailSizeDp.px

            ItemsPage(
                tag = "searchResults/$query/songs",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        Environment.searchPage(
                            body = SearchBody(
                                query = query,
                                params = Environment.SearchFilter.Song.value
                            ),
                            fromMusicShelfRendererContent = Environment.SongItem.Companion::from
                        )
                    } else {
                        Environment.searchPage(
                            body = ContinuationBody(context = Context.DefaultWebLocalized, continuation = continuation),
                            fromMusicShelfRendererContent = Environment.SongItem.Companion::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                // Songs is the tab every search opens on, so its answer is what the user got.
                // Once per query and run: coming back to the same results must not count again.
                onEmpty = {
                    if (reportedEmptySearches.add(query.trim().lowercase()))
                        AppEvents.log(AppEvents.SEARCH_EMPTY, detail = query)
                },
                headerContent = headerContent,
                itemContent = { song ->
                    //Log.d("mediaItem",song.toString())
                    if (parentalControlEnabled && song.explicit)
                        return@ItemsPage

                    SwipeablePlaylistItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            localBinder?.player?.addNext(song.asMediaItem, queue = selectedQueue ?: defaultQueue())
                        },
                        onEnqueue = {
                            localBinder?.player?.enqueue(song.asMediaItem, queue = it)
                        }
                    ) {
                        //var forceRecompose by remember { mutableStateOf(false) }
                        SongItem(
                            song = song,
                            thumbnailContent = {
                                NowPlayingSongIndicator(song.asMediaItem.mediaId, binder?.player)
                            },
                            thumbnailSizePx = thumbnailSizePx,
                            thumbnailSizeDp = thumbnailSizeDp,
                            modifier = Modifier
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
                                                    navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.key}")
                                                },
                                                disableScrollingText = disableScrollingText,
                                            )
                                        };
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onClick = {
                                        localBinder?.stopRadio()
                                        localBinder?.player?.forcePlay(song.asMediaItem)
                                        //fastPlay(song.asMediaItem, localBinder)
                                        //forceRecompose = true
                                        localBinder?.setupRadio(song.info?.endpoint)
                                    }
                                ),
                            //disableScrollingText = disableScrollingText,
                            //isNowPlaying = binder?.player?.isNowPlaying(song.key) ?: false,
                            //forceRecompose = forceRecompose
                        )
                    }
                },
                itemPlaceholderContent = {
                    Loader()
                    //SongItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
                },
                filterContentType = filterContentType
            )
        }

        1 -> {
            ItemsPage(
                tag = "searchResults/$query/albums",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        Environment.searchPage(
                            body = SearchBody(
                                query = query,
                                params = Environment.SearchFilter.Album.value
                            ),
                            fromMusicShelfRendererContent = Environment.AlbumItem::from
                        )
                    } else {
                        Environment.searchPage(
                            body = ContinuationBody(context = Context.DefaultWebLocalized, continuation = continuation),
                            fromMusicShelfRendererContent = Environment.AlbumItem::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                headerContent = headerContent,
                itemContent = { album ->
                    var albumPage by persist<Environment.PlaylistOrAlbumPage?>("album/${album.key}/albumPage")
                    SwipeableAlbumItem(
                        albumItem = album,
                        onPlayNext = {
                            CoroutineScope(Dispatchers.IO).launch {
                                Database
                                    .album(album.key)
                                    .combine(snapshotFlow { tabIndex }) { album, tabIndex -> album to tabIndex }
                                    .collect {
                                        if (albumPage == null)
                                            withContext(Dispatchers.IO) {
                                                Environment.albumPage(
                                                    BrowseBody(
                                                        browseId = album.key
                                                    )
                                                )
                                                    ?.onSuccess { currentAlbumPage ->
                                                        albumPage =
                                                            currentAlbumPage

                                                        println("mediaItem success home album songsPage ${currentAlbumPage.songsPage} description ${currentAlbumPage.description} year ${currentAlbumPage.year}")

                                                        albumPage
                                                            ?.songsPage
                                                            ?.items
                                                            ?.map(
                                                                Environment.SongItem::asMediaItem
                                                            )
                                                            ?.let { it1 ->
                                                                withContext(Dispatchers.Main) {
                                                                    binder?.player?.addNext(
                                                                        it1,
                                                                        context,
                                                                        selectedQueue ?: defaultQueue()
                                                                    )
                                                                }
                                                            }
                                                        println("mediaItem success add in queue album songsPage ${albumPage
                                                            ?.songsPage
                                                            ?.items?.size}")

                                                    }
                                                    ?.onFailure {
                                                        println("mediaItem error searchResultScreen album ${it.stackTraceToString()}")
                                                    }

                                            }

                                        //}
                                    }

                            }

                        },
                        onEnqueue = {
                            CoroutineScope(Dispatchers.IO).launch {
                                Database
                                    .album(album.key)
                                    .combine(snapshotFlow { tabIndex }) { album, tabIndex -> album to tabIndex }
                                    .collect {
                                        if (albumPage == null)
                                            withContext(Dispatchers.IO) {
                                                Environment.albumPage(
                                                    BrowseBody(
                                                        browseId = album.key
                                                    )
                                                )
                                                    ?.onSuccess { currentAlbumPage ->
                                                        albumPage =
                                                            currentAlbumPage

                                                        println("mediaItem success home album songsPage ${currentAlbumPage.songsPage} description ${currentAlbumPage.description} year ${currentAlbumPage.year}")

                                                        albumPage
                                                            ?.songsPage
                                                            ?.items
                                                            ?.map(
                                                                Environment.SongItem::asMediaItem
                                                            )
                                                            ?.let { it1 ->
                                                                withContext(Dispatchers.Main) {
                                                                    binder?.player?.enqueue(
                                                                        it1,
                                                                        context
                                                                    )
                                                                }
                                                            }
                                                        println("mediaItem success add in queue album songsPage ${albumPage
                                                            ?.songsPage
                                                            ?.items?.size}")

                                                    }
                                                    ?.onFailure {
                                                        println("mediaItem error searchResultScreen album ${it.stackTraceToString()}")
                                                    }

                                            }

                                        //}
                                    }

                            }

                        },
                        onBookmark = {
                            CoroutineScope(Dispatchers.IO).launch {
                                Database
                                    .album(album.key)
                                    .combine(snapshotFlow { tabIndex }) { album, tabIndex -> album to tabIndex }
                                    .collect {
                                        if (albumPage == null)
                                            withContext(Dispatchers.IO) {
                                                Environment.albumPage(
                                                    BrowseBody(
                                                        browseId = album.key
                                                    )
                                                )
                                                    ?.onSuccess { currentAlbumPage ->
                                                        albumPage =
                                                            currentAlbumPage

                                                        println("mediaItem success home album songsPage ${currentAlbumPage.songsPage} description ${currentAlbumPage.description} year ${currentAlbumPage.year}")

                                                        Database.upsert(
                                                            Album(
                                                                id = album.key,
                                                                title = currentAlbumPage.title,
                                                                thumbnailUrl = currentAlbumPage.thumbnail?.url,
                                                                year = currentAlbumPage.year,
                                                                authorsText = currentAlbumPage.authors
                                                                    ?.joinToString(
                                                                        ""
                                                                    ) {
                                                                        it.name
                                                                            ?: ""
                                                                    },
                                                                shareUrl = currentAlbumPage.url,
                                                                timestamp = System.currentTimeMillis(),
                                                                bookmarkedAt = System.currentTimeMillis()
                                                            ),
                                                            currentAlbumPage
                                                                .songsPage
                                                                ?.items
                                                                ?.map(
                                                                    Environment.SongItem::asMediaItem
                                                                )
                                                                ?.onEach(
                                                                    Database::insert
                                                                )
                                                                ?.mapIndexed { position, mediaItem ->
                                                                    SongAlbumMap(
                                                                        songId = mediaItem.mediaId,
                                                                        albumId = album.key,
                                                                        position = position
                                                                    )
                                                                }
                                                                ?: emptyList()
                                                        )

                                                    }
                                                    ?.onFailure {
                                                        println("mediaItem error searchResultScreen album ${it.stackTraceToString()}")
                                                    }

                                            }
                                    }
                            }
                        }
                    ) {
                        NoirResultRow(
                            image = album.thumbnail?.url,
                            title = album.info?.name.orEmpty(),
                            subtitle = listOfNotNull(
                                kindAlbum,
                                album.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }
                                    ?.joinToString(", ")?.takeIf { it.isNotEmpty() },
                                album.year
                            ).joinToString(" · "),
                            imageWidth = 64.dp,
                            imageHeight = 64.dp,
                            imageModifier = Modifier.sharedCover("search-album:${album.key}", RoundedCornerShape(10.dp)),
                            modifier = Modifier.clickable {
                                CoverHandoff.set(
                                    key = "search-album:${album.key}",
                                    browseId = album.key,
                                    url = album.thumbnail?.url.thumbnail(320),
                                    title = album.info?.name
                                )
                                navController.navigate("${NavRoutes.album.name}/${album.key}")
                            }
                        )
                    }
                },
                itemPlaceholderContent = {
                    NoirResultRowPlaceholder(imageWidth = 64.dp, imageHeight = 64.dp)
                },
                filterContentType = filterContentType
            )
        }

        2 -> {
            ItemsPage(
                tag = "searchResults/$query/artists",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        Environment.searchPage(
                            body = SearchBody(
                                query = query,
                                params = Environment.SearchFilter.Artist.value
                            ),
                            fromMusicShelfRendererContent = Environment.ArtistItem::from
                        )
                    } else {
                        Environment.searchPage(
                            body = ContinuationBody(context = Context.DefaultWebLocalized, continuation = continuation),
                            fromMusicShelfRendererContent = Environment.ArtistItem::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                headerContent = headerContent,
                itemContent = { artist ->
                    NoirResultRow(
                        image = artist.thumbnail?.url,
                        title = artist.info?.name.orEmpty(),
                        subtitle = listOfNotNull(kindArtist, artist.subscribersCountText).joinToString(" · "),
                        imageShape = CircleShape,
                        imageWidth = 64.dp,
                        imageHeight = 64.dp,
                        imageModifier = Modifier.sharedCover("search-artist:${artist.key}", CircleShape),
                        modifier = Modifier.clickable {
                            CoverHandoff.set(
                                key = "search-artist:${artist.key}",
                                browseId = artist.key,
                                url = artist.thumbnail?.url.thumbnail(320),
                                title = artist.info?.name
                            )
                            navController.navigate("${NavRoutes.artist.name}/${artist.key}")
                        }
                    )
                },
                itemPlaceholderContent = {
                    NoirResultRowPlaceholder(imageShape = CircleShape, imageWidth = 64.dp, imageHeight = 64.dp)
                },
                filterContentType = filterContentType
            )
        }

        3 -> {
            val localBinder = LocalPlayerServiceBinder.current
            val menuState = LocalGlobalSheetState.current
            val thumbnailHeightDp = 63.dp
            val thumbnailWidthDp = 112.dp

            ItemsPage(
                tag = "searchResults/$query/videos",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        Environment.searchPage(
                            body = SearchBody(
                                query = query,
                                params = Environment.SearchFilter.Video.value
                            ),
                            fromMusicShelfRendererContent = Environment.VideoItem::from
                        )
                    } else {
                        Environment.searchPage(
                            body = ContinuationBody(context = Context.DefaultWebLocalized, continuation = continuation),
                            fromMusicShelfRendererContent = Environment.VideoItem::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                headerContent = headerContent,
                itemContent = { video ->
                    SwipeablePlaylistItem(
                        mediaItem = video.asMediaItem,
                        onPlayNext = {
                            localBinder?.player?.addNext(video.asMediaItem, queue = selectedQueue ?: defaultQueue())
                        },
                        onEnqueue = {
                            localBinder?.player?.enqueue(video.asMediaItem, queue = it)
                        }
                    ) {
                        NoirResultRow(
                            image = video.thumbnail?.url,
                            title = video.info?.name.orEmpty(),
                            subtitle = listOfNotNull(
                                kindVideo,
                                video.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }
                                    ?.joinToString(", ")?.takeIf { it.isNotEmpty() },
                                video.viewsText
                            ).joinToString(" · "),
                            imageWidth = thumbnailWidthDp,
                            imageHeight = thumbnailHeightDp,
                            badge = video.durationText,
                            trailingIcon = null,
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                navController = navController,
                                                onDismiss = menuState::hide,
                                                mediaItem = video.asMediaItem,
                                                onInfo = {
                                                    navController.navigate("${NavRoutes.videoOrSongInfo.name}/${video.key}")
                                                },
                                                disableScrollingText = disableScrollingText,
                                            )
                                        };
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onClick = {
                                        localBinder?.stopRadio()
//                                                        if (isVideoEnabled)
//                                                            localBinder?.player?.playOnline(video.asMediaItem)
//                                                        else
                                        localBinder?.player?.forcePlay(video.asMediaItem)
                                        //binder?.setupRadio(video.info?.endpoint)
                                        //fastPlay(video.asMediaItem, localBinder)
                                        localBinder?.setupRadio(video.info?.endpoint)
                                    }
                                )
                        )
                    }
                },
                itemPlaceholderContent = {
                    NoirResultRowPlaceholder(imageWidth = thumbnailWidthDp, imageHeight = thumbnailHeightDp)
                },
                filterContentType = filterContentType
            )
        }

        4, 5 -> {
            ItemsPage(
                tag = "searchResults/$query/${
                    when (tabIndex) {
                        4 -> "playlists"
                        else -> "featured"
                    }
                }",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        val filter = when (tabIndex) {
                            4 -> Environment.SearchFilter.CommunityPlaylist
                            else -> Environment.SearchFilter.FeaturedPlaylist
                        }

                        Environment.searchPage(
                            body = SearchBody(query = query, params = filter.value),
                            fromMusicShelfRendererContent = Environment.PlaylistItem::from
                        )
                    } else {
                        Environment.searchPage(
                            body = ContinuationBody(context = Context.DefaultWebLocalized, continuation = continuation),
                            fromMusicShelfRendererContent = Environment.PlaylistItem::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                headerContent = headerContent,
                itemContent = { playlist ->
                    NoirResultRow(
                        image = playlist.thumbnail?.url,
                        title = playlist.info?.name.orEmpty(),
                        subtitle = listOfNotNull(
                            kindPlaylist,
                            playlist.channel?.name?.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        imageWidth = 64.dp,
                        imageHeight = 64.dp,
                        imageModifier = Modifier.sharedCover("search-playlist:${playlist.key}", RoundedCornerShape(10.dp)),
                        modifier = Modifier.clickable {
                            // Personal mixes start playing instead of opening a page.
                            if (!isPersonalMix(playlist.key)) {
                                CoverHandoff.set(
                                    key = "search-playlist:${playlist.key}",
                                    browseId = playlist.key,
                                    url = playlist.thumbnail?.url.thumbnail(320),
                                    title = playlist.info?.name
                                )
                            }
                            openPlaylistOrMix(navController, binder, playlist.key)
                        }
                    )
                },
                itemPlaceholderContent = {
                    NoirResultRowPlaceholder(imageWidth = 64.dp, imageHeight = 64.dp)
                },
                filterContentType = filterContentType
            )
        }

        6 -> {
            ItemsPage(
                tag = "searchResults/$query/podcasts",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        val filter = Environment.SearchFilter.Podcast

                        Environment.searchPage(
                            body = SearchBody(query = query, params = filter.value),
                            fromMusicShelfRendererContent = Environment.PlaylistItem::from
                        )
                    } else {
                        Environment.searchPage(
                            body = ContinuationBody(context = Context.DefaultWebLocalized, continuation = continuation),
                            fromMusicShelfRendererContent = Environment.PlaylistItem::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                headerContent = headerContent,
                itemContent = { playlist ->
                    NoirResultRow(
                        image = playlist.thumbnail?.url,
                        title = playlist.info?.name.orEmpty(),
                        subtitle = listOfNotNull(
                            kindPodcast,
                            playlist.channel?.name?.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        imageWidth = 76.dp,
                        imageHeight = 76.dp,
                        imageShape = RoundedCornerShape(14.dp),
                        imageModifier = Modifier.sharedCover("search-podcast:${playlist.key}", RoundedCornerShape(14.dp)),
                        modifier = Modifier.clickable {
                            CoverHandoff.set(
                                key = "search-podcast:${playlist.key}",
                                browseId = playlist.key,
                                url = playlist.thumbnail?.url.thumbnail(320),
                                title = playlist.info?.name
                            )
                            navController.navigate("${NavRoutes.podcast.name}/${playlist.key}")
                        }
                    )
                },
                itemPlaceholderContent = {
                    NoirResultRowPlaceholder(imageWidth = 76.dp, imageHeight = 76.dp, imageShape = RoundedCornerShape(14.dp))
                },
                filterContentType = filterContentType
            )
        }
    }
}