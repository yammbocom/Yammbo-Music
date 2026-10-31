package it.fast4x.riplay.ui.screens.statistics

import android.os.SystemClock
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.LayoutDirection
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.LocalPlayerAwareWindowInsets
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import com.yambo.music.R
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.enums.MaxStatisticsItems
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.StatisticsCategory
import it.fast4x.riplay.enums.StatisticsType
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.LocalScreenEnteredAt
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.themed.NoirChip
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.screens.album.rememberCoverTint
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.openPlaylistOrMix
import it.fast4x.riplay.extensions.preferences.maxStatisticsItemsKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.showStatsListeningTimeKey
import it.fast4x.riplay.extensions.preferences.statisticsCategoryKey
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.ui.components.SwipeablePlaylistItem
import kotlinx.coroutines.flow.distinctUntilChanged
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.utils.updateOnlineAlbum
import it.fast4x.riplay.utils.updateOnlineArtist
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalAnimationApi::class, ExperimentalTextApi::class, ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun StatisticsPage(
    navController: NavController,
    statisticsType: StatisticsType,
    onStatisticsTypeChange: (StatisticsType) -> Unit = {},
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalGlobalSheetState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val colors = colorPalette()

    val showStatsListeningTime by rememberPreference(showStatsListeningTimeKey, true)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)
    val navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.Bottom)

    val maxStatisticsItems by rememberPreference(maxStatisticsItemsKey, MaxStatisticsItems.`50`)
    var statisticsCategory by rememberPreference(statisticsCategoryKey, StatisticsCategory.Songs)

    var songs by persistList<Song>("statistics/songs")
    var allSongs by persistList<Song>("statistics/allsongs")
    var artists by persistList<Artist>("statistics/artists")
    var albums by persistList<Album>("statistics/albums")
    var playlists by persistList<PlaylistPreview>("statistics/playlists")

    val now = System.currentTimeMillis()
    val from = when (statisticsType) {
        StatisticsType.Today -> 1.days.inWholeMilliseconds
        StatisticsType.OneWeek -> 7.days.inWholeMilliseconds
        StatisticsType.OneMonth -> 30.days.inWholeMilliseconds
        StatisticsType.ThreeMonths -> 90.days.inWholeMilliseconds
        StatisticsType.SixMonths -> 180.days.inWholeMilliseconds
        StatisticsType.OneYear -> 365.days.inWholeMilliseconds
        StatisticsType.All -> 18250.days.inWholeMilliseconds // 50 years
    }

    val totalPlayTimes by remember(allSongs) {
        derivedStateOf { allSongs.sumOf { it.totalPlayTimeMs } }
    }

    LaunchedEffect(Unit, statisticsType, from) {
        if (showStatsListeningTime) {
            Database.songsMostPlayedByPeriod(from, now).collect { allSongs = it }
        }
    }
    LaunchedEffect(Unit, statisticsType, from, maxStatisticsItems) {
        Database.songsMostPlayedByPeriod(from, now, maxStatisticsItems.number).collect { songs = it }
    }
    LaunchedEffect(Unit, statisticsType, from, maxStatisticsItems) {
        Database.artistsMostPlayedByPeriod(from, now, maxStatisticsItems.number.toInt())
            .collect { artists = it }
    }
    LaunchedEffect(Unit, statisticsType, from, maxStatisticsItems) {
        Database.albumsMostPlayedByPeriod(from, now, maxStatisticsItems.number.toInt())
            .collect { albums = it }
    }
    LaunchedEffect(Unit, statisticsType, from, maxStatisticsItems) {
        Database.playlistsMostPlayedByPeriod(from, now, maxStatisticsItems.number.toInt())
            .collect { playlists = it }
    }

    val periods = listOf(
        StatisticsType.Today to R.string.stats_period_today,
        StatisticsType.OneWeek to R.string.stats_period_week,
        StatisticsType.OneMonth to R.string.stats_period_month,
        StatisticsType.ThreeMonths to R.string.stats_period_3m,
        StatisticsType.SixMonths to R.string.stats_period_6m,
        StatisticsType.OneYear to R.string.stats_period_year,
        StatisticsType.All to R.string.stats_period_all,
    )
    val categories = listOf(
        StatisticsCategory.Songs to stringResource(R.string.stats_cat_songs),
        StatisticsCategory.Artists to stringResource(R.string.stats_cat_artists),
        StatisticsCategory.Albums to stringResource(R.string.stats_cat_albums),
        StatisticsCategory.Playlists to stringResource(R.string.stats_cat_playlists),
    )

    val rangeText = when (statisticsType) {
        StatisticsType.Today -> stringResource(R.string.stats_range_today)
        StatisticsType.All -> stringResource(R.string.stats_range_all)
        else -> {
            val format = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
            "${format.format(Date(now - from))} - ${format.format(Date(now))}"
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(statisticsType) { listState.scrollToItem(0) }

    val localSelectedQueue = LocalSelectedQueue.current

    fun playSongs(index: Int) {
        binder?.stopRadio()
        binder?.player?.forcePlayAtIndex(songs.map(Song::asMediaItem), index)
    }

    fun songMenu(song: Song) {
        menuState.display {
            NonQueuedMediaItemMenu(
                navController = navController,
                onDismiss = { menuState.hide() },
                mediaItem = song.asMediaItem,
                onInfo = {
                    navController.navigate("${NavRoutes.videoOrSongInfo.name}/${song.id}")
                },
                disableScrollingText = disableScrollingText,
            )
        }
    }

    fun openArtist(artist: Artist) {
        if (artist.id.isNotEmpty()) {
            CoverHandoff.set("stats-artist:${artist.id}", artist.id, artist.thumbnailUrl, artist.name)
            navController.navigate("${NavRoutes.artist.name}/${artist.id}")
        }
    }

    fun openAlbum(album: Album) {
        if (album.id.isNotEmpty()) {
            CoverHandoff.set("stats-album:${album.id}", album.id, album.thumbnailUrl, album.title)
            navController.navigate("${NavRoutes.album.name}/${album.id}")
        }
    }

    fun openPlaylist(playlistPreview: PlaylistPreview) {
        val playlistId = playlistPreview.playlist.id.toString()
        if (playlistId.isEmpty()) return
        val pBrowseId = cleanPrefix(playlistPreview.playlist.browseId ?: "")
        if (pBrowseId.isNotEmpty()) {
            openPlaylistOrMix(navController, binder, pBrowseId)
        } else {
            navController.navigate("${NavRoutes.localPlaylist.name}/$playlistId")
        }
    }

    // Hero numbers: only those that can be read from the lists already loaded.
    val limit = maxStatisticsItems.number
    fun countText(n: Int) = if (n.toLong() >= limit) "$n+" else "$n"
    val minutesLabel = stringResource(R.string.stats_minutes_listened)
    val songsLabel = stringResource(R.string.stats_songs_played)
    val artistsLabel = stringResource(R.string.stats_artists_heard)
    val albumsLabel = stringResource(R.string.stats_albums_heard)
    val tiles = buildList {
        if (showStatsListeningTime && allSongs.isNotEmpty()) {
            add(String.format(Locale.getDefault(), "%,d", totalPlayTimes / 60_000L) to minutesLabel)
            add(String.format(Locale.getDefault(), "%,d", allSongs.size) to songsLabel)
        }
        if (artists.isNotEmpty()) add(countText(artists.size) to artistsLabel)
        if (albums.isNotEmpty()) add(countText(albums.size) to albumsLabel)
    }

    val categoryEmpty = when (statisticsCategory) {
        StatisticsCategory.Songs -> songs.isEmpty()
        StatisticsCategory.Artists -> artists.isEmpty()
        StatisticsCategory.Albums -> albums.isEmpty()
        StatisticsCategory.Playlists -> playlists.isEmpty()
    }

    // Replays the row cascade when the period or the category changes.
    val enteredAt = remember(statisticsType, statisticsCategory) { SystemClock.uptimeMillis() }

    Box(
        modifier = Modifier
            .background(colors.background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Right) Dimensions.contentWidthRightBar else 1f
            )
    ) {
        CompositionLocalProvider(LocalScreenEnteredAt provides enteredAt) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                contentPadding = run {
                    val side = rememberNoirLayout().sidePadding(NoirContentMax.List, 0.dp)
                    PaddingValues(
                        start = side,
                        end = side + windowInsets.only(WindowInsetsSides.End).asPaddingValues()
                            .calculateEndPadding(LayoutDirection.Ltr)
                    )
                }
            ) {

                item(key = "title") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
                    ) {
                        BasicText(
                            text = stringResource(R.string.stats_title),
                            style = typography().xxl.tight.copy(
                                color = colors.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        NoirEyebrow(rangeText)
                    }
                }

                item(key = "periods") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        periods.forEach { (type, label) ->
                            item(key = type.name) {
                                NoirChip(
                                    text = stringResource(label),
                                    selected = type == statisticsType,
                                    onClick = { onStatisticsTypeChange(type) }
                                )
                            }
                        }
                    }
                }

                if (tiles.isNotEmpty()) {
                    item(key = "hero") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 20.dp)
                        ) {
                            tiles.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pair.forEach { (value, label) -> StatsTile(value, label) }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item(key = "categories") {
                    StatsSegmented(
                        options = categories,
                        selected = statisticsCategory,
                        onSelect = { statisticsCategory = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                }

                if (categoryEmpty) {
                    item(key = "empty") {
                        BasicText(
                            text = stringResource(R.string.stats_empty),
                            style = typography().xs.copy(color = colors.textSecondary, fontSize = 14.sp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                when (statisticsCategory) {
                    StatisticsCategory.Songs -> {
                        songs.firstOrNull()?.let { top ->
                            item(key = "spotlight_song") {
                                StatsSpotlight(
                                    eyebrow = stringResource(R.string.stats_top_song),
                                    title = top.title,
                                    subtitle = top.artistsText,
                                    image = top.thumbnailUrl,
                                    tint = rememberCoverTint(top.thumbnailUrl),
                                    circle = false,
                                    onClick = { playSongs(0) },
                                    onLongClick = { songMenu(top) },
                                    modifier = Modifier.cascadeEntry(0)
                                )
                            }
                        }
                        itemsIndexed(songs.drop(1), key = { _, song -> "s_${song.id}" }) { i, song ->
                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(song.asMediaItem, queue = localSelectedQueue ?: defaultQueue())
                                }
                            ) {
                                StatsRankRow(
                                    rank = i + 2,
                                    image = song.thumbnailUrl,
                                    title = song.title,
                                    subtitle = song.artistsText,
                                    modifier = Modifier.combinedClickable(
                                        onLongClick = { songMenu(song) },
                                        onClick = { playSongs(i + 1) }
                                    )
                                )
                            }
                        }
                    }

                    StatisticsCategory.Artists -> {
                        artists.firstOrNull()?.let { top ->
                            item(key = "spotlight_artist") {
                                LaunchedEffect(top.id) {
                                    if (top.thumbnailUrl.isNullOrBlank() || top.thumbnailUrl == "null")
                                        updateOnlineArtist(top.id)
                                }
                                StatsSpotlight(
                                    eyebrow = stringResource(R.string.stats_top_artist),
                                    title = top.name.orEmpty(),
                                    subtitle = null,
                                    image = top.thumbnailUrl,
                                    tint = rememberCoverTint(top.thumbnailUrl),
                                    circle = true,
                                    onClick = { openArtist(top) },
                                    modifier = Modifier.cascadeEntry(0),
                                    imageModifier = Modifier.sharedCover("stats-artist:${top.id}", CircleShape)
                                )
                            }
                        }
                        itemsIndexed(artists.drop(1), key = { _, artist -> "a_${artist.id}" }) { i, artist ->
                            LaunchedEffect(artist.id) {
                                if (artist.thumbnailUrl.isNullOrBlank() || artist.thumbnailUrl == "null")
                                    updateOnlineArtist(artist.id)
                            }
                            StatsRankRow(
                                rank = i + 2,
                                image = artist.thumbnailUrl,
                                title = artist.name.orEmpty(),
                                subtitle = stringResource(R.string.stats_kind_artist),
                                circle = true,
                                imageModifier = Modifier.sharedCover("stats-artist:${artist.id}", CircleShape),
                                modifier = Modifier.combinedClickable(onClick = { openArtist(artist) })
                            )
                        }
                    }

                    StatisticsCategory.Albums -> {
                        albums.firstOrNull()?.let { top ->
                            item(key = "spotlight_album") {
                                LaunchedEffect(top.id) {
                                    if (top.thumbnailUrl.isNullOrBlank() || top.thumbnailUrl == "null")
                                        updateOnlineAlbum(top.id)
                                }
                                StatsSpotlight(
                                    eyebrow = stringResource(R.string.stats_top_album),
                                    title = top.title.orEmpty(),
                                    subtitle = listOfNotNull(top.authorsText, top.year)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    image = top.thumbnailUrl,
                                    tint = rememberCoverTint(top.thumbnailUrl),
                                    circle = false,
                                    onClick = { openAlbum(top) },
                                    modifier = Modifier.cascadeEntry(0),
                                    imageModifier = Modifier.sharedCover("stats-album:${top.id}", RoundedCornerShape(16.dp))
                                )
                            }
                        }
                        itemsIndexed(albums.drop(1), key = { _, album -> "b_${album.id}" }) { i, album ->
                            LaunchedEffect(album.id) {
                                if (album.thumbnailUrl.isNullOrBlank() || album.thumbnailUrl == "null")
                                    updateOnlineAlbum(album.id)
                            }
                            StatsRankRow(
                                rank = i + 2,
                                image = album.thumbnailUrl,
                                title = album.title.orEmpty(),
                                subtitle = listOfNotNull(album.authorsText, album.year)
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                imageModifier = Modifier.sharedCover("stats-album:${album.id}", RoundedCornerShape(10.dp)),
                                modifier = Modifier.combinedClickable(onClick = { openAlbum(album) })
                            )
                        }
                    }

                    StatisticsCategory.Playlists -> {
                        playlists.firstOrNull()?.let { top ->
                            item(key = "spotlight_playlist") {
                                val cover = playlistCoverUrl(top)
                                StatsSpotlight(
                                    eyebrow = stringResource(R.string.stats_top_playlist),
                                    title = top.playlist.name,
                                    subtitle = songCountText(top.songCount),
                                    image = cover,
                                    tint = rememberCoverTint(cover),
                                    circle = false,
                                    onClick = { openPlaylist(top) },
                                    modifier = Modifier.cascadeEntry(0)
                                )
                            }
                        }
                        itemsIndexed(playlists.drop(1), key = { _, p -> "p_${p.playlist.id}" }) { i, playlistPreview ->
                            StatsRankRow(
                                rank = i + 2,
                                image = playlistCoverUrl(playlistPreview),
                                title = playlistPreview.playlist.name,
                                subtitle = songCountText(playlistPreview.songCount),
                                modifier = Modifier.combinedClickable(onClick = { openPlaylist(playlistPreview) })
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }
        }
    }
}

/** The first cover of a playlist, from the songs it holds. */
@Composable
private fun playlistCoverUrl(playlistPreview: PlaylistPreview): String? {
    val thumbnails by remember(playlistPreview.playlist.id) {
        Database.playlistThumbnailUrls(playlistPreview.playlist.id).distinctUntilChanged()
    }.collectAsState(initial = emptyList(), context = Dispatchers.IO)
    return thumbnails.firstOrNull()
}

@Composable
private fun songCountText(count: Int): String =
    LocalContext.current.resources.getQuantityString(R.plurals.mymusic_count_songs, count, count)
