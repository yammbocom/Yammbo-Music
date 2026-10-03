package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.extensions.liveradio.RadioBrowser
import it.fast4x.riplay.extensions.liveradio.RadioStation
import it.fast4x.riplay.extensions.liveradio.deviceCountryCode
import it.fast4x.riplay.extensions.liveradio.playRadioStation
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.showLiveRadioKey
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.themed.TextPlaceholder
import it.fast4x.riplay.ui.components.themed.Title
import it.fast4x.riplay.ui.items.RadioStationCard
import it.fast4x.riplay.ui.items.rememberNowPlayingMediaId
import it.fast4x.riplay.ui.styling.shimmer
import it.fast4x.riplay.utils.SkeletonSwap
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.radioStreamUrlOf
import it.fast4x.riplay.utils.thumbnailShape
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home shelf with the most listened stations of the listener's country (topped up worldwide).
 * The title always renders so the full Radio tab stays one tap away even when the directory is
 * slow or down; only the row waits for data.
 */
@UnstableApi
@Composable
fun HomeLiveRadioSection(
    thumbnailSizeDp: Dp,
    onOpenAll: () -> Unit,
) {
    val showLiveRadio by rememberPreference(showLiveRadioKey, true)
    if (!showLiveRadio) return

    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()
    var stations by persist<List<RadioStation>?>("home/liveRadio", null)
    // Set when the request failed or timed out, so the skeleton never spins forever
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (stations != null) return@LaunchedEffect
        failed = false
        // A failed fetch stays null on purpose, so the next time Home composes it tries again
        // instead of remembering "no stations" for the rest of the session.
        stations = withTimeoutOrNull(15_000L) {
            RadioBrowser.topStations(deviceCountryCode(context), limit = 15).getOrNull()
        }
        if (stations == null) failed = true
    }

    Title(
        title = stringResource(R.string.live_radio),
        onClick = onOpenAll,
    )

    val list = stations
    // Answered with nothing, or failed: only the title stays
    if (list != null && list.isEmpty()) return
    if (list == null && failed) return

    val nowPlayingId = rememberNowPlayingMediaId()

    SkeletonSwap(
        loading = list == null,
        skeleton = {
            // Same card width and spacing as the final row, so nothing jumps when data arrives
            ShimmerHost {
                LazyRow(
                    userScrollEnabled = false,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(6) {
                        Column(modifier = Modifier.width(thumbnailSizeDp)) {
                            Spacer(
                                modifier = Modifier
                                    .background(colorPalette().shimmer, thumbnailShape())
                                    .size(thumbnailSizeDp)
                            )
                            TextPlaceholder(modifier = Modifier.width(thumbnailSizeDp * 0.8f))
                            TextPlaceholder(modifier = Modifier.width(thumbnailSizeDp * 0.5f))
                        }
                    }
                }
            }
        }
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = list.orEmpty(),
                key = { it.stationuuid },
                contentType = { "station" }
            ) { station ->
                RadioStationCard(
                    station = station,
                    thumbnailSizeDp = thumbnailSizeDp,
                    isPlaying = nowPlayingId?.let(::radioStreamUrlOf) == station.streamUrl,
                    onClick = { playRadioStation(binder, station, coroutineScope) }
                )
            }
        }
    }
}
