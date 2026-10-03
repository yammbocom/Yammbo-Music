package it.fast4x.riplay.ui.screens.podcast

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.models.bodies.SearchBody
import it.fast4x.environment.requests.searchPage
import it.fast4x.environment.utils.from
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.bold
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.resizeNoCrop
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

// ---------------------------------------------------------------------------------------------
// Data + loading
// ---------------------------------------------------------------------------------------------

/** A podcast show as listed by the search; [key] is the browse id of its episode list. */
internal data class PodcastShow(
    val key: String,
    val title: String,
    val author: String?,
    val cover: String?,
)

internal sealed interface PodcastLoad {
    data object Loading : PodcastLoad
    data object Failed : PodcastLoad
    data class Done(val shows: List<PodcastShow>) : PodcastLoad
}

private const val SEARCH_TIMEOUT_MS = 12_000L
private const val FEATURED_COUNT = 5

/**
 * Per-query results kept for the process lifetime, so flipping between category chips (or
 * leaving and re-entering the tab, which destroys its composition) is instant.
 */
private object PodcastSearchCache {
    private val map = HashMap<String, List<PodcastShow>>()

    @Synchronized
    fun get(query: String): List<PodcastShow>? = map[query]

    @Synchronized
    fun put(query: String, shows: List<PodcastShow>) {
        map[query] = shows
    }
}

/** Returns null when the request failed or timed out; an empty list means "no results". */
private suspend fun fetchPodcastShows(query: String): List<PodcastShow>? {
    val result = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            Environment.searchPage(
                body = SearchBody(query = query, params = Environment.SearchFilter.Podcast.value),
                fromMusicShelfRendererContent = Environment.PlaylistItem::from
            )
        }
    } ?: return null
    if (result.isFailure) return null

    // Only "MPSP" ids open into an episode list. The search also returns channels and single
    // episodes, whose ids browse into a page with no episodes at all.
    return result.getOrNull()?.items.orEmpty()
        .filter { it.key.startsWith("MPSP") }
        .distinctBy { it.key }
        .take(30)
        .map { p ->
            PodcastShow(
                key = p.key,
                title = p.title.orEmpty(),
                author = p.channel?.name,
                cover = p.thumbnail?.url
            )
        }
}

/** Loads [query] (null = do nothing); [retryTick] re-runs it after a failure. */
@Composable
internal fun rememberPodcastLoad(query: String?, retryTick: Int): PodcastLoad {
    var state by remember(query) {
        mutableStateOf<PodcastLoad>(
            query?.let(PodcastSearchCache::get)?.let { PodcastLoad.Done(it) } ?: PodcastLoad.Loading
        )
    }
    LaunchedEffect(query, retryTick) {
        if (query == null) return@LaunchedEffect
        PodcastSearchCache.get(query)?.let {
            state = PodcastLoad.Done(it)
            return@LaunchedEffect
        }
        state = PodcastLoad.Loading
        val shows = fetchPodcastShows(query)
        state = if (shows == null) PodcastLoad.Failed else PodcastLoad.Done(shows)
        // An empty page may be transient, so only real results are remembered.
        if (!shows.isNullOrEmpty()) PodcastSearchCache.put(query, shows)
    }
    return state
}

private enum class PodcastCategory(@StringRes val label: Int) {
    Popular(R.string.podcasts_cat_popular),
    News(R.string.podcasts_cat_news),
    Comedy(R.string.podcasts_cat_comedy),
    TrueCrime(R.string.podcasts_cat_truecrime),
    History(R.string.podcasts_cat_history),
    Tech(R.string.podcasts_cat_tech),
    Health(R.string.podcasts_cat_health),
    Business(R.string.podcasts_cat_business),
    Sports(R.string.podcasts_cat_sports),
    Education(R.string.podcasts_cat_education),
    Science(R.string.podcasts_cat_science),
    Music(R.string.podcasts_cat_music),
    Mystery(R.string.podcasts_cat_mystery),
}

// ---------------------------------------------------------------------------------------------
// Shared building blocks (also used by MyPodcastsTab)
// ---------------------------------------------------------------------------------------------

/** Pulsing placeholder with the final size of the content it stands in for. */
@Composable
internal fun PodcastSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    val transition = rememberInfiniteTransition(label = "podcastSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "podcastSkeletonAlpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(colorPalette().background2.copy(alpha = alpha))
    )
}

@Composable
internal fun PodcastCover(
    url: String?,
    modifier: Modifier = Modifier,
    sizePx: Int = 512,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(colorPalette().background2)
    ) {
        if (url.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.podcast),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                modifier = Modifier.fillMaxSize(0.4f)
            )
        } else {
            // Whole cover, no server-side smart crop
            AsyncImage(
                model = url.resizeNoCrop(sizePx, sizePx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Square cover with title and a secondary line; the grid unit of every podcast list. */
@Composable
internal fun PodcastShowCard(
    title: String,
    subtitle: String?,
    cover: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        PodcastCover(
            url = cover,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        BasicText(
            text = title,
            style = typography().xs.semiBold.color(colorPalette().text),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            BasicText(
                text = subtitle,
                style = typography().xxs.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** A saved show: its cover is the first cover among the episodes stored with it. */
@Composable
internal fun SavedPodcastCard(
    preview: PlaylistPreview,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val thumbs by Database.playlistThumbnailUrls(preview.playlist.id).collectAsState(initial = emptyList())
    PodcastShowCard(
        title = preview.playlist.name,
        subtitle = stringResource(R.string.podcasts_episodes_count, preview.songCount),
        cover = thumbs.firstOrNull { !it.isNullOrBlank() },
        modifier = modifier,
        onClick = onClick
    )
}

/** Opens the online podcast page when the show kept its browse id, else the local copy. */
internal fun openSavedPodcast(navController: NavController, playlist: Playlist) {
    val browseId = playlist.browseId
    if (!browseId.isNullOrBlank()) {
        navController.navigate("${NavRoutes.podcast.name}/$browseId")
    } else {
        navController.navigate("${NavRoutes.localPlaylist.name}/${playlist.id}")
    }
}

/** Compact episode card: tap anywhere to play. */
@Composable
internal fun PodcastEpisodeRow(
    song: Song,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .glassSurface(RoundedCornerShape(18.dp), elevation = 4.dp)
            .clickable(onClick = onPlay)
            .padding(10.dp)
    ) {
        PodcastCover(
            url = song.thumbnailUrl,
            modifier = Modifier.size(64.dp),
            sizePx = 256,
            shape = RoundedCornerShape(12.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            BasicText(
                text = song.title,
                style = typography().xs.semiBold.color(colorPalette().text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = listOfNotNull(song.artistsText, song.durationText)
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  "),
                style = typography().xxs.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colorPalette().text)
        ) {
            Image(
                painter = painterResource(R.drawable.play),
                contentDescription = stringResource(R.string.podcasts_play),
                colorFilter = ColorFilter.tint(colorPalette().background0),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
internal fun PodcastSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
    ) {
        BasicText(
            text = title,
            style = typography().m.bold.color(colorPalette().text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            BasicText(
                text = actionLabel,
                style = typography().xs.semiBold.color(colorPalette().textSecondary),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Glass pill; the selected one is inverted (brand rule: emphasis = inversion). */
@Composable
internal fun PodcastPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val base = if (selected) {
        modifier.clip(shape).background(colorPalette().text)
    } else {
        modifier.glassSurface(shape, elevation = 0.dp)
    }
    BasicText(
        text = text,
        style = typography().xs.semiBold.color(
            if (selected) colorPalette().background0 else colorPalette().text
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = base
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    )
}

@Composable
private fun PodcastSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(colorPalette().background2)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().textSecondary),
            modifier = Modifier.size(18.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
        ) {
            if (value.isEmpty()) {
                BasicText(text = hint, style = typography().xs.secondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = typography().xs.color(colorPalette().text),
                singleLine = true,
                cursorBrush = SolidColor(colorPalette().text),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Image(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().textSecondary),
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Featured carousel
// ---------------------------------------------------------------------------------------------

private val FeaturedHeight = 176.dp

@Composable
private fun FeaturedCarousel(
    shows: List<PodcastShow>,
    onClick: (PodcastShow) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { shows.size })
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        pageSpacing = 12.dp,
        key = { shows[it].key }
    ) { page ->
        FeaturedCard(show = shows[page], onClick = { onClick(shows[page]) })
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        repeat(shows.size) { index ->
            val selected = index == pagerState.currentPage
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(if (selected) 18.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (selected) colorPalette().text else colorPalette().textDisabled)
            )
        }
    }
}

@Composable
private fun FeaturedCard(show: PodcastShow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FeaturedHeight)
            .glassSurface(RoundedCornerShape(24.dp), elevation = 8.dp)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        PodcastCover(
            url = show.cover,
            modifier = Modifier.size(FeaturedHeight - 28.dp),
            sizePx = 512,
            shape = RoundedCornerShape(16.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(start = 14.dp)
        ) {
            BasicText(
                text = stringResource(R.string.podcasts_featured_label).uppercase(),
                style = typography().xxs.semiBold.color(colorPalette().textSecondary)
            )
            BasicText(
                text = show.title,
                style = typography().m.bold.color(colorPalette().text),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!show.author.isNullOrBlank()) {
                BasicText(
                    text = show.author,
                    style = typography().xs.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            BasicText(
                text = stringResource(R.string.podcasts_open_show),
                style = typography().xs.semiBold.color(colorPalette().background0),
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(colorPalette().text)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------------

@OptIn(UnstableApi::class)
@Composable
fun PodcastsDiscover(
    navController: NavController,
    modifier: Modifier = Modifier,
    // When set, "Ver todo" in "Mis podcasts" calls this instead of expanding the row inline.
    onSeeAllMyPodcasts: (() -> Unit)? = null,
) {
    val binder = LocalPlayerServiceBinder.current

    var searchInput by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var categoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var myExpanded by rememberSaveable { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }

    // Debounced search: querying on every keystroke hammers the backend for nothing.
    LaunchedEffect(searchInput) {
        delay(400)
        val trimmed = searchInput.trim()
        search = if (trimmed.length >= 2) trimmed else ""
    }
    val isSearching = search.isNotEmpty()

    val seed = stringResource(R.string.podcasts_search_seed)
    val categories = PodcastCategory.entries
    val categoryLabels = categories.map { stringResource(it.label) }
    val category = categories.getOrElse(categoryIndex) { PodcastCategory.Popular }

    val popularQuery = seed
    val categoryQuery = if (category == PodcastCategory.Popular) popularQuery
    else "$seed ${categoryLabels[categories.indexOf(category)]}"
    val gridQuery = if (isSearching) search else categoryQuery

    // The featured carousel always shows the popular shows. When the grid is on the same
    // query it reuses that load, so the request is made once.
    val featuredLoad = rememberPodcastLoad(popularQuery, retryTick)
    val otherLoad = rememberPodcastLoad(gridQuery.takeIf { it != popularQuery }, retryTick)
    val gridLoad = if (gridQuery == popularQuery) featuredLoad else otherLoad

    val recent by Database.lastPlayedPodcasts(12).collectAsState(initial = emptyList())
    val saved by Database.podcastPlaylists().collectAsState(initial = emptyList())

    fun openShow(key: String) {
        navController.navigate("${NavRoutes.podcast.name}/$key")
    }

    fun playEpisode(song: Song) {
        binder?.stopRadio()
        binder?.player?.forcePlay(song.asMediaItem)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
    ) {
        item(key = "search") {
            PodcastSearchField(
                value = searchInput,
                onValueChange = { searchInput = it },
                hint = stringResource(R.string.podcasts_search_hint)
            )
        }

        if (!isSearching) {
            // a) Featured
            item(key = "featuredHeader") {
                PodcastSectionHeader(title = stringResource(R.string.podcasts_featured))
            }
            item(key = "featured") {
                when (val load = featuredLoad) {
                    is PodcastLoad.Done -> {
                        val featured = load.shows.take(FEATURED_COUNT)
                        if (featured.isNotEmpty()) {
                            FeaturedCarousel(featured, onClick = { openShow(it.key) })
                        }
                    }
                    // Failure and empty are reported once, in the grid below
                    PodcastLoad.Failed -> Unit
                    PodcastLoad.Loading -> PodcastSkeleton(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(FeaturedHeight)
                    )
                }
            }

            // b) Continue listening
            if (recent.isNotEmpty()) {
                item(key = "continueHeader") {
                    PodcastSectionHeader(title = stringResource(R.string.podcasts_continue))
                }
                item(key = "continue") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recent, key = { it.id }) { song ->
                            PodcastEpisodeRow(
                                song = song,
                                modifier = Modifier.width(300.dp),
                                onPlay = { playEpisode(song) }
                            )
                        }
                    }
                }
            }

            // c) My podcasts
            if (saved.isNotEmpty()) {
                item(key = "myHeader") {
                    PodcastSectionHeader(
                        title = stringResource(R.string.podcasts_my),
                        actionLabel = if (saved.size > 2 || onSeeAllMyPodcasts != null) {
                            stringResource(
                                if (myExpanded && onSeeAllMyPodcasts == null) R.string.podcasts_see_less
                                else R.string.podcasts_see_all
                            )
                        } else null,
                        onAction = {
                            if (onSeeAllMyPodcasts != null) onSeeAllMyPodcasts() else myExpanded = !myExpanded
                        }
                    )
                }
                if (myExpanded && onSeeAllMyPodcasts == null) {
                    items(saved.chunked(2), key = { "my_${it.first().playlist.id}" }) { row ->
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
                } else {
                    item(key = "my") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(saved, key = { it.playlist.id }) { preview ->
                                SavedPodcastCard(
                                    preview = preview,
                                    modifier = Modifier.width(132.dp),
                                    onClick = { openSavedPodcast(navController, preview.playlist) }
                                )
                            }
                        }
                    }
                }
            }

            // d) Category chips
            item(key = "chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = categories.size, key = { it }) { index ->
                        PodcastPill(
                            text = categoryLabels[index],
                            selected = index == categoryIndex,
                            onClick = { categoryIndex = index }
                        )
                    }
                }
            }
        } else {
            item(key = "resultsHeader") {
                PodcastSectionHeader(title = stringResource(R.string.podcasts_results))
            }
        }

        // Results grid (category or search)
        when (val load = gridLoad) {
            PodcastLoad.Loading -> {
                item(key = "gridSkeleton") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        repeat(3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                repeat(2) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        PodcastSkeleton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                        )
                                        PodcastSkeleton(
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .fillMaxWidth(0.8f)
                                                .height(12.dp)
                                        )
                                        PodcastSkeleton(
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .fillMaxWidth(0.5f)
                                                .height(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PodcastLoad.Failed -> {
                item(key = "gridError") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                    ) {
                        BasicText(
                            text = stringResource(R.string.podcasts_error),
                            style = typography().xs.secondary.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        )
                        BasicText(
                            text = stringResource(R.string.podcasts_retry),
                            style = typography().xs.semiBold.color(colorPalette().background0),
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(colorPalette().text)
                                .clickable { retryTick++ }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            is PodcastLoad.Done -> {
                // The featured carousel already shows the first shows of the popular list
                val shows = if (gridQuery == popularQuery && load.shows.size > FEATURED_COUNT)
                    load.shows.drop(FEATURED_COUNT) else load.shows
                if (shows.isEmpty()) {
                    item(key = "gridEmpty") {
                        BasicText(
                            text = stringResource(R.string.podcasts_empty),
                            style = typography().xs.secondary.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                } else {
                    items(shows.chunked(2), key = { "show_${it.first().key}" }) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        ) {
                            row.forEach { show ->
                                PodcastShowCard(
                                    title = show.title,
                                    subtitle = show.author,
                                    cover = show.cover,
                                    modifier = Modifier.weight(1f),
                                    onClick = { openShow(show.key) }
                                )
                            }
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item(key = "footer") {
            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
        }
    }
}
