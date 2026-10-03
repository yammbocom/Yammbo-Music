package it.fast4x.riplay.service

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.utils.EXPLICIT_BUNDLE_TAG
import it.fast4x.riplay.utils.SongVersion
import it.fast4x.riplay.utils.mayHaveSongVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * "Songs, not videos" across the whole app: a music video reached in the playing queue (from a
 * playlist, an album, an artist page, search, history, a shared link, a radio...) plays as its
 * song version (ATV) when an exact one exists. Videos the user asked for as videos carry the
 * chosen-video extra and are never touched.
 *
 * Ahead of time: after every transition or queue change the next [AHEAD] items in play order are
 * looked up in the background and replaced in the queue before they play, so the video never
 * shows. Replacing an item that is not the current one fires no transition (ExoPlayer only
 * reports one when the playing period changes), so ads, history, scrobbles and the position
 * are left alone. As a fallback the current item is swapped through [swapCurrent], which only
 * acts in the first seconds of the song and tells the ads it is the same song.
 *
 * Only the playing queue changes. The song row is stored (as the existing swap does) so the
 * queue can be restored, but saved playlists keep what the user put in them.
 *
 * Everything here runs on the main thread except the search itself; results are cached by
 * [SongVersion] for the session, so each video is searched at most once.
 */
@OptIn(UnstableApi::class)
internal class SongVersionQueue(
    private val scope: CoroutineScope,
    private val player: () -> ExoPlayer,
    /** Swaps the playing item; must check by itself that it is still current and early enough. */
    private val swapCurrent: (videoId: String, song: MediaItem) -> Unit,
    /** True while something else is rebuilding the queue around the current item (a radio
     *  being loaded): the current item is left to it, the upcoming ones are still handled. */
    private val currentBusy: () -> Boolean,
) {
    private var job: Job? = null
    // Set while this class changes the timeline itself, so its own callbacks do not restart it.
    private var replacing = false
    // Searches in flight, outside the job: a job cancelled by a queue change leaves its search
    // running, and the next job picks the same answer up instead of searching again.
    private val inFlight = HashMap<String, Deferred<SongVersion.Match?>>()

    /** Call on Main for every media item transition and playlist change of the player. */
    fun onQueueChanged() {
        if (replacing) return
        job?.cancel()
        job = scope.launch(Dispatchers.Main) {
            // Coalesces the burst of changes a queue rebuild makes (set items, add the radio...).
            delay(DEBOUNCE_MS)
            runCatching { process() }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it
                    Timber.e("SongVersionQueue failed ${it.message}") }
        }
    }

    private suspend fun process() {
        val player = player()
        if (player.mediaItemCount == 0) return
        val current = player.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return

        val targets = buildList {
            add(current)
            var index = current
            repeat(AHEAD) {
                index = player.currentTimeline.getNextWindowIndex(
                    index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled
                )
                if (index == C.INDEX_UNSET) return@buildList
                add(index)
            }
        }

        for (index in targets) {
            if (index >= player.mediaItemCount) continue
            val item = player.getMediaItemAt(index)
            if (!item.mayHaveSongVersion) continue
            val isCurrent = index == player.currentMediaItemIndex
            if (isCurrent && currentBusy()) continue

            val match = resolve(item) ?: continue
            if (!match.exact) continue
            val song = match.mediaItem
            // A song the user blacklisted or disliked is no stand-in: the video stays.
            if (!SongVersion.isAcceptable(song)) continue

            // The queue may have moved while the search ran.
            if (index >= player.mediaItemCount || player.getMediaItemAt(index).mediaId != item.mediaId) continue

            if (index == player.currentMediaItemIndex) {
                if (currentBusy()) continue
                replacing = true
                try {
                    // Stores the song and keeps the ads count; does nothing past its time limit.
                    swapCurrent(item.mediaId, song)
                } finally {
                    replacing = false
                }
                // The swap's own callbacks were held back above, and the targets were picked
                // before it: look ahead again from the new current item.
                if (player.currentMediaItem?.mediaId == song.mediaId) {
                    onQueueChanged()
                    return
                }
            } else {
                replaceUpcoming(player, index, item.mediaId, song)
            }
        }
    }

    private suspend fun resolve(item: MediaItem): SongVersion.Match? {
        val id = item.mediaId
        val deferred = inFlight[id] ?: scope.async(Dispatchers.IO) {
            runCatching { SongVersion.resolve(item) }.getOrNull()
        }.also { inFlight[id] = it }
        return try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) inFlight.remove(id)
        }
    }

    private fun replaceUpcoming(player: ExoPlayer, index: Int, videoId: String, song: MediaItem) {
        Timber.d("SongVersionQueue song version ${song.mediaId} replaces upcoming music video $videoId at $index")
        Database.asyncTransaction { insert(song) }
        replacing = true
        try {
            player.replaceKeepingShuffle(index, player.getMediaItemAt(index).songStandIn(song))
        } finally {
            replacing = false
        }
    }

    private companion object {
        const val AHEAD = 2
        const val DEBOUNCE_MS = 400L
    }
}

// Describe the item itself; everything else in the extras (idQueue, isFromPersistentQueue...)
// is queue bookkeeping that belongs to the queue slot and goes over to the song.
private val itemDescriptionExtras = listOf(
    "isVideo", "isOfficialMusicVideo", "isUserGeneratedContent", "isOfficialUploadByArtistContent",
    "setVideoId", "durationText", "albumId", "artistNames", "artistIds", "mediaId",
    "isLiked", "isDisliked", "isPodcast", "isRadio", EXPLICIT_BUNDLE_TAG, SongVersion.CHOSEN_VIDEO_EXTRA
)

/** [song] with this queue item's bookkeeping extras, to stand in for it in the same slot. */
@OptIn(UnstableApi::class)
internal fun MediaItem.songStandIn(song: MediaItem): MediaItem {
    val original = mediaMetadata.extras ?: return song
    val extras = Bundle(original)
    itemDescriptionExtras.forEach { extras.remove(it) }
    song.mediaMetadata.extras?.let { extras.putAll(it) }
    return song.buildUpon()
        .setMediaMetadata(song.mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}

/**
 * replaceMediaItem is an insertion plus a removal for ExoPlayer, and the default shuffle order
 * puts an inserted item at a random place: in shuffle the replaced slot would move. The order
 * read before is put back (same count, same indices).
 */
@OptIn(UnstableApi::class)
internal fun ExoPlayer.replaceKeepingShuffle(index: Int, item: MediaItem) {
    val shuffled = if (shuffleModeEnabled) shuffleIndices() else null
    replaceMediaItem(index, item)
    if (shuffled != null && shuffled.size == mediaItemCount)
        shuffleOrder = DefaultShuffleOrder(shuffled, System.currentTimeMillis())
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.shuffleIndices(): IntArray? {
    val order = shuffleOrder
    val indices = IntArray(order.length)
    var index = order.firstIndex
    var count = 0
    while (index != C.INDEX_UNSET && count < indices.size) {
        indices[count++] = index
        index = order.getNextIndex(index)
    }
    return if (count == indices.size) indices else null
}
