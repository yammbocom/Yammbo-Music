package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map


// Item count plus the first few covers of a library section, shown on its hub card.
private data class HubPreview(val count: Int, val covers: List<String>)

// Maps a Room flow to a HubPreview off the main thread. Null until the first emission, so the
// cards show their static hint instead of flashing a wrong "0".
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

@Composable
fun MyMusicTab(
    onSongsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onRadioClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onPodcastsClick: () -> Unit = {}
) {
    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    val songs = rememberHubPreview(
        maxCovers = 4,
        source = { Database.songsFavoritesByLikedAtDesc() },
        cover = { it.song.thumbnailUrl }
    )
    val artists = rememberHubPreview(
        maxCovers = 3,
        source = { Database.artistsByRowIdDesc() },
        cover = { it.thumbnailUrl }
    )
    val albums = rememberHubPreview(
        maxCovers = 3,
        source = { Database.albumsByRowIdDesc() },
        cover = { it.thumbnailUrl }
    )
    val radios = rememberHubPreview(
        maxCovers = 0,
        source = { Database.favoriteRadios() },
        cover = { null }
    )
    val playlistsFlow = remember { Database.playlistsCount() }
    val playlistsCount: Int? by playlistsFlow.collectAsState(initial = null)

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
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
                .verticalScroll(rememberScrollState())
        ) {
            LibraryTitleBlock(
                title = stringResource(R.string.my_music),
                subtitle = stringResource(R.string.mymusic_hub_subtitle)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cards stagger in (40 ms each) on first composition; indexes are sequential.
                StaggeredEntry(index = 0) {
                    HubHeroCard(
                        iconId = R.drawable.musical_notes,
                        label = stringResource(R.string.local_songs),
                        subtitle = songs?.takeIf { it.count > 0 }?.let {
                            libraryCountText(R.plurals.mymusic_count_songs, it.count)
                        } ?: stringResource(R.string.my_music_hint_songs),
                        covers = songs?.covers.orEmpty(),
                        onClick = onSongsClick
                    )
                }

                // Right under Canciones: at the bottom of the list it sat behind the mini player.
                StaggeredEntry(index = 1) {
                    HubWideCard(
                        iconId = R.drawable.radio,
                        label = stringResource(R.string.favorite_radios),
                        subtitle = radios?.takeIf { it.count > 0 }?.let {
                            libraryCountText(R.plurals.mymusic_count_radios, it.count)
                        } ?: stringResource(R.string.favorite_radios_hint),
                        onClick = onRadioClick
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredEntry(index = 2) {
                            HubSquareCard(
                                iconId = R.drawable.person,
                                label = stringResource(R.string.artists),
                                subtitle = artists?.takeIf { it.count > 0 }?.let {
                                    libraryCountText(R.plurals.mymusic_count_artists, it.count)
                                } ?: stringResource(R.string.my_music_hint_artists),
                                covers = artists?.covers.orEmpty(),
                                coverShape = CircleShape,
                                onClick = onArtistsClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredEntry(index = 3) {
                            HubSquareCard(
                                iconId = R.drawable.album,
                                label = stringResource(R.string.albums),
                                subtitle = albums?.takeIf { it.count > 0 }?.let {
                                    libraryCountText(R.plurals.mymusic_count_albums, it.count)
                                } ?: stringResource(R.string.my_music_hint_albums),
                                covers = albums?.covers.orEmpty(),
                                coverShape = RoundedCornerShape(12.dp),
                                onClick = onAlbumsClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                StaggeredEntry(index = 4) {
                    HubWideCard(
                        iconId = R.drawable.library,
                        label = stringResource(R.string.playlists),
                        subtitle = playlistsCount?.takeIf { it > 0 }?.let {
                            libraryCountText(R.plurals.mymusic_count_playlists, it)
                        } ?: stringResource(R.string.my_music_hint_playlists),
                        onClick = onPlaylistsClick
                    )
                }

                StaggeredEntry(index = 5) {
                    HubWideCard(
                        iconId = R.drawable.podcast,
                        label = stringResource(R.string.mymusic_podcasts),
                        subtitle = stringResource(R.string.mymusic_podcasts_hint),
                        onClick = onPodcastsClick
                    )
                }

                StaggeredEntry(index = 6) {
                    OnDeviceBannerCard(onClick = onDeviceClick)
                }
            }

            // Room for the mini player, which floats over the bottom of the page
            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
        }
    }
}

// Shared glass surface for every hub card.
@Composable
private fun HubCard(
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .glassSurface(shape = shape, elevation = 6.dp)
            .pressable(onClick = onClick),
        content = content
    )
}

@Composable
private fun HubHeroCard(
    iconId: Int,
    label: String,
    subtitle: String,
    covers: List<String>,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    HubCard(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(172.dp)
    ) {
        if (covers.isEmpty()) {
            // No covers yet: oversized watermark icon keeps the card from looking empty
            Image(
                painter = painterResource(id = iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text.copy(alpha = 0.09f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(120.dp)
            )
        } else {
            CoverStack(
                covers = covers,
                size = 68.dp,
                step = 28.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp)
                .fillMaxWidth(0.5f),
            verticalArrangement = Arrangement.Center
        ) {
            CategoryBadge(iconId = iconId, badgeSize = 44.dp, iconSize = 22.dp)
            Spacer(modifier = Modifier.height(14.dp))
            BasicText(
                text = label,
                style = typography().l.semiBold.copy(color = colors.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typography().xs.copy(color = colors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HubSquareCard(
    iconId: Int,
    label: String,
    subtitle: String,
    covers: List<String>,
    coverShape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    HubCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.aspectRatio(1f)
    ) {
        if (covers.isEmpty()) {
            CategoryBadge(
                iconId = iconId,
                badgeSize = 44.dp,
                iconSize = 22.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        } else {
            CoverStack(
                covers = covers,
                size = 46.dp,
                step = 22.dp,
                shape = coverShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            BasicText(
                text = label,
                style = typography().m.semiBold.copy(color = colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typography().xs.copy(color = colors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HubWideCard(
    iconId: Int,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    HubCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryBadge(iconId = iconId, badgeSize = 52.dp, iconSize = 26.dp)
            Spacer(modifier = Modifier.size(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = label,
                    style = typography().m.semiBold.copy(color = colors.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = subtitle,
                    style = typography().xs.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    modifier: Modifier = Modifier
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
                    .padding(2.dp)
            ) {
                val model = remember(covers[i]) { covers[i].thumbnail(200)?.let { cleanPrefix(it) } }
                AsyncImage(
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

// Inverted badge: a text-colored circle with a background-colored icon.
@Composable
private fun CategoryBadge(
    iconId: Int,
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(colors.text),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.background0),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun OnDeviceBannerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()

    HubCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.text),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.download),
                    contentDescription = stringResource(R.string.on_device),
                    colorFilter = ColorFilter.tint(colors.background0),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = stringResource(R.string.my_music_on_device_title),
                    style = typography().m.semiBold.copy(color = colors.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = stringResource(R.string.my_music_on_device_subtitle),
                    style = typography().xs.copy(color = colors.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
