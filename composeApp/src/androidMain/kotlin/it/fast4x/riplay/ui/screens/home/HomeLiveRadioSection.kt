package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import it.fast4x.riplay.ui.components.themed.Title
import it.fast4x.riplay.ui.items.RadioStationCard
import it.fast4x.riplay.ui.items.rememberNowPlayingMediaId
import it.fast4x.riplay.utils.radioStreamUrlOf

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

    LaunchedEffect(Unit) {
        if (stations != null) return@LaunchedEffect
        // A failed fetch stays null on purpose, so the next time Home composes it tries again
        // instead of remembering "no stations" for the rest of the session.
        stations = RadioBrowser.topStations(deviceCountryCode(context), limit = 15).getOrNull()
    }

    Title(
        title = stringResource(R.string.live_radio),
        onClick = onOpenAll,
    )

    val list = stations
    if (list.isNullOrEmpty()) return

    val nowPlayingId = rememberNowPlayingMediaId()

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list, key = { it.stationuuid }) { station ->
            RadioStationCard(
                station = station,
                thumbnailSizeDp = thumbnailSizeDp,
                isPlaying = nowPlayingId?.let(::radioStreamUrlOf) == station.streamUrl,
                onClick = { playRadioStation(binder, station, coroutineScope) }
            )
        }
    }
}
