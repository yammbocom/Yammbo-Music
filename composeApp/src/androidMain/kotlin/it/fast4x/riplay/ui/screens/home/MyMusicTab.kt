package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.BuiltInPlaylist
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.preferences.builtInPlaylistKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.CollapsingTitleEffect
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.collapsingTitle
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.rememberTitleCollapse
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.NoirTextAction
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map


// Item count plus the first few covers of a library section, shown on its hub card.
private data class HubPreview(val count: Int, val covers: List<String>)

// Maps a Room flow to a HubPreview off the main thread. Null until the first emission, so the
// cards show nothing instead of flashing a wrong "0".
@Composable
private fun <T> rememberHubPreview(
    maxCovers: Int,
    source: () -> Flow<List<T>>,
    cover: (T) -> String?
): HubPreview? {
    val flow = remember {
        source()
            .map { list ->
                HubPreview(
                    count = list.size,
                    covers = list.asSequence()
                        .mapNotNull(cover)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(maxCovers)
                        .toList()
                )
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }
    val state: HubPreview? by flow.collectAsState(initial = null)
    return state
}

// Count of a flow, null until the first emission (no "0" flash).
@Composable
private fun rememberHubCount(source: () -> Flow<Int>): Int? {
    val flow = remember { source().distinctUntilChanged() }
    val state: Int? by flow.collectAsState(initial = null)
    return state
}

private const val RECENT_ITEMS = 12

@OptIn(UnstableApi::class)
@Composable
fun MyMusicTab(
    navController: NavController,
    onSongsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onRadioClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onPodcastsClick: () -> Unit = {}
) {
    val colors = colorPalette()
    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )
    var builtInPlaylist by rememberPreference(builtInPlaylistKey, BuiltInPlaylist.Favorites)

    val likedCount = rememberHubCount { Database.likedMusicCount() }
    val downloadsCount = rememberHubCount { Database.downloadedMediaIds().map { it.size } }
    val radiosCount = rememberHubCount { Database.favoriteRadios().map { it.size } }
    val podcastsCount = rememberHubCount { Database.podcastPlaylists().map { it.size } }
    val playlistsCount = rememberHubCount { Database.playlistsCount() }
    val artists = rememberHubPreview(
        maxCovers = 3,
        source = { Database.artistsByRowIdDesc() },
        cover = { it.thumbnailUrl }
    )
    val albums = rememberHubPreview(
        maxCovers = 2,
        source = { Database.albumsByRowIdDesc() },
        cover = { it.thumbnailUrl }
    )

    val binder = LocalPlayerServiceBinder.current
    val recentFlow = remember {
        // Wider window than shown: the home filter drops videos after the SQL one.
        Database.lastPlayedSongsOnly(RECENT_ITEMS * 4)
            .map { list -> list.filter { it.isHomeSong }.distinctBy { it.id }.take(RECENT_ITEMS) }
            .flowOn(Dispatchers.Default)
    }
    val recent by recentFlow.collectAsState(initial = emptyList())

    val noirLayout = rememberNoirLayout()
    val scrollState = rememberScrollState()
    val titleProgress = rememberTitleCollapse(scrollState)
    CollapsingTitleEffect(stringResource(R.string.my_music), titleProgress)

    Box(
        modifier = Modifier
            .background(colors.background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .noirContentWidth(NoirContentMax.List)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.my_music),
                    style = typography().xxl.tight.copy(
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).collapsingTitle(titleProgress)
                )
                Spacer(modifier = Modifier.width(12.dp))
                val addShape = RoundedCornerShape(14.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .pressable(onClick = onPlaylistsClick)
                        .clip(addShape)
                        .border(1.dp, colors.text.copy(alpha = 0.16f), addShape)
                ) {
                    Image(
                        painter = painterResource(R.drawable.add),
                        contentDescription = stringResource(R.string.create_new_playlist),
                        colorFilter = ColorFilter.tint(colors.text),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                AccountAvatarButton(navController = navController)
            }

            Column(
                modifier = Modifier
                    .noirContentWidth(NoirContentMax.List)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cards stagger in (40 ms each) on first composition; indexes are sequential.
                StaggeredEntry(index = 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LikedHeroCard(
                            count = likedCount,
                            onClick = {
                                builtInPlaylist = BuiltInPlaylist.Favorites
                                onSongsClick()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SmallStatCard(
                                iconId = R.drawable.download,
                                label = stringResource(R.string.noir_downloads),
                                count = downloadsCount,
                                onClick = onDeviceClick
                            )
                            // No count of every non-radio song exists in the DB, so none is shown.
                            SmallStatCard(
                                iconId = R.drawable.musical_notes,
                                label = stringResource(R.string.noir_all_songs),
                                count = null,
                                onClick = {
                                    builtInPlaylist = BuiltInPlaylist.All
                                    onSongsClick()
                                }
                            )
                        }
                    }
                }

                StaggeredEntry(index = 1) {
                    ArtistsCard(artists = artists, onClick = onArtistsClick)
                }

                StaggeredEntry(index = 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SmallStatCard(
                            iconId = R.drawable.album,
                            label = stringResource(R.string.albums),
                            count = albums?.count,
                            onClick = onAlbumsClick,
                            modifier = Modifier.weight(1f),
                            top = albums?.covers?.takeIf { it.isNotEmpty() }?.let { covers ->
                                { CoverStack(covers = covers, size = 36.dp, step = 42.dp, shape = RoundedCornerShape(8.dp), ring = 0.dp) }
                            }
                        )
                        SmallStatCard(
                            iconId = R.drawable.playlist,
                            label = stringResource(R.string.playlists),
                            count = playlistsCount,
                            onClick = onPlaylistsClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                StaggeredEntry(index = 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SmallStatCard(
                            iconId = R.drawable.radio,
                            label = stringResource(R.string.favorite_radios),
                            count = radiosCount,
                            onClick = onRadioClick,
                            modifier = Modifier.weight(1f)
                        )
                        SmallStatCard(
                            iconId = R.drawable.podcast,
                            label = stringResource(R.string.mymusic_podcasts),
                            count = podcastsCount,
                            onClick = onPodcastsClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (recent.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .noirContentWidth(NoirContentMax.List)
                        .padding(start = 16.dp, end = 10.dp, top = 22.dp, bottom = 10.dp)
                ) {
                    NoirEyebrow(
                        text = stringResource(R.string.noir_recently_played),
                        modifier = Modifier.weight(1f)
                    )
                    NoirTextAction(
                        text = stringResource(R.string.history).uppercase(),
                        onClick = { navController.navigate(NavRoutes.history.name) }
                    )
                }
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = noirLayout.sidePadding(NoirContentMax.List)
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recent, key = { it.id }) { song ->
                        RecentCover(song = song, shelfScale = noirLayout.shelfScale) {
                            // Same play logic as JumpBackInSection.
                            binder?.stopRadio()
                            binder?.player?.forcePlayAtIndex(
                                recent.map(Song::asMediaItem),
                                recent.indexOf(song)
                            )
                        }
                    }
                }
            }

            // Room for the mini player, which floats over the bottom of the page
            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
        }
    }
}

// Shared Noir card surface. pressable is outermost so the whole card scales on press, and the
// clip comes before the background so the fill follows the rounded corners.
@Composable
private fun NoirCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .pressable(onClick = onClick)
            .clip(shape)
            .background(if (solid) colors.text else colors.background1)
            .border(1.dp, colors.text.copy(alpha = if (solid) 0f else 0.08f), shape),
        content = content
    )
}

@Composable
private fun CountMono(count: Int, modifier: Modifier = Modifier) {
    BasicText(
        text = count.toString(),
        style = typography().xxs.mono.copy(color = colorPalette().textSecondary, fontSize = 13.sp),
        maxLines = 1,
        modifier = modifier
    )
}

// The big white card: the one solid action of the page.
@Composable
private fun LikedHeroCard(
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    NoirCard(
        onClick = onClick,
        solid = true,
        modifier = modifier.height(236.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.heart),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.background0),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
                .size(28.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            BasicText(
                text = stringResource(R.string.noir_liked_songs),
                style = typography().xl.tight.copy(
                    color = colors.background0,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (count != null) {
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = libraryCountText(R.plurals.mymusic_count_songs, count),
                    style = typography().xs.copy(color = colors.background0.copy(alpha = 0.6f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Half-width card: icon (or a custom [top] such as mini covers) on top, label and count below.
@Composable
private fun SmallStatCard(
    iconId: Int,
    label: String,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    top: (@Composable () -> Unit)? = null
) {
    val colors = colorPalette()
    NoirCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(112.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
        ) {
            if (top != null) top()
            else Image(
                painter = painterResource(iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
                modifier = Modifier.size(24.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            BasicText(
                text = label,
                style = typography().s.copy(
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (count != null) {
                Spacer(modifier = Modifier.width(8.dp))
                CountMono(count = count)
            }
        }
    }
}

@Composable
private fun ArtistsCard(
    artists: HubPreview?,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    NoirCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            val covers = artists?.covers.orEmpty()
            if (covers.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.background2)
                ) {
                    Image(
                        painter = painterResource(R.drawable.person),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.textDisabled),
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                // Each circle overlaps the previous one by 16 dp (56 dp wide, 40 dp step).
                CoverStack(covers = covers, size = 56.dp, step = 40.dp, shape = CircleShape)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = stringResource(R.string.artists),
                    style = typography().m.copy(
                        color = colors.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artists != null && artists.count > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = stringResource(R.string.noir_artists_following, artists.count),
                        style = typography().xs.copy(color = colors.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Image(
                painter = painterResource(R.drawable.chevron_forward),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.textSecondary),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecentCover(
    song: Song,
    shelfScale: Float = 1f,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .width(132.dp * shelfScale)
            .pressable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(132.dp * shelfScale)
                .clip(shape)
                .background(colors.background2)
        ) {
            if (!song.thumbnailUrl.isNullOrEmpty()) {
                RevealImage(
                    // The stored URL is often the 60-120px list size: ask the CDN for the card's.
                    model = remember(song.thumbnailUrl) { song.thumbnailUrl.thumbnail(480)?.let { cleanPrefix(it) } },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.musical_notes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDisabled),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        BasicText(
            text = song.cleanTitle(),
            style = typography().xs.copy(
                color = colors.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        song.artistsText?.takeIf { it.isNotEmpty() }?.let { artists ->
            BasicText(
                text = artists,
                style = typography().xxs.copy(color = colors.textSecondary, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Overlapping covers, the first one on top. The caller guarantees a non-empty list.
@Composable
private fun CoverStack(
    covers: List<String>,
    size: Dp,
    step: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    ring: Dp = 2.dp
) {
    val colors = colorPalette()
    Box(modifier = modifier.size(width = size + step * (covers.size - 1), height = size)) {
        for (i in covers.indices.reversed()) {
            Box(
                modifier = Modifier
                    .offset(x = step * i)
                    .size(size)
                    .clip(shape)
                    .background(colors.background1)
                    .padding(ring)
            ) {
                val model = remember(covers[i]) { covers[i].thumbnail(200)?.let { cleanPrefix(it) } }
                RevealImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(colors.background2)
                )
            }
        }
    }
}
