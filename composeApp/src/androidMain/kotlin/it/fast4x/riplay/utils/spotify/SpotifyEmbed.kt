package it.fast4x.riplay.utils.spotify

import it.fast4x.riplay.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** A Spotify playlist or album, as named by a link. */
data class SpotifyRef(val type: String, val id: String) {
    val url: String get() = "https://open.spotify.com/$type/$id"
    val embedUrl: String get() = "https://open.spotify.com/embed/$type/$id"
}

data class SpotifyTrack(
    val uri: String,
    val title: String,
    val artists: String,
    val durationMs: Long,
)

data class SpotifyCollection(
    val ref: SpotifyRef?,
    val name: String,
    val tracks: List<SpotifyTrack>,
)

class SpotifyImportException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { InvalidLink, NotFound, Network, Unparsable, Empty }
}

/**
 * Reads public Spotify playlists and albums without an account.
 *
 * The Web API needs an app registered by a developer and, in development mode, only lets five
 * listed users in, so it is of no use to the public. The embed player page
 * (open.spotify.com/embed/{type}/{id}) is served to anyone and carries the track list in its
 * Next.js state (`__NEXT_DATA__`): uri, title, subtitle (the artists, joined with ",&nbsp;")
 * and duration in ms. It lists at most 100 tracks: a longer playlist is cut at 100.
 */
object SpotifyEmbed {

    const val MAX_TRACKS = 100

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0 Safari/537.36"

    // open.spotify.com/playlist/ID, /intl-es/playlist/ID, /embed/album/ID,
    // /user/someone/playlist/ID and spotify:playlist:ID. Ids are 22 base62 characters.
    private val linkRegex = Regex(
        """(?:open\.spotify\.com/(?:[^\s?#"']*?/)?|spotify:)(playlist|album)[/:]([A-Za-z0-9]{22})(?![A-Za-z0-9])"""
    )
    private val shortLinkRegex = Regex(
        """https?://(?:spotify\.link|spotify\.app\.link|spoti\.fi)/[A-Za-z0-9_\-]+"""
    )
    private val nextDataRegex = Regex(
        """<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL
    )
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient by lazy {
        okHttpClient().newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** The playlist/album named in [text] (a link, a URI or a share text with a link in it). */
    fun parseRef(text: String?): SpotifyRef? {
        if (text.isNullOrBlank()) return null
        val match = linkRegex.find(text.replace("\\/", "/")) ?: return null
        return SpotifyRef(match.groupValues[1], match.groupValues[2])
    }

    fun findShortLink(text: String?): String? =
        text?.let { shortLinkRegex.find(it)?.value }

    /** True when [text] holds something [resolve] can turn into a playlist or album. */
    fun looksLikeSpotifyCollection(text: String?): Boolean =
        parseRef(text) != null || findShortLink(text) != null

    /** Turns a link (long or short) into a ref; short links are followed to the real page. */
    suspend fun resolve(text: String): SpotifyRef {
        parseRef(text)?.let { return it }
        val short = findShortLink(text)
            ?: throw SpotifyImportException(SpotifyImportException.Reason.InvalidLink, "Not a Spotify link: $text")
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(short).header("User-Agent", USER_AGENT).build()
            val (finalUrl, body) = runCatching {
                client.newCall(request).execute().use { response ->
                    response.request.url.toString() to (response.body?.string().orEmpty())
                }
            }.getOrElse {
                throw SpotifyImportException(SpotifyImportException.Reason.Network, it.message ?: "network")
            }
            // A 3xx lands on open.spotify.com; branch.io pages for browsers carry the target
            // in the HTML (meta refresh / script) instead.
            parseRef(finalUrl) ?: parseRef(body)
                ?: throw SpotifyImportException(SpotifyImportException.Reason.InvalidLink, "Short link $short -> $finalUrl")
        }
    }

    suspend fun fetch(ref: SpotifyRef): SpotifyCollection = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ref.embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en")
            .build()
        val html = runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 404)
                    throw SpotifyImportException(SpotifyImportException.Reason.NotFound, "404 ${ref.embedUrl}")
                if (!response.isSuccessful)
                    throw SpotifyImportException(SpotifyImportException.Reason.Network, "HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
        }.getOrElse {
            if (it is SpotifyImportException) throw it
            throw SpotifyImportException(SpotifyImportException.Reason.Network, it.message ?: "network")
        }
        parse(html, ref)
    }

    /** Parses an embed page. Public so it can be checked against a saved page. */
    fun parse(html: String, ref: SpotifyRef?): SpotifyCollection {
        val entity: JsonObject? = nextDataRegex.find(html)?.groupValues?.get(1)
            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            ?.let { findTrackListOwner(it) }

        val (name, trackArray) = if (entity != null) {
            (entity.string("name") ?: entity.string("title")).orEmpty() to (entity["trackList"] as JsonArray)
        } else {
            // No Next.js state (page layout changed): cut the array out of the raw HTML.
            val array = extractArray(html, "\"trackList\":")
                ?.let { runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
                ?: throw SpotifyImportException(SpotifyImportException.Reason.Unparsable, "No trackList")
            val rawName = Regex(""""name":"((?:[^"\\]|\\.)*)"""").find(html)?.groupValues?.get(1)
            val decoded = rawName?.let { runCatching { (json.parseToJsonElement("\"$it\"") as JsonPrimitive).content }.getOrNull() }
            decoded.orEmpty() to array
        }

        val tracks = trackArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val entityType = obj.string("entityType")
            if (entityType != null && entityType != "track") return@mapNotNull null
            val title = obj.string("title")?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            SpotifyTrack(
                uri = obj.string("uri") ?: "spotify:track:${title.hashCode()}",
                title = title,
                artists = obj.string("subtitle").orEmpty().replace(' ', ' ').trim(),
                durationMs = (obj["duration"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }
        if (tracks.isEmpty())
            throw SpotifyImportException(SpotifyImportException.Reason.Empty, "Empty trackList")
        Timber.d("SpotifyEmbed parsed '${name}' ${tracks.size} tracks")
        return SpotifyCollection(ref, name.trim(), tracks)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun findTrackListOwner(element: JsonElement): JsonObject? = when (element) {
        is JsonObject ->
            if (element["trackList"] is JsonArray) element
            else element.values.firstNotNullOfOrNull { findTrackListOwner(it) }
        is JsonArray -> element.firstNotNullOfOrNull { findTrackListOwner(it) }
        else -> null
    }

    /** The JSON array that follows [key] in [text], matched bracket by bracket. */
    private fun extractArray(text: String, key: String): String? {
        val start = text.indexOf(key).takeIf { it >= 0 }?.let { text.indexOf('[', it) } ?: return null
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '[', '{' -> depth++
                ']', '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
