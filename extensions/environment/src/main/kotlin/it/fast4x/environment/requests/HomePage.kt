package it.fast4x.environment.requests

import it.fast4x.environment.Environment
import it.fast4x.environment.Environment.getBestQuality
import it.fast4x.environment.models.BrowseEndpoint
import it.fast4x.environment.models.MusicCarouselShelfRenderer
import it.fast4x.environment.models.MusicResponsiveListItemRenderer
import it.fast4x.environment.models.MusicTwoRowItemRenderer
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.environment.models.SectionListRenderer
import it.fast4x.environment.models.oddElements
import kotlinx.serialization.Serializable

@Serializable
data class HomePage(
    val sections: List<Section>,
    val chips: List<Environment.Chip>?,
    val continuation: String? = null,
) {

    @Serializable
    data class Section(
        val title: String,
        val label: String?,
        val thumbnail: String?,
        val endpoint: BrowseEndpoint?,
        val items: List<Environment.Item?>,
    ) {
        companion object {
            fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): Section? {

                return Section(
                    title = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                        ?: "",
                    label = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.strapline?.runs?.firstOrNull()?.text,
                    thumbnail = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),

                    endpoint = BrowseEndpoint(
                        browseId = renderer.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId
                            ?: "",
                    ),
                    items = renderer.contents
                        .map {
                            // A carousel can mix two shapes. Reading only the card one left
                            // shelves like "Listen together" with a single item and a hole
                            // where every list-shaped entry should have been.
                            fromMusicTwoRowItemRenderer(
                                it.musicTwoRowItemRenderer,
                                renderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                            ) ?: fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                        } //.filter { it?.title?.isNotEmpty() == true }

                )
            }

            /**
             * The list-shaped carousel entry: same content as the card one, different json.
             * Navigation buttons (the chips at the end of some shelves) are not playable and
             * stay unmapped on purpose.
             */
            private fun fromMusicResponsiveListItemRenderer(
                renderer: MusicResponsiveListItemRenderer?
            ): Environment.Item? {
                if (renderer == null) return null

                val name = renderer.flexColumns
                    .firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                val subtitle = renderer.flexColumns
                    .getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                    ?: renderer.thumbnail?.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()

                val browse = renderer.navigationEndpoint?.browseEndpoint
                val watch = renderer.navigationEndpoint?.watchEndpoint

                return when {
                    renderer.isAlbum && browse != null -> Environment.AlbumItem(
                        info = Environment.Info(name, browse),
                        authors = subtitle?.let { listOf(Environment.Info<NavigationEndpoint.Endpoint.Browse>(it, null)) },
                        year = null,
                        thumbnail = thumbnail,
                    )

                    renderer.isArtist && browse != null -> Environment.ArtistItem(
                        info = Environment.Info(name, browse),
                        subscribersCountText = subtitle,
                        thumbnail = thumbnail,
                    )

                    renderer.isPlaylist && browse != null -> Environment.PlaylistItem(
                        info = Environment.Info(name, browse),
                        channel = subtitle?.let { Environment.Info<NavigationEndpoint.Endpoint.Browse>(it, null) },
                        songCount = null,
                        thumbnail = thumbnail,
                        isEditable = false,
                    )

                    watch != null -> Environment.SongItem(
                        info = Environment.Info(name, watch),
                        authors = subtitle?.let { listOf(Environment.Info<NavigationEndpoint.Endpoint.Browse>(it, null)) },
                        album = null,
                        durationText = null,
                        thumbnail = thumbnail,
                    )

                    else -> null
                }
            }

            private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer?, sectionTitle: String? = null): Environment.Item? {

                return when {
                    renderer?.isSong == true -> {

                        Environment.SongItem(
                            info = Environment.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.watchEndpoint
                            ),
                            authors = renderer.subtitle?.runs?.map {
                                Environment.Info(
                                    name = it.text,
                                    endpoint = it.navigationEndpoint?.browseEndpoint
                                )
                            },
                            album = null,
                            durationText = null,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                                ?: renderer.thumbnailRenderer?.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
                            explicit = renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null
                        )
                    }

                    renderer?.isAlbum == true -> {

                        Environment.AlbumItem(
                            info = Environment.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.browseEndpoint
                            ),
//                            playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
//                                ?.musicPlayButtonRenderer?.playNavigationEndpoint
//                                ?.watchPlaylistEndpoint?.playlistId ?: return null,
//                            title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                            authors = renderer.subtitle?.runs?.oddElements()?.drop(1)?.map {
                                Environment.Info(
                                    name = it.text,
                                    endpoint = it.navigationEndpoint?.browseEndpoint
                                )
                            },
                            year = renderer.subtitle?.runs?.lastOrNull()?.text,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                                ?: renderer.thumbnailRenderer?.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
//                            explicit = renderer.subtitleBadges?.find {
//                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
//                            } != null
                        )
                    }

                    renderer?.isPlaylist == true || renderer?.isPodcast == true  -> {

                        Environment.PlaylistItem(
                            info = Environment.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.browseEndpoint
                            ),
                            songCount = null,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                                ?: renderer.thumbnailRenderer?.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
                            channel = null,
                            isEditable = false
                        )
                    }

                    renderer?.isArtist == true -> {

                        Environment.ArtistItem(
                            info = Environment.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.browseEndpoint
                            ),
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                                ?: renderer.thumbnailRenderer?.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
                            subscribersCountText = null
                        )
                    }

                    renderer?.isVideo == true -> {

                        Environment.VideoItem(
                            info = Environment.Info(
                                renderer.title?.runs?.firstOrNull()?.text,
                                renderer.navigationEndpoint?.watchEndpoint
                            ),
                            authors = renderer.subtitle?.runs?.map {
                                Environment.Info(
                                    name = it.text,
                                    endpoint = it.navigationEndpoint?.browseEndpoint
                                )
                            },
                            durationText = null,
                            thumbnail = renderer.thumbnailRenderer?.musicThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality()
                                ?: renderer.thumbnailRenderer?.croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.getBestQuality(),
                            viewsText = null
                        )

                    }

                    else -> {
                        //println("getHomePage() fromMusicTwoRowItemRenderer else renderer: ${renderer}")
                        null
                    }
                }
            }

        }
    }
}

