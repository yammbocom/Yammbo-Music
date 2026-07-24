package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.models.bodies.SearchBody
import it.fast4x.environment.requests.searchPage
import it.fast4x.environment.utils.from
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.showPodcastsKey
import it.fast4x.riplay.ui.components.themed.Title
import it.fast4x.riplay.ui.items.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared Home carousel that showcases podcasts. It is used by both the Classic
 * and the Extended Home renderers. When the user has already listened to
 * podcasts, the search is seeded with their last played podcast title
 * (recommended); otherwise it falls back to a generic seed (popular). The
 * carousel title adapts accordingly. Renders nothing when disabled or empty.
 */
@Composable
fun HomePodcastsSection(
    navController: NavController,
    thumbnailSizeDp: Dp,
    thumbnailSizePx: Int,
    disableScrollingText: Boolean,
    contentPadding: PaddingValues
) {
    val showPodcasts by rememberPreference(showPodcastsKey, true)
    if (!showPodcasts) return

    val recent by Database.lastPlayedPodcasts(1).collectAsState(initial = emptyList())

    val genericSeed = stringResource(R.string.podcasts_search_seed)
    val forYouTitle = stringResource(R.string.home_podcasts_for_you)
    val popularTitle = stringResource(R.string.home_podcasts_popular)

    val hasHistory = recent.isNotEmpty()
    val seed = if (hasHistory) recent.first().title else genericSeed
    val title = if (hasHistory) forYouTitle else popularTitle

    var podcasts by remember { mutableStateOf(emptyList<Environment.PlaylistItem>()) }

    LaunchedEffect(seed) {
        withContext(Dispatchers.IO) {
            Environment.searchPage(
                body = SearchBody(query = seed, params = Environment.SearchFilter.Podcast.value),
                fromMusicShelfRendererContent = Environment.PlaylistItem::from
            )
        }?.onSuccess { page ->
            podcasts = page?.items?.distinctBy { p -> p.key }?.take(15) ?: emptyList()
        }
    }

    // Do not paint an empty section
    if (podcasts.isEmpty()) return

    // No onClick: there is no "see all podcasts" screen yet, so we omit the
    // trailing arrow (Title only draws it when onClick != null).
    Title(title = title)

    LazyRow(contentPadding = contentPadding) {
        items(podcasts, key = { it.key }) { p ->
            PlaylistItem(
                playlist = p,
                thumbnailSizePx = thumbnailSizePx,
                thumbnailSizeDp = thumbnailSizeDp,
                alternative = true,
                showSongsCount = false,
                modifier = Modifier.clickable {
                    navController.navigate("${NavRoutes.podcast.name}/${p.key}")
                },
                disableScrollingText = disableScrollingText
            )
        }
    }
}
