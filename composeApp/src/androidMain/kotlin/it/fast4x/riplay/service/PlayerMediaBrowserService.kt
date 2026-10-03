package it.fast4x.riplay.service

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import androidx.annotation.DrawableRes
import androidx.compose.ui.util.fastFilter
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.util.UnstableApi
import it.fast4x.riplay.commonutils.MONTHLY_PREFIX
import it.fast4x.riplay.commonutils.PINNED_PREFIX
import com.yambo.music.R
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.AlbumSortBy
import it.fast4x.riplay.enums.ArtistSortBy
import it.fast4x.riplay.enums.SortOrder
import it.fast4x.riplay.extensions.preferences.getEnum
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.commonutils.removePrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.enums.HomeItemSize
import it.fast4x.riplay.enums.PlaylistSortBy
import it.fast4x.riplay.extensions.preferences.albumSortByKey
import it.fast4x.riplay.extensions.preferences.albumSortOrderKey
import it.fast4x.riplay.extensions.preferences.albumsItemSizeKey
import it.fast4x.riplay.extensions.preferences.artistSortByKey
import it.fast4x.riplay.extensions.preferences.artistSortOrderKey
import it.fast4x.riplay.extensions.preferences.artistsItemSizeKey
import it.fast4x.riplay.extensions.preferences.playlistSongSortByKey
import it.fast4x.riplay.extensions.preferences.playlistSortByKey
import it.fast4x.riplay.extensions.preferences.songSortByKey
import it.fast4x.riplay.extensions.preferences.songSortOrderKey
import it.fast4x.riplay.utils.getTitleMonthlyPlaylist
import it.fast4x.riplay.utils.intent
import it.fast4x.riplay.utils.isRadio
import it.fast4x.riplay.utils.showFavoritesPlaylistsAA
import it.fast4x.riplay.utils.showGridAA
import it.fast4x.riplay.utils.showInLibraryAA
import it.fast4x.riplay.utils.showMonthlyPlaylistsAA
import it.fast4x.riplay.utils.showOnDeviceAA
import it.fast4x.riplay.utils.showTopPlaylistAA
import it.fast4x.riplay.utils.shuffleSongsAAEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@UnstableApi
class PlayerMediaBrowserService : MediaBrowserServiceCompat(),
    ServiceConnection,
    SharedPreferences.OnSharedPreferenceChangeListener {

    // Children and search results are built here (database and network) and sent back with
    // Result.sendResult once ready: blocking the binder thread made Android Auto time out.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var playlistSortBy: PlaylistSortBy = PlaylistSortBy.DateAdded
    private var artistSortBy: ArtistSortBy = ArtistSortBy.DateAdded
    private var albumSortBy: AlbumSortBy = AlbumSortBy.DateAdded

    private var songSortOrder: SortOrder = SortOrder.Descending
    private var artistSortOrder: SortOrder = SortOrder.Descending
    private var albumSortOrder: SortOrder = SortOrder.Descending

    private var bindRequested = false
    private var playerServiceBinder: PlayerService.Binder? = null

    // Android Auto passes how many root children it can show as tabs (4 on current versions)
    private var rootChildrenLimit = DEFAULT_ROOT_CHILDREN_LIMIT


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        sharedPreferences ?: return
        Timber.d("PlayerMediaBrowserService onSharedPreferenceChanged $key")
        when (key) {
            songSortByKey, songSortOrderKey -> {
                songSortOrder = sharedPreferences.getEnum(songSortOrderKey, SortOrder.Descending)
                notifyChildrenChanged(MediaId.SONGS)
                notifyChildrenChanged(MediaId.SONGS_FAVORITES)
            }

            artistSortOrderKey, artistSortByKey -> {
                artistSortOrder = sharedPreferences.getEnum(artistSortOrderKey, SortOrder.Descending)
                artistSortBy = sharedPreferences.getEnum(artistSortByKey, ArtistSortBy.DateAdded)
                notifyChildrenChanged(MediaId.ARTISTS_FAVORITES)
            }

            albumSortOrderKey, albumSortByKey -> {
                albumSortOrder = sharedPreferences.getEnum(albumSortOrderKey, SortOrder.Descending)
                albumSortBy = sharedPreferences.getEnum(albumSortByKey, AlbumSortBy.DateAdded)
                notifyChildrenChanged(MediaId.ALBUMS_FAVORITES)
            }

            playlistSongSortByKey, playlistSortByKey -> {
                playlistSortBy = sharedPreferences.getEnum(playlistSortByKey, PlaylistSortBy.DateAdded)
                notifyChildrenChanged(MediaId.PLAYLISTS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        songSortOrder = preferences.getEnum(songSortOrderKey, SortOrder.Descending)
        playlistSortBy = preferences.getEnum(playlistSortByKey, PlaylistSortBy.DateAdded)
        artistSortBy = preferences.getEnum(artistSortByKey, ArtistSortBy.DateAdded)
        artistSortOrder = preferences.getEnum(artistSortOrderKey, SortOrder.Descending)
        albumSortBy = preferences.getEnum(albumSortByKey, AlbumSortBy.DateAdded)
        albumSortOrder = preferences.getEnum(albumSortOrderKey, SortOrder.Descending)

        preferences.registerOnSharedPreferenceChangeListener(this)

        Timber.d("PlayerMediaBrowserService onCreate")
    }


    override fun onDestroy() {
        serviceScope.cancel()
        if (bindRequested) {
            runCatching { unbindService(this) }
            bindRequested = false
        }
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    @UnstableApi
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        if (service is PlayerService.Binder) {
            playerServiceBinder = service
            // Clients that connected before this point (onGetRoot has to answer right away) are
            // kept pending by MediaBrowserServiceCompat and get onConnect when the token is set.
            // The token can be set only once: a second set throws IllegalStateException.
            if (sessionToken == null) sessionToken = service.mediaSession.sessionToken
            service.cancelSleepTimer()
            // IMPORTANT: Do not override the MediaSession callback here.
            // PlayerService owns the callback and implements the authoritative queue/skip logic.
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        playerServiceBinder = null
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        // Every client (car, Assistant, system media controls) ends up here: bind once
        if (!bindRequested) {
            bindRequested = runCatching { bindService(intent<PlayerService>(), this, BIND_AUTO_CREATE) }
                .onFailure { Timber.e("PlayerMediaBrowserService bindService failed ${it.message}") }
                .getOrDefault(false)
        }
        rootHints?.getInt(ROOT_CHILDREN_LIMIT_HINT, 0)?.takeIf { it > 0 }?.let { rootChildrenLimit = it }

        return BrowserRoot(
            MediaId.ROOT,
            Bundle().apply {
                putBoolean(MEDIA_SEARCH_SUPPORTED, true)
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, if (showGridAA()) CONTENT_STYLE_GRID else CONTENT_STYLE_LIST)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
            }
        )
    }

    override fun onSearch(
        query: String,
        extras: Bundle?,
        result: Result<List<MediaItem>>
    ) {
        playerServiceBinder?.cancelSleepTimer()

        result.detach()
        serviceScope.launch {
            val parentId = CarLibrary.searchParent(query.trim())
            val items = runCatching {
                if (query.isBlank()) emptyList()
                else CarLibrary.songsFor(this@PlayerMediaBrowserService, parentId)
                    .map { it.asPlayableMediaItem(parentId) }
            }.onFailure {
                Timber.e("PlayerMediaBrowserService onSearch '$query' failed ${it.stackTraceToString()}")
            }.getOrDefault(emptyList())

            withContext(Dispatchers.Main) {
                runCatching { result.sendResult(items) }
                    .onFailure { Timber.e("PlayerMediaBrowserService onSearch sendResult ${it.message}") }
            }
        }
    }


    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaItem?>?>
    ) {
        playerServiceBinder?.cancelSleepTimer()
        Timber.d("PlayerMediaBrowserService onLoadChildren parentId $parentId")

        result.detach()
        serviceScope.launch {
            // A failure must still answer, or the car keeps spinning on that node
            val items = runCatching { children(parentId) }
                .onFailure {
                    Timber.e("PlayerMediaBrowserService onLoadChildren $parentId failed ${it.stackTraceToString()}")
                }
                .getOrDefault(emptyList())

            withContext(Dispatchers.Main) {
                runCatching { result.sendResult(items) }
                    .onFailure { Timber.e("PlayerMediaBrowserService onLoadChildren sendResult ${it.message}") }
            }
        }
    }

    private suspend fun children(parentId: String): List<MediaItem> {
        val head = parentId.substringBefore('/')
        val id = parentId.substringAfter('/', "")

        return when (head) {
            MediaId.FAULT -> listOf(faultBrowserMediaItem)

            // Tabs on Android Auto, most used first
            MediaId.ROOT -> listOf(
                recentBrowserMediaItem,
                favoritesBrowserMediaItem,
                playlistsBrowserMediaItem,
                libraryBrowserMediaItem
            ).take(rootChildrenLimit.coerceAtLeast(1))

            CarLibrary.LIBRARY -> buildList {
                add(songsBrowserMediaItem)
                add(artistsFavoritesBrowserMediaItem)
                add(albumsFavoritesBrowserMediaItem)
                add(radioBrowserMediaItem)
                add(topBrowserMediaItem)
                if (showOnDeviceAA()) add(ondeviceBrowserMediaItem)
            }

            MediaId.SONGS -> songListItems(parentId).toMutableList().apply {
                // Browsable shortcuts kept for the existing Android Auto settings
                if (showOnDeviceAA()) add(0, ondeviceBrowserMediaItem)
                if (showTopPlaylistAA()) add(0, topBrowserMediaItem)
                if (showFavoritesPlaylistsAA()) add(0, favoritesBrowserMediaItem)
            }

            CarLibrary.RECENT,
            MediaId.SONGS_FAVORITES,
            MediaId.SONGS_TOP,
            MediaId.SONGS_ONDEVICE -> songListItems(parentId)

            // Stations only, so next/previous in the car switch station
            MediaId.RADIO -> CarLibrary.songsFor(this, parentId)
                .map { it.asPlayableMediaItem(parentId) }

            MediaId.PLAYLISTS -> if (id.isEmpty()) {
                playlistPreviews { showMonthlyPlaylistsAA() || !it.playlist.name.startsWith(MONTHLY_PREFIX) }
                    .map { it.asCleanMediaItem }
                    .toMutableList()
                    .apply {
                        add(0, playlistsInLibraryBrowserMediaItem)
                        add(1, playlistsPodcastBrowserMediaItem)
                        add(2, playlistsPinnedBrowserMediaItem)
                        add(3, playlistsMonthlyBrowserMediaItem)
                        add(4, playlistsOnDeviceBrowserMediaItem)
                    }
            } else songListItems(parentId)

            MediaId.PLAYLISTS_IN_LIBRARY -> playlistPreviews { it.playlist.isYoutubePlaylist }.map { it.asCleanMediaItem }
            MediaId.PLAYLISTS_PODCAST -> playlistPreviews { it.playlist.isPodcast }.map { it.asCleanMediaItem }
            MediaId.PLAYLISTS_PINNED -> playlistPreviews { it.playlist.isPinned }.map { it.asCleanMediaItem }
            MediaId.PLAYLISTS_MONTHLY -> playlistPreviews { it.playlist.isMonthly }.map { it.asCleanMediaItem }

            MediaId.PLAYLISTS_ONDEVICE -> if (id.isEmpty()) onDeviceFolders() else songListItems(parentId)

            MediaId.ARTISTS_FAVORITES -> if (id.isEmpty()) {
                Database
                    .artists(artistSortBy, artistSortOrder)
                    .first()
                    .map { it.asBrowserMediaItem(MediaId.ARTISTS_FAVORITES) }
                    .toMutableList()
                    .apply {
                        if (showOnDeviceAA()) add(0, artistsOnDeviceBrowserMediaItem)
                        if (showInLibraryAA()) add(0, artistsInLibraryBrowserMediaItem)
                    }
            } else songListItems(parentId)

            MediaId.ARTISTS_ONDEVICE -> if (id.isEmpty()) {
                Database
                    .artistsOnDevice(artistSortBy, artistSortOrder)
                    .first()
                    .map { it.asBrowserMediaItem(MediaId.ARTISTS_ONDEVICE) }
            } else songListItems(parentId)

            MediaId.ARTISTS_IN_LIBRARY -> if (id.isEmpty()) {
                Database
                    .artistsInLibrary(artistSortBy, artistSortOrder)
                    .first()
                    .map { it.asBrowserMediaItem(MediaId.ARTISTS_IN_LIBRARY) }
            } else songListItems(parentId)

            MediaId.ALBUMS_FAVORITES -> if (id.isEmpty()) {
                Database
                    .albums(albumSortBy, albumSortOrder)
                    .first()
                    .map { it.asBrowserMediaItem(MediaId.ALBUMS_FAVORITES) }
                    .toMutableList()
                    .apply {
                        if (showOnDeviceAA()) add(0, albumsOnDeviceBrowserMediaItem)
                        if (showInLibraryAA()) add(0, albumsInLibraryBrowserMediaItem)
                    }
            } else songListItems(parentId)

            MediaId.ALBUMS_ON_DEVICE -> if (id.isEmpty()) {
                Database
                    .albumsOnDevice(albumSortBy, albumSortOrder)
                    .first()
                    .map { it.asBrowserMediaItem(MediaId.ALBUMS_ON_DEVICE) }
            } else songListItems(parentId)

            MediaId.ALBUMS_IN_LIBRARY -> if (id.isEmpty()) {
                Database
                    .albumsInLibrary(albumSortBy, albumSortOrder)
                    .first()
                    .map { it.asBrowserMediaItem(MediaId.ALBUMS_IN_LIBRARY) }
            } else songListItems(parentId)

            else -> emptyList()
        }
    }

    /** The songs of a list node, led by a "shuffle play" entry when there is something to shuffle. */
    private suspend fun songListItems(parentId: String): List<MediaItem> {
        val songs = CarLibrary.songsFor(this, parentId)
        return buildList {
            if (songs.size > 1 && shuffleSongsAAEnabled()) add(shufflePlayMediaItem(parentId))
            songs.forEach { add(it.asPlayableMediaItem(parentId)) }
        }
    }

    private suspend fun playlistPreviews(filter: (PlaylistPreview) -> Boolean): List<MediaItem> =
        Database
            .playlistPreviews(playlistSortBy, songSortOrder)
            .first()
            .fastFilter(filter)
            .map { it.asBrowserMediaItem(Database.playlistThumbnailUrls(it.playlist.id).first().take(1)) }
            .sortedBy { it.description.title.toString() }

    private suspend fun onDeviceFolders(): List<MediaItem> {
        val folders = CarLibrary.onDevice(this).audioFoldersAsPlaylists().first()
        return when (playlistSortBy) {
            PlaylistSortBy.Name -> when (songSortOrder) {
                SortOrder.Ascending -> folders.sortedBy { it.playlist.name }
                SortOrder.Descending -> folders.sortedByDescending { it.playlist.name }
            }

            PlaylistSortBy.DateAdded, PlaylistSortBy.MostPlayed -> when (songSortOrder) {
                SortOrder.Ascending -> folders.sortedBy { it.totalPlayTimeMs }
                SortOrder.Descending -> folders.sortedByDescending { it.totalPlayTimeMs }
            }

            PlaylistSortBy.SongCount -> when (songSortOrder) {
                SortOrder.Ascending -> folders.sortedBy { it.songCount }
                SortOrder.Descending -> folders.sortedByDescending { it.songCount }
            }
        }
            .map { it.asBrowserMediaItem(Database.playlistThumbnailUrls(it.playlist.id).first().take(1), true) }
            .sortedBy { it.description.title.toString() }
            .map { it.asCleanMediaItem }
    }

    private fun uriFor(@DrawableRes id: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(resources.getResourcePackageName(id))
        .appendPath(resources.getResourceTypeName(id))
        .appendPath(resources.getResourceEntryName(id))
        .build()


    /**
     * A song (or station) as shown in the car. Its media id names [parentId], so playing it
     * queues the rest of that list (see CarLibrary and SessionPlayback).
     */
    private fun Song.asPlayableMediaItem(parentId: String) = MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(CarLibrary.playId(parentId, id))
            .setTitle(title.removePrefix())
            .setSubtitle(artistsText)
            .setIconUri(
                if (isRadio) thumbnailUrl?.toUri() ?: uriFor(R.drawable.radio)
                // Sized for the car screen: the stored url is often a small list thumbnail
                else thumbnailUrl.thumbnail(CAR_ARTWORK_SIZE)?.toUri()
            )
            .build(),
        MediaItem.FLAG_PLAYABLE
    )

    private fun shufflePlayMediaItem(parentId: String) = MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(CarLibrary.shuffleId(parentId))
            .setTitle(getString(R.string.aa_shuffle_play))
            .setIconUri(uriFor(R.drawable.shuffle))
            .build(),
        MediaItem.FLAG_PLAYABLE
    )

    private fun PlaylistPreview.asBrowserMediaItem(thumbnailUrls: List<String?>, onDevice: Boolean? = false) =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(if (onDevice == false) MediaId.forPlaylist(playlist.id) else MediaId.forPlaylistOnDevice(folder ?: ""))
                .setTitle(if (playlist.name.startsWith(PINNED_PREFIX)) playlist.name.replace(PINNED_PREFIX,"0:",true) else
                    if (playlist.name.startsWith(MONTHLY_PREFIX)) playlist.name.replace(
                        MONTHLY_PREFIX,"1:",true) else playlist.name.removePrefix())
                .setSubtitle("$songCount ${(this@PlayerMediaBrowserService as Context).resources.getString(R.string.songs)}")
                .setIconUri(
                    if (playlist.browseId?.trim() == "LM") "https://www.gstatic.com/youtube/media/ytm/images/pbg/liked-music-@1200.png".toUri()
                    else {
                        uriFor(
                            if (playlist.name.startsWith(PINNED_PREFIX)) R.drawable.pin else
                                if (playlist.name.startsWith(MONTHLY_PREFIX)) R.drawable.stat_month else R.drawable.playlist
                        )
                    }
                )
                .setExtras(
                    bundleOf(
                        "browseId" to playlist.browseId,
                    ).apply {
                        thumbnailUrls.forEachIndexed { index, url ->
                            putString("thumbnailUrl$index", url)
                        }
                    }
                )
                .build(),
            MediaItem.FLAG_BROWSABLE
        )

    private fun Album.asBrowserMediaItem(type: String) =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(
                    when(type) {
                        MediaId.ALBUMS_FAVORITES -> MediaId.forAlbumFavorites(id)
                        MediaId.ALBUMS_ON_DEVICE -> MediaId.forAlbumOnDevice(id)
                        MediaId.ALBUMS_IN_LIBRARY -> MediaId.forAlbumInLibrary(id)
                        else -> MediaId.forAlbumFavorites(id)
                    }
                )
                .setTitle(title?.removePrefix())
                .setSubtitle(authorsText)
                .setIconUri(thumbnailUrl?.thumbnail(preferences.getEnum(albumsItemSizeKey,HomeItemSize.BIG).size)?.toUri())
                .build(),
            MediaItem.FLAG_BROWSABLE
        )

    private fun Artist.asBrowserMediaItem(type: String) =
        MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(
                    when(type) {
                        MediaId.ARTISTS_FAVORITES -> MediaId.forArtistFavorites(id)
                        MediaId.ARTISTS_ONDEVICE -> MediaId.forArtistOnDevice(id)
                        MediaId.ARTISTS_IN_LIBRARY -> MediaId.forArtistInLibrary(id)
                        else -> MediaId.forArtistFavorites(id)
                    }
                )
                .setTitle(name?.removePrefix())
                .setIconUri(thumbnailUrl?.thumbnail(preferences.getEnum(artistsItemSizeKey,HomeItemSize.BIG).size)?.toUri())
                .build(),
            MediaItem.FLAG_BROWSABLE
        )

    private val MediaItem.asCleanMediaItem
        inline get() = MediaItem(
            MediaDescriptionCompat.Builder()
                .setMediaId(mediaId)
                .setTitle(if (description.title.toString().startsWith("0:")) description.title.toString().substringAfter("0:") else
                    if (description.title.toString().startsWith("1:")) getTitleMonthlyPlaylist(description.title.toString().substringAfter("1:"), this@PlayerMediaBrowserService) else description.title.toString())
                .setSubtitle(description.subtitle)
                .setIconUri(
                    when {
                        description.extras?.getString("browseId") == "LM" -> "https://www.gstatic.com/youtube/media/ytm/images/pbg/liked-music-@1200.png".toUri()
                        description.extras?.getString("browseId") != "LM"
                                && description.extras?.getString("thumbnailUrl0") != null -> description.extras?.getString("thumbnailUrl0").thumbnail(CAR_ARTWORK_SIZE)?.toUri()
                        else -> {
                            uriFor(
                                if (description.title.toString().startsWith("0:")) R.drawable.pin else
                                    if (description.title.toString()
                                            .startsWith("1:")
                                    ) R.drawable.stat_month else R.drawable.playlist
                            )
                        }
                    }

                )
                .build(),
            MediaItem.FLAG_BROWSABLE
        )

    private fun browsableItem(mediaId: String, title: String, @DrawableRes icon: Int) = MediaItem(
        MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle(title)
            .setIconUri(uriFor(icon))
            .build(),
        MediaItem.FLAG_BROWSABLE
    )

    private val faultBrowserMediaItem
        get() = browsableItem(MediaId.FAULT, "Fault", R.drawable.close)

    private val recentBrowserMediaItem
        get() = browsableItem(CarLibrary.RECENT, getString(R.string.aa_recent), R.drawable.history)

    private val libraryBrowserMediaItem
        get() = browsableItem(CarLibrary.LIBRARY, getString(R.string.library), R.drawable.music_library)

    private val songsBrowserMediaItem
        get() = browsableItem(MediaId.SONGS, getString(R.string.songs), R.drawable.musical_notes)

    private val radioBrowserMediaItem
        get() = browsableItem(MediaId.RADIO, getString(R.string.android_auto_radio), R.drawable.radio)

    private val playlistsBrowserMediaItem
        get() = browsableItem(MediaId.PLAYLISTS, getString(R.string.playlists), R.drawable.playlist)

    private val playlistsInLibraryBrowserMediaItem
        get() = browsableItem(MediaId.PLAYLISTS_IN_LIBRARY, getString(R.string.library), R.drawable.music_library)

    private val playlistsPinnedBrowserMediaItem
        get() = browsableItem(MediaId.PLAYLISTS_PINNED, getString(R.string.pinned_playlists), R.drawable.pin)

    private val playlistsMonthlyBrowserMediaItem
        get() = browsableItem(MediaId.PLAYLISTS_MONTHLY, getString(R.string.monthly_playlists), R.drawable.stat_month)

    private val playlistsOnDeviceBrowserMediaItem
        get() = browsableItem(MediaId.PLAYLISTS_ONDEVICE, getString(R.string.on_device), R.drawable.folder)

    private val playlistsPodcastBrowserMediaItem
        get() = browsableItem(MediaId.PLAYLISTS_PODCAST, getString(R.string.podcasts), R.drawable.podcast)

    private val albumsFavoritesBrowserMediaItem
        get() = browsableItem(MediaId.ALBUMS_FAVORITES, getString(R.string.albums), R.drawable.music_album)

    private val albumsInLibraryBrowserMediaItem
        get() = browsableItem(MediaId.ALBUMS_IN_LIBRARY, getString(R.string.library), R.drawable.music_album)

    private val albumsOnDeviceBrowserMediaItem
        get() = browsableItem(MediaId.ALBUMS_ON_DEVICE, getString(R.string.on_device), R.drawable.music_album)

    private val artistsFavoritesBrowserMediaItem
        get() = browsableItem(MediaId.ARTISTS_FAVORITES, getString(R.string.artists), R.drawable.music_artist)

    private val artistsInLibraryBrowserMediaItem
        get() = browsableItem(MediaId.ARTISTS_IN_LIBRARY, getString(R.string.library), R.drawable.music_artist)

    private val artistsOnDeviceBrowserMediaItem
        get() = browsableItem(MediaId.ARTISTS_ONDEVICE, getString(R.string.on_device), R.drawable.music_artist)

    private val favoritesBrowserMediaItem
        get() = browsableItem(MediaId.SONGS_FAVORITES, getString(R.string.aa_liked), R.drawable.heart)

    private val topBrowserMediaItem
        get() = browsableItem(
            MediaId.SONGS_TOP,
            getString(R.string.my_playlist_top).format(CarLibrary.maxTopItems(this)),
            R.drawable.trending
        )

    private val ondeviceBrowserMediaItem
        get() = browsableItem(MediaId.SONGS_ONDEVICE, getString(R.string.on_device), R.drawable.musical_notes)

    fun reloadPlaylist(){
        notifyChildrenChanged(MediaId.PLAYLISTS)
    }

    object MediaId {
        const val FAULT = "fault"
        const val ROOT = "root"
        const val SONGS = "songs"
        const val PLAYLISTS = "playlists"
        const val PLAYLISTS_IN_LIBRARY = "playlistsInLibrary"
        const val PLAYLISTS_PODCAST = "playlistsPodcast"
        const val PLAYLISTS_PINNED = "playlistsPinned"
        const val PLAYLISTS_MONTHLY = "playlistsMonthly"
        const val PLAYLISTS_ONDEVICE = "playlistsOnDevice"
        const val ALBUMS_FAVORITES = "albumsFavorites"
        const val ALBUMS_IN_LIBRARY = "albumsInLibrary"
        const val ALBUMS_ON_DEVICE = "albumsOnDevice"
        const val ARTISTS_FAVORITES = "artistsFavorites"
        const val ARTISTS_IN_LIBRARY = "artistsInLibrary"
        const val ARTISTS_ONDEVICE = "artistsOnDevice"

        // Media id prefix of search results in older builds, still understood when played
        const val SEARCHED = "searched"

        const val SONGS_FAVORITES = "favorites"
        const val SONGS_ONDEVICE = "ondevice"
        const val SONGS_TOP = "top"

        const val RADIO = "radio"

        fun forPlaylist(id: Long) = "$PLAYLISTS/$id"
        fun forPlaylistOnDevice(folder: String) = "$PLAYLISTS_ONDEVICE/$folder"
        fun forAlbumFavorites(id: String) = "$ALBUMS_FAVORITES/$id"
        fun forAlbumInLibrary(id: String) = "$ALBUMS_IN_LIBRARY/$id"
        fun forAlbumOnDevice(id: String) = "$ALBUMS_ON_DEVICE/$id"
        fun forArtistFavorites(id: String) = "$ARTISTS_FAVORITES/$id"
        fun forArtistInLibrary(id: String) = "$ARTISTS_IN_LIBRARY/$id"
        fun forArtistOnDevice(id: String) = "$ARTISTS_ONDEVICE/$id"
    }
}

private const val MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
private const val CONTENT_STYLE_LIST = 1
private const val CONTENT_STYLE_GRID = 2

// androidx.media.utils.MediaConstants.BROWSER_ROOT_HINTS_KEY_ROOT_CHILDREN_LIMIT
private const val ROOT_CHILDREN_LIMIT_HINT = "androidx.media.MediaBrowserCompat.Extras.KEY_ROOT_CHILDREN_LIMIT"
private const val DEFAULT_ROOT_CHILDREN_LIMIT = 4

// Artwork requested from the image CDN for car screens
private const val CAR_ARTWORK_SIZE = 544
