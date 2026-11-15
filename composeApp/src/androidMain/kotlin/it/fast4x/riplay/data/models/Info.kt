package it.fast4x.riplay.data.models

import androidx.media3.common.MediaItem

data class Info(
    val id: String,
    val name: String?,
    val size: Int = 0
)

/**
 * An empty artist list from the database (song not mapped yet) falls back to the artists carried
 * in the media item's extras. Null and non-empty lists are returned unchanged.
 */
fun List<Info>?.orArtistsFromExtras(mediaItem: MediaItem): List<Info>? {
    if (this == null || isNotEmpty()) return this
    val extras = mediaItem.mediaMetadata.extras ?: return this
    val names = extras.getStringArrayList("artistNames") ?: return this
    val ids = extras.getStringArrayList("artistIds") ?: return this
    return names.zip(ids).map { (name, id) -> Info(id, name) }
}
