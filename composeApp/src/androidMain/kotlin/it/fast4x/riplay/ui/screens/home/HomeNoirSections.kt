package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.themed.NoirChip
import it.fast4x.riplay.ui.components.themed.NoirSectionHeader
import it.fast4x.riplay.ui.screens.search.ChartsCache
import it.fast4x.riplay.ui.screens.search.rememberChartsPage
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.isPersonalMix
import it.fast4x.riplay.utils.openPlaylistOrMix
import it.fast4x.riplay.utils.typography
import java.util.Locale

// Yammbo Noir Home: the sections that give the feed its rhythm between the shelves of square
// covers — a chip strip, big album covers, round artists, wide playlist cards and a ranked chart.

private val shelfPadding = PaddingValues(horizontal = 16.dp)

// The chart is a playlist of music videos: "(Official Music Video)", "[Video Oficial]"... are
// noise next to a song title.
private val videoTitleNoise =
    Regex("""\s*[(\[][^)\]]*(official|oficial|video|vídeo|visualizer|lyric|letra)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

/** YouTube's moods and categories as one strip of chips under the greeting. */
@Composable
fun HomeMoodChipStrip(chips: List<Environment.Chip>?, onChipClick: (Environment.Chip) -> Unit) {
    val usable = chips?.filter { it.endpoint?.browseId != null }
        ?.distinctBy { it.endpoint?.params ?: it.title }.orEmpty()
    if (usable.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = shelfPadding,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
    ) {
        items(usable, key = { it.endpoint?.params ?: it.title }) { chip ->
            NoirChip(text = chip.title, selected = false, onClick = { onChipClick(chip) })
        }
    }
}

/**
 * "Because you listened to <artist>": the albums YouTube relates to the seed song, as big covers.
 * Without a seed artist the header just says the albums are picked for the user.
 */
@Composable
fun HomeBecauseYouListened(
    seedArtist: String?,
    albums: List<Environment.AlbumItem>?,
    navController: NavController
) {
    val list = albums?.filter { it.key.isNotEmpty() }?.distinctBy { it.key }.orEmpty()
    if (list.isEmpty()) return
    val artist = seedArtist?.split(",", "&", " feat", " ft.")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    val shelfScale = rememberNoirLayout().shelfScale
    NoirSectionHeader(
        title = artist ?: stringResource(R.string.noir_albums_for_you),
        eyebrow = if (artist != null) stringResource(R.string.noir_because_you_listened) else null
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = shelfPadding) {
        items(list, key = { it.key }) { album ->
            val cover = album.thumbnail?.url.thumbnail(480)
            val sharedKey = "home-album:${album.key}"
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .width(164.dp * shelfScale)
                    .pressable(onClick = {
                        CoverHandoff.set(key = sharedKey, browseId = album.key, url = cover, title = album.info?.name)
                        navController.navigate("${NavRoutes.album.name}/${album.key}")
                    })
            ) {
                RevealImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(164.dp * shelfScale)
                        .sharedCover(sharedKey, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorPalette().background2)
                )
                Spacer(Modifier.height(5.dp))
                BasicText(
                    text = album.info?.name.orEmpty(),
                    style = typography().xs.copy(color = colorPalette().text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = listOfNotNull(
                        album.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")
                            ?.takeIf { it.isNotEmpty() },
                        album.year
                    ).joinToString(" · "),
                    style = typography().xxs.copy(color = colorPalette().textSecondary, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Artists related to what the user plays, as big circles. */
@Composable
fun HomeSimilarArtists(artists: List<Environment.ArtistItem>?, navController: NavController) {
    val list = artists?.filter { it.key.isNotEmpty() }?.distinctBy { it.key }.orEmpty()
    if (list.isEmpty()) return
    val shelfScale = rememberNoirLayout().shelfScale
    NoirSectionHeader(title = stringResource(R.string.noir_similar_artists))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = shelfPadding) {
        items(list, key = { it.key }) { artist ->
            val cover = artist.thumbnail?.url.thumbnail(360)
            val sharedKey = "home-artist:${artist.key}"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(116.dp * shelfScale)
                    .clip(RoundedCornerShape(16.dp))
                    .pressable(onClick = {
                        CoverHandoff.set(key = sharedKey, browseId = artist.key, url = cover, title = artist.info?.name)
                        navController.navigate("${NavRoutes.artist.name}/${artist.key}")
                    })
                    .padding(vertical = 4.dp)
            ) {
                RevealImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(108.dp * shelfScale)
                        .sharedCover(sharedKey, CircleShape)
                        .clip(CircleShape)
                        .background(colorPalette().background2)
                        .border(1.dp, colorPalette().text.copy(alpha = 0.12f), CircleShape)
                )
                BasicText(
                    text = artist.info?.name.orEmpty(),
                    style = typography().xs.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Playlists and mixes YouTube relates to the seed, as wide cards with the name over the cover. */
@UnstableApi
@Composable
fun HomePlaylistsForYou(playlists: List<Environment.PlaylistItem>?, navController: NavController) {
    val list = playlists?.filter { it.key.isNotEmpty() }?.distinctBy { it.key }.orEmpty()
    if (list.isEmpty()) return
    val binder = LocalPlayerServiceBinder.current
    val shelfScale = rememberNoirLayout().shelfScale
    NoirSectionHeader(title = stringResource(R.string.noir_playlists_for_you))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = shelfPadding) {
        items(list, key = { it.key }) { playlist ->
            val cover = playlist.thumbnail?.url.thumbnail(544)
            val sharedKey = "home-playlist:${playlist.key}"
            Box(
                modifier = Modifier
                    .size(width = 268.dp * shelfScale, height = 152.dp * shelfScale)
                    .sharedCover(sharedKey, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(colorPalette().background1)
                    .pressable(onClick = {
                        // Personal mixes start playing instead of opening a page.
                        if (!isPersonalMix(playlist.key)) {
                            CoverHandoff.set(key = sharedKey, browseId = playlist.key, url = cover, title = playlist.info?.name)
                        }
                        openPlaylistOrMix(navController, binder, playlist.key)
                    })
            ) {
                RevealImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.05f),
                                0.5f to Color.Black.copy(alpha = 0.3f),
                                1f to Color.Black.copy(alpha = 0.85f)
                            )
                        )
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    BasicText(
                        text = playlist.info?.name.orEmpty(),
                        style = typography().l.tight.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    playlist.channel?.name?.takeIf { it.isNotBlank() }?.let {
                        BasicText(
                            text = it,
                            style = typography().xxs.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * The country's chart as a ranked list, four rows per column, swiped sideways. Shares the
 * request (and its process cache) with the Search page's popular sections.
 */
@UnstableApi
@Composable
fun HomeTopCharts() {
    val page = rememberChartsPage() ?: return
    val songs = (page.songs?.takeIf { it.isNotEmpty() } ?: page.trending).orEmpty()
        .filter { it.key.isNotEmpty() }
        .distinctBy { it.key }
        .take(20)
    if (songs.size < 4) return
    val binder = LocalPlayerServiceBinder.current
    val shelfScale = rememberNoirLayout().shelfScale
    val country = remember(ChartsCache.countryCode) {
        ChartsCache.countryCode?.let { Locale("", it).displayCountry }?.takeIf { it.isNotBlank() }
    }
    NoirSectionHeader(
        title = stringResource(R.string.noir_top_charts),
        eyebrow = country?.let { stringResource(R.string.noir_top_charts_eyebrow, it) }
    )
    LazyHorizontalGrid(
        rows = GridCells.Fixed(4),
        contentPadding = shelfPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp * 4)
    ) {
        itemsIndexed(songs, key = { _, song -> song.key }) { index, song ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(300.dp * shelfScale)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .pressable(onClick = {
                        binder?.stopRadio()
                        binder?.player?.forcePlay(song.asMediaItem)
                        binder?.setupRadio(
                            endpoint = song.info?.endpoint ?: NavigationEndpoint.Endpoint.Watch(videoId = song.key),
                            swapSeed = true
                        )
                    })
            ) {
                BasicText(
                    text = (index + 1).toString(),
                    style = typography().l.mono.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    modifier = Modifier.width(30.dp)
                )
                Spacer(Modifier.width(8.dp))
                RevealImage(
                    model = song.thumbnail?.url.thumbnail(160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorPalette().background2)
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = song.info?.name.orEmpty().replace(videoTitleNoise, "").ifBlank { song.info?.name.orEmpty() },
                        style = typography().xs.copy(color = colorPalette().text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    BasicText(
                        text = song.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ").orEmpty(),
                        style = typography().xxs.copy(color = colorPalette().textSecondary, fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
