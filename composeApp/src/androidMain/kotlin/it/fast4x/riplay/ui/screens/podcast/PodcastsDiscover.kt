package it.fast4x.riplay.ui.screens.podcast

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
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
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.ui.components.CollapsingTitleEffect
import it.fast4x.riplay.ui.components.CoverHandoff
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.components.collapsingTitle
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.rememberTitleCollapse
import it.fast4x.riplay.ui.components.sharedCover
import it.fast4x.riplay.ui.components.themed.NoirChip
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.components.themed.NoirSectionHeader
import it.fast4x.riplay.ui.components.themed.NoirTextAction
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.bold
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.ui.styling.tight
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
    sharedKey: String? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .sharedCover(sharedKey, shape)
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
            RevealImage(
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
    sharedKey: String? = null,
    onClick: () -> Unit,
) {
    // Not clipped as a whole: only the cover is rounded, so the text is never cut.
    Column(modifier = modifier.pressable(onClick = onClick)) {
        PodcastCover(
            url = cover,
            shape = RoundedCornerShape(20.dp),
            sharedKey = sharedKey,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        BasicText(
            text = title,
            style = typography().xs.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            BasicText(
                text = subtitle,
                style = typography().xs.copy(color = colorPalette().textSecondary, fontSize = 13.sp),
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
    val cover = thumbs.firstOrNull { !it.isNullOrBlank() }
    // Only shows that kept their browse id open the online page the cover can fly into.
    val browseId = preview.playlist.browseId?.takeIf { it.isNotBlank() }
    val sharedKey = browseId?.let { "podcast-saved:$it" }
    PodcastShowCard(
        title = preview.playlist.name,
        subtitle = stringResource(R.string.podcasts_episodes_count, preview.songCount),
        cover = cover,
        modifier = modifier,
        sharedKey = sharedKey,
        onClick = {
            if (browseId != null && sharedKey != null) {
                CoverHandoff.set(key = sharedKey, browseId = browseId, url = cover?.resizeNoCrop(512, 512), title = preview.playlist.name)
            }
            onClick()
        }
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

@Composable
private fun PodcastSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(colorPalette().background1)
            .padding(horizontal = 16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().textSecondary),
            modifier = Modifier.size(20.dp)
        )
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            if (value.isEmpty()) {
                BasicText(
                    text = hint,
                    style = typography().s.copy(color = colorPalette().textSecondary, fontSize = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = typography().s.copy(color = colorPalette().text, fontSize = 16.sp),
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
// Noir blocks: hero, header, "keep listening" rows
// ---------------------------------------------------------------------------------------------

private val HeroHeight = 300.dp
private val HeroShape = RoundedCornerShape(28.dp)

/** The one big card: first show of the list. Only the image is clipped, never the text. */
@Composable
private fun PodcastHero(show: PodcastShow, model: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(HeroHeight)
            .pressable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .sharedCover("podcast-hero:${show.key}", HeroShape)
                .clip(HeroShape)
                .background(colorPalette().background1)
        ) {
            RevealImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
        }
        BasicText(
            text = stringResource(R.string.noir_featured_week).uppercase(),
            style = typography().xxxs.mono.copy(color = Color.White, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                BasicText(
                    text = show.title,
                    style = typography().xxl.tight.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!show.author.isNullOrBlank()) {
                    BasicText(
                        text = show.author,
                        style = typography().xs.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Always white over the darkened cover, whatever the theme.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .clickable(onClick = onClick)
            ) {
                Image(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.podcasts_open_show),
                    colorFilter = ColorFilter.tint(Color.Black),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun PodcastHeroSkeleton() {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(HeroHeight)
            .clip(HeroShape)
            .background(colorPalette().background1)
    )
}

/** Big title with the "My podcasts · N" outlined pill on the right. */
@Composable
private fun PodcastsHeader(
    savedCount: Int,
    onSeeAllMyPodcasts: (() -> Unit)?,
    titleModifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)
    ) {
        BasicText(
            text = stringResource(R.string.podcasts_tab_podcasts),
            style = typography().xxl.tight.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(titleModifier)
        )
        if (onSeeAllMyPodcasts != null) {
            val shape = RoundedCornerShape(percent = 50)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .height(44.dp)
                    .clip(shape)
                    .border(1.dp, colorPalette().text.copy(alpha = 0.25f), shape)
                    .clickable(onClick = onSeeAllMyPodcasts)
                    .padding(horizontal = 16.dp)
            ) {
                BasicText(
                    text = if (savedCount > 0) stringResource(R.string.noir_my_podcasts_count, savedCount)
                    else stringResource(R.string.podcasts_my),
                    style = typography().xs.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

/** One recently played episode: square cover, "show" eyebrow, title and duration. */
@Composable
private fun ContinueRow(song: Song, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PodcastCover(
            url = song.thumbnailUrl,
            modifier = Modifier.size(84.dp),
            sizePx = 256,
            shape = RoundedCornerShape(16.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            BasicText(
                text = (song.artistsText?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.noir_kind_podcast)).uppercase(),
                style = typography().xxxs.mono.copy(color = colorPalette().textSecondary, fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = song.title,
                style = typography().s.copy(
                    color = colorPalette().text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!song.durationText.isNullOrBlank()) {
                BasicText(
                    text = song.durationText.orEmpty(),
                    style = typography().xs.copy(color = colorPalette().textSecondary, fontSize = 14.sp),
                    maxLines = 1
                )
            }
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
    // The board's first chip reads "Destacados"; its query stays the popular one.
    val chipLabels = categoryLabels.mapIndexed { index, label ->
        if (index == 0) stringResource(R.string.podcasts_featured) else label
    }
    val category = categories.getOrElse(categoryIndex) { PodcastCategory.Popular }

    val popularQuery = seed
    val categoryQuery = if (category == PodcastCategory.Popular) popularQuery
    else "$seed ${categoryLabels[categories.indexOf(category)]}"
    val gridQuery = if (isSearching) search else categoryQuery

    // The popular shows are always loaded (prefetch). When the grid is on the same
    // query it reuses that load, so the request is made once.
    val featuredLoad = rememberPodcastLoad(popularQuery, retryTick)
    val otherLoad = rememberPodcastLoad(gridQuery.takeIf { it != popularQuery }, retryTick)
    val gridLoad = if (gridQuery == popularQuery) featuredLoad else otherLoad

    val recent by Database.lastPlayedPodcasts(12).collectAsState(initial = emptyList())
    val saved by Database.podcastPlaylists().collectAsState(initial = emptyList())

    fun openShow(key: String, sharedKey: String, coverUrl: String?, title: String?) {
        CoverHandoff.set(key = sharedKey, browseId = key, url = coverUrl, title = title)
        navController.navigate("${NavRoutes.podcast.name}/$key")
    }

    fun playEpisode(song: Song) {
        binder?.stopRadio()
        binder?.player?.forcePlay(song.asMediaItem)
    }

    val listState = rememberLazyListState()
    val titleProgress = rememberTitleCollapse(listState, titleItemIndex = 0)
    CollapsingTitleEffect(stringResource(R.string.podcasts_tab_podcasts), titleProgress)

    // 2 columns on phones (as before); more on tablets, with the whole page capped at grid width.
    val noirLayout = rememberNoirLayout()
    val cols = noirLayout.columns(minItem = 170.dp, max = 4)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = noirLayout.sidePadding(NoirContentMax.Grid, minPadding = 0.dp)),
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
    ) {
        item(key = "title") {
            Box(Modifier.cascadeEntry(0)) {
                PodcastsHeader(
                    savedCount = saved.size,
                    onSeeAllMyPodcasts = onSeeAllMyPodcasts,
                    titleModifier = Modifier.collapsingTitle(titleProgress)
                )
            }
        }
        item(key = "search") {
            Box(Modifier.cascadeEntry(1)) {
                PodcastSearchField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    hint = stringResource(R.string.podcasts_search_hint)
                )
            }
        }

        if (!isSearching) {
            // a) Category chips
            item(key = "chips") {
                LazyRow(
                    modifier = Modifier.cascadeEntry(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = categories.size, key = { it }) { index ->
                        NoirChip(
                            text = chipLabels[index],
                            selected = index == categoryIndex,
                            onClick = { categoryIndex = index }
                        )
                    }
                }
            }

            // b) Featured hero: first show of the selected category
            item(key = "hero") {
                Box(Modifier.cascadeEntry(3)) {
                    when (val load = gridLoad) {
                        is PodcastLoad.Done -> {
                            val first = load.shows.firstOrNull()
                            if (first != null) {
                                val heroModel = first.cover.thumbnail(720)
                                PodcastHero(
                                    show = first,
                                    model = heroModel,
                                    onClick = { openShow(first.key, "podcast-hero:${first.key}", heroModel, first.title) }
                                )
                            }
                        }
                        // Failure and empty are reported once, in the list below
                        PodcastLoad.Failed -> Unit
                        PodcastLoad.Loading -> PodcastHeroSkeleton()
                    }
                }
            }

            // c) Continue listening
            if (recent.isNotEmpty()) {
                item(key = "continueHeader") {
                    NoirEyebrow(
                        text = stringResource(R.string.podcasts_continue),
                        modifier = Modifier.cascadeEntry(4).padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 4.dp)
                    )
                }
                item(key = "continue") {
                    Column {
                        recent.take(4).forEachIndexed { index, song ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(colorPalette().text.copy(alpha = 0.08f))
                                )
                            }
                            ContinueRow(
                                song = song,
                                onPlay = { playEpisode(song) },
                                modifier = Modifier.cascadeEntry(5 + index)
                            )
                        }
                    }
                }
            }

            // d) My podcasts
            if (saved.isNotEmpty()) {
                item(key = "myHeader") {
                    Box(Modifier.cascadeEntry(7)) {
                        NoirSectionHeader(
                            title = stringResource(R.string.podcasts_my),
                            trailing = {
                                if (saved.size > 2 || onSeeAllMyPodcasts != null) {
                                    NoirTextAction(
                                        text = stringResource(
                                            if (myExpanded && onSeeAllMyPodcasts == null) R.string.podcasts_see_less
                                            else R.string.podcasts_see_all
                                        ),
                                        onClick = {
                                            if (onSeeAllMyPodcasts != null) onSeeAllMyPodcasts() else myExpanded = !myExpanded
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
                if (myExpanded && onSeeAllMyPodcasts == null) {
                    items(saved.chunked(cols), key = { "my_${it.first().playlist.id}" }) { row ->
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
                            repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
                                    modifier = Modifier.width(132.dp * noirLayout.shelfScale),
                                    onClick = { openSavedPodcast(navController, preview.playlist) }
                                )
                            }
                        }
                    }
                }
            }

        } else {
            item(key = "resultsHeader") {
                Box(Modifier.cascadeEntry(2)) {
                    NoirSectionHeader(title = stringResource(R.string.podcasts_results))
                }
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
                                repeat(cols) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        PodcastSkeleton(
                                            shape = RoundedCornerShape(20.dp),
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
                // The hero already shows the first show of the selected category
                val shows = if (!isSearching) load.shows.drop(1) else load.shows
                if (load.shows.isEmpty()) {
                    item(key = "gridEmpty") {
                        BasicText(
                            text = stringResource(R.string.podcasts_empty),
                            style = typography().xs.secondary.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                } else if (shows.isNotEmpty()) {
                    if (!isSearching) {
                        item(key = "moreHeader") {
                            Box(Modifier.cascadeEntry(8)) {
                                NoirSectionHeader(title = stringResource(R.string.noir_more_shows))
                            }
                        }
                    }
                    itemsIndexed(shows.chunked(cols), key = { _, row -> "show_${row.first().key}" }) { rowIndex, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .cascadeEntry((if (isSearching) 3 else 9) + rowIndex)
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                        ) {
                            row.forEach { show ->
                                PodcastShowCard(
                                    title = show.title,
                                    subtitle = show.author,
                                    cover = show.cover,
                                    modifier = Modifier.weight(1f),
                                    sharedKey = "podcast-grid:${show.key}",
                                    onClick = {
                                        openShow(show.key, "podcast-grid:${show.key}", show.cover?.resizeNoCrop(512, 512), show.title)
                                    }
                                )
                            }
                            repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
