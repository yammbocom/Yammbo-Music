package it.fast4x.riplay.data.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import it.fast4x.riplay.commonutils.YAMBO_ALBUM_SHARE_BASEURL
import it.fast4x.riplay.commonutils.slugify

@Immutable
@Entity
data class Album(
    @PrimaryKey val id: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val year: String? = null,
    val authorsText: String? = null,
    val shareUrl: String? = null,
    val timestamp: Long? = null,
    val bookmarkedAt: Long? = null,
    val isYoutubeAlbum: Boolean = false,
) {
    val shareYTUrl: String?
        get() = shareUrl?.replace("music.","www.")

    val shareYTMUrl: String?
        get() = shareUrl?.replace("www.","music.")

    val shareYamboUrl: String?
        get() = "${YAMBO_ALBUM_SHARE_BASEURL}$id/${slugify(title ?: "album")}"

    fun toggleBookmark(): Album {
        return copy(
            bookmarkedAt = if (bookmarkedAt == null) System.currentTimeMillis() else null
        )
    }
}
