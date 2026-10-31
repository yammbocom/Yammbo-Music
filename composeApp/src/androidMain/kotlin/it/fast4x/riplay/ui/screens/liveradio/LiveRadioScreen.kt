package it.fast4x.riplay.ui.screens.liveradio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.liveradio.RadioBrowser
import it.fast4x.riplay.extensions.liveradio.RadioCountry
import it.fast4x.riplay.extensions.liveradio.RadioStation
import it.fast4x.riplay.extensions.liveradio.StationFilters
import it.fast4x.riplay.extensions.liveradio.StationOrder
import it.fast4x.riplay.extensions.liveradio.playRadioStation
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.ui.components.CollapsingTitleEffect
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.components.collapsingTitle
import it.fast4x.riplay.ui.components.likeBounce
import it.fast4x.riplay.ui.components.rememberTitleCollapse
import it.fast4x.riplay.ui.components.themed.Header
import it.fast4x.riplay.ui.components.themed.NoirChip
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.items.FilterPill
import it.fast4x.riplay.ui.items.RadioArtworkBox
import it.fast4x.riplay.ui.items.RadioLiveBadge
import it.fast4x.riplay.ui.items.RadioStationArtwork
import it.fast4x.riplay.ui.items.RadioStationRow
import it.fast4x.riplay.ui.items.rememberNowPlayingMediaId
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.isRadioId
import it.fast4x.riplay.utils.mediaItemToggleLike
import it.fast4x.riplay.utils.radioStreamUrlOf
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/**
 * The Radio tab: browser over the radio-browser.info directory with debounced name search,
 * one-tap genres, a country picker (defaults to the phone's country) and an order picker, paged
 * 100 at a time. Tapping a station hands it to the local player like a song, so mini-player,
 * notification and full player all work unchanged.
 *
 * Home tabs are torn down when the user switches away, so everything that costs a network call
 * lives in `persist` (kept for the process lifetime) rather than `remember`.
 */
/** Pauses between retries of a failed station load; the directory often answers on a retry. */
private val RADIO_RETRY_DELAYS = listOf(1_500L, 4_000L)

@UnstableApi
@Composable
fun LiveRadio() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.Bottom)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)

    val coroutineScope = rememberCoroutineScope()

    var searchInput by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    // Worldwide by default: the country picker is right there for narrowing it down
    var countryCode by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(StationOrder.Popular) }
    // Low data: only stations streaming at 64 kbps or less, filtered over what is already loaded
    // (the directory has no bitrate range filter). A station at 64 kbps spends about 30 MB an hour.
    var lowData by rememberSaveable { mutableStateOf(false) }

    // Debounced search: querying on every keystroke hammers the directory for nothing.
    LaunchedEffect(searchInput) {
        delay(400)
        val trimmed = searchInput.trim()
        search = if (trimmed.length >= 2) trimmed else ""
    }

    val filters = StationFilters(name = search, tag = genre, countryCode = countryCode, order = order)

    var stations by persist<List<RadioStation>>("liveRadio/stations", emptyList())
    var apiOffset by persist("liveRadio/offset", 0)
    var canLoadMore by persist("liveRadio/canLoadMore", false)
    var loadedFilters by persist<StationFilters?>("liveRadio/loadedFilters", null)
    var loadMoreTick by persist("liveRadio/loadMoreTick", 0)
    var handledTick by persist("liveRadio/handledTick", 0)
    var isLoading by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    // One loader for both cases: a filter change starts over, a "load more" appends the next
    // page. The cursor counts rows requested from the API, not rows kept after filtering, or
    // paging would skip whatever was dropped. Re-entering the tab re-runs this effect with the
    // same filters and tick, which must be a no-op: the previous pages are still in `persist`.
    //
    // The directory (radio-browser.info) is one server that keeps dropping requests, so a first
    // page is shown from the on-disk copy at once and refreshed behind it, and a failed refresh
    // is retried a few times instead of leaving the tab empty.
    LaunchedEffect(filters, loadMoreTick) {
        val fresh = filters != loadedFilters
        if (!fresh && loadMoreTick == handledTick) return@LaunchedEffect
        handledTick = loadMoreTick
        val offset = if (fresh) 0 else apiOffset
        if (fresh) {
            val cached = RadioBrowser.cachedStations(filters)
            stations = cached?.stations.orEmpty()
            canLoadMore = false
            // Forget the previous page cursor now: if the user flips back to the old filters
            // while this request is in flight, that load must start from page one again.
            loadedFilters = null
        }
        isError = false
        // A list already on screen refreshes silently; the spinner is for an empty tab or the
        // next page.
        isLoading = !fresh || stations.isEmpty()
        var result = RadioBrowser.searchStations(filters, offset)
        for (wait in RADIO_RETRY_DELAYS) {
            if (result.isSuccess) break
            delay(wait)
            result = RadioBrowser.searchStations(filters, offset)
        }
        result
            .onSuccess { page ->
                stations = if (fresh) page.stations
                else (stations + page.stations).distinctBy { it.stationuuid }
                canLoadMore = page.apiCount >= RadioBrowser.PAGE_SIZE
                apiOffset = offset + RadioBrowser.PAGE_SIZE
                loadedFilters = filters
            }
            .onFailure { isError = fresh && stations.isEmpty() || !fresh }
        isLoading = false
    }

    var countries by persist<List<RadioCountry>>("liveRadio/countries", emptyList())
    LaunchedEffect(Unit) {
        if (countries.isEmpty()) RadioBrowser.countries().onSuccess { countries = it }
    }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showOrderPicker by remember { mutableStateOf(false) }
    var showGenrePicker by remember { mutableStateOf(false) }

    val nowPlayingId = rememberNowPlayingMediaId()
    val shownStations = if (lowData) stations.filter { it.bitrate in 1..64 } else stations
    val hasFilters = search.isNotBlank() || genre.isNotBlank() || countryCode.isNotBlank()
    val allCountriesLabel = stringResource(R.string.live_radio_all_countries)
    val allGenresLabel = stringResource(R.string.live_radio_all_genres)
    // The directory only reports the size of a page, so the live total comes from the per-country
    // station counts, and only when no name or genre narrows the list.
    val liveCount: Int? = if (search.isNotBlank() || genre.isNotBlank()) null
    else if (countryCode.isBlank()) countries.sumOf { it.stationcount }.takeIf { it > 0 }
    else countries.firstOrNull { it.code.equals(countryCode, ignoreCase = true) }?.stationcount?.takeIf { it > 0 }
    val player = binder?.player
    val nowPlayingMediaItem: MediaItem? =
        if (nowPlayingId?.isRadioId == true) remember(nowPlayingId) { player?.currentMediaItem } else null

    fun play(station: RadioStation) = playRadioStation(binder, station, coroutineScope)

    val listState = rememberLazyListState()
    // The "header" item comes right after the optional eyebrow
    val titleProgress = rememberTitleCollapse(listState, if (liveCount != null) 1 else 0)
    CollapsingTitleEffect(stringResource(R.string.live_radio_tab), titleProgress)

    Column(
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
        // Rows keep their own 16dp padding; this only centres the list at list width on tablets.
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = rememberNoirLayout().sidePadding(NoirContentMax.List, minPadding = 0.dp)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            if (liveCount != null) {
                item(key = "eyebrow") {
                    NoirEyebrow(
                        text = stringResource(
                            R.string.noir_radio_live_count,
                            NumberFormat.getIntegerInstance(Locale.getDefault()).format(liveCount)
                        ),
                        modifier = Modifier.cascadeEntry(0).padding(start = 16.dp, end = 16.dp, top = 20.dp)
                    )
                }
            }

            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .cascadeEntry(1)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
                ) {
                    BasicText(
                        text = stringResource(R.string.live_radio_tab),
                        style = typography().xxl.tight.copy(
                            color = colorPalette().text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f).collapsingTitle(titleProgress)
                    )
                    BasicText(
                        text = stringResource(R.string.data_saver),
                        style = typography().xs.copy(color = colorPalette().textSecondary, fontSize = 14.sp),
                        maxLines = 1,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    NoirSwitch(checked = lowData, onCheckedChange = { lowData = it })
                }
            }

            item(key = "search") {
                Box(Modifier.cascadeEntry(2)) {
                    RadioSearchField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        hint = stringResource(R.string.noir_radio_search_hint)
                    )
                }
            }

            item(key = "filters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .cascadeEntry(3)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    NoirChip(
                        text = countryLabel(countryCode, allCountriesLabel),
                        selected = countryCode.isNotBlank(),
                        onClick = { showCountryPicker = true },
                        trailingIcon = R.drawable.chevron_down
                    )
                    NoirChip(
                        text = if (genre.isBlank()) stringResource(R.string.noir_genre)
                        else genre.replaceFirstChar { it.titlecase(Locale.ROOT) },
                        selected = genre.isNotBlank(),
                        onClick = { showGenrePicker = true },
                        trailingIcon = R.drawable.chevron_down
                    )
                    NoirChip(
                        text = orderLabel(order),
                        selected = false,
                        onClick = { showOrderPicker = true },
                        trailingIcon = R.drawable.chevron_down
                    )
                    if (hasFilters) {
                        NoirChip(
                            text = stringResource(R.string.live_radio_clear_filters),
                            selected = false,
                            onClick = {
                                searchInput = ""
                                search = ""
                                genre = ""
                                countryCode = ""
                            },
                            icon = R.drawable.close
                        )
                    }
                }
            }

            if (nowPlayingMediaItem != null && player != null) {
                item(key = "nowPlaying") {
                    val matched = stations.firstOrNull {
                        it.streamUrl == radioStreamUrlOf(nowPlayingMediaItem.mediaId)
                    }
                    Box(Modifier.cascadeEntry(4)) {
                        RadioNowPlayingCard(
                            mediaItem = nowPlayingMediaItem,
                            station = matched,
                            player = player
                        )
                    }
                }
            }

            item(key = "popular") {
                NoirEyebrow(
                    text = if (countryCode.isBlank()) stringResource(R.string.noir_radio_popular_worldwide)
                    else stringResource(R.string.noir_radio_popular_in, countryLabel(countryCode, allCountriesLabel)),
                    modifier = Modifier.cascadeEntry(5).padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
                )
            }

            itemsIndexed(shownStations, key = { _, station -> station.stationuuid }) { rowIndex, station ->
                RadioStationRow(
                    modifier = Modifier.cascadeEntry(6 + rowIndex),
                    station = station,
                    isPlaying = nowPlayingId?.let(::radioStreamUrlOf) == station.streamUrl,
                    onClick = { play(station) }
                )
            }

            item(key = "state") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    when {
                        isLoading -> CircularProgressIndicator(color = colorPalette().text)
                        isError -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(
                                text = stringResource(R.string.live_radio_error),
                                style = typography().xs.secondary.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                            )
                            NoirChip(
                                text = stringResource(R.string.live_radio_retry),
                                selected = false,
                                onClick = { loadMoreTick++ }
                            )
                        }
                        // With low data on, an empty page may still have matches further down the list
                        shownStations.isEmpty() && !(lowData && canLoadMore) -> BasicText(
                            text = stringResource(R.string.live_radio_empty),
                            style = typography().xs.secondary.copy(textAlign = TextAlign.Center),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        canLoadMore -> NoirChip(
                            text = stringResource(R.string.live_radio_load_more),
                            selected = false,
                            onClick = { loadMoreTick++ }
                        )
                    }
                }
            }

            item(key = "footer") {
                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
            }
        }
    }

    if (showCountryPicker) {
        RadioPickerDialog(
            title = stringResource(R.string.live_radio_country),
            options = listOf("" to allCountriesLabel) + countries.map { country ->
                country.code to "${countryLabel(country.code, country.name)} · ${country.stationcount}"
            },
            selected = countryCode,
            searchable = true,
            onDismiss = { showCountryPicker = false },
            onSelect = { countryCode = it }
        )
    }

    if (showGenrePicker) {
        RadioPickerDialog(
            title = stringResource(R.string.noir_genre),
            options = listOf("" to allGenresLabel) + RadioBrowser.QUICK_GENRES.map { tag ->
                tag to tag.replaceFirstChar { it.titlecase(Locale.ROOT) }
            },
            selected = genre,
            searchable = false,
            onDismiss = { showGenrePicker = false },
            onSelect = { genre = it }
        )
    }

    if (showOrderPicker) {
        RadioPickerDialog(
            title = stringResource(R.string.live_radio_order),
            options = StationOrder.entries.map { it.name to orderLabel(it) },
            selected = order.name,
            searchable = false,
            onDismiss = { showOrderPicker = false },
            onSelect = { order = StationOrder.valueOf(it) }
        )
    }
}

/** Localised country name for an ISO code; the fallback covers codes Java does not know. */
private fun countryLabel(code: String, fallback: String): String =
    if (code.isBlank()) fallback
    else runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault()) }
        .getOrDefault("")
        .ifBlank { fallback }

@Composable
private fun orderLabel(order: StationOrder): String = stringResource(
    when (order) {
        StationOrder.Popular -> R.string.live_radio_order_popular
        StationOrder.TopRated -> R.string.live_radio_order_votes
        StationOrder.Quality -> R.string.live_radio_order_bitrate
        StationOrder.Name -> R.string.live_radio_order_name
    }
)

@Composable
private fun RadioSearchField(
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
            .height(56.dp)
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
                    maxLines = 1
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

/** Scrollable single-choice list; `options` are (value, label) pairs. */
@Composable
private fun RadioPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    searchable: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = if (query.isBlank()) options
    else options.filter { it.second.contains(query.trim(), ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colorPalette().background1)
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(vertical = 12.dp)
        ) {
            BasicText(
                text = title,
                style = typography().m.semiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (searchable) {
                RadioSearchField(
                    value = query,
                    onValueChange = { query = it },
                    hint = stringResource(R.string.live_radio_search_hint)
                )
            }
            LazyColumn {
                items(visible, key = { it.first }) { (value, label) ->
                    BasicText(
                        text = label,
                        style = typography().xs.semiBold.color(
                            if (value == selected) colorPalette().accent else colorPalette().text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/** Noir switch: white track with a black thumb when on, outlined track when off. */
@Composable
private fun NoirSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(shape)
            .background(if (checked) colorPalette().text else Color.Transparent)
            .border(1.dp, colorPalette().text.copy(alpha = if (checked) 0f else 0.35f), shape)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) colorPalette().background0 else colorPalette().textSecondary)
        )
    }
}

/** Twelve thin bars that dance while [playing] and rest at a fixed profile when paused. */
@Composable
private fun NoirEqualizer(playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radioEq")
    val rest = listOf(0.35f, 0.6f, 0.45f, 0.8f, 0.5f, 0.7f, 0.4f, 0.9f, 0.55f, 0.65f, 0.4f, 0.5f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.height(32.dp)
    ) {
        rest.forEachIndexed { index, resting ->
            val animated by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 380 + index * 57, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(32.dp * (if (playing) animated else resting))
                    .clip(RoundedCornerShape(50))
                    .background(colorPalette().text)
            )
        }
    }
}

/** The station on air: badge, artwork, name and the stop/like controls. */
@Composable
private fun RadioNowPlayingCard(
    mediaItem: MediaItem,
    station: RadioStation?,
    player: Player,
) {
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose { player.removeListener(listener) }
    }

    val likedAt by remember(mediaItem.mediaId) { Database.likedAt(mediaItem.mediaId) }
        .collectAsState(initial = null)
    val liked = (likedAt ?: 0L) > 0L

    val metadata = mediaItem.mediaMetadata
    val name = station?.displayName ?: metadata.title?.toString().orEmpty()
    val subtitle = if (station != null) listOf(
        station.country,
        station.tagList.take(2).joinToString(", "),
        if (station.bitrate > 0) "${station.bitrate} kbps" else ""
    ).filter { it.isNotBlank() }.joinToString(" · ")
    else (metadata.artist ?: metadata.subtitle)?.toString().orEmpty()

    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colorPalette().background1, colorPalette().background0)))
            .border(1.dp, colorPalette().text.copy(alpha = 0.10f), shape)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            RadioLiveBadge()
            Spacer(Modifier.weight(1f))
            NoirEyebrow(stringResource(R.string.noir_on_air))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                if (station != null) {
                    RadioStationArtwork(station = station, size = 76.dp, modifier = Modifier.clip(CircleShape))
                } else {
                    RadioArtworkBox(
                        letter = name.trim().take(1).uppercase(),
                        primary = metadata.artworkUri?.toString()?.takeIf { it.startsWith("http", ignoreCase = true) },
                        alternative = null,
                        size = 76.dp,
                        modifier = Modifier.clip(CircleShape)
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                BasicText(
                    text = name,
                    style = typography().l.tight.copy(
                        color = colorPalette().text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    BasicText(
                        text = subtitle,
                        style = typography().xs.copy(color = colorPalette().textSecondary, fontSize = 14.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            NoirEqualizer(playing = isPlaying, modifier = Modifier.weight(1f))
            val heartShape = RoundedCornerShape(16.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    // Before the clip so the ring can grow past the rounded square
                    .likeBounce(liked, colorPalette().text, key = mediaItem.mediaId)
                    .clip(heartShape)
                    .border(1.dp, colorPalette().text.copy(alpha = 0.20f), heartShape)
                    .clickable { mediaItemToggleLike(mediaItem) }
            ) {
                Image(
                    painter = painterResource(if (liked) R.drawable.heart else R.drawable.heart_outline),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().text),
                    modifier = Modifier.size(22.dp)
                )
            }
            val stopShape = RoundedCornerShape(20.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(64.dp)
                    .clip(stopShape)
                    .background(colorPalette().text)
                    .clickable { if (isPlaying) player.pause() else player.play() }
            ) {
                // No dedicated stop glyph in the resources: pause stands in for "Detener".
                Image(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(R.string.noir_stop),
                    colorFilter = ColorFilter.tint(colorPalette().background0),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
