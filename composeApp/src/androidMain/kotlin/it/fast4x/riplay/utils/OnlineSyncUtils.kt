package it.fast4x.riplay.utils

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import io.ktor.http.isSuccess
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.utils.completed
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.Database.Companion.getAlbumsList
import it.fast4x.riplay.data.Database.Companion.getArtistsList
import it.fast4x.riplay.data.Database.Companion.update
import com.yambo.music.R
import it.fast4x.riplay.commonutils.YTP_PREFIX
import it.fast4x.riplay.extensions.preferences.autosyncKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.data.models.Album
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.SongPlaylistMap
import it.fast4x.riplay.ui.components.tab.toolbar.Descriptive
import it.fast4x.riplay.ui.components.tab.toolbar.DynamicColor
import it.fast4x.riplay.ui.components.tab.toolbar.MenuIcon
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

// ---------------------------------------------------------------------------------------------
// Two-way likes sync with the YouTube Music account
// ---------------------------------------------------------------------------------------------

private const val LIKE_FETCH_TIMEOUT_MS = 120_000L
private const val LIKE_SYNC_MIN_INTERVAL_MS = 60_000L
private const val LIKE_FETCH_MAX_PAGES = 200
// Bulk changes above this size look like a bad response or a wiped database, not a user action.
private const val BULK_CHANGE_LIMIT = 25
// Likes pushed from the app can take a while to show up in the account's list.
private const val REMOTE_LAG_GRACE_MS = 10 * 60 * 1000L
private const val MAX_LOCAL_LIKES_PUSHED_PER_SYNC = 50
private val videoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

private val likeSyncMutex = Mutex()
private var lastLikeSyncOkAt = 0L

/**
 * Sends a like/unlike to the account. The Innertube wrapper reports "success" even when the server
 * answered with an HTTP error, so the status code is checked here.
 */
suspend fun pushYtLike(videoId: String, like: Boolean): Boolean = withContext(Dispatchers.IO) {
    val result = if (like) EnvironmentExt.likeVideoOrSong(videoId)
    else EnvironmentExt.removelikeVideoOrSong(videoId)
    result.map { it.status.isSuccess() }.getOrDefault(false)
}

/**
 * Downloads the whole liked list ("LM"). Returns null on ANY failure or timeout: a partial or
 * failed read must never be mistaken for "the account has no likes", or a sync would wipe them.
 */
private suspend fun fetchAllLikedSongsStrict(): List<Environment.SongItem>? =
    withTimeoutOrNull(LIKE_FETCH_TIMEOUT_MS) {
        val first = EnvironmentExt.getPlaylist("LM").getOrNull() ?: return@withTimeoutOrNull null
        val songs = first.songs.toMutableList()
        var token = first.songsContinuation
        val seenTokens = mutableSetOf<String>()

        while (token != null) {
            if (!seenTokens.add(token) || seenTokens.size > LIKE_FETCH_MAX_PAGES) return@withTimeoutOrNull null
            val result = EnvironmentExt.getPlaylistContinuation(token)
            // A failed page, or a success without items, stops the sync instead of truncating the list.
            val page = result.getOrNull() ?: return@withTimeoutOrNull null
            songs += page.songs
            token = page.continuation
        }
        songs.distinctBy { it.key }
    }

/**
 * Two-way sync of liked songs. Returns true when the account was read completely and applied.
 *
 * Removals only affect songs that are known to be mirrored (see [YtSyncState]), so a like the user
 * made locally is never erased just because it is not on the account.
 */
suspend fun syncYtmLikedSongs(force: Boolean = false): Boolean {
    if (!isYtLikeSyncEnabled() || Environment.cookie.isNullOrBlank()) return false

    fun recentlyDone() = lastLikeSyncOkAt != 0L &&
            SystemClock.elapsedRealtime() - lastLikeSyncOkAt < LIKE_SYNC_MIN_INTERVAL_MS

    if (!force && recentlyDone()) return true

    return likeSyncMutex.withLock {
        if (!force && recentlyDone()) return@withLock true
        withContext(Dispatchers.IO) {
            val ok = runCatching { runLikeSync() }
                .onFailure { Timber.e(it, "syncYtmLikedSongs failed") }
                .getOrDefault(false)
            if (ok) {
                lastLikeSyncOkAt = SystemClock.elapsedRealtime()
                YtSyncState.markSynced()
            }
            ok
        }
    }
}

@OptIn(UnstableApi::class)
private suspend fun runLikeSync(): Boolean {
    // Unknown or switched account: never apply one account's bookkeeping to another.
    if (!YtSyncState.ensureOwner()) return false
    val startedAt = System.currentTimeMillis()

    // 1. Retry the operations that never reached the account.
    for ((id, like) in YtSyncState.pendingLikes()) {
        if (pushYtLike(id, like)) {
            YtSyncState.clearPending(id)
            // A pushed like only becomes "synced" once the account lists it (step 3).
            if (like) YtSyncState.addPushedLiked(listOf(id))
            else { YtSyncState.removeSyncedLiked(listOf(id)); YtSyncState.removePushedLiked(listOf(id)) }
        }
        delay(300)
    }

    // 2. Read the account. Null means "unknown": leave everything as it is.
    val remote = fetchAllLikedSongsStrict() ?: return false
    val pending = YtSyncState.pendingLikes()
    val tracked = YtSyncState.syncedLikedIds()
    val pushed = YtSyncState.pushedLikedIds()
    val remoteIds = remote.map { it.key }.toSet()

    val newlyTracked = mutableSetOf<String>()
    val toPushUnlike = mutableListOf<String>()
    val now = System.currentTimeMillis()

    // 3. Account -> app.
    remote.forEachIndexed { index, item ->
        val id = item.key
        val existed = Database.songExist(id) > 0
        // The song row must exist before it can be liked or listed in a playlist.
        if (!existed) Database.insert(item.asMediaItem)
        if (id in pending) return@forEachIndexed

        val local = Database.getLikedAt(id)
        when {
            local != null && local > 0L -> newlyTracked += id
            // It was mirrored before and is not liked here any more: the user unliked it locally.
            existed && id in tracked -> toPushUnlike += id
            // An explicit dislike is respected.
            local == -1L -> Unit
            // Keeping the account's order (newest first) because favorites are sorted by likedAt.
            else -> {
                Database.like(id, now - index)
                newlyTracked += id
            }
        }
    }

    // 4. Account -> app removals (only for mirrored songs, only after a complete read).
    val untracked = mutableSetOf<String>()
    if (remoteIds.isNotEmpty()) {
        val absent = tracked.filter { it !in remoteIds && it !in pending }
        val stillLiked = absent.filter {
            val at = Database.getLikedAt(it) ?: 0L
            at > 0L && at < now - REMOTE_LAG_GRACE_MS
        }
        val tooMany = stillLiked.size > BULK_CHANGE_LIMIT && stillLiked.size * 2 > tracked.size
        if (tooMany) {
            Timber.w("syncYtmLikedSongs: ${stillLiked.size} removals look suspicious, skipped")
        } else {
            stillLiked.forEach { Database.like(it, null) }
        }
        untracked += absent.filter { id ->
            val at = Database.getLikedAt(id) ?: 0L
            if (id in stillLiked) !tooMany else at <= 0L
        }
    }

    // 5. App -> account: songs unliked locally.
    val tooManyUnlikes = toPushUnlike.size > BULK_CHANGE_LIMIT && toPushUnlike.size * 2 > tracked.size
    toPushUnlike.forEach { id ->
        if (tooManyUnlikes) {
            // More likely a restored/wiped database than real unlikes: the account wins.
            Database.like(id, now)
            newlyTracked += id
        } else {
            if (pushYtLike(id, false)) untracked += id else YtSyncState.setPending(id, false)
            delay(300)
        }
    }

    // 6. App -> account: songs liked locally since the first sync (older ones are not pushed).
    val baseline = appContext().preferences.getLong(ytLikeBaselineKey, 0L)
    if (baseline == 0L) {
        appContext().preferences.edit { putLong(ytLikeBaselineKey, startedAt) }
    } else {
        Database.favorites().first()
            .filter {
                (it.likedAt ?: 0L) > baseline && it.id !in remoteIds && it.id !in tracked &&
                        it.id !in pending && it.id !in pushed && videoIdRegex.matches(it.id)
            }
            .take(MAX_LOCAL_LIKES_PUSHED_PER_SYNC)
            .forEach { song ->
                if (pushYtLike(song.id, true)) YtSyncState.addPushedLiked(listOf(song.id))
                else YtSyncState.setPending(song.id, true)
                delay(300)
            }
    }

    YtSyncState.addSyncedLiked(newlyTracked)
    YtSyncState.removeSyncedLiked(untracked)
    // Pushed likes the account now lists are ordinary synced ones from here on.
    YtSyncState.removePushedLiked(pushed.filter { it in remoteIds })

    // 7. Keep the "liked music" playlist in step (an empty read never rewrites it).
    if (remote.isNotEmpty()) mirrorLikedPlaylist(remote)
    return true
}

private fun mirrorLikedPlaylist(remote: List<Environment.SongItem>) {
    val existing = Database.playlistWithBrowseId("LM")
    val playlistId = if (existing == null) {
        Database.insert(
            Playlist(
                name = YTP_PREFIX + "YTM Liked Music",
                browseId = "LM",
                isYoutubePlaylist = true,
                isEditable = true
            )
        )
    } else {
        // The prefix used to be stacked on every pass; keep exactly one.
        var clean = existing.name
        while (clean.startsWith(YTP_PREFIX)) clean = clean.removePrefix(YTP_PREFIX)
        clean = YTP_PREFIX + clean
        if (clean != existing.name) Database.updatePlaylistName(clean, existing.id)
        existing.id
    }
    if (playlistId <= 0L) return

    // One transaction so observers never see the playlist half rebuilt.
    Database.asyncTransaction {
        clearPlaylist(playlistId)
        remote.forEachIndexed { position, item ->
            upsert(
                SongPlaylistMap(
                    songId = item.key,
                    playlistId = playlistId,
                    position = position,
                    setVideoId = item.setVideoId,
                ).default()
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library import (playlists, artists, albums)
// ---------------------------------------------------------------------------------------------

/**
 * Imports the account's playlists. The liked list ("LM") is handled by [syncYtmLikedSongs] when
 * the likes switch is on, so it is no longer touched here.
 *
 * [refreshExisting] re-reads playlists that already have songs (manual "sync now"); automatic runs
 * only fill the empty ones to keep the number of requests low.
 */
suspend fun importYTMPrivatePlaylists(silent: Boolean = false, refreshExisting: Boolean = false): Boolean {
    if (!isYtSyncEnabled()) return false

    var ok = true
    val likesOn = isYtLikeSyncEnabled()
    val playlistsOn = isYtPlaylistSyncEnabled()
    if (!likesOn && !playlistsOn) return false

    if (likesOn && !syncYtmLikedSongs(force = refreshExisting)) ok = false
    if (!playlistsOn) return ok

    if (!silent) {
        SmartMessage(
            message = appContext().resources.getString(R.string.syncing),
            durationLong = true,
            context = appContext(),
        )
    }

    Environment.library("FEmusic_liked_playlists").completed().onSuccess { page ->

        val ytmPrivatePlaylists = page.items.filterIsInstance<Environment.PlaylistItem>()
            .filterNot { it.key == "VLLM" || it.key == "VLSE" }

        val localPlaylists = Database.ytmPrivatePlaylists().firstOrNull()

        // A request that comes back empty is not proof the account has no playlists: a missing
        // cookie or a bad response also lands here, and this used to delete every imported
        // playlist as "no longer on YouTube". Only mirror deletions when the account answered.
        if (ytmPrivatePlaylists.isNotEmpty()) {
            (localPlaylists?.filter { playlist -> playlist.browseId != "LM" && playlist.browseId !in ytmPrivatePlaylists.map { if (it.key.startsWith("VL")) it.key.substringAfter("VL") else it.key }  })?.forEach { playlist ->
                Database.asyncTransaction{ delete(playlist) }
            }
        } else {
            Timber.w("importYTMPrivatePlaylists: empty library response, keeping local playlists")
        }

        ytmPrivatePlaylists.forEach { remotePlaylist ->
            withContext(Dispatchers.IO) {
                val playlistIdChecked =
                    if (remotePlaylist.key.startsWith("VL")) remotePlaylist.key.substringAfter("VL") else remotePlaylist.key
                var localPlaylist =
                    localPlaylists?.find { it.browseId == playlistIdChecked }

                if (localPlaylist == null && playlistIdChecked.isNotEmpty()) {
                    localPlaylist = Playlist(
                        name = (remotePlaylist.title) ?: "",
                        browseId = playlistIdChecked,
                        isYoutubePlaylist = true,
                        isEditable = (remotePlaylist.isEditable == true)
                    )
                    Database.insert(localPlaylist.copy(browseId = playlistIdChecked))
                } else {
                    Database.updatePlaylistName(YTP_PREFIX +remotePlaylist.title, localPlaylist?.id ?: 0L)
                }

                Database.playlistWithSongsByBrowseId(playlistIdChecked).firstOrNull()?.let {
                    if (it.playlist.id != 0L && (it.songs.isEmpty() || refreshExisting))
                        ytmPrivatePlaylistSync(it.playlist, it.playlist.id)
                }
            }
        }

    }.onFailure {
        Timber.e("Error importing YTM private playlists: ${it.message}")
        return false
    }
    if (ok) YtSyncState.markSynced()
    return ok
}

@OptIn(UnstableApi::class)
fun ytmPrivatePlaylistSync(playlist: Playlist, playlistId: Long) {
    playlist.let { plist ->
        Database.asyncTransaction {
            runBlocking(Dispatchers.IO) {
                withContext(Dispatchers.IO) {
                    plist.browseId?.let {
                        EnvironmentExt.getPlaylist(
                            playlistId = it
                        ).completed()
                    }
                }
            }?.getOrNull()?.let { remotePlaylist ->
                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.IO) {

                        println("ytmPrivatePlaylistSync Remote playlist editable: ${remotePlaylist.isEditable}")

                        // Update here playlist isEditable flag because library contain playlists but isEditable isn't always available
                        if (remotePlaylist.isEditable == true)
                            Database.update(playlist.copy(isEditable = true))

                        if (remotePlaylist.songs.isNotEmpty()) {
                            //Database.clearPlaylist(playlistId)

                            // The account's liked songs arrive as the playlist with browseId "LM".
                            // They are normally mirrored by syncYtmLikedSongs; this path only
                            // fills missing likes and never removes any.
                            val isLikedMusic = plist.browseId == "LM"

                            remotePlaylist.songs
                                .map(Environment.SongItem::asMediaItem)
                                .onEach(Database::insert)
                                .onEach { mediaItem ->
                                    if (isLikedMusic && Database.likedAt(mediaItem.mediaId).firstOrNull() == null) {
                                        Database.like(mediaItem.mediaId, System.currentTimeMillis())
                                        YtSyncState.addSyncedLiked(listOf(mediaItem.mediaId))
                                    }
                                }
                                .mapIndexed { position, mediaItem ->
                                    SongPlaylistMap(
                                        songId = mediaItem.mediaId,
                                        playlistId = playlistId,
                                        position = position,
                                        setVideoId = mediaItem.mediaMetadata.extras?.getString("setVideoId"),
                                    ).default()
                                }
                                .onEach {
                                    Timber.d("ytmPrivatePlaylistSync synced list of setvideoid ${it.setVideoId}")
                                    Database.upsert(it)
                                }
                        }

                        /*localPlaylistSongs.filter { it.asMediaItem.mediaId !in remotePlaylist.songs.map { it.asMediaItem.mediaId } }
                            .forEach { song ->
                                deleteSongFromPlaylist(song.asMediaItem.mediaId, playlistId)
                            }*/
                    }
                }
            }
        }
    }
}

suspend fun importYTMSubscribedChannels(silent: Boolean = false): Boolean {
    println("importYTMSubscribedChannels isYouTubeSyncEnabled() = ${isYtSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYtLibrarySyncEnabled()) {

        if (!silent) {
            SmartMessage(
                message = appContext().resources.getString(R.string.syncing),
                durationLong = true,
                context = appContext(),
            )
        }

        Environment.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->

            val ytmArtists = page.items.filterIsInstance<Environment.ArtistItem>()

            println("YTM artists: $ytmArtists")

            ytmArtists.forEach { remoteArtist ->
                withContext(Dispatchers.IO) {

                    var localArtist = Database.artist(remoteArtist.key).firstOrNull()
                    println("Local artist: $localArtist")
                    println("Remote artist: $remoteArtist")

                    if (localArtist == null) {
                        localArtist = Artist(
                            id = remoteArtist.key,
                            name = remoteArtist.title,
                            thumbnailUrl = remoteArtist.thumbnail?.url,
                            bookmarkedAt = System.currentTimeMillis(),
                            isYoutubeArtist = true
                        )
                        Database.insert(localArtist)
                    } else {
                        localArtist.copy(
                            bookmarkedAt = localArtist.bookmarkedAt ?: System.currentTimeMillis(),
                            thumbnailUrl = remoteArtist.thumbnail?.url,
                            isYoutubeArtist = true
                        ).let(::update)
                    }


                }
            }
            // An empty answer is not proof the account follows nobody (bad cookie, bad response).
            if (ytmArtists.isNotEmpty()) {
                val Artists = getArtistsList().firstOrNull()
                Database.asyncTransaction {
                    Artists?.filter {artist -> artist?.isYoutubeArtist == true && artist.id !in ytmArtists.map { it.key } }?.forEach { artist ->
                        if (artist != null) update(artist.copy(isYoutubeArtist = false, bookmarkedAt = null))
                    }
                }
            }
        }
            .onFailure {
                println("Error importing YTM subscribed artists channels: ${it.stackTraceToString()}")
                return false
            }
        YtSyncState.markSynced()
        return true
    } else
        return false
}

suspend fun importYTMLikedAlbums(silent: Boolean = false): Boolean {
    println("importYTMLikedAlbums isYouTubeSyncEnabled() = ${isYtSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYtLibrarySyncEnabled()) {

        if (!silent) {
            SmartMessage(
                message = appContext().resources.getString(R.string.syncing),
                durationLong = true,
                context = appContext(),
            )
        }

        Environment.library("FEmusic_liked_albums").completed().onSuccess { page ->

            val ytmAlbums = page.items.filterIsInstance<Environment.AlbumItem>()

            println("YTM albums: $ytmAlbums")

            ytmAlbums.forEach { remoteAlbum ->
                withContext(Dispatchers.IO) {

                    var localAlbum = Database.album(remoteAlbum.key).firstOrNull()
                    println("Local album: $localAlbum")
                    println("Remote album: $remoteAlbum")

                    if (localAlbum == null) {
                        localAlbum = Album(
                            id = remoteAlbum.key,
                            title = remoteAlbum.title,
                            thumbnailUrl = remoteAlbum.thumbnail?.url,
                            bookmarkedAt = System.currentTimeMillis(),
                            year = remoteAlbum.year,
                            authorsText = remoteAlbum.authors?.getOrNull(1)?.name,
                            isYoutubeAlbum = true
                        )
                        Database.insert(localAlbum)
                    } else {
                        localAlbum.copy(
                            isYoutubeAlbum = true,
                            bookmarkedAt = localAlbum.bookmarkedAt ?: System.currentTimeMillis(),
                            thumbnailUrl = remoteAlbum.thumbnail?.url)
                            .let(::update)
                    }

                }
            }
            // Same guard as for artists: an empty answer never clears the local library.
            if (ytmAlbums.isNotEmpty()) {
                val Albums = getAlbumsList().firstOrNull()
                Database.asyncTransaction {
                    Albums?.filter {album -> album?.isYoutubeAlbum == true && album.id !in ytmAlbums.map { it.key } }?.forEach { album->
                        if (album != null) update(album.copy(isYoutubeAlbum = false, bookmarkedAt = null))
                    }
                }
            }
        }
            .onFailure {
                println("Error importing YTM liked albums: ${it.stackTraceToString()}")
                return false
            }
        YtSyncState.markSynced()
        return true
    } else
        return false
}

suspend fun removeYTSongFromPlaylist(
    songId: String,
    playlistBrowseId: String,
    playlistId: Long,
): Boolean {

    Timber.d("removeYTSongFromPlaylist removeSongFromPlaylist params songId = $songId, playlistBrowseId = $playlistBrowseId, playlistId = $playlistId")

    if (isYtSyncEnabled()) {
        Database.asyncTransaction {
            CoroutineScope(Dispatchers.IO).launch {
                val songSetVideoId = Database.getSetVideoIdFromPlaylist(songId, playlistId).firstOrNull()
                Timber.d("removeYTSongFromPlaylist removeSongFromPlaylist songSetVideoId = $songSetVideoId")
                if (songSetVideoId != null)
                    EnvironmentExt.removeFromPlaylist(playlistId = playlistBrowseId, videoId =  songId, setVideoId = songSetVideoId)
            }
        }

        return true
    } else
        return false
}


@Composable
fun autoSyncToolbutton(messageId: Int): MenuIcon = object : MenuIcon, DynamicColor, Descriptive {

    override var isFirstColor: Boolean by rememberPreference(autosyncKey, false)
    override val iconId: Int = R.drawable.sync
    override val messageId: Int = messageId
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() {
        isFirstColor = !isFirstColor
    }
}
