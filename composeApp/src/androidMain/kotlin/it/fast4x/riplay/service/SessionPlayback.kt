package it.fast4x.riplay.service

import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.environment.models.bodies.SearchBody
import it.fast4x.environment.requests.searchPage
import it.fast4x.environment.requests.song
import it.fast4x.environment.utils.from
import it.fast4x.riplay.commonutils.removePrefix
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.enums.PlaylistSongSortBy
import it.fast4x.riplay.enums.PlaylistSortBy
import it.fast4x.riplay.enums.SortOrder
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.isRadio
import it.fast4x.riplay.utils.isRadioId
import it.fast4x.riplay.utils.isPersistentQueueEnabled
import it.fast4x.riplay.utils.usesLocalPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * "Play this" requests that arrive from outside the app's own screens: a tap in the Android Auto
 * browse tree (play from media id) and voice searches ("Hey Google, play X on Yammbo Music"),
 * whether they come through the media session (car, Assistant with the app running) or through
 * the MEDIA_PLAY_FROM_SEARCH intent (Assistant on the phone, app closed).
 */
@UnstableApi
object SessionPlayback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // MediaStore.Audio.*.ENTRY_CONTENT_TYPE, spelled out: MediaStore.Audio.Playlists is deprecated
    private const val FOCUS_ARTIST = "vnd.android.cursor.item/artist"
    private const val FOCUS_ALBUM = "vnd.android.cursor.item/album"
    private const val FOCUS_SONG = "vnd.android.cursor.item/audio"
    private const val FOCUS_PLAYLIST = "vnd.android.cursor.item/playlist"

    private val favoritesQueries = setOf(
        "favoritos", "mis favoritos", "mis favoritas", "canciones favoritas", "mis canciones favoritas",
        "me gusta", "mis me gusta", "canciones que me gustan", "lo que me gusta",
        "favorites", "my favorites", "liked songs", "my liked songs", "liked", "my likes"
    )

    private var lastSearchKey: String? = null
    private var lastSearchAt = 0L
    private var lastMediaId: String? = null
    private var lastMediaIdAt = 0L

    // ---------------------------------------------------------------- play from media id

    fun playFromMediaId(binder: PlayerService.Binder, mediaId: String?) {
        if (mediaId.isNullOrEmpty()) return
        // prepareFromMediaId followed by playFromMediaId for the same item is one request
        val now = SystemClock.elapsedRealtime()
        val duplicate = synchronized(this) {
            (mediaId == lastMediaId && now - lastMediaIdAt < 4_000).also {
                lastMediaId = mediaId
                lastMediaIdAt = now
            }
        }
        if (duplicate) {
            Timber.d("SessionPlayback playFromMediaId ignoring duplicate $mediaId")
            return
        }
        scope.launch {
            runCatching { playFromMediaIdInternal(binder, mediaId) }
                .onFailure { Timber.e("SessionPlayback playFromMediaId $mediaId failed ${it.stackTraceToString()}") }
        }
    }

    private suspend fun playFromMediaIdInternal(binder: PlayerService.Binder, mediaId: String) {
        val target = CarLibrary.parse(mediaId) ?: run {
            Timber.w("SessionPlayback playFromMediaId unknown media id $mediaId")
            return
        }
        Timber.d("SessionPlayback playFromMediaId parent ${target.parentId} song ${target.songId} shuffle ${target.shuffle}")
        awaitRestoredQueue(binder)

        // The cached list is the one the user is looking at; rebuilt when the process was killed
        // in between, or when it no longer contains the tapped song.
        val songs = CarLibrary.cached(target.parentId)
            ?.takeIf { list -> target.songId == null || list.any { it.id == target.songId } }
            ?: if (target.parentId.isEmpty()) emptyList()
            else CarLibrary.songsFor(appContext(), target.parentId, forPlayback = true)

        if (target.shuffle) {
            if (songs.isNotEmpty()) playQueue(binder, songs.shuffled().map { it.asMediaItem }, 0)
            return
        }

        val songId = target.songId ?: return
        val index = songs.indexOfFirst { it.id == songId }
        val isSearch = target.parentId.startsWith("${CarLibrary.SEARCH}/")

        if (index >= 0 && !isSearch) {
            playQueue(binder, songs.map { it.asMediaItem }, index)
            return
        }

        // A search result (its neighbours are other matches, not a queue), a song no longer in its
        // list, or an id from an older build: play it alone and let similar songs follow.
        val mediaItem = songs.getOrNull(index)?.asMediaItem
            ?: Database.song(songId).first()?.asMediaItem
            ?: songId.takeUnless { it.isRadioId }
                ?.let { Environment.song(it)?.getOrNull()?.asMediaItem }
            ?: return
        playWithRadio(binder, mediaItem)
    }

    // ---------------------------------------------------------------- play from search

    /**
     * [onDone] runs on the main thread once the request was handled (or dropped), whatever the
     * outcome, so a caller that bound the service only for this can unbind.
     */
    fun playFromSearch(
        binder: PlayerService.Binder,
        query: String?,
        extras: Bundle?,
        onDone: (() -> Unit)? = null
    ) {
        val cleanQuery = query?.trim().orEmpty()
        val focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)

        // Assistant may send prepareFromSearch and then playFromSearch for the same request
        val key = "${cleanQuery.lowercase()}|$focus"
        val now = SystemClock.elapsedRealtime()
        val duplicate = synchronized(this) {
            (key == lastSearchKey && now - lastSearchAt < 4_000).also {
                lastSearchKey = key
                lastSearchAt = now
            }
        }
        if (duplicate) {
            Timber.d("SessionPlayback playFromSearch ignoring duplicate '$cleanQuery'")
            onDone?.invoke()
            return
        }

        scope.launch {
            Timber.d("SessionPlayback playFromSearch query '$cleanQuery' focus $focus extras ${extras?.keySet()}")
            val played = runCatching { search(binder, cleanQuery, extras) }
                .onFailure { Timber.e("SessionPlayback playFromSearch failed ${it.stackTraceToString()}") }
                .getOrDefault(false)

            if (!played) {
                val context = appContext()
                SmartMessage(
                    if (cleanQuery.isEmpty()) context.getString(R.string.voice_search_nothing_to_play)
                    else context.getString(R.string.voice_search_no_results, cleanQuery),
                    context = context
                )
            }
            withContext(Dispatchers.Main) { onDone?.invoke() }
        }
    }

    private suspend fun search(binder: PlayerService.Binder, query: String, extras: Bundle?): Boolean {
        val focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.trim().orEmpty()
        val album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.trim().orEmpty()
        val title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.trim().orEmpty()
        val playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)?.trim().orEmpty()

        awaitRestoredQueue(binder)

        // "Play music on Yammbo Music"
        if (query.isEmpty() && artist.isEmpty() && album.isEmpty() && title.isEmpty() && playlist.isEmpty())
            return resumeOrDefault(binder)

        return when (focus) {
            FOCUS_ARTIST -> {
                val name = artist.ifEmpty { query }
                playArtist(binder, name) || playBestSong(binder, query.ifEmpty { name })
            }

            FOCUS_ALBUM -> {
                val text = listOf(album.ifEmpty { query }, artist).filter { it.isNotEmpty() }.joinToString(" ")
                playAlbum(binder, text) || playBestSong(binder, query.ifEmpty { text })
            }

            FOCUS_PLAYLIST -> {
                val name = playlist.ifEmpty { query }
                playLocalPlaylist(binder, name, exact = false)
                        || playOnlinePlaylist(binder, name)
                        || playBestSong(binder, name)
            }

            FOCUS_SONG -> {
                val text = if (title.isNotEmpty()) listOf(title, artist).filter { it.isNotEmpty() }.joinToString(" ")
                else query
                playBestSong(binder, text)
            }

            // Genre, "anything" or no focus at all: a plain query
            else -> {
                val text = query.ifEmpty { listOf(title, artist, album, playlist).firstOrNull { it.isNotEmpty() }.orEmpty() }
                (text.lowercase() in favoritesQueries && playFavorites(binder))
                        || playLocalPlaylist(binder, text, exact = true)
                        || playBestSong(binder, text)
            }
        }
    }

    /** Resume what is queued; with nothing queued, the liked songs shuffled, else the history. */
    private suspend fun resumeOrDefault(binder: PlayerService.Binder): Boolean {
        // On a cold start the saved queue is restored asynchronously
        withTimeoutOrNull(3_000) {
            while (withContext(Dispatchers.Main) { binder.player.mediaItemCount } == 0) delay(200)
        }
        val resumed = withContext(Dispatchers.Main) {
            if (binder.player.mediaItemCount > 0) {
                // Through the session callback, which owns the online/local/cast play logic
                binder.mediaSession.controller.transportControls.play()
                true
            } else false
        }
        if (resumed) return true
        if (playFavorites(binder)) return true

        val recent = Database.lastPlayedSongsOnly(50).first()
        if (recent.isEmpty()) return false
        playQueue(binder, recent.shuffled().map { it.asMediaItem }, 0)
        return true
    }

    private suspend fun playFavorites(binder: PlayerService.Binder): Boolean {
        val favorites = Database.favorites().first().filterNot { it.isRadio }.take(300)
        if (favorites.isEmpty()) return false
        playQueue(binder, favorites.shuffled().map { it.asMediaItem }, 0)
        return true
    }

    /** Best "song" match (audio version), then a radio of similar songs. */
    private suspend fun playBestSong(binder: PlayerService.Binder, text: String): Boolean {
        if (text.isBlank()) return false
        val song = CarLibrary.searchSongs(text).firstOrNull() ?: return false
        playWithRadio(binder, song.asMediaItem)
        return true
    }

    private suspend fun playArtist(binder: PlayerService.Binder, name: String): Boolean {
        if (name.isBlank()) return false
        val artist = Environment.searchPage(
            body = SearchBody(query = name, params = Environment.SearchFilter.Artist.value),
            fromMusicShelfRendererContent = { content -> Environment.ArtistItem.from(content) }
        )?.getOrNull()?.items?.firstOrNull { it.key.isNotEmpty() } ?: return false

        val page = EnvironmentExt.getArtistPage(browseId = artist.key).getOrNull() ?: return false

        // Shuffle = the artist's own songs; radio = the artist mixed with similar ones
        val endpoint = page.shuffleEndpoint ?: page.radioEndpoint
        if (endpoint != null) {
            withContext(Dispatchers.Main) {
                markRequest()
                binder.stopRadio()
                binder.playRadio(endpoint)
            }
            return true
        }

        val songs = page.sections
            .flatMap { section -> section.items.filterIsInstance<Environment.SongItem>() }
            .distinctBy { it.key }
        if (songs.isEmpty()) return false
        playQueue(binder, songs.map { it.asMediaItem }, 0)
        return true
    }

    private suspend fun playAlbum(binder: PlayerService.Binder, text: String): Boolean {
        if (text.isBlank()) return false
        val album = Environment.searchPage(
            body = SearchBody(query = text, params = Environment.SearchFilter.Album.value),
            fromMusicShelfRendererContent = { content -> Environment.AlbumItem.from(content) }
        )?.getOrNull()?.items?.firstOrNull { it.key.isNotEmpty() } ?: return false

        val songs = EnvironmentExt.getAlbum(album.key).getOrNull()?.songs?.distinct().orEmpty()
        if (songs.isEmpty()) return false
        playQueue(binder, songs.map { it.asMediaItem }, 0)
        return true
    }

    /** One of the user's own playlists, by name. */
    private suspend fun playLocalPlaylist(binder: PlayerService.Binder, name: String, exact: Boolean): Boolean {
        val wanted = name.trim().lowercase()
        if (wanted.isEmpty()) return false

        val previews = Database.playlistPreviews(PlaylistSortBy.Name, SortOrder.Ascending).first()
        fun nameOf(preview: PlaylistPreview) =
            preview.playlist.name.removePrefix().trim().lowercase()

        val preview = previews.firstOrNull { nameOf(it) == wanted }
            ?: if (exact) null
            else previews.firstOrNull { nameOf(it).startsWith(wanted) }
                ?: previews.firstOrNull { nameOf(it).contains(wanted) }
        preview ?: return false

        val songs = Database.songsPlaylist(preview.playlist.id, PlaylistSongSortBy.Position, SortOrder.Ascending)
            .first()
            .map { it.song }
            .filterNot { it.isRadio }
        if (songs.isNotEmpty()) {
            playQueue(binder, songs.map { it.asMediaItem }, 0)
            return true
        }

        // A saved YouTube playlist whose songs were never stored here
        val playlistId = preview.playlist.browseId?.withoutVlPrefix()?.takeIf { it.isNotBlank() } ?: return false
        withContext(Dispatchers.Main) {
            markRequest()
            binder.stopRadio()
            binder.playRadio(NavigationEndpoint.Endpoint.Watch(playlistId = playlistId))
        }
        return true
    }

    private suspend fun playOnlinePlaylist(binder: PlayerService.Binder, name: String): Boolean {
        if (name.isBlank()) return false
        val playlist = listOf(
            Environment.SearchFilter.FeaturedPlaylist,
            Environment.SearchFilter.CommunityPlaylist
        ).firstNotNullOfOrNull { filter ->
            Environment.searchPage(
                body = SearchBody(query = name, params = filter.value),
                fromMusicShelfRendererContent = { content -> Environment.PlaylistItem.from(content) }
            )?.getOrNull()?.items?.firstOrNull { it.key.isNotEmpty() }
        } ?: return false

        withContext(Dispatchers.Main) {
            markRequest()
            binder.stopRadio()
            binder.playRadio(NavigationEndpoint.Endpoint.Watch(playlistId = playlist.key.withoutVlPrefix()))
        }
        return true
    }

    /**
     * On a cold start the service restores the saved queue asynchronously, with setMediaItems:
     * playing before that lands would have the request replaced by the old queue. An empty saved
     * queue never signals, hence the short bound.
     */
    private suspend fun awaitRestoredQueue(binder: PlayerService.Binder) {
        if (!isPersistentQueueEnabled() || binder.isQueueReady.value) return
        withTimeoutOrNull(1_500) { binder.isQueueReady.first { it } }
    }

    @Volatile
    private var lastRequestPlayedAt = 0L

    private fun markRequest() {
        lastRequestPlayedAt = SystemClock.elapsedRealtime()
    }

    /**
     * True for a minute after a car tap or voice request started playback. The cold-start restore
     * (saved queue, saved song, resume on start) checks it so it does not replace that request.
     */
    fun hasRecentRequest(): Boolean =
        lastRequestPlayedAt != 0L && SystemClock.elapsedRealtime() - lastRequestPlayedAt < 60_000

    // A playlist browse id is "VL" + the playlist id that a watch endpoint expects
    private fun String.withoutVlPrefix() = if (startsWith("VL")) substring(2) else this

    // ---------------------------------------------------------------- player

    private suspend fun playQueue(binder: PlayerService.Binder, items: List<MediaItem>, index: Int) {
        if (items.isEmpty()) return
        withContext(Dispatchers.Main) {
            markRequest()
            binder.stopRadio()
            binder.player.forcePlayAtIndex(items, index)
        }
    }

    private suspend fun playWithRadio(binder: PlayerService.Binder, item: MediaItem) {
        withContext(Dispatchers.Main) {
            markRequest()
            binder.stopRadio()
            binder.player.forcePlay(item)
            // Same as a tap on a song in the app: similar songs follow it
            if (!item.usesLocalPlayer)
                binder.setupRadio(
                    swapSeed = true,
                    endpoint = NavigationEndpoint.Endpoint.Watch(videoId = item.mediaId)
                )
        }
    }
}
