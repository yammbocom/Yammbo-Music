package it.fast4x.riplay.service

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.models.BrowseEndpoint
import it.fast4x.environment.models.bodies.SearchBody
import it.fast4x.environment.requests.searchPage
import it.fast4x.environment.utils.completed
import it.fast4x.environment.utils.from
import it.fast4x.riplay.commonutils.MODIFIED_PREFIX
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.data.models.SongAlbumMap
import it.fast4x.riplay.data.models.SongArtistMap
import it.fast4x.riplay.enums.MaxTopPlaylistItems
import it.fast4x.riplay.enums.PlaylistSongSortBy
import it.fast4x.riplay.enums.SongSortBy
import it.fast4x.riplay.enums.SortOrder
import it.fast4x.riplay.extensions.ondevice.OnDeviceViewModel
import it.fast4x.riplay.extensions.preferences.MaxTopPlaylistItemsKey
import it.fast4x.riplay.extensions.preferences.getEnum
import it.fast4x.riplay.extensions.preferences.playlistSongSortByKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.songSortByKey
import it.fast4x.riplay.extensions.preferences.songSortOrderKey
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.isRadio
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Song lists behind the Android Auto browse tree, shared by the browser (to list them) and by the
 * media session (to play them).
 *
 * A playable item carries the node it was listed under in its media id, so tapping a song plays it
 * with the rest of that same list as the queue, and the list can be rebuilt from the database when
 * the process died between browsing and tapping. The in-memory cache only saves the rebuild (and,
 * for artists, albums and search, a network round trip).
 */
@UnstableApi
object CarLibrary {
    private const val PLAY = "play"
    private const val SHUFFLE_PLAY = "shuffleplay"

    /** Recently played songs (history). */
    const val RECENT = "recent"

    /** Browsable node grouping songs, artists, albums, top and radio. */
    const val LIBRARY = "library"

    /** Parent of search results: "search/<query>". */
    const val SEARCH = "search"

    // An unbounded list overruns the binder transaction limit
    private const val MAX_ITEMS = 500

    fun playId(parentId: String, songId: String) =
        "$PLAY/${Uri.encode(parentId)}/${Uri.encode(songId)}"

    fun shuffleId(parentId: String) = "$SHUFFLE_PLAY/${Uri.encode(parentId)}"

    fun searchParent(query: String) = "$SEARCH/$query"

    /** What a playable media id asks for; [songId] null means "the whole list". */
    class Target(val parentId: String, val songId: String?, val shuffle: Boolean)

    /**
     * Encoded parts never contain '/', so the split is exact. Ids from older builds
     * ("songs/<id>", "searched/<id>", "radio/<station id with slashes>") are still understood.
     */
    fun parse(mediaId: String): Target? {
        val parts = mediaId.split('/')
        return when (parts.firstOrNull()) {
            PLAY -> if (parts.size == 3) Target(Uri.decode(parts[1]), Uri.decode(parts[2]), false) else null
            SHUFFLE_PLAY -> if (parts.size == 2) Target(Uri.decode(parts[1]), null, true) else null
            PlayerMediaBrowserService.MediaId.RADIO ->
                mediaId.substringAfter('/', "").takeIf { it.isNotEmpty() }
                    ?.let { Target(PlayerMediaBrowserService.MediaId.RADIO, it, false) }
            PlayerMediaBrowserService.MediaId.SONGS,
            PlayerMediaBrowserService.MediaId.SEARCHED ->
                mediaId.substringAfter('/', "").takeIf { it.isNotEmpty() }
                    ?.let { Target("", it, false) }
            else -> null
        }
    }

    private val cache = object : LinkedHashMap<String, List<Song>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Song>>?) = size > 12
    }

    fun cached(parentId: String): List<Song>? = synchronized(cache) { cache[parentId] }

    private fun remember(parentId: String, songs: List<Song>) {
        if (songs.isNotEmpty()) synchronized(cache) { cache[parentId] = songs }
    }

    @Volatile
    private var onDeviceViewModel: OnDeviceViewModel? = null

    fun onDevice(context: Context): OnDeviceViewModel =
        onDeviceViewModel ?: synchronized(this) {
            onDeviceViewModel ?: OnDeviceViewModel(context.applicationContext as Application)
                .also { onDeviceViewModel = it }
        }

    /**
     * Songs of a list node. Blocking database and network calls: call it off the main thread.
     * [forPlayback] prefers what is already stored over the network where the browser would
     * fetch fresh data (artists), since the user is waiting for sound.
     */
    suspend fun songsFor(context: Context, parentId: String, forPlayback: Boolean = false): List<Song> {
        val head = parentId.substringBefore('/')
        val id = parentId.substringAfter('/', "")
        val prefs = context.preferences
        val songSortBy = prefs.getEnum(songSortByKey, SongSortBy.DateAdded)
        val songSortOrder = prefs.getEnum(songSortOrderKey, SortOrder.Descending)

        val songs: List<Song> = when (head) {
            PlayerMediaBrowserService.MediaId.SONGS -> Database
                .songs(songSortBy, songSortOrder, 0).first()
                .map { it.song }
                // Stations have their own Radio node
                .filterNot { it.isRadio }
                .take(MAX_ITEMS)

            PlayerMediaBrowserService.MediaId.SONGS_ONDEVICE -> Database
                .songsOnDevice().first()
                .take(MAX_ITEMS)

            PlayerMediaBrowserService.MediaId.SONGS_FAVORITES -> Database
                .songsFavorites(songSortBy, songSortOrder).first()
                .map { it.song }
                .filterNot { it.isRadio }
                .take(MAX_ITEMS)

            PlayerMediaBrowserService.MediaId.SONGS_TOP -> Database
                .trending(maxTopItems(context)).first()
                // A live station never ends: it must not be queued among songs
                .filterNot { it.isRadio }

            RECENT -> Database.lastPlayedSongsOnly(100).first()

            // Hearted stations first, then the ones played lately, one entry per station
            PlayerMediaBrowserService.MediaId.RADIO ->
                (Database.favoriteRadios().first() + Database.recentRadios(30).first())
                    .distinctBy { it.id }

            PlayerMediaBrowserService.MediaId.PLAYLISTS -> id.toLongOrNull()?.let { playlistId ->
                Database.songsPlaylist(
                    playlistId,
                    prefs.getEnum(playlistSongSortByKey, PlaylistSongSortBy.DateAdded),
                    songSortOrder
                ).first()
                    .map { it.song }
                    .filterNot { it.isRadio }
                    .take(MAX_ITEMS)
            } ?: emptyList()

            PlayerMediaBrowserService.MediaId.PLAYLISTS_ONDEVICE ->
                if (id.isEmpty()) emptyList()
                else onDevice(context).audioFilesFromFolder(id).first().map { it.song }

            PlayerMediaBrowserService.MediaId.ARTISTS_FAVORITES ->
                if (forPlayback) Database.artistAllSongs(id).first().ifEmpty { artistSongsOnline(id) }
                else artistSongsOnline(id).ifEmpty { Database.artistAllSongs(id).first() }

            PlayerMediaBrowserService.MediaId.ARTISTS_IN_LIBRARY ->
                Database.artistAllSongs(id).first().ifEmpty { artistSongsOnline(id) }

            PlayerMediaBrowserService.MediaId.ARTISTS_ONDEVICE ->
                Database.artistTopSongs(id, 100).first()

            PlayerMediaBrowserService.MediaId.ALBUMS_FAVORITES,
            PlayerMediaBrowserService.MediaId.ALBUMS_IN_LIBRARY -> albumSongs(id)

            PlayerMediaBrowserService.MediaId.ALBUMS_ON_DEVICE -> Database.albumSongs(id).first()

            SEARCH -> if (id.isBlank()) emptyList() else searchSongs(id)

            else -> emptyList()
        }

        remember(parentId, songs)
        return songs
    }

    fun maxTopItems(context: Context): Int =
        context.preferences.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`).number.toInt()

    /** Song filter of YouTube Music: audio song versions, not music videos. */
    suspend fun searchSongs(query: String): List<Song> =
        Environment.searchPage(
            body = SearchBody(query = query, params = Environment.SearchFilter.Song.value),
            fromMusicShelfRendererContent = Environment.SongItem.Companion::from
        )?.getOrNull()
            ?.items
            ?.filter { it.isAudioOnly && it.key.isNotEmpty() }
            ?.map { it.asSong }
            ?: emptyList()

    private suspend fun artistSongsOnline(artistId: String): List<Song> {
        val page = EnvironmentExt.getArtistPage(browseId = artistId).getOrNull() ?: return emptyList()
        val songSections = page.sections.filter { it.items.firstOrNull() is Environment.SongItem }
        val more = songSections.lastOrNull()?.moreEndpoint

        val songItems = more?.browseId
            ?.let { browseId ->
                EnvironmentExt.getArtistItemsPage(BrowseEndpoint(browseId = browseId, params = more?.params))
                    .completed().getOrNull()?.items?.filterIsInstance<Environment.SongItem>()
            }
            ?.takeIf { it.isNotEmpty() }
            // No "more" page: the few songs shown on the artist page itself
            ?: songSections.flatMap { it.items.filterIsInstance<Environment.SongItem>() }

        val songs = songItems.map { it.asSong }
        runCatching {
            val artist = Database.artist(artistId).first()
            songs.forEach { song ->
                Database.insert(song)
                if (artist != null) Database.insert(SongArtistMap(songId = song.id, artistId = artist.id))
            }
        }.onFailure { Timber.e("CarLibrary artistSongsOnline store failed ${it.message}") }
        return songs
    }

    private suspend fun albumSongs(albumId: String): List<Song> {
        val stored = Database.albumSongs(albumId).first()
        if (stored.isNotEmpty()) return stored

        val album = Database.album(albumId).first()
        val page = EnvironmentExt.getAlbum(albumId).getOrNull() ?: return emptyList()
        val songItems = page.songs.distinct()

        runCatching {
            val maps = songItems
                .map { it.asMediaItem }
                .onEach(Database::insert)
                .mapIndexed { position, mediaItem ->
                    SongAlbumMap(songId = mediaItem.mediaId, albumId = albumId, position = position)
                }
            Database.upsert(
                Album(
                    id = albumId,
                    title = album?.title ?: page.album.title,
                    thumbnailUrl = if (album?.thumbnailUrl?.startsWith(MODIFIED_PREFIX) == true)
                        album.thumbnailUrl else page.album.thumbnail?.url,
                    year = page.album.year,
                    authorsText = if (album?.authorsText?.startsWith(MODIFIED_PREFIX) == true)
                        album.authorsText else page.album.authors?.joinToString(", ") { it.name ?: "" },
                    shareUrl = page.url,
                    timestamp = System.currentTimeMillis(),
                    bookmarkedAt = album?.bookmarkedAt,
                    isYoutubeAlbum = album?.isYoutubeAlbum == true
                ),
                maps
            )
        }.onFailure { Timber.e("CarLibrary albumSongs store failed ${it.message}") }

        return songItems.map { it.asSong }
    }
}
