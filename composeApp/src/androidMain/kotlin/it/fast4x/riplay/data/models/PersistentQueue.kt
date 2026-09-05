package it.fast4x.riplay.data.models

import android.content.ContentUris
import android.provider.MediaStore
import androidx.core.net.toUri
import it.fast4x.riplay.utils.playbackUriOf
import it.fast4x.riplay.utils.radioMimeTypeOf
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import it.fast4x.riplay.utils.LOCAL_KEY_PREFIX
import java.io.Serializable

data class PersistentQueue(
    val title: String?,
    val songMediaItems: List<PersistentSong>,
    val mediaItemIndex: Int,
    val position: Long,
) : Serializable

data class PersistentSong(
    val id: String,
    val title: String,
    val artistsText: String? = null,
    val durationText: String?,
    val thumbnailUrl: String?,
    val likedAt: Long? = null,
    val totalPlayTimeMs: Long = 0
) : Serializable

val PersistentSong.asMediaItem: MediaItem
    @UnstableApi
    get() = MediaItem.Builder()
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistsText)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setExtras(
                    bundleOf(
                        "durationText" to durationText
                    )
                )
                .build()
        )
        .setMediaId(id)
        .setUri(playbackUriOf(id))
        .setMimeType(radioMimeTypeOf(id))
        .setCustomCacheKey(id)
        .build()