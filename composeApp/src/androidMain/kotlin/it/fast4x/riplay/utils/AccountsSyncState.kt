package it.fast4x.riplay.utils

import androidx.core.content.edit
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import it.fast4x.riplay.extensions.preferences.ytAccountChannelHandleKey
import it.fast4x.riplay.extensions.preferences.ytAccountEmailKey
import it.fast4x.riplay.extensions.preferences.ytDataSyncIdKey

// Per-category switches under the YouTube Music master switch (enableYouTubeSyncKey).
const val ytSyncLikesKey = "accounts_yt_sync_likes"
const val ytSyncPlaylistsKey = "accounts_yt_sync_playlists"
const val ytSyncLibraryKey = "accounts_yt_sync_library"
const val ytSyncHistoryKey = "accounts_yt_sync_history"

// Bookkeeping for the two-way likes sync.
const val ytLastSyncAtKey = "accounts_yt_last_sync_at"
const val ytLikeBaselineKey = "accounts_yt_like_baseline"
const val ytSyncedLikedIdsKey = "accounts_yt_synced_liked_ids"
const val ytPendingLikesKey = "accounts_yt_pending_likes"
const val ytPushedLikedIdsKey = "accounts_yt_pushed_liked_ids"
const val ytSyncOwnerKey = "accounts_yt_sync_owner"

fun isYtLikeSyncEnabled() = isYtSyncEnabled() && appContext().preferences.getBoolean(ytSyncLikesKey, true)
fun isYtPlaylistSyncEnabled() = isYtSyncEnabled() && appContext().preferences.getBoolean(ytSyncPlaylistsKey, true)
fun isYtLibrarySyncEnabled() = isYtSyncEnabled() && appContext().preferences.getBoolean(ytSyncLibraryKey, true)
fun isYtHistorySyncEnabled() = isYtSyncEnabled() && appContext().preferences.getBoolean(ytSyncHistoryKey, true)

/**
 * Small persistent state for the YouTube Music sync.
 *
 * - synced ids: songs whose liked state is known to be mirrored on the account (pulled from it
 *   or pushed to it). Only those are ever un-liked locally because of a remote change, so a like
 *   the user made on their own is never wiped by a sync.
 * - pending: like/unlike operations that failed to reach the account (offline, HTTP error); they
 *   are retried on the next sync and win over the remote state meanwhile.
 *
 * SharedPreferences string sets must never be mutated in place, hence the copies.
 */
object YtSyncState {
    private val lock = Any()

    private fun prefs() = appContext().preferences

    fun syncedLikedIds(): Set<String> = synchronized(lock) {
        prefs().getStringSet(ytSyncedLikedIdsKey, emptySet())?.toSet() ?: emptySet()
    }

    fun addSyncedLiked(ids: Collection<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        val updated = (prefs().getStringSet(ytSyncedLikedIdsKey, emptySet()) ?: emptySet()).toMutableSet()
        if (updated.addAll(ids)) prefs().edit { putStringSet(ytSyncedLikedIdsKey, updated) }
    }

    fun removeSyncedLiked(ids: Collection<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        val updated = (prefs().getStringSet(ytSyncedLikedIdsKey, emptySet()) ?: emptySet()).toMutableSet()
        if (updated.removeAll(ids.toSet())) prefs().edit { putStringSet(ytSyncedLikedIdsKey, updated) }
    }

    /** video id -> true when the pending operation is a like, false when it is an unlike. */
    fun pendingLikes(): Map<String, Boolean> = synchronized(lock) {
        (prefs().getStringSet(ytPendingLikesKey, emptySet()) ?: emptySet())
            .mapNotNull { entry ->
                val sep = entry.lastIndexOf(':')
                if (sep <= 0) null else entry.substring(0, sep) to (entry.substring(sep + 1) == "1")
            }.toMap()
    }

    fun setPending(videoId: String, like: Boolean) = synchronized(lock) {
        val updated = (prefs().getStringSet(ytPendingLikesKey, emptySet()) ?: emptySet()).toMutableSet()
        // The latest intent wins, so drop any older entry for the same song.
        updated.removeAll { it.substringBeforeLast(':') == videoId }
        updated.add("$videoId:${if (like) 1 else 0}")
        prefs().edit { putStringSet(ytPendingLikesKey, updated) }
    }

    fun clearPending(videoId: String) = synchronized(lock) {
        val updated = (prefs().getStringSet(ytPendingLikesKey, emptySet()) ?: emptySet()).toMutableSet()
        if (updated.removeAll { it.substringBeforeLast(':') == videoId })
            prefs().edit { putStringSet(ytPendingLikesKey, updated) }
    }

    // Likes pushed from the app that the account's "liked music" list has not shown yet. Kept
    // apart from the synced set: some items (podcast episodes, plain videos) never appear in that
    // list, and anything in the synced set that is missing from it would be un-liked locally.
    fun pushedLikedIds(): Set<String> = synchronized(lock) {
        prefs().getStringSet(ytPushedLikedIdsKey, emptySet())?.toSet() ?: emptySet()
    }

    fun addPushedLiked(ids: Collection<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        val updated = (prefs().getStringSet(ytPushedLikedIdsKey, emptySet()) ?: emptySet()).toMutableSet()
        if (updated.addAll(ids)) prefs().edit { putStringSet(ytPushedLikedIdsKey, updated) }
    }

    fun removePushedLiked(ids: Collection<String>) = synchronized(lock) {
        if (ids.isEmpty()) return@synchronized
        val updated = (prefs().getStringSet(ytPushedLikedIdsKey, emptySet()) ?: emptySet()).toMutableSet()
        if (updated.removeAll(ids.toSet())) prefs().edit { putStringSet(ytPushedLikedIdsKey, updated) }
    }

    /**
     * The bookkeeping belongs to one account. Switching account (which only swaps the cookie)
     * must start from scratch, or the old account's synced set would un-like songs locally and
     * its pending operations would be replayed on the new account.
     *
     * @return false when the account can't be identified yet; the likes sync must not run then.
     */
    fun ensureOwner(): Boolean = synchronized(lock) {
        val p = prefs()
        val owner = listOf(ytDataSyncIdKey, ytAccountEmailKey, ytAccountChannelHandleKey)
            .firstNotNullOfOrNull { key -> p.getString(key, null)?.takeIf { it.isNotBlank() } }
            ?: return@synchronized false
        if (p.getString(ytSyncOwnerKey, null) != owner) {
            reset()
            p.edit { putString(ytSyncOwnerKey, owner) }
        }
        true
    }

    fun lastSyncAt(): Long = prefs().getLong(ytLastSyncAtKey, 0L)

    fun markSynced() = prefs().edit { putLong(ytLastSyncAtKey, System.currentTimeMillis()) }

    /** Called when the account is disconnected: its bookkeeping means nothing for the next one. */
    fun reset() = synchronized(lock) {
        prefs().edit {
            remove(ytSyncedLikedIdsKey)
            remove(ytPendingLikesKey)
            remove(ytPushedLikedIdsKey)
            remove(ytLikeBaselineKey)
            remove(ytLastSyncAtKey)
        }
    }
}
