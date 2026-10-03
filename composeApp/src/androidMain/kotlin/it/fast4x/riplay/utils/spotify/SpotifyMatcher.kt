package it.fast4x.riplay.utils.spotify

import it.fast4x.environment.Environment
import it.fast4x.environment.models.bodies.SearchBody
import it.fast4x.environment.requests.searchPage
import it.fast4x.environment.utils.from
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Finds the YouTube Music song for a Spotify track (title, artists, duration).
 *
 * Searches with the Songs filter and scores the first results by title, artist and duration;
 * audio songs (ATV) are preferred over videos. A track with no convincing candidate is left
 * unmatched rather than filled with a wrong song.
 */
object SpotifyMatcher {

    private const val CONCURRENCY = 4
    private const val CANDIDATES = 8

    private val qualifiers = listOf(
        "live", "en vivo", "acoustic", "acustico", "remix", "instrumental",
        "karaoke", "sped up", "slowed", "unplugged", "demo", "a capella", "acapella"
    )

    /**
     * [Found]; [NoMatch]: searched fine and nothing was believable (remembered as not found);
     * [Failed]: network error or timeout, so the track must be asked again next time.
     */
    sealed interface Result {
        data class Found(val item: Environment.SongItem) : Result
        data object NoMatch : Result
        data object Failed : Result
    }

    /**
     * Matches every track, [CONCURRENCY] at a time. The answer keeps the order of [tracks].
     * Same uri, same search: duplicates are looked up once.
     */
    suspend fun matchAll(
        tracks: List<SpotifyTrack>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<Result> = coroutineScope {
        val semaphore = Semaphore(CONCURRENCY)
        val done = AtomicInteger(0)
        val byUri = HashMap<String, kotlinx.coroutines.Deferred<Result>>()
        val jobs = tracks.map { track ->
            byUri.getOrPut(track.uri) {
                async {
                    semaphore.withPermit { runCatching { match(track) }.getOrElse { Result.Failed } }
                }
            }
        }
        onProgress(0, tracks.size)
        jobs.map { job ->
            async {
                job.await().also { onProgress(done.incrementAndGet(), tracks.size) }
            }
        }.awaitAll()
    }

    suspend fun match(track: SpotifyTrack): Result {
        val artists = artistsOf(track.artists)
        val mainArtists = track.artists.split(',').take(2).joinToString(" ") { it.trim() }
        val queries = listOf(
            "${queryTitle(track.title)} $mainArtists".trim(),
            "${track.title} ${track.artists.replace(",", "")}".trim(),
        ).distinct()

        var failed = false
        for (query in queries) {
            val items = search(query)
            if (items == null) {
                failed = true
                continue
            }
            val best = items.take(CANDIDATES)
                .mapIndexedNotNull { index, item -> score(track, artists, item, index)?.let { item to it } }
                .maxByOrNull { it.second }
            if (best != null) {
                Timber.d("SpotifyMatcher '${track.title}' / ${track.artists} -> ${best.first.key} '${best.first.info?.name}' score ${"%.2f".format(best.second)}")
                return Result.Found(best.first)
            }
        }
        Timber.d("SpotifyMatcher no match for '${track.title}' / ${track.artists} failed=$failed")
        // A query that never got an answer may have held the song: not a final "not found".
        return if (failed) Result.Failed else Result.NoMatch
    }

    /** One retry on a network failure or timeout; an empty answer is final. */
    private suspend fun search(query: String): List<Environment.SongItem>? {
        repeat(2) {
            val result = withTimeoutOrNull(12_000) {
                Environment.searchPage(
                    body = SearchBody(query = query, params = Environment.SearchFilter.Song.value),
                    fromMusicShelfRendererContent = Environment.SongItem.Companion::from
                )
            }
            val items = result?.getOrNull()?.items
            if (items != null) return items
            if (result?.isSuccess == true) return emptyList()
        }
        return null
    }

    /** null = not acceptable. */
    private fun score(
        track: SpotifyTrack,
        wantedArtists: Set<String>,
        item: Environment.SongItem,
        index: Int,
    ): Double? {
        if (item.key.isBlank()) return null
        val candidateTitle = item.info?.name ?: return null

        val wanted = baseTitle(track.title)
        val candidate = baseTitle(candidateTitle)
        val titleScore = when {
            wanted.isEmpty() || candidate.isEmpty() -> 0.0
            wanted == candidate -> 1.0
            containsWords(candidate, wanted) || containsWords(wanted, candidate) -> 0.75
            else -> jaccard(wanted, candidate)
        }
        if (titleScore < 0.5) return null

        val candidateArtists = item.authors?.mapNotNull { it.name }?.flatMap { artistsOf(it) }.orEmpty()
        val artistHit = wantedArtists.any { w ->
            candidateArtists.any { c -> c == w || containsWords(c, w) || containsWords(w, c) }
        }

        val candidateMs = durationMs(item.durationText)
        val durationDiff = if (track.durationMs <= 0 || candidateMs == null) null
            else abs(track.durationMs - candidateMs)
        // Another song of the same artist usually differs in length: past 20 s only the exact
        // title by the same artist (radio edit, live cut) is allowed, and never past 45 s.
        if (durationDiff != null) {
            if (durationDiff > 45_000L) return null
            if (durationDiff > 20_000L && !(titleScore == 1.0 && artistHit)) return null
        }
        val durationScore = if (durationDiff == null) 0.4 else {
            when (durationDiff) {
                in 0L..3_000L -> 1.0
                in 0L..10_000L -> 0.85
                in 0L..20_000L -> 0.4
                in 0L..45_000L -> 0.15
                else -> 0.0
            }
        }

        // Without the artist only an exact title at the same length is believable.
        if (!artistHit && !(titleScore == 1.0 && durationScore >= 0.85)) return null

        var total = 0.45 * titleScore + (if (artistHit) 0.30 else 0.0) + 0.25 * durationScore
        if (qualifiersOf(track.title) != qualifiersOf(candidateTitle)) total -= 0.15
        when {
            item.isOfficialUploadByArtistContent -> total += 0.08
            item.isUserGeneratedContent -> total -= 0.10
        }
        total += (CANDIDATES - index).coerceAtLeast(0) * 0.005
        return total.takeIf { it >= 0.6 }
    }

    // ---- text normalisation ----

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('’', '\'')
            .replace(' ', ' ')

    private fun stripBrackets(text: String): String =
        text.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Spotify suffixes: "Song - Remastered 2011", "Song - From \"Film\"", "Song - Live". */
    private fun stripDashSuffix(text: String): String =
        text.replace(Regex("\\s[-–—]\\s.*$"), "").trim()

    private fun queryTitle(title: String): String =
        stripBrackets(stripDashSuffix(title)).ifBlank { title }

    private fun baseTitle(title: String): String =
        normalize(stripBrackets(stripDashSuffix(title)))
            .replace(Regex("\\s(ft\\.?|feat\\.?|featuring|con|with)\\s.*$"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun qualifiersOf(title: String): Set<String> {
        val text = " " + normalize(title).replace(Regex("[^\\p{L}\\p{N}]+"), " ") + " "
        return qualifiers.filter { text.contains(" $it ") }.toSet()
    }

    private fun artistsOf(names: String?): Set<String> =
        normalize(names.orEmpty())
            .split(Regex(",|&| y | and | x |\\bfeat\\.?|\\bft\\.?"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim() }
            .filter { it.isNotBlank() }
            .toSet()

    /** [words] appears in [text] as whole words (both normalised, single-spaced). */
    private fun containsWords(text: String, words: String): Boolean =
        words.isNotBlank() && " $text ".contains(" $words ")

    private fun jaccard(a: String, b: String): Double {
        val ta = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size
    }

    /** "3:20" or "1:02:03" in ms. */
    fun durationMs(text: String?): Long? {
        val parts = text?.trim()?.split(':')?.map { it.toLongOrNull() ?: return null } ?: return null
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0L) { acc, v -> acc * 60 + v } * 1000
    }
}
