package it.fast4x.riplay.extensions.liveradio

import androidx.media3.common.util.UnstableApi
import it.fast4x.riplay.service.PlayerService
import it.fast4x.riplay.utils.forcePlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Starts a station and fills the queue behind it with related ones.
 *
 * Without the queue a station is a one-item playlist: the next and previous buttons in the player
 * and the notification have nowhere to go and do nothing. The related stations are fetched after
 * playback starts so the sound is never delayed by that request.
 */
@UnstableApi
fun playRadioStation(
    binder: PlayerService.Binder?,
    station: RadioStation,
    scope: CoroutineScope,
) {
    val service = binder ?: return
    val mediaItem = station.asMediaItem()

    service.stopRadio()
    service.player.forcePlay(mediaItem)
    RadioBrowser.registerClick(station.stationuuid)

    scope.launch {
        val related = RadioBrowser.relatedStations(station)
        if (related.isEmpty()) return@launch
        withContext(Dispatchers.Main) {
            // The listener may have moved on while the directory answered; only extend the queue
            // that still belongs to this station.
            if (service.player.currentMediaItem?.mediaId != mediaItem.mediaId) return@withContext
            if (service.player.mediaItemCount > 1) return@withContext
            runCatching { service.player.addMediaItems(related.map { it.asMediaItem() }) }
                .onFailure { Timber.e("playRadioStation could not queue related stations: ${it.message}") }
        }
    }
}
