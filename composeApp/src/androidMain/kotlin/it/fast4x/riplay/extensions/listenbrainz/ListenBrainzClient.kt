package it.fast4x.riplay.extensions.listenbrainz

import androidx.core.content.edit
import androidx.media3.common.MediaItem
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.extensions.encryptedpreferences.encryptedPreferences
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.utils.appContext
import it.fast4x.riplay.utils.usesLocalPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

// The token lives in the encrypted store; only the switch and the user name are plain prefs.
const val listenBrainzTokenKey = "listenbrainz_token"
const val listenBrainzEnabledKey = "accounts_listenbrainz_enabled"
const val listenBrainzUserKey = "accounts_listenbrainz_user"

/** Outcome of checking a pasted token against the server. */
sealed interface ListenBrainzValidation {
    data class Valid(val userName: String) : ListenBrainzValidation
    data object Invalid : ListenBrainzValidation
    data object NetworkError : ListenBrainzValidation
}

fun isListenBrainzConnected(): Boolean = runCatching {
    !appContext().encryptedPreferences.getString(listenBrainzTokenKey, null).isNullOrBlank()
}.getOrDefault(false)

fun isListenBrainzEnabled(): Boolean =
    appContext().preferences.getBoolean(listenBrainzEnabledKey, true) && isListenBrainzConnected()

fun listenBrainzUserName(): String =
    appContext().preferences.getString(listenBrainzUserKey, "") ?: ""

/**
 * ListenBrainz scrobbling: "playing now" when a track starts, a "single" listen once enough of it
 * has been heard (half the track, at least 30 s, at most 4 min: the service's own rule).
 * The token is never logged.
 */
object ListenBrainzClient {

    private const val BASE_URL = "https://api.listenbrainz.org/1"
    private const val MIN_LISTEN_MS = 30_000L
    private const val MAX_LISTEN_MS = 240_000L
    private const val UNKNOWN_DURATION_LISTEN_MS = 60_000L
    private val RETRY_DELAYS_MS = longArrayOf(5_000L, 30_000L)
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingListen: Job? = null

    private fun token(): String? = runCatching {
        appContext().encryptedPreferences.getString(listenBrainzTokenKey, null)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Stores the token and user name after a successful validation. */
    fun saveConnection(token: String, userName: String) {
        appContext().encryptedPreferences.edit { putString(listenBrainzTokenKey, token.trim()) }
        appContext().preferences.edit {
            putString(listenBrainzUserKey, userName)
            putBoolean(listenBrainzEnabledKey, true)
        }
    }

    fun disconnect() {
        pendingListen?.cancel()
        pendingListen = null
        appContext().encryptedPreferences.edit { remove(listenBrainzTokenKey) }
        appContext().preferences.edit {
            remove(listenBrainzUserKey)
            remove(listenBrainzEnabledKey)
        }
    }

    /** Checks a token without storing it. Safe to call from the main thread. */
    suspend fun validateToken(rawToken: String): ListenBrainzValidation = withContext(Dispatchers.IO) {
        val candidate = rawToken.trim()
        if (candidate.isEmpty()) return@withContext ListenBrainzValidation.Invalid
        try {
            val request = Request.Builder()
                .url("$BASE_URL/validate-token")
                .header("Authorization", "Token $candidate")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                // 429 and 5xx say nothing about the token, so they are not "invalid".
                if (response.code == 429 || response.code >= 500) {
                    return@use ListenBrainzValidation.NetworkError
                }
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@use ListenBrainzValidation.NetworkError
                val user = json.optString("user_name", "")
                if (json.optBoolean("valid", false) && user.isNotBlank())
                    ListenBrainzValidation.Valid(user)
                else ListenBrainzValidation.Invalid
            }
        } catch (e: IOException) {
            Timber.d("ListenBrainz validate-token network error: ${e.javaClass.simpleName}")
            ListenBrainzValidation.NetworkError
        } catch (e: Exception) {
            Timber.d("ListenBrainz validate-token failed: ${e.javaClass.simpleName}")
            ListenBrainzValidation.NetworkError
        }
    }

    /** Called on every track change: announce it and schedule the listen for later. */
    fun onTrackStarted(mediaItem: MediaItem) {
        pendingListen?.cancel()
        pendingListen = null
        // Local files and radio streams have no catalogue id worth scrobbling.
        // This runs on the main thread at every track change: gate on plain prefs only (the user
        // name is saved on connect). The encrypted token is read later, on IO, by submit().
        val prefs = appContext().preferences
        if (!prefs.getBoolean(listenBrainzEnabledKey, true) || listenBrainzUserName().isBlank() ||
            mediaItem.usesLocalPlayer) return

        val artist = mediaItem.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: return
        val title = mediaItem.mediaMetadata.title?.toString()?.let { cleanPrefix(it) }
            ?.takeIf { it.isNotBlank() && it != "null" } ?: return
        val album = mediaItem.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val startedAt = System.currentTimeMillis() / 1000
        val duration = mediaItem.mediaMetadata.durationMs

        val listenAfter = if (duration != null && duration > 0)
            (duration / 2).coerceIn(MIN_LISTEN_MS, MAX_LISTEN_MS)
        else UNKNOWN_DURATION_LISTEN_MS

        pendingListen = scope.launch {
            submit("playing_now", artist, title, album, mediaItem.mediaId, null)
            delay(listenAfter)
            submit("single", artist, title, album, mediaItem.mediaId, startedAt)
        }
    }

    private suspend fun submit(
        type: String,
        artist: String,
        title: String,
        album: String?,
        videoId: String,
        listenedAt: Long?,
    ) {
        val token = token() ?: return

        val metadata = JSONObject()
            .put("artist_name", artist)
            .put("track_name", title)
        if (album != null) metadata.put("release_name", album)
        metadata.put(
            "additional_info",
            JSONObject()
                .put("media_player", "Yammbo Music")
                .put("submission_client", "Yammbo Music")
                .put("music_service", "youtube.com")
                .put("origin_url", "https://music.youtube.com/watch?v=$videoId")
        )

        val listen = JSONObject().put("track_metadata", metadata)
        if (listenedAt != null) listen.put("listened_at", listenedAt)

        val payload = JSONObject()
            .put("listen_type", type)
            .put("payload", JSONArray().put(listen))
            .toString()

        // "playing_now" is only useful while it is true, so it is not retried.
        val attempts = if (type == "single") RETRY_DELAYS_MS.size + 1 else 1
        for (attempt in 0 until attempts) {
            when (post(token, payload)) {
                PostResult.OK -> {
                    Timber.d("ListenBrainz $type accepted")
                    return
                }
                PostResult.REJECTED -> {
                    Timber.d("ListenBrainz $type rejected")
                    return
                }
                PostResult.RETRY -> {
                    if (attempt < attempts - 1) delay(RETRY_DELAYS_MS[attempt])
                }
            }
        }
        Timber.d("ListenBrainz $type dropped after retries")
    }

    private enum class PostResult { OK, REJECTED, RETRY }

    private suspend fun post(token: String, payload: String): PostResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/submit-listens")
                .header("Authorization", "Token $token")
                .post(payload.toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> PostResult.OK
                    response.code == 429 || response.code >= 500 -> PostResult.RETRY
                    else -> PostResult.REJECTED
                }
            }
        } catch (e: IOException) {
            PostResult.RETRY
        } catch (e: Exception) {
            Timber.d("ListenBrainz submit failed: ${e.javaClass.simpleName}")
            PostResult.REJECTED
        }
    }
}
