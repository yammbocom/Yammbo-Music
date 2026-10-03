package it.fast4x.riplay.utils

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import it.fast4x.environment.Environment
import it.fast4x.environment.models.bodies.SearchBody
import it.fast4x.environment.requests.searchPage
import it.fast4x.environment.utils.from
import it.fast4x.riplay.data.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

val MediaItem.isMusicVideo: Boolean
    get() = mediaMetadata.extras?.let {
        it.getBoolean("isOfficialMusicVideo") || it.getBoolean("isUserGeneratedContent")
    } == true

/** Set on items built from a video entity (Video search tab, the player's video switch):
 *  the user asked for the video, so it is never swapped for the song. */
val MediaItem.isChosenVideo: Boolean
    get() = mediaMetadata.extras?.getBoolean(SongVersion.CHOSEN_VIDEO_EXTRA) == true

private val youTubeIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * A queue item that may be a music video standing in for a song, so worth looking up its song
 * version. Network items carry the YTM video type (OMV / UGC); rows from the database (saved
 * playlists, history) lose it, and there the stored video flag or the 16:9 i.ytimg.com frame is
 * the signal: songs (ATV) come with square lh3.googleusercontent.com art, the same rule
 * LocalPlaylistSongs uses to tell videos apart. Never a video the user picked as a video, a
 * device file, a station or a podcast episode.
 */
val MediaItem.mayHaveSongVersion: Boolean
    get() {
        if (isChosenVideo || isLocal || isRadio || isPodcast) return false
        if (!youTubeIdRegex.matches(mediaId)) return false
        if (isMusicVideo || isVideo) return true
        return mediaMetadata.artworkUri?.toString()?.contains("ytimg.com") == true
    }

/**
 * Finds the song (ATV) behind an official music video (OMV).
 *
 * YouTube Music builds a radio in the image of its seed: seeded with an OMV id it returns only
 * music videos, so every next item in the queue played as a video. The song/video counterpart
 * that music.youtube.com shows is only sent to signed-in sessions (an anonymous /next carries
 * no playlistPanelVideoWrapperRenderer), so the song is looked up with the Songs search filter,
 * the same search the player's "switch to song" button opens, and accepted only when the title
 * and an artist match.
 */
object SongVersion {

    /** [exact]: same title and same qualifiers (live, remix...): a stand-in for the item itself.
     *  Otherwise only the same song, good enough to seed a radio of related songs. */
    data class Match(val mediaItem: MediaItem, val exact: Boolean)

    private val cache = ConcurrentHashMap<String, Match>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    private val qualifiers = listOf(
        "live", "en vivo", "acoustic", "acustico", "remix", "cover", "instrumental",
        "karaoke", "sped up", "slowed", "unplugged", "version", "edit", "mix"
    )

    const val CHOSEN_VIDEO_EXTRA = "videoChosen"

    @OptIn(UnstableApi::class)
    suspend fun resolve(video: MediaItem): Match? {
        val videoId = video.mediaId
        cache[videoId]?.let { return it }
        if (videoId in misses) return null

        val title = video.mediaMetadata.title?.toString().orEmpty()
        val artists = artistsOf(video.mediaMetadata.artist?.toString())
        val baseTitle = baseTitle(title)
        if (baseTitle.isBlank() || artists.isEmpty()) return null

        val query = "${video.mediaMetadata.artist} ${stripBrackets(title)}".trim()
        // A missing answer only costs the song swap; it must never hold up the radio for long.
        val result = withTimeoutOrNull(6_000) {
            Environment.searchPage(
                body = SearchBody(query = query, params = Environment.SearchFilter.Song.value),
                fromMusicShelfRendererContent = Environment.SongItem.Companion::from
            )
        }
        // null = timeout or network error: not a "no song" answer, so it is not remembered.
        val items = result?.getOrNull()?.items ?: return null

        val wantedQualifiers = qualifiersOf(title)
        var loose: Match? = null
        // Listed by the Songs filter itself: the item already is the song (a database row whose
        // art looked like a video's), so there is nothing to swap it for.
        if (items.take(6).any {
                it.key == videoId && (it.isOfficialUploadByArtistContent || it.info?.endpoint?.type == null)
            }) {
            misses += videoId
            return null
        }
        for (item in items.take(6)) {
            if (item.key.isBlank() || item.key == videoId) continue
            if (!item.isOfficialUploadByArtistContent && item.info?.endpoint?.type != null) continue
            val candidateArtists = item.authors?.mapNotNull { it.name }?.flatMap { artistsOf(it) }.orEmpty()
            if (candidateArtists.none { it in artists }) continue
            val candidateTitle = item.info?.name.orEmpty()
            if (baseTitle(candidateTitle) != baseTitle) continue
            val match = Match(item.asMediaItem, exact = qualifiersOf(candidateTitle) == wantedQualifiers)
            if (match.exact) {
                loose = match
                break
            }
            if (loose == null) loose = match
        }

        Timber.d("SongVersion $videoId '$title' -> ${loose?.mediaItem?.mediaId} exact=${loose?.exact}")
        if (loose != null) cache[videoId] = loose else misses += videoId
        return loose
    }

    /** False for a song version the user blacklisted or disliked: the video stays then. */
    suspend fun isAcceptable(song: MediaItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Database.blacklisted(song.mediaId) == 0L && Database.getLikedAt(song.mediaId) != -1L
        }.getOrDefault(false)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('’', '\'')

    private fun stripBrackets(text: String): String =
        text.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun baseTitle(title: String): String =
        normalize(stripBrackets(title))
            // Only unambiguous featuring markers. A bare "con" is also an ordinary word ("Café con
            // leche" is not "Café"); in brackets it is already gone with them.
            .replace(Regex("\\s(ft\\.?|feat\\.?|featuring)\\s.*$"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun qualifiersOf(title: String): Set<String> {
        val text = " " + normalize(title).replace(Regex("[^\\p{L}\\p{N}]+"), " ") + " "
        return qualifiers.filter { text.contains(" $it ") }.toSet()
    }

    private fun artistsOf(names: String?): Set<String> =
        names.orEmpty()
            .split(Regex(",|&| y | and | x |\\bfeat\\.?|\\bft\\.?"))
            .map { normalize(it).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() }
            .filter { it.isNotBlank() }
            .toSet()
}
