package it.fast4x.riplay.extensions.liveradio

import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import it.fast4x.riplay.utils.RADIO_HLS_MARKER
import it.fast4x.riplay.utils.RADIO_KEY_PREFIX

/** Shown where a song would show its length: a station has none. */
const val RADIO_LIVE_DURATION_TEXT = "LIVE"

/**
 * Artwork url that actually loaded for a station, remembered by the list and card composables.
 * A station is always tapped from a list that already tried its icons, so the MediaItem (and the
 * Song row the service writes from it) can carry the sharp one instead of guessing.
 */
object RadioArtwork {
    private val resolved = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun remember(stationUuid: String, url: String) {
        resolved[stationUuid] = url
    }

    fun artworkFor(station: RadioStation): String? =
        resolved[station.stationuuid] ?: station.faviconUrl
}

/**
 * A station becomes a MediaItem whose id is `radio:<stream url>`. Carrying the url inside the id
 * is what lets a station round-trip through the songs table (history, favourites, playlists):
 * playbackUriOf rebuilds the stream uri from the id alone, so no extra table is needed. When the
 * directory says the stream is HLS but the url does not end in .m3u8, the id carries a marker so
 * the mime type survives that round trip too.
 */
fun RadioStation.asMediaItem(): MediaItem {
    val stream = streamUrl
    val needsHlsMarker = isHls && !stream.contains(".m3u8", ignoreCase = true)
    val id = RADIO_KEY_PREFIX + stream + (if (needsHlsMarker) RADIO_HLS_MARKER else "")
    val subtitle = listOf(country.trim(), tagList.take(2).joinToString(", "))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(stream)
        .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
        .setCustomCacheKey(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(displayName)
                .setArtist(subtitle)
                .setArtworkUri(RadioArtwork.artworkFor(this)?.toUri())
                .setExtras(
                    bundleOf(
                        "durationText" to RADIO_LIVE_DURATION_TEXT,
                        "isRadio" to true,
                        "isVideo" to false,
                        "stationUuid" to stationuuid,
                        "mediaId" to id,
                    )
                )
                .build()
        )
        .build()
}
