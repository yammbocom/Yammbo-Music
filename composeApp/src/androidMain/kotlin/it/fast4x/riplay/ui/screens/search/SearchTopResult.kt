package it.fast4x.riplay.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.environment.models.bodies.SearchSuggestionsBody
import it.fast4x.environment.requests.searchSuggestionsWithItems
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.NoirPlayCircle
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

// The card sits in the results list header, which is disposed when scrolled away: keep what
// each query answered so coming back doesn't ask again (or blink).
private val topResultCache = java.util.Collections.synchronizedMap(HashMap<String, Environment.SearchSuggestions?>())

private fun String.normalizedForMatch(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/**
 * "Top result" card over the song results. YouTube Music's own top result needs an unfiltered
 * search; the suggestions endpoint already names the best artist / song / album for the query,
 * so it is reused here: the artist when its name is what was typed, otherwise the song, otherwise
 * the album, otherwise nothing.
 */
@UnstableApi
@Composable
fun SearchTopResult(query: String, navController: NavController) {
    var suggestions by remember(query) { mutableStateOf(topResultCache[query]) }
    LaunchedEffect(query) {
        if (topResultCache.containsKey(query)) return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            Environment.searchSuggestionsWithItems(SearchSuggestionsBody(input = query))?.getOrNull()
        }
        // Failures are not cached, so the next visit tries again.
        if (result != null) topResultCache[query] = result
        suggestions = result
    }
    val result = suggestions ?: return

    val typed = query.normalizedForMatch()
    val artist = result.recommendedArtist?.takeIf { artist ->
        val name = artist.info?.name?.normalizedForMatch().orEmpty()
        typed.isNotEmpty() && name.isNotEmpty() && artist.key.isNotEmpty() &&
                (typed.contains(name) || name.contains(typed))
    }
    val binder = LocalPlayerServiceBinder.current

    when {
        artist != null -> TopResultCard(
            image = artist.thumbnail?.url,
            round = true,
            title = artist.info?.name.orEmpty(),
            subtitle = listOfNotNull(stringResource(R.string.noir_kind_artist), artist.subscribersCountText)
                .joinToString(" · "),
            sharedKey = "search-top:${artist.key}",
            onClick = {
                CoverHandoff.set(
                    key = "search-top:${artist.key}",
                    browseId = artist.key,
                    url = artist.thumbnail?.url.thumbnail(300),
                    title = artist.info?.name
                )
                navController.navigate("${NavRoutes.artist.name}/${artist.key}")
            }
        )

        result.recommendedSong != null -> {
            val song = result.recommendedSong!!
            val mediaItem = song.asMediaItem
            val play: () -> Unit = {
                binder?.stopRadio()
                binder?.player?.forcePlay(mediaItem)
                binder?.setupRadio(song.info?.endpoint ?: NavigationEndpoint.Endpoint.Watch(videoId = song.key))
                Unit
            }
            TopResultCard(
                image = song.thumbnail?.url,
                round = false,
                title = cleanPrefix(song.info?.name.orEmpty()),
                subtitle = listOfNotNull(
                    stringResource(R.string.noir_kind_song),
                    song.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")
                ).joinToString(" · "),
                onClick = play,
                onPlay = play
            )
        }

        result.recommendedAlbum?.key?.isNotEmpty() == true -> {
            val album = result.recommendedAlbum!!
            TopResultCard(
                image = album.thumbnail?.url,
                round = false,
                title = album.info?.name.orEmpty(),
                subtitle = listOfNotNull(
                    stringResource(R.string.noir_kind_album),
                    album.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", "),
                    album.year
                ).joinToString(" · "),
                sharedKey = "search-top:${album.key}",
                onClick = {
                    CoverHandoff.set(
                        key = "search-top:${album.key}",
                        browseId = album.key,
                        url = album.thumbnail?.url.thumbnail(300),
                        title = album.info?.name
                    )
                    navController.navigate("${NavRoutes.album.name}/${album.key}")
                }
            )
        }
    }
}

@Composable
private fun TopResultCard(
    image: String?,
    round: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    sharedKey: String? = null
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp)
    ) {
        NoirEyebrow(stringResource(R.string.noir_top_result))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colorPalette().background1)
                .border(1.dp, colorPalette().text.copy(alpha = 0.08f), shape)
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            val imageShape = if (round) CircleShape else RoundedCornerShape(12.dp)
            RevealImage(
                model = image.thumbnail(300),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(84.dp)
                    .sharedCover(sharedKey, imageShape)
                    .clip(imageShape)
                    .background(colorPalette().background2)
            )
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = title,
                    style = typography().xl.tight.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = subtitle,
                    style = typography().xs.copy(color = colorPalette().textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onPlay != null) {
                Spacer(Modifier.width(12.dp))
                NoirPlayCircle(onClick = onPlay)
            }
        }
    }
}
