package it.fast4x.riplay.data.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import it.fast4x.riplay.commonutils.MONTHLY_PREFIX
import it.fast4x.riplay.commonutils.PINNED_PREFIX
import it.fast4x.riplay.commonutils.YAMBO_PLAYLIST_SHARE_BASEURL
import it.fast4x.riplay.commonutils.YTM_PLAYLIST_SHARE_BASEURL
import it.fast4x.riplay.commonutils.YT_PLAYLIST_SHARE_BASEURL
import it.fast4x.riplay.commonutils.slugify

private const val PODCAST_BROWSE_PREFIX = "MPSP"
private const val YAMBO_PODCAST_SHARE_BASEURL = "https://music.yammbo.com/podcast/"

@Immutable
@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val browseId: String? = null,
    val isEditable: Boolean = true,
    val isYoutubePlaylist: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isPodcast: Boolean = false,
) {
    // Transient cover URL carried into FastShare so podcasts can be shared with
    // their artwork. Not a Room column (@Ignore) — avoids a schema migration.
    @androidx.room.Ignore
    var thumbnailUrl: String? = null

    // Podcast shows are browse ids starting with MPSP; the web has its own /podcast/ route for them.
    val isPodcastShow: Boolean
        get() = browseId?.startsWith(PODCAST_BROWSE_PREFIX) == true

    // YouTube playlist ids never start with MPSP, so stripping it is what turns a podcast show
    // id into a list= value that actually opens (MPSPPL... is not a valid one).
    val shareYTUrl: String?
        get() = browseId?.let { "$YT_PLAYLIST_SHARE_BASEURL${it.removePrefix("VL").removePrefix(PODCAST_BROWSE_PREFIX)}" }
    val shareYTMUrl: String?
        get() =  browseId?.let { "$YTM_PLAYLIST_SHARE_BASEURL${it.removePrefix("VL").removePrefix(PODCAST_BROWSE_PREFIX)}" }
    val shareYTUrlAsPodcast: String?
        get() = browseId?.let { "$YT_PLAYLIST_SHARE_BASEURL${it.removePrefix(PODCAST_BROWSE_PREFIX)}" }
    val shareYTMUrlAsPodcast: String?
        get() =  browseId?.let { "$YTM_PLAYLIST_SHARE_BASEURL${it.removePrefix(PODCAST_BROWSE_PREFIX)}" }

    val shareYamboUrl: String?
        get() = browseId?.let { id ->
            // An empty slug (title with no latin letters) must not leave a trailing slash:
            // the site answers that with a redirect.
            val slug = slugify(name).let { if (it.isEmpty()) "" else "/$it" }
            if (isPodcastShow) "$YAMBO_PODCAST_SHARE_BASEURL$id$slug"
            else "${YAMBO_PLAYLIST_SHARE_BASEURL}${id.removePrefix("VL")}$slug"
        }

    val isPinned: Boolean
        get() = name.startsWith(PINNED_PREFIX)
    val isMonthly: Boolean
        get() = name.startsWith(MONTHLY_PREFIX)

    fun toPlaylistPreview(songs: Int): PlaylistPreview {
        return PlaylistPreview(
            playlist = this,
            songCount = songs
        )
    }

}
