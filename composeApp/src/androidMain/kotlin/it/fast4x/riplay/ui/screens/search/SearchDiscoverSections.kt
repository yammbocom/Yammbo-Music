package it.fast4x.riplay.ui.screens.search

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.styling.tight
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
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.requests.chartsPageComplete
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.enums.Countries
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.selectedCountryCodeKey
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.isPersonalMix
import it.fast4x.riplay.utils.openPlaylistOrMix
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
internal object ChartsCache {
    var page: Environment.ChartsPage? = null
    var countryCode: String? = null
}

/**
 * Fetches the chart once per country and hands it to both sections. Returns null while it is
 * in flight or if the request fails, and each section simply renders nothing — an empty
 * heading over a blank row looks broken.
 */
@Composable
internal fun rememberChartsPage(): Environment.ChartsPage? {
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
    val layout = rememberNoirLayout()
    // 4 on phones (as before), up to 8 on tablets.
    val artistCount = layout.columns(minItem = 96.dp, spacing = 14.dp, min = POPULAR_ARTISTS, max = 8)
    val artists = rememberChartsPage()?.artists?.take(artistCount).orEmpty()

    if (artists.isEmpty()) return

    Column(modifier = Modifier.noirContentWidth(NoirContentMax.Grid)) {
        SearchSectionTitle(stringResource(R.string.search_popular_artists))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            artists.forEachIndexed { index, artist ->
                val cover = artist.thumbnail?.url.thumbnail(240)
                val sharedKey = "search-popular-artist:${artist.key}"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .pressable(onClick = {
                            if (artist.key.isNotEmpty()) {
                                CoverHandoff.set(key = sharedKey, browseId = artist.key, url = cover, title = artist.info?.name)
                                navController.navigate("${NavRoutes.artist.name}/${artist.key}")
                            }
                        })
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp * layout.shelfScale)
                            .sharedCover(sharedKey, CircleShape)
                            .clip(CircleShape)
                            .background(colors.background2)
                            // The reference rings each avatar in green; the accent is the
                            // brand's only "colour", so it carries the same emphasis here.
                            .border(width = 2.dp, color = colors.accent, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val url = artist.thumbnail?.url
                        if (!url.isNullOrEmpty()) {
                            RevealImage(
                                model = cover,
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
            repeat(artistCount - artists.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

/** Playlists from the same chart, as a 2x2 wall of covers. */
@UnstableApi
@Composable
fun PopularPlaylistsGrid(navController: NavController) {
    val binder = LocalPlayerServiceBinder.current
    // 2 columns x 2 rows on phones (as before); more columns, still 2 rows, on tablets.
    val cols = rememberNoirLayout().columns(minItem = 170.dp, max = 4)
    val playlists = rememberChartsPage()?.playlists
        ?.take(if (cols > 2) cols * 2 else POPULAR_PLAYLISTS).orEmpty()

    if (playlists.isEmpty()) return

    Column(modifier = Modifier.noirContentWidth(NoirContentMax.Grid)) {
        SearchSectionTitle(stringResource(R.string.search_popular_playlists))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            playlists.chunked(cols).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { playlist ->
                        PlaylistCard(
                            title = playlist.title.orEmpty(),
                            subtitle = playlist.channel?.name.orEmpty(),
                            thumbnailUrl = playlist.thumbnail?.url,
                            sharedKey = "search-popular-playlist:${playlist.key}",
                            modifier = Modifier.weight(1f)
                        ) {
                            if (playlist.key.isNotEmpty()) {
                                // Personal mixes start playing instead of opening a page.
                                if (!isPersonalMix(playlist.key)) {
                                    CoverHandoff.set(
                                        key = "search-popular-playlist:${playlist.key}",
                                        browseId = playlist.key,
                                        url = playlist.thumbnail?.url.thumbnail(544),
                                        title = playlist.title
                                    )
                                }
                                openPlaylistOrMix(navController, binder, playlist.key)
                            }
                        }
                    }
                    // Keep the last cards at cell width instead of stretching them.
                    repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
    sharedKey: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .sharedCover(sharedKey, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(colors.background2)
            .pressable(onClick = onClick)
    ) {
        if (!thumbnailUrl.isNullOrEmpty()) {
            RevealImage(
                model = thumbnailUrl.thumbnail(544),
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
        style = typography().xl.tight.copy(color = colorPalette().text, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 22.dp, bottom = 12.dp)
    )
}
