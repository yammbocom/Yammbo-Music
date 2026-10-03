package it.fast4x.riplay.ui.screens.podcast

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.bold
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.typography

/**
 * Library of saved podcast shows (Playlist.isPodcast = 1) as a cover grid, followed by the
 * podcast episodes played most recently.
 *
 * Playback progress is not stored anywhere in the app, so the second section lists recent
 * episodes rather than "in progress" ones.
 */
@OptIn(UnstableApi::class)
@Composable
fun MyPodcastsTab(navController: NavController) {
    val binder = LocalPlayerServiceBinder.current

    // null = Room has not emitted yet (loading), as opposed to an empty library
    val saved: List<PlaylistPreview>? by Database.podcastPlaylists().collectAsState(initial = null)
    val recent: List<Song> by Database.lastPlayedPodcasts(10).collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette().background0)
    ) {
        item(key = "title") {
            PodcastSectionHeader(title = stringResource(R.string.podcasts_library_title))
        }

        when (val shows = saved) {
            null -> item(key = "loading") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    repeat(2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(2) {
                                PodcastSkeleton(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                )
                            }
                        }
                    }
                }
            }

            else -> if (shows.isEmpty()) {
                item(key = "empty") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .glassSurface(RoundedCornerShape(24.dp), elevation = 4.dp)
                            .padding(horizontal = 24.dp, vertical = 32.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.podcast),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                            modifier = Modifier.size(56.dp)
                        )
                        BasicText(
                            text = stringResource(R.string.podcasts_library_empty_title),
                            style = typography().s.bold.color(colorPalette().text)
                                .copy(textAlign = TextAlign.Center),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        BasicText(
                            text = stringResource(R.string.podcasts_library_empty_body),
                            style = typography().xs.secondary.copy(textAlign = TextAlign.Center),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                items(shows.chunked(2), key = { "row_${it.first().playlist.id}" }) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    ) {
                        row.forEach { preview ->
                            SavedPodcastCard(
                                preview = preview,
                                modifier = Modifier.weight(1f),
                                onClick = { openSavedPodcast(navController, preview.playlist) }
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item(key = "recentHeader") {
                PodcastSectionHeader(title = stringResource(R.string.podcasts_library_recent))
            }
            items(recent, key = { "ep_${it.id}" }) { song ->
                PodcastEpisodeRow(
                    song = song,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    onPlay = {
                        binder?.stopRadio()
                        binder?.player?.forcePlay(song.asMediaItem)
                    }
                )
            }
        }

        item(key = "footer") {
            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
        }
    }
}
