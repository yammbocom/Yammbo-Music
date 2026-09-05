package it.fast4x.riplay.extensions.cast

import android.content.Context
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.isRadio
import java.util.Locale
import it.fast4x.riplay.utils.radioStreamUrlOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Google Cast sender.
 *
 * Two ways to put sound on the TV, because the two kinds of media the app plays are nothing
 * alike:
 *
 * - A live radio station is a plain audio url, so it goes as normal Cast media and plays on any
 *   Chromecast, including Google's Default Media Receiver.
 * - A YouTube song has no url the TV could fetch (that is why the app plays it in a WebView), so
 *   it is sent as a video id over [CastMessages.NAMESPACE] to a receiver that runs the YouTube
 *   IFrame player. Receivers that do not speak that protocol simply cannot play songs, and
 *   [canCastCurrentItem] says so before anything is attempted.
 */
object CastManager {

    private var castContext: CastContext? = null
    private var appContext: Context? = null
    private var session: CastSession? = null
    private var youTubeChannelOpen = false

    /** Receiver the next session will launch; see [prepareFor]. */
    private var receiverId: String? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    /**
     * Whether the TV has actually said it is playing what it was sent.
     *
     * The phone cannot tell a working session from a receiver that took the song and did
     * nothing with it: both look identical from here, and the second one left the phone
     * muted and the room silent. Nothing is assumed any more; the TV has to say so.
     */
    private val _isPlayingOnTv = MutableStateFlow(false)
    val isPlayingOnTv: StateFlow<Boolean> = _isPlayingOnTv.asStateFlow()

    /** Fired the moment the TV confirms it is playing, so the phone can go quiet again. */
    var onPlaybackConfirmed: (() -> Unit)? = null

    /** Position and duration as the TV sees them, once a second: (seconds, duration). */
    var onRemoteTime: ((Float, Float) -> Unit)? = null

    /**
     * The TV cannot play this song, with YouTube's code. 150 and 101 mean the owner does
     * not allow the video outside YouTube, which the phone gets away with because its own
     * player passes for youtube.com and a receiver page cannot.
     */
    var onPlaybackFailed: ((String) -> Unit)? = null

    /** Set by whoever owns playback, so a session that starts later can pick up what is playing. */
    var onSessionStarted: (() -> Unit)? = null
    var onSessionEnded: (() -> Unit)? = null

    /**
     * A button pressed on the TV remote. The receiver cannot skip on its own: only the phone
     * knows the queue, so it asks for "next", "previous", "play" or "pause".
     */
    var onRemoteAction: ((String) -> Unit)? = null

    /**
     * Why a session refused to start, in the framework's own words. Surfaced to the listener
     * because a silent "it does not connect" is impossible to act on: the code says whether the
     * receiver is unreachable, still propagating, or refusing the launch.
     */
    var onSessionError: ((String) -> Unit)? = null

    /** Google Play services must be present and current, which is not a given on every device. */
    fun isAvailable(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (castContext != null || !isAvailable(context)) return
        runCatching { CastContext.getSharedInstance(context) }
            .onSuccess { ctx ->
                castContext = ctx
                ctx.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
                // A session can appear without the session listener firing here (started
                // before this ran, resumed by the framework, handed over from another app),
                // and then the app believed it was not casting while the TV clearly was.
                ctx.addCastStateListener {
                    // Whether a live session exists, never the state code. CONNECTING is
                    // reported during a reconnect while the session is perfectly alive, and
                    // taking that as "not casting" dropped the app out mid-song: the 4 Sep
                    // log has the app choosing a receiver again at 15:42:52 while the TV was
                    // still talking to it.
                    val live = ctx.sessionManager.currentCastSession?.takeIf { it.isConnected }
                    if (live != null) attach(live) else if (_isConnected.value) detach()
                }
                ctx.sessionManager.currentCastSession?.let { attach(it) }
            }
            .onFailure { Timber.w("CastManager: Cast unavailable: ${it.message}") }
    }

    val castState: Int
        get() = castContext?.castState ?: CastState.NO_DEVICES_AVAILABLE

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, sessionId: String) = attach(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = attach(s)
        override fun onSessionEnded(s: CastSession, error: Int) = detach()
        override fun onSessionSuspended(s: CastSession, reason: Int) = detach()
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, sessionId: String) {}
        override fun onSessionStartFailed(s: CastSession, error: Int) {
            reportError("start", error)
            detach()
        }
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResumeFailed(s: CastSession, error: Int) {
            reportError("resume", error)
            detach()
        }
    }

    private fun reportError(stage: String, code: Int) {
        val name = runCatching { CastStatusCodes.getStatusCodeString(code) }.getOrNull()
        val detail = listOfNotNull(name, "$code").joinToString(" · ")
        Timber.w("CastManager: session $stage failed ($detail) receiver=$receiverId")
        onSessionError?.invoke(detail)
    }

    private fun attach(castSession: CastSession) {
        // The state listener can report the same session more than once; re-opening the
        // channel would send a second INIT and reset the receiver mid-song.
        if (session === castSession && _isConnected.value) return
        session = castSession
        _isConnected.value = true
        _deviceName.value = runCatching { castSession.castDevice?.friendlyName }.getOrNull()
        openYouTubeChannel(castSession)
        onSessionStarted?.invoke()
    }

    private fun detach() {
        session = null
        youTubeChannelOpen = false
        _isPlayingOnTv.value = false
        _isConnected.value = false
        _deviceName.value = null
        onSessionEnded?.invoke()
    }

    /**
     * Points the framework at the receiver that can play [mediaItem], before the user picks a
     * device. A live session cannot change receiver, so this is a no-op while connected.
     */
    fun prepareFor(mediaItem: MediaItem?) {
        val context = appContext ?: return
        val item = mediaItem ?: return
        if (_isConnected.value) return
        val target = CastReceivers.forItem(context, item.isRadio)
        if (target == receiverId) return
        runCatching { castContext?.setReceiverApplicationId(target) }
            .onSuccess {
                receiverId = target
                Timber.d("CastManager: receiver set to $target")
            }
            .onFailure { Timber.w("CastManager: could not set receiver $target: ${it.message}") }
    }

    private fun openYouTubeChannel(castSession: CastSession) {
        // Decided from the receiver the app actually launched, not from session metadata, which
        // is null for the first moments of a session.
        val receiver = receiverId
            ?: runCatching { castSession.applicationMetadata?.applicationId }.getOrNull()
            ?: appContext?.let { CastReceivers.current(it) }
        if (receiver == null || !CastReceivers.supportsYouTube(receiver)) {
            youTubeChannelOpen = false
            return
        }
        runCatching {
            castSession.setMessageReceivedCallbacks(CastMessages.NAMESPACE) { _, _, message ->
                Timber.d("CastManager: receiver says $message")
                CastMessages.remoteActionOf(message)?.let { action ->
                    onRemoteAction?.invoke(action)
                }
                CastMessages.errorCodeOf(message)?.let { code ->
                    _isPlayingOnTv.value = false
                    onSessionError?.invoke(youTubeErrorText(code))
                    onPlaybackFailed?.invoke(code)
                }
                CastMessages.currentTimeOf(message)?.let { (time, duration) ->
                    // A clock that moves is the strongest proof there is that the TV is
                    // really playing, stronger than any state number.
                    if (time > 0f && !_isPlayingOnTv.value) {
                        _isPlayingOnTv.value = true
                        onPlaybackConfirmed?.invoke()
                    }
                    onRemoteTime?.invoke(time, duration)
                }
                CastMessages.playerStateOf(message)?.let { state ->
                    // 1 playing, 3 buffering: either way the embed is really working on it.
                    if (state == "1" || state == "3") {
                        if (!_isPlayingOnTv.value) {
                            _isPlayingOnTv.value = true
                            onPlaybackConfirmed?.invoke()
                        }
                    }
                }
            }
            // The receiver waits for this before it loads the IFrame API; without it every
            // later command is ignored and the TV just sits on the splash.
            castSession.sendMessage(CastMessages.NAMESPACE, CastMessages.initPayload())
            youTubeChannelOpen = true
        }.onFailure { Timber.w("CastManager: no YouTube channel on this receiver: ${it.message}") }
    }

    /** Whether the connected receiver can play this item at all. */
    fun canCastCurrentItem(mediaItem: MediaItem?): Boolean {
        if (mediaItem == null) return false
        // A file on the phone has no address the TV could fetch, so it stays here.
        if (mediaItem.isLocal) return false
        if (mediaItem.isRadio) return true
        // Only online songs can travel, as a video id.
        return youTubeChannelOpen
    }

    /**
     * Sends whatever is playing to the TV. Returns false when this receiver cannot take it, so
     * the caller can keep playing locally and say why.
     */
    fun castItem(mediaItem: MediaItem?, positionSeconds: Float = 0f): Boolean {
        val castSession = session
        if (castSession == null) {
            Timber.w("CastManager: castItem with no session for ${mediaItem?.mediaId}")
            return false
        }
        val item = mediaItem ?: return false
        // Sending a local id as if it were a video used to report success and leave both the
        // phone (muted) and the TV (nothing to play) silent.
        if (item.isLocal) return false

        radioStreamUrlOf(item.mediaId)?.let { streamUrl ->
            val loaded = castRadio(castSession, item, streamUrl)
            Timber.d("CastManager: radio $streamUrl loaded=$loaded")
            // Our receiver says when the station is really playing, so nothing is assumed
            // here. A foreign one never reports, so there the load has to count as proof.
            _isPlayingOnTv.value = loaded && !youTubeChannelOpen
            return loaded
        }

        _isPlayingOnTv.value = false

        if (!youTubeChannelOpen) {
            Timber.w("CastManager: no YouTube channel, ${item.mediaId} stays on the phone")
            return false
        }
        Timber.d("CastManager: sending ${item.mediaId} to the TV at ${positionSeconds}s")
        return runCatching {
            castSession.sendMessage(
                CastMessages.NAMESPACE,
                CastMessages.command(
                    CastMessages.LOAD,
                    "videoId" to item.mediaId,
                    "startSeconds" to positionSeconds.toString(),
                    // Everything the TV screen shows. A receiver that does not understand these
                    // (the published sample one) ignores them and still plays the song.
                    // Without this the TV showed "e:Espresso": the app keeps markers like
                    // the explicit flag inside the title.
                    "title" to cleanPrefix(item.mediaMetadata.title?.toString().orEmpty()),
                    "artist" to item.mediaMetadata.artist?.toString().orEmpty(),
                    "artwork" to item.mediaMetadata.artworkUri?.toString().orEmpty(),
                    "locale" to Locale.getDefault().language,
                    "showVideo" to showVideo.toString(),
                )
            )
            true
        }.getOrElse {
            Timber.e("CastManager: could not send video ${item.mediaId}: ${it.message}")
            false
        }
    }

    /** Whether the TV shows the video or just the cover. Off by default: this is a music app. */
    var showVideo: Boolean = false
        set(value) {
            field = value
            val castSession = session ?: return
            if (!youTubeChannelOpen) return
            runCatching {
                castSession.sendMessage(
                    CastMessages.NAMESPACE,
                    CastMessages.command(CastMessages.SET_VIDEO_MODE, "showVideo" to value.toString())
                )
            }
        }

    /** Sends the song's lyrics so the TV can follow along. Empty text clears them. */
    fun sendLyrics(text: String?) {
        val castSession = session ?: return
        if (!youTubeChannelOpen) return
        runCatching {
            castSession.sendMessage(
                CastMessages.NAMESPACE,
                CastMessages.command(CastMessages.SET_LYRICS, "lyrics" to text.orEmpty())
            )
        }
    }

    private fun castRadio(castSession: CastSession, item: MediaItem, streamUrl: String): Boolean {
        // Our own receiver plays the stream in an audio element, which works out the format
        // by itself. The framework needs a content type declared up front and the phone can
        // only guess it from the url: an aac station announced as audio/mpeg played nothing
        // at all, with no error anywhere.
        if (youTubeChannelOpen) {
            return runCatching {
                castSession.sendMessage(
                    CastMessages.NAMESPACE,
                    CastMessages.command(
                        CastMessages.RADIO_LOAD,
                        "url" to streamUrl,
                        "title" to cleanPrefix(item.mediaMetadata.title?.toString().orEmpty()),
                        "artist" to item.mediaMetadata.artist?.toString().orEmpty(),
                        "artwork" to item.mediaMetadata.artworkUri?.toString().orEmpty(),
                        "locale" to Locale.getDefault().language,
                    )
                )
                true
            }.getOrElse {
                Timber.e("CastManager: could not send station $streamUrl: ${it.message}")
                false
            }
        }

        val client = castSession.remoteMediaClient ?: return false
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, item.mediaMetadata.title?.toString().orEmpty())
            putString(MediaMetadata.KEY_ARTIST, item.mediaMetadata.artist?.toString().orEmpty())
            item.mediaMetadata.artworkUri?.let { addImage(WebImage(it)) }
        }
        val info = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(if (streamUrl.contains(".m3u8", true)) "application/x-mpegurl" else "audio/mpeg")
            .setMetadata(metadata)
            .build()
        return runCatching {
            client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
            true
        }.getOrElse {
            Timber.e("CastManager: could not load stream $streamUrl: ${it.message}")
            false
        }
    }

    fun play() {
        val castSession = session ?: return
        if (youTubeChannelOpen) {
            runCatching { castSession.sendMessage(CastMessages.NAMESPACE, CastMessages.command(CastMessages.PLAY)) }
        }
        runCatching { castSession.remoteMediaClient?.play() }
    }

    fun pause() {
        val castSession = session ?: return
        if (youTubeChannelOpen) {
            runCatching { castSession.sendMessage(CastMessages.NAMESPACE, CastMessages.command(CastMessages.PAUSE)) }
        }
        runCatching { castSession.remoteMediaClient?.pause() }
    }

    /**
     * Volume of the TV, from the phone's volume keys.
     *
     * The session volume is what the Chromecast itself applies, so it works on every receiver;
     * the player message is only useful on a receiver running the YouTube embed, whose own
     * player has a separate level.
     */
    fun setVolume(fraction: Float) {
        val castSession = session ?: return
        val level = fraction.coerceIn(0f, 1f)
        // Not the session volume: a Chromecast with a TV attached reports fixed volume control
        // and the framework answers with a "device does not support volume change" toast.
        if (youTubeChannelOpen) {
            runCatching {
                castSession.sendMessage(
                    CastMessages.NAMESPACE,
                    CastMessages.command(
                        CastMessages.SET_VOLUME,
                        "volumePercent" to (level * 100).toInt().toString()
                    )
                )
            }
        }
    }

    fun seekTo(seconds: Float) {
        val castSession = session ?: return
        if (youTubeChannelOpen) {
            runCatching {
                castSession.sendMessage(
                    CastMessages.NAMESPACE,
                    CastMessages.command(CastMessages.SEEK_TO, "time" to seconds.toString())
                )
            }
        }
        runCatching { castSession.remoteMediaClient?.seek((seconds * 1000).toLong()) }
    }

    /** The few YouTube player errors worth telling the listener apart. */
    private fun youTubeErrorText(code: String): String = when (code) {
        "101", "150" -> "YouTube $code: this song cannot be played on a TV"
        "100" -> "YouTube 100: this song is no longer on YouTube"
        else -> "YouTube $code"
    }

    fun stop() {
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    }
}
