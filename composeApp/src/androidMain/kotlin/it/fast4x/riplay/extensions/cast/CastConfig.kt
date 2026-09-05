package it.fast4x.riplay.extensions.cast

import android.content.Context
import it.fast4x.riplay.extensions.preferences.castReceiverIdKey
import it.fast4x.riplay.extensions.preferences.preferences

/**
 * Which Cast receiver application the app launches on the TV.
 *
 * A receiver is a web page registered in the Google Cast console. Until ours is registered and
 * published, the app ships with receivers that are already published by someone else, so casting
 * works out of the box with no account and no fee:
 *
 * - [DEFAULT_MEDIA] plays any audio stream url. Live radio casts to any Chromecast with it.
 * - [YOUTUBE_TEST] is the public sample receiver of the android-youtube-player project. It runs
 *   the YouTube IFrame player, which is the only way to cast a YouTube song (their audio urls
 *   are not reachable, which is why the app itself plays them in a WebView).
 * - [YAMMBO] is our own page at music.yammbo.com/cast/, which does both. It only becomes
 *   selectable once it is registered in the Cast console; an unregistered receiver refuses to
 *   launch on devices that are not registered for development.
 */
object CastReceivers {
    /** Google's Default Media Receiver: plays plain media urls, always available. */
    const val DEFAULT_MEDIA = "CC1AD845"

    /** Public sample receiver that speaks the YouTube IFrame protocol below. */
    const val YOUTUBE_TEST = "C5CBE8CA"

    /**
     * Ours, hosted at https://music.yammbo.com/cast/ and registered in the Cast console on
     * 2026-09-04. It plays both radio streams and YouTube songs, so it replaces the two
     * borrowed receivers above.
     */
    const val YAMMBO = "63E0D60C"

    fun current(context: Context): String = forItem(context, isRadio = true)

    /**
     * The receiver to launch for what is about to play. Since no free published receiver does
     * both, the app picks per content and switches before the session starts; once our own is
     * registered it takes over and this stops mattering.
     */
    fun forItem(context: Context, isRadio: Boolean): String {
        val override = context.preferences.getString(castReceiverIdKey, "").orEmpty().trim()
        if (override.isNotBlank()) return override
        if (YAMMBO.isNotBlank()) return YAMMBO
        return if (isRadio) DEFAULT_MEDIA else YOUTUBE_TEST
    }

    /** True when the selected receiver understands [CastMessages], i.e. can play YouTube songs. */
    fun supportsYouTube(receiverId: String): Boolean =
        receiverId == YOUTUBE_TEST || (YAMMBO.isNotBlank() && receiverId == YAMMBO)
}

/**
 * Sender to receiver protocol for YouTube playback, kept byte for byte compatible with the
 * android-youtube-player receiver so the published sample can be used for testing.
 */
object CastMessages {
    const val NAMESPACE = "urn:x-cast:com.pierfrancescosoffritti.androidyoutubeplayer.chromecast.communication"

    const val INIT_COMMUNICATION_CONSTANTS = "INIT_COMMUNICATION_CONSTANTS"

    // receiver to sender
    const val IFRAME_API_READY = "IFRAME_API_READY"
    const val READY = "READY"
    const val STATE_CHANGED = "STATE_CHANGED"
    const val PLAYBACK_QUALITY_CHANGED = "PLAYBACK_QUALITY_CHANGED"
    const val PLAYBACK_RATE_CHANGED = "PLAYBACK_RATE_CHANGED"
    const val ERROR = "ERROR"
    const val API_CHANGED = "API_CHANGED"
    const val VIDEO_CURRENT_TIME = "VIDEO_CURRENT_TIME"
    const val VIDEO_DURATION = "VIDEO_DURATION"
    const val VIDEO_ID = "VIDEO_ID"

    // sender to receiver
    const val LOAD = "LOAD"
    const val CUE = "CUE"
    const val PLAY = "PLAY"
    const val PAUSE = "PAUSE"
    const val SET_VOLUME = "SET_VOLUME"
    const val SEEK_TO = "SEEK_TO"
    const val MUTE = "MUTE"
    const val UNMUTE = "UNMUTE"
    const val SET_PLAYBACK_RATE = "SET_PLAYBACK_RATE"

    // Ours only; the published sample receiver ignores what it does not know
    const val RADIO_LOAD = "RADIO_LOAD"
    const val SET_LYRICS = "SET_LYRICS"
    const val SET_VIDEO_MODE = "SET_VIDEO_MODE"

    /**
     * The action behind a remote button press reported by our receiver, or null for the state
     * messages every receiver sends. Parsed by hand: the payload is one flat object.
     */
    /**
     * The YouTube error code the receiver reports, if this message is one. Without this a
     * song that the TV cannot play (embedding disabled, video removed) looked exactly like
     * a bug in the app: the phone showed it playing and the TV stayed quiet.
     */
    /**
     * The IFrame player state the receiver reports: "1" playing, "2" paused, "3" buffering,
     * "5" cued, "-1" not started. Anything but null means the TV is alive and answering.
     */
    /**
     * Where the TV is in the song, and how long it is, reported once a second. The phone
     * needs this because its own copy of the song is muted and can be throttled to a
     * standstill by the system, freezing its seek bar at 0:00 while the TV plays on.
     */
    fun currentTimeOf(message: String): Pair<Float, Float>? {
        if (!Regex("\"type\"\\s*:\\s*\"VIDEO_CURRENT_TIME\"").containsMatchIn(message)) return null
        val time = Regex("\"time\"\\s*:\\s*\"?([0-9.]+)\"?").find(message)
            ?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        val duration = Regex("\"duration\"\\s*:\\s*\"?([0-9.]+)\"?").find(message)
            ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        return time to duration
    }

    fun playerStateOf(message: String): String? {
        if (!Regex("\"type\"\\s*:\\s*\"STATE_CHANGED\"").containsMatchIn(message)) return null
        return Regex("\"state\"\\s*:\\s*\"?(-?[0-9]+)\"?").find(message)?.groupValues?.get(1)
    }

    fun errorCodeOf(message: String): String? {
        if (!Regex("\"type\"\\s*:\\s*\"ERROR\"").containsMatchIn(message)) return null
        return Regex("\"error\"\\s*:\\s*\"?([0-9]+)\"?").find(message)?.groupValues?.get(1)
    }

    fun remoteActionOf(message: String): String? {
        if (!message.contains("\"REMOTE\"")) return null
        val match = Regex("\"action\"\\s*:\\s*\"([a-z]+)\"").find(message) ?: return null
        return match.groupValues[1]
    }

    /**
     * The receiver has no hardcoded vocabulary: the sender tells it which strings to use before
     * anything else, and only then does the receiver load the IFrame API.
     */
    fun initPayload(): String = buildString {
        append("{\"command\":\"").append(INIT_COMMUNICATION_CONSTANTS).append("\",")
        append("\"communicationConstants\":{")
        listOf(
            IFRAME_API_READY, READY, STATE_CHANGED, PLAYBACK_QUALITY_CHANGED, PLAYBACK_RATE_CHANGED,
            ERROR, API_CHANGED, VIDEO_CURRENT_TIME, VIDEO_DURATION, VIDEO_ID,
            LOAD, CUE, PLAY, PAUSE, SET_VOLUME, SEEK_TO, MUTE, UNMUTE, SET_PLAYBACK_RATE,
        ).forEachIndexed { index, constant ->
            if (index > 0) append(',')
            append('"').append(constant).append("\":\"").append(constant).append('"')
        }
        // The phone's language, from the first message: the TV shows its splash before any
        // song arrives, and until now that was the only thing carrying a locale.
        append("},\"locale\":\"").append(java.util.Locale.getDefault().language).append('"')
        append('}')
    }

    fun command(command: String, vararg extras: Pair<String, String>): String = buildString {
        append("{\"command\":\"").append(command).append('"')
        extras.forEach { (key, value) ->
            append(",\"").append(key).append("\":\"").append(escape(value)).append('"')
        }
        append('}')
    }

    /**
     * JSON string escaping. Lyrics are full of real line breaks and titles carry backslashes;
     * either one produced a payload the receiver could not parse, so the message was dropped
     * whole and the TV silently ignored it.
     */
    private fun escape(value: String): String = buildString(value.length + 16) {
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
    }
}
