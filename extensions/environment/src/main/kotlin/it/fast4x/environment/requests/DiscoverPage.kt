package it.fast4x.environment.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.fast4x.environment.Environment
import it.fast4x.environment.models.BrowseResponse
import it.fast4x.environment.models.Context
import it.fast4x.environment.models.Context.Companion.DefaultWeb
import it.fast4x.environment.models.Context.Companion.hl
import it.fast4x.environment.models.MusicTwoRowItemRenderer
import it.fast4x.environment.models.bodies.BrowseBodyWithLocale
import it.fast4x.environment.models.oddElements
import it.fast4x.environment.models.splitBySeparator
import java.util.Locale

suspend fun Environment.discoverPage() = runCatching {

    val response = client.post(_3djbhqyLpE) {
        setBody(
            BrowseBodyWithLocale(
                context = DefaultWeb.copy(
                    client = DefaultWeb.client.copy(hl = Locale.getDefault().language)
                ),
                browseId = "FEmusic_explore"
            )
        )
        mask("contents")
    }.body<BrowseResponse>()

    val shelfAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs
        ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
            it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
                ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
        }?.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicTwoRowItemRenderer?.toNewReleaseAlbumPage() }
        .orEmpty()

    // The explore shelf holds 24 albums and the accounts that flood the feed sit at the top of
    // it — on the day this was measured one of them held 5 of the first 9 slots. The standalone
    // page returns about 150 for the same day, which is what gives one-album-per-artist enough
    // material to fill the row with everyone else. Only replaces the shelf when it actually came
    // back with albums: an empty page must not empty the section.
    val pageAlbums = runCatching { discoverPageNewAlbumsComplete().getOrNull()?.newReleaseAlbums }
        .getOrNull().orEmpty()

    val pool = pageAlbums.ifEmpty { shelfAlbums }
    val curated = pool.curateNewReleases()
    println("Environment.discoverPage newReleases shelf=${shelfAlbums.size} page=${pageAlbums.size} curated=${curated.size}")

    Environment.DiscoverPage(
        newReleaseAlbums = curated,
        moods = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
                    ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
            }?.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicNavigationButtonRenderer?.toMood() }
            .orEmpty()
    )
}

/**
 * Puts YouTube's raw new-releases feed into an order that reads as music.
 *
 * The feed is unfiltered, and a handful of accounts publish enough albums a day to take it over:
 * in the batch this was written against, one act held 5 of the first 9 slots and the row looked
 * like spam rather than like new music.
 *
 * This does NOT try to tell generated albums from real ones, because the response does not say.
 * A real release and a filler one come back as the same renderer with the same fields, the same
 * two thumbnail sizes and the same menu; the only structural difference found between them was
 * an "Explicit" badge, which of course means nothing. Guessing from artist names would quietly
 * hide real bands, so the only thing used here is what the response does state plainly: how much
 * of one batch a single act occupies. One album per artist, and whoever floods the batch goes
 * last. Nothing is dropped, so the section keeps as many albums as it ever had.
 */
internal fun List<Environment.AlbumItem>.curateNewReleases(): List<Environment.AlbumItem> {
    // Channel id first, artist name second, and an album with neither keeps a key of its own.
    // Collapsing everything that lacks an artist link into one bucket would have merged unrelated
    // acts into a single slot — several genuine releases in the sample carried a name but no channel.
    fun Environment.AlbumItem.artistKey(): String {
        val channels = authors?.mapNotNull { it.endpoint?.browseId }?.filter { it.isNotBlank() }.orEmpty()
        if (channels.isNotEmpty()) return "ch:" + channels.sorted().joinToString("|")

        val names = authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }.orEmpty()
        if (names.isNotEmpty()) return "name:" + names.joinToString("|").lowercase()

        return "album:" + key
    }

    val byAlbum = distinctBy { it.key.ifBlank { it.info?.name.orEmpty() } }
    val albumsPerArtist = byAlbum.groupingBy { it.artistKey() }.eachCount()

    val seenArtists = mutableSetOf<String>()
    return byAlbum
        .filter { seenArtists.add(it.artistKey()) }
        .withIndex()
        .sortedWith(compareBy({ albumsPerArtist[it.value.artistKey()] ?: 1 }, { it.index }))
        .map { it.value }
}

suspend fun Environment.discoverPageNewAlbums() = runCatching {
    val response = client.post(_3djbhqyLpE) {
        setBody(BrowseBodyWithLocale(browseId = "FEmusic_explore"))
        mask("contents")
    }.body<BrowseResponse>()

    Environment.DiscoverPageAlbums(
        newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs
            ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
                    ?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
            }?.musicCarouselShelfRenderer?.contents?.mapNotNull { it.musicTwoRowItemRenderer?.toNewReleaseAlbumPage() }
            .orEmpty()
    )
}

suspend fun Environment.discoverPageNewAlbumsComplete() = runCatching {
    val response = client.post(_3djbhqyLpE) {
        setBody(BrowseBodyWithLocale(browseId = "FEmusic_new_releases_albums"))
        mask("contents")
    }.body<BrowseResponse>()

    // Left uncurated on purpose: this is the pool discoverPage() curates, and running the
    // one-per-artist rule twice over the same list is harmless but misleading to read.
    val albums = response.contents?.singleColumnBrowseResultsRenderer?.tabs
        ?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        ?.gridRenderer?.items?.mapNotNull { it.musicTwoRowItemRenderer?.toNewReleaseAlbumPage() }
        .orEmpty()
    println("Environment.discoverPageNewAlbumsComplete grid=${albums.size}")

    Environment.DiscoverPageAlbums(newReleaseAlbums = albums)
}

fun MusicTwoRowItemRenderer.toNewReleaseAlbumPage() = Environment.AlbumItem(
    info = Environment.Info(
        name = title?.text,
        endpoint = navigationEndpoint?.browseEndpoint
    ),
    authors = subtitle?.runs?.splitBySeparator()?.getOrNull(1)?.oddElements()?.map {
        Environment.Info(
            name = it.text,
            endpoint = it.navigationEndpoint?.browseEndpoint
        )
    },
    year = subtitle?.runs?.lastOrNull()?.text,
    thumbnail = (thumbnailRenderer?.musicThumbnailRenderer ?: thumbnailRenderer?.croppedSquareThumbnailRenderer)?.thumbnail?.thumbnails?.firstOrNull()
)