package it.fast4x.lrclib

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import it.fast4x.lrclib.models.Track
import it.fast4x.lrclib.utils.ProxyPreferences
import it.fast4x.lrclib.utils.getProxy
import it.fast4x.lrclib.utils.runCatchingCancellable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.Duration

object LrcLib {
    @OptIn(ExperimentalSerializationApi::class)
    private val client by lazy {
        HttpClient(OkHttp) {
            BrowserUserAgent()

            expectSuccess = true

            install(ContentNegotiation) {
                val feature = Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                }

                json(feature)
                //json(feature, ContentType.Text.Html)
                //json(feature, ContentType.Text.Plain)
            }

            install(ContentEncoding) {
                gzip()
                deflate()
            }

            ProxyPreferences.preference?.let {
                engine {
                    proxy = getProxy(it)
                }
            }

            defaultRequest {
                url("https://lrclib.net")
            }
        }
    }


    private suspend fun queryLyrics(artist: String, title: String, album: String? = null) =
        client.get("/api/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            if (album != null) parameter("album_name", album)
        }.body<List<Track>>() //.filter { it.syncedLyrics != null }

    private suspend fun queryFreeText(query: String) =
        client.get("/api/search") {
            parameter("q", query)
        }.body<List<Track>>()

    /**
     * Runs one search, retrying twice on server errors: LrcLib answers 503 to a share of
     * requests (about one in ten when measured), and one bad answer used to sink the whole
     * lookup. Returns null when every attempt failed.
     */
    private suspend fun searchOrNull(block: suspend () -> List<Track>): List<Track>? {
        repeat(3) { attempt ->
            try {
                return block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                kotlinx.coroutines.delay(400L * (attempt + 1))
            }
        }
        return null
    }

    /**
     * Synced lyrics for a track, trying progressively looser searches. The first one is the
     * exact title and artist as before; the others exist for videos, whose titles carry
     * "(Official Video)", "(feat. ...)" and the like and never match a catalogue entry.
     *
     * Timing matters more than the first hit: an official video often runs longer than the
     * song, so a result whose length matches ours wins over one that merely matches the
     * name. Fails when nothing synced turns up (or LrcLib never answered), so the caller
     * falls through to its next source.
     */
    suspend fun lyrics(
        artist: String,
        title: String,
        duration: Duration,
        album: String? = null
    ) = runCatchingCancellable {
        val seconds = duration.inWholeSeconds
        fun List<Track>.synced() = filter { !it.syncedLyrics.isNullOrBlank() }
        fun List<Track>.timed() = firstOrNull { seconds > 0 && kotlin.math.abs(it.duration - seconds) <= 3 }

        var answered = false
        var fallback: Track? = null
        suspend fun step(block: suspend () -> List<Track>): Track? {
            val found = searchOrNull(block)?.also { answered = true }?.synced().orEmpty()
            if (fallback == null) fallback = found.firstOrNull()
            return found.timed()
        }

        val artists = splitArtists(artist)
        val mainArtist = artists.firstOrNull() ?: artist
        val cleanTitle = cleanTrackTitle(title, artists)

        val exact = step { queryLyrics(artist, title, album) }
        // The exact query is the old behaviour: take its first hit unless a better-timed
        // one can still be found for a video-style title.
        if (exact != null) return@runCatchingCancellable Lyrics(exact.syncedLyrics!!)
        if (fallback != null && cleanTitle == title)
            return@runCatchingCancellable Lyrics(fallback!!.syncedLyrics!!)

        if (cleanTitle != title || mainArtist != artist)
            step { queryLyrics(mainArtist, cleanTitle) }
                ?.let { return@runCatchingCancellable Lyrics(it.syncedLyrics!!) }

        // Title only: keep hits credited to one of our artists.
        step {
            queryFreeText(cleanTitle).filter { track ->
                artists.any { track.artistName.contains(it, ignoreCase = true) }
            }
        }?.let { return@runCatchingCancellable Lyrics(it.syncedLyrics!!) }

        fallback?.syncedLyrics?.let(LrcLib::Lyrics)
            ?: error(if (answered) "No synced lyrics on LrcLib" else "LrcLib unavailable")
    }

    private val titleNoise = Regex(
        """\s*[(\[][^)\]]*(official|oficial|video|vídeo|audio|lyric|letra|visuali[sz]er|feat\.?|ft\.|prod\.?|\bhd\b|\b4k\b|\bmv\b|en vivo|live)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val trailingFeat = Regex("""\s+(feat\.?|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE)

    private fun splitArtists(artist: String): List<String> =
        artist.split(",", "&", " x ", " X ", " y ", " and ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** "Noriel - De las 2 (Official Video) (feat. Bad Bunny)" becomes "De las 2". */
    private fun cleanTrackTitle(title: String, artists: List<String>): String {
        var t = title
        // "Artist - Title" uploads: drop the artist part when it is one of ours.
        val dash = t.indexOf(" - ")
        if (dash > 0 && artists.any { t.substring(0, dash).contains(it, ignoreCase = true) })
            t = t.substring(dash + 3)
        t = titleNoise.replace(t, "")
        t = trailingFeat.replace(t, "")
        return t.replace(Regex("""\s{2,}"""), " ").trim().ifEmpty { title }
    }

    suspend fun lyrics(artist: String, title: String) = runCatchingCancellable {
        queryLyrics(artist = artist, title = title, album = null)
    }

    @JvmInline
    value class Lyrics(val text: String) {

        val sentences: List<Pair<Long, String>>
            get() = mutableListOf(0L to "").apply {
                for (line in text.trim().lines()) {
                    try {
                        val position = line.take(10).run {
                            get(8).digitToInt() * 10L +
                                    get(7).digitToInt() * 100 +
                                    get(5).digitToInt() * 1000 +
                                    get(4).digitToInt() * 10000 +
                                    get(2).digitToInt() * 60 * 1000 +
                                    get(1).digitToInt() * 600 * 1000
                        }

                        add(position to line.substring(10))
                    } catch (_: Throwable) {
                    }
                }
            }

    }

}
