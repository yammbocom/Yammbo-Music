package it.fast4x.riplay.service

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand
import timber.log.Timber

@UnstableApi
class PlayerMediaSessionCallback (
    val binder: PlayerService.Binder,
    val onPlayClick: () -> Unit,
    val onPauseClick: () -> Unit,
    val onSeekToPos: (Long) -> Unit,
    val onPlayNext: () -> Unit,
    val onPlayPrevious: () -> Unit,
    val onPlayQueueItem: (Long) -> Unit,
    val onCustomClick: (String) -> Unit,
) : MediaSessionCompat.Callback() {

    override fun onPlay() {
        Timber.d("MediaSessionCallback onPlay()")
        onPlayClick()
    }
    override fun onPause() {
        Timber.d("MediaSessionCallback onPause()")
        onPauseClick()
    }

    override fun onStop() {
        Timber.d("MediaSessionCallback onStop()")
        onPause()
    }
    override fun onSkipToPrevious() {
        Timber.d("MediaSessionCallback onSkipToPrevious()")
        onPlayPrevious()
        //binder.player.playPrevious()
    }
    override fun onSkipToNext() {
        Timber.d("MediaSessionCallback onSkipToNext()")
        onPlayNext()
        //binder.player.playNext()
    }
    override fun onSeekTo(pos: Long) {
        Timber.d("MediaSessionCallback onSeekTo() $pos")
        onSeekToPos(pos)
    }

    override fun onRewind() {
        Timber.d("MediaSessionCallback onRewind()")
        binder.player.seekToDefaultPosition()
    }
    override fun onSkipToQueueItem(id: Long) {
        Timber.d("MediaSessionCallback onSkipToQueueItem() $id")
        onPlayQueueItem(id)
        //binder.player.seekToDefaultPosition(id.toInt())
    }
    override fun onCustomAction(action: String, extras: Bundle?) {
        Timber.d("MediaSessionCallback onCustomAction() action $action")
        onCustomClick(action)
    }
    // "Hey Google, play X" in the car, or on the phone while the app is running. Assistant may
    // prepare before it plays: preparing starts playback as well (a repeated request within a few
    // seconds is dropped), so the play() that may follow only confirms it.
    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        Timber.d("MediaSessionCallback onPlayFromSearch query $query")
        SessionPlayback.playFromSearch(binder, query, extras)
    }

    override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
        Timber.d("MediaSessionCallback onPrepareFromSearch query $query")
        SessionPlayback.playFromSearch(binder, query, extras)
    }

    // A tap on a playable item of the Android Auto browse tree: its media id names the list it
    // was shown in, so the rest of that list becomes the queue (see CarLibrary).
    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
        Timber.d("MediaSessionCallback onPlayFromMediaId mediaId $mediaId")
        SessionPlayback.playFromMediaId(binder, mediaId)
    }

    override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
        Timber.d("MediaSessionCallback onPrepareFromMediaId mediaId $mediaId")
        SessionPlayback.playFromMediaId(binder, mediaId)
    }

    // getParcelableExtra(String) is deprecated on API 33+, where the typed overload is used instead
    @Suppress("DEPRECATION")
    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        mediaButtonEvent?.let {
            if (it.action == Intent.ACTION_MEDIA_BUTTON) {
                // EXTRA_KEY_EVENT is a Parcelable KeyEvent: reading it as a boolean was always false,
                // so none of this ran and Bluetooth / wired / car buttons fell to the default handling.
                val keyEvent: KeyEvent? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        it.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    else
                        it.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

                // PLAY_PAUSE and HEADSETHOOK (and any other key) are left to the framework: it owns
                // the single / double / triple click of one-button headsets (double = next, triple =
                // previous) and picks play or pause from the session state, which already reflects
                // whichever engine is sounding. Handling PLAY_PAUSE here lost the double click.
                if (keyEvent == null || keyEvent.keyCode !in handledKeyCodes) return false

                // Act once per press: the ACTION_UP and the auto-repeats of a key we handle are
                // consumed, otherwise the framework would fire the same command a second time.
                if (keyEvent.action != KeyEvent.ACTION_DOWN || keyEvent.repeatCount != 0) return true

                when(keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> onSkipToNext()
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onSkipToPrevious()
                    KeyEvent.KEYCODE_MEDIA_STOP -> onStop()
                    KeyEvent.KEYCODE_MEDIA_PLAY -> onPlay()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> onPause()
                }
                return true
            }
        }
        // false hands the event to MediaSession's default handling: MediaSessionCompat's API 21
        // callback returns `result || super.onMediaButtonEvent(...)`
        return false
    }

    private val handledKeyCodes = setOf(
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE
    )

}

object MediaSessionConstants {
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_START_RADIO = "START_RADIO"
    const val ACTION_SEARCH = "ACTION_SEARCH"
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleRepeatMode = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val CommandStartRadio = SessionCommand(ACTION_START_RADIO, Bundle.EMPTY)
    val CommandSearch = SessionCommand(ACTION_SEARCH, Bundle.EMPTY)
}