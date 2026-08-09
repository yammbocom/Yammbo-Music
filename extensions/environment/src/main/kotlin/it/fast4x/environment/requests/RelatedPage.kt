package it.fast4x.environment.requests


import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.fast4x.environment.Environment
import it.fast4x.environment.models.BrowseResponse
import it.fast4x.environment.models.MusicCarouselShelfRenderer
import it.fast4x.environment.models.NextResponse
import it.fast4x.environment.models.bodies.BrowseBody
import it.fast4x.environment.models.bodies.NextBody
import it.fast4x.environment.utils.findSectionByStrapline
import it.fast4x.environment.utils.findSectionByTitle
import it.fast4x.environment.utils.from
import it.fast4x.environment.utils.runCatchingNonCancellable



suspend fun Environment.relatedPage(body: NextBody) = runCatchingNonCancellable {
    val nextResponse = client.post(_NXIvG4ve8N) {
        setBody(body)
        mask("contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.tabRenderer(endpoint,title)")
    }.body<NextResponse>()

    val tabs = nextResponse
        .contents
        ?.singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs

    // Diagnostic: how many watch-next tabs came back and what they are called.
    println("Innertube RelatedPage tabs: " + tabs?.map { it.tabRenderer?.title })

    // The watch-next tabs are Up next / Lyrics / Related, and the related one used to
    // sit at index 2. That position is not guaranteed, but the id prefix is: related
    // pages are "MPTRt...", lyrics are "MPLYt...". Picking by prefix identifies the
    // right tab whatever the order or language. Falling back to "first tab with a
    // browseId" is what fetched the lyrics page instead — it returns a valid response
    // with no songs in it, which looks exactly like a working request that found nothing.
    val allBrowseIds = tabs?.mapNotNull { it.tabRenderer?.endpoint?.browseEndpoint?.browseId }
    val browseId = allBrowseIds?.firstOrNull { it.startsWith("MPTRt") }
        ?: tabs?.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint?.browseId
        ?: return@runCatchingNonCancellable null

    println("Innertube RelatedPage browseIds=$allBrowseIds picked=$browseId")

    val response = client.post(_3djbhqyLpE) {
        setBody(BrowseBody(browseId = browseId))
        // No field mask here (the default is "*"). A narrow mask is what silently drops
        // whichever shelf shape YouTube happened to use, and the loss happens server-side,
        // so no downstream parsing can recover it. This costs a little bandwidth once per
        // home load and removes a whole class of "the section is mysteriously empty" bugs.
        mask()
    }.body<BrowseResponse>()

    val sectionListRenderer = response
        .contents
        ?.sectionListRenderer

    // Diagnostic: the section titles actually returned. When the songs shelf comes
    // back empty this is the only way to tell "request failed" from "title didn't match".
    println("Innertube RelatedPage sections: " + sectionListRenderer?.contents?.map { content ->
        content.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer
            ?.title?.runs?.firstOrNull()?.text
    })

    // Matching the literal English title breaks whenever YouTube returns the shelf
    // under a different heading (localised response, or a renamed section) — the whole
    // "quick picks" row then silently collapses to the seed song. Fall back to matching
    // by shape: the songs shelf is the only carousel whose items are
    // musicResponsiveListItemRenderer; playlists, albums and artists all use
    // musicTwoRowItemRenderer.
    val songsFromCarousel = (
            sectionListRenderer
                ?.findSectionByTitle("You might also like")
                ?.musicCarouselShelfRenderer
                ?: sectionListRenderer
                    ?.contents
                    ?.firstOrNull { content ->
                        content.musicCarouselShelfRenderer
                            ?.contents
                            ?.any { it.musicResponsiveListItemRenderer != null } == true
                    }
                    ?.musicCarouselShelfRenderer
            )
        ?.contents
        ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicResponsiveListItemRenderer)

    // Same shelf, other shape: a flat list rather than a carousel.
    val songsFromShelf = sectionListRenderer
        ?.contents
        ?.firstOrNull { content ->
            content.musicShelfRenderer
                ?.contents
                ?.any { it.musicResponsiveListItemRenderer != null } == true
        }
        ?.musicShelfRenderer
        ?.contents
        ?.mapNotNull { it.musicResponsiveListItemRenderer }

    println(
        "Innertube RelatedPage songs: carousel=${songsFromCarousel?.size} " +
                "shelf=${songsFromShelf?.size} sections=${sectionListRenderer?.contents?.size}"
    )

    Environment.RelatedPage(
        songs = (songsFromCarousel?.takeIf { it.isNotEmpty() } ?: songsFromShelf)
            ?.mapNotNull(Environment.SongItem::from),
        playlists = sectionListRenderer
            ?.findSectionByTitle("Recommended playlists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Environment.PlaylistItem::from)
            ?.sortedByDescending { it.channel?.name == "YouTube Music" },
        albums = sectionListRenderer
            ?.findSectionByStrapline("MORE FROM")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Environment.AlbumItem::from),
        artists = sectionListRenderer
            ?.findSectionByTitle("Similar artists")
            ?.musicCarouselShelfRenderer
            ?.contents
            ?.mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
            ?.mapNotNull(Environment.ArtistItem::from),
    )
}?.onFailure {
    println("ERROR in Innertube Failed relatedPage ${it.stackTraceToString()}")
}
