package it.fast4x.riplay.extensions.download

import android.content.Context
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.ads.PremiumFeature
import it.fast4x.riplay.extensions.ads.PremiumGuard
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.isRadio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * UI-facing entry points for in-app downloads. Every one of them launches its own scope:
 * menus close the moment they are tapped and take their composition scope with them, so
 * working inside it would cancel the download half way through the query.
 *
 * Downloading is a Premium feature: every entry point checks it first, on the calling (main)
 * thread, so the subscription message shows whichever button was tapped.
 */

// A playlist continuation page holds ~100 songs; this is a ceiling against a runaway token chain.
private const val MAX_PLAYLIST_PAGES = 30
private const val RESOLVE_TIMEOUT_MS = 60_000L

/** Queues [songs] and tells the user what happened. */
fun downloadSongs(context: Context, songs: List<Song>, albumTitle: String? = null) {
    if (!downloadAllowed(context)) return
    val app = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch { enqueueAndReport(app, songs, albumTitle) }
}

/** One song; says so instead of queuing it when a copy is already on the device. */
fun downloadSong(context: Context, song: Song) {
    if (!downloadAllowed(context)) return
    val app = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        if (!song.isLocal && !song.isRadio && SongDownloader.hasPlayableCopy(app, song.id)) {
            report(app, app.getString(R.string.download_already_downloaded))
            return@launch
        }
        enqueueAndReport(app, listOf(song), null)
    }
}

fun downloadAlbum(context: Context, albumId: String, albumTitle: String?) =
    resolveAndDownload(context, albumTitle) { resolveAlbumSongs(albumId) }

fun downloadPlaylist(context: Context, playlist: Playlist) =
    resolveAndDownload(context, null) { resolvePlaylistSongs(playlist) }

fun downloadArtist(context: Context, artistId: String) =
    resolveAndDownload(context, null) { resolveArtistSongs(artistId) }

// ---- resolvers ----

/** Same source as AlbumDetails: the songs saved for the album, else the album page online. */
suspend fun resolveAlbumSongs(albumId: String): List<Song> = withContext(Dispatchers.IO) {
    Database.albumSongsList(albumId).ifEmpty {
        EnvironmentExt.getAlbum(albumId).getOrNull()?.songs.orEmpty().map { it.asSong }
    }
}

/**
 * A playlist's songs from the database; a YouTube playlist that has none saved yet
 * (the menu was opened from a list, never from the playlist screen) is fetched online.
 */
suspend fun resolvePlaylistSongs(playlist: Playlist): List<Song> = withContext(Dispatchers.IO) {
    val saved = Database.songsInPlaylist(playlist.id).firstOrNull().orEmpty().map { it.song }
    if (saved.isNotEmpty() || playlist.browseId.isNullOrBlank()) return@withContext saved

    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
        val first = EnvironmentExt.getPlaylist(playlist.browseId).getOrNull() ?: return@withTimeoutOrNull null
        val items = first.songs.toMutableList()
        var token = first.songsContinuation
        val seen = mutableSetOf<String>()
        while (token != null && seen.add(token) && seen.size <= MAX_PLAYLIST_PAGES) {
            // A failed page ends the walk: what was read so far is still worth downloading.
            val page = EnvironmentExt.getPlaylistContinuation(token).getOrNull() ?: break
            items += page.songs
            token = page.continuation
        }
        items.distinctBy { it.key }.map { it.asSong }
    }.orEmpty()
}

/** Same list ArtistOverview downloads (the artist's songs in the library); the page's songs otherwise. */
suspend fun resolveArtistSongs(artistId: String): List<Song> = withContext(Dispatchers.IO) {
    Database.artistSongs(artistId).firstOrNull().orEmpty().ifEmpty {
        EnvironmentExt.getArtistPage(artistId).getOrNull()
            ?.sections.orEmpty()
            .flatMap { it.items }
            .filterIsInstance<Environment.SongItem>()
            .distinctBy { it.key }
            .map { it.asSong }
    }
}

/**
 * Flips "download automatically on Wi-Fi" for [key] (see [AutoDownloads]) and says what it means.
 * Turning it off leaves what was downloaded where it is.
 */
fun toggleAutoDownload(context: Context, key: String) {
    val turnOn = !AutoDownloads.isEnabled(context, key)
    if (turnOn && !downloadAllowed(context)) return
    AutoDownloads.setEnabled(context, key, turnOn)
    SmartMessage(
        context.getString(if (turnOn) R.string.auto_download_collection_on else R.string.auto_download_collection_off),
        PopupType.Info,
        context = context.applicationContext
    )
}

// ---- internals ----

private fun downloadAllowed(context: Context): Boolean =
    PremiumGuard.checkFeature(context, PremiumFeature.Download)

private fun resolveAndDownload(
    context: Context,
    albumTitle: String?,
    resolve: suspend () -> List<Song>,
) {
    if (!downloadAllowed(context)) return
    val app = context.applicationContext
    CoroutineScope(Dispatchers.IO).launch {
        report(app, app.getString(R.string.download_preparing))
        val songs = runCatching { resolve() }.getOrDefault(emptyList())
        enqueueAndReport(app, songs, albumTitle)
    }
}

private suspend fun enqueueAndReport(app: Context, songs: List<Song>, albumTitle: String?) {
    val downloadable = songs.filterNot { it.isLocal || it.isRadio || it.id.isBlank() }
    val added = if (downloadable.isEmpty()) 0 else Downloads.enqueue(app, downloadable, albumTitle)
    report(
        app,
        when {
            added > 0 -> app.resources.getQuantityString(R.plurals.download_queued, added, added)
            downloadable.isNotEmpty() -> app.getString(R.string.download_nothing_new)
            else -> app.getString(R.string.nothing_to_download)
        }
    )
}

private suspend fun report(app: Context, message: String) {
    withContext(Dispatchers.Main) { SmartMessage(message, PopupType.Info, context = app) }
}
