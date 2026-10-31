package it.fast4x.riplay.extensions.liveradio

import android.content.Context
import android.telephony.TelephonyManager
import com.yambo.music.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import it.fast4x.riplay.utils.appContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.net.URLEncoder
import java.util.Locale

/**
 * One station of the community directory at radio-browser.info, the same source
 * music.yammbo.com/live-radio reads. Nothing goes through our backend: the app asks the
 * directory directly and the audio comes straight from each station.
 */
@Serializable
data class RadioStation(
    val stationuuid: String,
    val name: String = "",
    val url: String = "",
    @SerialName("url_resolved") val urlResolved: String = "",
    val homepage: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val countrycode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val votes: Int = 0,
    val clickcount: Int = 0,
    val hls: Int = 0,
) {
    /** What actually plays: the directory resolves .pls/.m3u playlists into `url_resolved`. */
    val streamUrl: String get() = urlResolved.ifBlank { url }.trim()
    val isHls: Boolean get() = hls == 1 || streamUrl.contains(".m3u8", ignoreCase = true)
    val tagList: List<String> get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val displayName: String get() = name.trim().ifBlank { streamUrl }
    val faviconUrl: String? get() = favicon.trim().takeIf { it.startsWith("http", ignoreCase = true) }

    /**
     * Google's icon service for the station's homepage. Where the directory only knows a 16-32 px
     * favicon (blurry once scaled to a card), this usually returns the site's 128-256 px touch icon.
     * It answers 404 when it knows nothing about the site, so a failed load is a clean fallback.
     */
    val homepageIconUrl: String? get() = homepage.trim()
        .takeIf { it.startsWith("http", ignoreCase = true) }
        ?.let { site ->
            "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&size=256&url=" +
                URLEncoder.encode(site, "UTF-8")
        }
}

@Serializable
data class RadioCountry(
    val name: String = "",
    @SerialName("iso_3166_1") val code: String = "",
    val stationcount: Int = 0,
)

enum class StationOrder(val apiValue: String) {
    Popular("clickcount"),
    TopRated("votes"),
    Quality("bitrate"),
    Name("name"),
}

data class StationFilters(
    val name: String = "",
    val tag: String = "",
    val countryCode: String = "",
    val order: StationOrder = StationOrder.Popular,
)

/**
 * @param apiCount rows the API returned before our own filtering. It is the only reliable
 * end-of-list signal: a page can filter down to nothing and still have data behind it.
 */
data class StationPage(val stations: List<RadioStation>, val apiCount: Int)

object RadioBrowser {
    const val PAGE_SIZE = 100

    // Genres shown as one-tap chips. The directory's full tag list is 12k entries ordered by
    // station count, which surfaces junk like "music" first; these are what people browse by.
    val QUICK_GENRES = listOf(
        "pop", "rock", "latin", "reggaeton", "salsa", "cumbia", "banda", "electronic", "dance",
        "hip hop", "jazz", "classical", "country", "metal", "indie", "oldies", "80s", "90s",
        "news", "talk", "sports", "christian", "ambient",
    )

    // The directory asks clients to identify themselves.
    private const val USER_AGENT = "YammboMusic/${BuildConfig.VERSION_NAME} (Android; https://music.yammbo.com)"

    // `all.` is the directory's round-robin across whatever servers are alive; de1 is the
    // only named host still resolving (nl1/at1/fr1 are gone), so it is the fallback.
    private val hosts = listOf(
        "https://all.api.radio-browser.info/json",
        "https://de1.api.radio-browser.info/json",
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            // A host that accepts the connection and then stalls would otherwise hold the
            // failover loop for minutes. Cap each attempt instead.
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 8_000
            }
            defaultRequest { header(HttpHeaders.UserAgent, USER_AGENT) }
        }
    }

    // The directory is, in practice, one server (`all.` and de1 resolve to the same machine)
    // and it goes down for hours at a time. Every good answer is kept on disk, so an outage
    // shows the last stations seen instead of an empty tab.
    private const val NETWORK_BUDGET_MILLIS = 7_000L

    private val cacheDir by lazy { File(appContext().filesDir, "radio-browser") }

    private fun cacheFile(endpoint: String, params: Map<String, String>): File {
        val query = params.filterValues { it.isNotBlank() }.toSortedMap().entries
            .joinToString("&") { "${it.key}=${it.value}" }
        val digest = MessageDigest.getInstance("SHA-1").digest("$endpoint?$query".toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    private const val CACHE_MAX_FILES = 150

    // Every query, page and typed name gets its own file; keep only the most recent ones.
    private fun pruneCache() {
        val files = cacheDir.listFiles() ?: return
        if (files.size <= CACHE_MAX_FILES) return
        files.sortedByDescending { it.lastModified() }.drop(CACHE_MAX_FILES).forEach { it.delete() }
    }

    private suspend inline fun <reified T> fetch(endpoint: String, params: Map<String, String>): Result<T> {
        val cached = cacheFile(endpoint, params)
        var last: Throwable? = null
        // One budget for all hosts: they are the same server, so waiting on each in turn only
        // doubles the time before the cached copy is shown.
        val fresh = withTimeoutOrNull(NETWORK_BUDGET_MILLIS) {
            for (host in hosts) {
                try {
                    val response = client.get("$host/$endpoint") {
                        params.forEach { (key, value) -> if (value.isNotBlank()) parameter(key, value) }
                    }
                    if (!response.status.isSuccess()) error("Radio Browser answered ${response.status.value}")
                    val text = response.bodyAsText()
                    val value = json.decodeFromString<T>(text)
                    withContext(Dispatchers.IO) {
                        runCatching {
                            cacheDir.mkdirs()
                            cached.writeText(text)
                            pruneCache()
                        }.onFailure { Timber.d("RadioBrowser cache write failed: ${it.message}") }
                    }
                    return@withTimeoutOrNull value
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Timber.w("RadioBrowser $host/$endpoint failed: ${e.message}")
                    last = e
                }
            }
            null
        }
        if (fresh != null) return Result.success(fresh)

        val stale = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<T>(cached.readText()) }.getOrNull()
        }
        if (stale != null) {
            Timber.i("RadioBrowser $endpoint served from the offline copy")
            return Result.success(stale)
        }
        return Result.failure(last ?: IllegalStateException("Radio Browser unreachable"))
    }

    /**
     * One endpoint for every filter combination: `/stations/search` takes name, tag and
     * country together plus a real offset, which the per-filter endpoints do not.
     */
    suspend fun searchStations(
        filters: StationFilters,
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
    ): Result<StationPage> =
        fetch<List<RadioStation>>("stations/search", searchParams(filters, offset, limit)).map(::toPage)

    /**
     * The last answer seen for this exact query, straight from disk and without touching the
     * network, so a screen can show it at once and refresh behind it. Null when never loaded.
     */
    suspend fun cachedStations(
        filters: StationFilters,
        offset: Int = 0,
        limit: Int = PAGE_SIZE,
    ): StationPage? = withContext(Dispatchers.IO) {
        val file = cacheFile("stations/search", searchParams(filters, offset, limit))
        runCatching { toPage(json.decodeFromString<List<RadioStation>>(file.readText())) }.getOrNull()
    }

    private fun searchParams(filters: StationFilters, offset: Int, limit: Int) = mapOf(
        "name" to filters.name.trim(),
        "tag" to filters.tag.trim(),
        "countrycode" to filters.countryCode.trim(),
        "order" to filters.order.apiValue,
        // name reads naturally ascending; every other order wants biggest first
        "reverse" to (filters.order != StationOrder.Name).toString(),
        "offset" to offset.toString(),
        "limit" to limit.toString(),
        "hidebroken" to "true",
    )

    private fun toPage(list: List<RadioStation>) = StationPage(
        stations = list
            .filter { it.streamUrl.startsWith("http", ignoreCase = true) }
            .distinctBy { it.stationuuid },
        apiCount = list.size,
    )

    /**
     * Most listened stations for the Home shelf: the listener's country first, topped up with
     * the worldwide list when the country has too few to fill a row.
     */
    suspend fun topStations(countryCode: String?, limit: Int = 15): Result<List<RadioStation>> {
        val local = if (countryCode.isNullOrBlank()) emptyList()
        else searchStations(StationFilters(countryCode = countryCode), limit = limit)
            .getOrNull()?.stations.orEmpty()
        if (local.size >= limit) return Result.success(local)
        return searchStations(StationFilters(), limit = limit).map { page ->
            (local + page.stations).distinctBy { it.stationuuid }.take(limit)
        }
    }

    /**
     * Stations to queue behind the one just started, so next/previous have somewhere to go.
     *
     * A station's own genre comes first (its most specific tag, skipping the generic ones the
     * directory is full of), then its country, then the worldwide popular list. Each step only
     * runs when the previous one did not fill the queue, and the station itself is never repeated.
     */
    suspend fun relatedStations(station: RadioStation, limit: Int = 20): List<RadioStation> {
        val genre = station.tagList.firstOrNull { it.lowercase(Locale.ROOT) !in GENERIC_TAGS }
        val attempts = listOfNotNull(
            genre?.let { StationFilters(tag = it, countryCode = station.countrycode) },
            genre?.let { StationFilters(tag = it) },
            station.countrycode.takeIf { it.isNotBlank() }?.let { StationFilters(countryCode = it) },
            StationFilters(),
        )
        val collected = LinkedHashMap<String, RadioStation>()
        for (filters in attempts) {
            searchStations(filters, limit = limit * 2).getOrNull()?.stations.orEmpty()
                .filter { it.stationuuid != station.stationuuid }
                .forEach { collected.putIfAbsent(it.stationuuid, it) }
            if (collected.size >= limit) break
        }
        return collected.values.take(limit)
    }

    // Tags almost every station carries; queueing by one of these is the same as not filtering.
    private val GENERIC_TAGS = setOf("music", "radio", "fm", "am", "online", "internet", "live")

    suspend fun countries(): Result<List<RadioCountry>> = fetch<List<RadioCountry>>(
        "countries",
        mapOf("order" to "stationcount", "reverse" to "true", "hidebroken" to "true", "limit" to "250"),
    ).map { list -> list.filter { it.code.isNotBlank() && it.name.isNotBlank() } }

    /** Counts the play in the directory (their "clicks"). Good-citizen behaviour, never awaited. */
    fun registerClick(stationUuid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { client.get("${hosts[0]}/url/$stationUuid") }
                .onFailure { Timber.d("RadioBrowser registerClick failed: ${it.message}") }
        }
    }
}

/** ISO country of the phone's network, then SIM, then locale. Drives the default station list. */
fun deviceCountryCode(context: Context): String {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val fromNetwork = telephony?.networkCountryIso?.takeIf { it.length == 2 }
        ?: telephony?.simCountryIso?.takeIf { it.length == 2 }
    return (fromNetwork ?: Locale.getDefault().country).uppercase(Locale.ROOT)
}
