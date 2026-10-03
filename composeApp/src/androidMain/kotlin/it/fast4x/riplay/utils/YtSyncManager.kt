package it.fast4x.riplay.utils

import it.fast4x.environment.Environment
import it.fast4x.riplay.ui.screens.settings.isYtSyncEnabled
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Runs the whole YouTube Music sync on demand ("Sincronizar ahora") and exposes its progress.
 * Lives outside the composition so leaving the screen does not cancel a sync in flight.
 */
object YtSyncManager {

    enum class Step { LIKES_AND_LISTS, LIBRARY }

    data class State(
        val running: Boolean = false,
        val failed: Boolean = false,
        val step: Step? = null,
    )

    private const val SYNC_TIMEOUT_MS = 180_000L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val runLock = Mutex()

    /** Returns false when a sync was already running, or when this one failed. */
    suspend fun syncNow(): Boolean {
        if (!runLock.tryLock()) return false
        try {
            if (!isYtSyncEnabled() || Environment.cookie.isNullOrBlank()) {
                _state.value = State(failed = true)
                return false
            }

            _state.value = State(running = true)

            // The timeout turns a stuck request into a visible error instead of an endless spinner.
            val ok = withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                var allOk = true

                if (isYtLikeSyncEnabled() || isYtPlaylistSyncEnabled()) {
                    _state.value = State(running = true, step = Step.LIKES_AND_LISTS)
                    if (!importYTMPrivatePlaylists(silent = true, refreshExisting = true)) allOk = false
                }

                if (isYtLibrarySyncEnabled()) {
                    _state.value = State(running = true, step = Step.LIBRARY)
                    if (!importYTMSubscribedChannels(silent = true)) allOk = false
                    if (!importYTMLikedAlbums(silent = true)) allOk = false
                }
                allOk
            } ?: false

            if (ok) YtSyncState.markSynced()
            else Timber.w("YtSyncManager: sync finished with errors or timed out")

            _state.value = State(failed = !ok)
            return ok
        } catch (e: CancellationException) {
            _state.value = State()
            throw e
        } catch (e: Exception) {
            Timber.e(e, "YtSyncManager: sync crashed")
            _state.value = State(failed = true)
            return false
        } finally {
            runLock.unlock()
        }
    }
}
