package it.fast4x.riplay.ui.screens.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.requests.chartsPageComplete
import it.fast4x.riplay.enums.Countries
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.selectedCountryCodeKey
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How many entries each of the two discover sections shows. */
private const val POPULAR_ARTISTS = 4
private const val POPULAR_PLAYLISTS = 4

/**
 * Both sections come from the same YouTube chart for the user's country, so they share one
 * request. Held at process level: the search tab is opened often and the chart barely moves,
 * so re-fetching on every visit would be wasteful.
 */
private object ChartsCache {
    var page: Environment.ChartsPage? = null
    var countryCode: String? = null
}

/**
 * Fetches the chart once per country and hands it to both sections. Returns null while it is
 * in flight or if the request fails, and each section simply renders nothing — an empty
 * heading over a blank row looks broken.
 */
@Composable
private fun rememberChartsPage(): Environment.ChartsPage? {
    val selectedCountryCode by rememberPreference(selectedCountryCodeKey, Countries.ZZ)

    // ZZ means "Global", and the global YouTube Music chart is dominated by the highest
    // play-count markets — it comes back as Bollywood artists regardless of where the user
    // is. Nobody picks "Global" expecting that, so when no country has been chosen fall back
    // to the device's own region, which is what the user actually means by "popular".
    val effectiveCountry = remember(selectedCountryCode) {
        if (selectedCountryCode == Countries.ZZ) {
            java.util.Locale.getDefault().country.takeIf { it.isNotBlank() } ?: "US"
        } else {
            selectedCountryCode.name
        }
    }

    var page by remember { mutableStateOf(ChartsCache.page) }

    LaunchedEffect(effectiveCountry) {
        if (ChartsCache.page != null && ChartsCache.countryCode == effectiveCountry) {
            page = ChartsCache.page
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            Environment.chartsPageComplete(countryCode = effectiveCountry).getOrNull()
        }
        println(
            "Innertube charts country=$effectiveCountry artists=${result?.artists?.size} " +
                    "playlists=${result?.playlists?.size} songs=${result?.songs?.size}"
        )
        if (result != null) {
            ChartsCache.page = result
            ChartsCache.countryCode = effectiveCountry
            page = result
        }
    }

    return page
}

/** Ranked artists from the real YouTube chart, not the user's own listening history. */
@UnstableApi
@Composable
fun PopularArtistsRow(navController: NavController) {
    val colors = colorPalette()
    val artists = rememberChartsPage()?.artists?.take(POPULAR_ARTISTS).orEmpty()

    if (artists.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        SearchSectionTitle(stringResource(R.string.search_popular_artists))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            artists.forEachIndexed { index, artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .pressable(onClick = {
                            if (artist.key.isNotEmpty())
                                navController.navigate("${NavRoutes.artist.name}/${artist.key}")
                        })
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(colors.background2)
                            // The reference rings each avatar in green; the accent is the
                            // brand's only "colour", so it carries the same emphasis here.
                            .border(width = 2.dp, color = colors.accent, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val url = artist.thumbnail?.url
                        if (!url.isNullOrEmpty()) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.person),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colors.textDisabled),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    BasicText(
                        text = artist.title.orEmpty(),
                        style = typography().xxs.semiBold.copy(
                            color = colors.text,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = stringResource(R.string.search_top_rank, index + 1),
                        style = typography().xxs.secondary.copy(textAlign = TextAlign.Center),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Playlists from the same chart, as a 2x2 wall of covers. */
@UnstableApi
@Composable
fun PopularPlaylistsGrid(navController: NavController) {
    val playlists = rememberChartsPage()?.playlists?.take(POPULAR_PLAYLISTS).orEmpty()

    if (playlists.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        SearchSectionTitle(stringResource(R.string.search_popular_playlists))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            playlists.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { playlist ->
                        PlaylistCard(
                            title = playlist.title.orEmpty(),
                            subtitle = playlist.channel?.name.orEmpty(),
                            thumbnailUrl = playlist.thumbnail?.url,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (playlist.key.isNotEmpty())
                                navController.navigate("${NavRoutes.playlist.name}/${playlist.key}")
                        }
                    }
                    // Keep a lone last card at half width instead of stretching it.
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.background2)
            .pressable(onClick = onClick)
    ) {
        if (!thumbnailUrl.isNullOrEmpty()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Scrim only over the lower half, where the text sits.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            BasicText(
                text = title,
                style = typography().xs.semiBold.copy(color = Color.White),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = subtitle,
                    style = typography().xxs.copy(
                        color = Color.White.copy(alpha = 0.85f),
                        fontStyle = FontStyle.Italic
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    BasicText(
        text = title,
        style = typography().m.semiBold.copy(color = colorPalette().text),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 12.dp)
    )
}
