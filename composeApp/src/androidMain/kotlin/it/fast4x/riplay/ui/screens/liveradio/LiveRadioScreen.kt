package it.fast4x.riplay.ui.screens.liveradio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import it.fast4x.riplay.ui.components.themed.Header
import it.fast4x.riplay.ui.items.FilterPill
import it.fast4x.riplay.ui.items.RadioStationRow
import it.fast4x.riplay.ui.items.rememberNowPlayingMediaId
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.radioStreamUrlOf
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.delay
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
@UnstableApi
@Composable
fun LiveRadio() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.Bottom)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val coroutineScope = rememberCoroutineScope()

    var searchInput by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    // Worldwide by default: the country picker is right there for narrowing it down
    var countryCode by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(StationOrder.Popular) }

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
    LaunchedEffect(filters, loadMoreTick) {
        val fresh = filters != loadedFilters
        if (!fresh && loadMoreTick == handledTick) return@LaunchedEffect
        handledTick = loadMoreTick
        val offset = if (fresh) 0 else apiOffset
        if (fresh) {
            stations = emptyList()
            canLoadMore = false
            // Forget the previous page cursor now: if the user flips back to the old filters
            // while this request is in flight, that load must start from page one again.
            loadedFilters = null
        }
        isError = false
        isLoading = true
        RadioBrowser.searchStations(filters, offset)
            .onSuccess { page ->
                stations = if (fresh) page.stations
                else (stations + page.stations).distinctBy { it.stationuuid }
                canLoadMore = page.apiCount >= RadioBrowser.PAGE_SIZE
                apiOffset = offset + RadioBrowser.PAGE_SIZE
                loadedFilters = filters
            }
            .onFailure { isError = true }
        isLoading = false
    }

    var countries by persist<List<RadioCountry>>("liveRadio/countries", emptyList())
    LaunchedEffect(Unit) {
        if (countries.isEmpty()) RadioBrowser.countries().onSuccess { countries = it }
    }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showOrderPicker by remember { mutableStateOf(false) }

    val nowPlayingId = rememberNowPlayingMediaId()
    val hasFilters = search.isNotBlank() || genre.isNotBlank() || countryCode.isNotBlank()
    val allCountriesLabel = stringResource(R.string.live_radio_all_countries)

    fun play(station: RadioStation) = playRadioStation(binder, station, coroutineScope)

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
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                Header(
                    title = stringResource(R.string.live_radio),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    disableScrollingText = disableScrollingText
                )
            }

            item(key = "search") {
                RadioSearchField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    hint = stringResource(R.string.live_radio_search_hint)
                )
            }

            item(key = "filters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    FilterPill(
                        text = countryLabel(countryCode, allCountriesLabel),
                        selected = countryCode.isNotBlank(),
                        trailingIcon = R.drawable.chevron_down,
                        onClick = { showCountryPicker = true }
                    )
                    FilterPill(
                        text = orderLabel(order),
                        selected = false,
                        trailingIcon = R.drawable.chevron_down,
                        onClick = { showOrderPicker = true }
                    )
                    if (hasFilters) {
                        FilterPill(
                            text = stringResource(R.string.live_radio_clear_filters),
                            selected = false,
                            trailingIcon = R.drawable.close,
                            onClick = {
                                searchInput = ""
                                search = ""
                                genre = ""
                                countryCode = ""
                            }
                        )
                    }
                }
            }

            item(key = "genres") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "all") {
                        FilterPill(
                            text = stringResource(R.string.live_radio_all_genres),
                            selected = genre.isBlank(),
                            onClick = { genre = "" }
                        )
                    }
                    items(RadioBrowser.QUICK_GENRES, key = { it }) { tag ->
                        FilterPill(
                            text = tag.replaceFirstChar { it.titlecase(Locale.ROOT) },
                            selected = genre == tag,
                            onClick = { genre = if (genre == tag) "" else tag }
                        )
                    }
                }
            }

            items(stations, key = { it.stationuuid }) { station ->
                RadioStationRow(
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
                        isLoading -> CircularProgressIndicator(color = colorPalette().accent)
                        isError -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(
                                text = stringResource(R.string.live_radio_error),
                                style = typography().xs.secondary.copy(textAlign = TextAlign.Center),
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                            )
                            FilterPill(
                                text = stringResource(R.string.live_radio_retry),
                                selected = true,
                                onClick = { loadMoreTick++ }
                            )
                        }
                        stations.isEmpty() -> BasicText(
                            text = stringResource(R.string.live_radio_empty),
                            style = typography().xs.secondary.copy(textAlign = TextAlign.Center),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        canLoadMore -> FilterPill(
                            text = stringResource(R.string.live_radio_load_more),
                            selected = true,
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
