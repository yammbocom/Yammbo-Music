package it.fast4x.riplay.ui.screens.moodandchip

import androidx.annotation.OptIn
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.valentinilk.shimmer.shimmer
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.Environment
import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.requests.HomePage
import it.fast4x.riplay.LocalPlayerAwareWindowInsets
import it.fast4x.riplay.LocalPlayerServiceBinder
import com.yambo.music.R
import it.fast4x.riplay.data.models.Chip
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.themed.HeaderPlaceholder
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.CollapsingTitleEffect
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.rememberTitleCollapse
import it.fast4x.riplay.ui.components.themed.NoirSectionHeader
import it.fast4x.riplay.ui.screens.newreleases.NoirMediaCard
import it.fast4x.riplay.ui.screens.newreleases.NoirPageTitle
import it.fast4x.riplay.ui.components.themed.TextPlaceholder
import it.fast4x.riplay.ui.items.AlbumItem
import it.fast4x.riplay.ui.items.AlbumItemPlaceholder
import it.fast4x.riplay.ui.items.ArtistItem
import it.fast4x.riplay.ui.items.PlaylistItem
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.themed.LoaderScreen
import it.fast4x.riplay.ui.components.themed.LoadingEmptyScreen
import it.fast4x.riplay.ui.components.themed.LoadingErrorScreen
import it.fast4x.riplay.ui.components.themed.TitleMiniSection
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.items.VideoItem
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.forcePlay
import it.fast4x.riplay.utils.openPlaylistOrMix
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@OptIn(UnstableApi::class)
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun ChipList(
    navController: NavController,
    chip: Chip
) {
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val binder = LocalPlayerServiceBinder.current
    val browseId = chip.browseId ?: "FEmusic_home"
    var chipPage by persist<Result<HomePage>>("playlist/$browseId${chip.params?.let { "/$it" } ?: ""}")

    var retry by rememberSaveable { mutableStateOf(0) }

    LoaderScreen(show = chipPage == null)

    LaunchedEffect(retry) {
        // Drop a persisted failure so the loader shows again while retrying
        if (retry > 0 || chipPage?.isFailure == true) chipPage = null
        // A request that hangs becomes a failure with retry instead of an endless loader
        chipPage = withTimeoutOrNull(20_000) {
            EnvironmentExt.getHomePage(params = chip.params) //Environment.browse(BrowseBodyWithLocale(browseId = browseId, params = mood.params))
        } ?: Result.failure(Exception("ChipList timeout"))
        Timber.d("MoodList chipPage $chipPage")
    }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val lazyListState = rememberLazyListState()
    val titleProgress = rememberTitleCollapse(lazyListState, 0)
    CollapsingTitleEffect(chip.name, titleProgress)

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)

    Column (
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if( NavigationBarPosition.Right.isCurrent() )
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
    ) {
        // A success whose sections are all unusable would render a blank screen, so treat it as empty
        chipPage?.getOrNull()?.takeIf { page ->
            page.sections.any { it.items.isNotEmpty() && it.items.firstOrNull()?.key != null }
        }?.let { moodResult ->
            LazyListContainer(
                state = lazyListState,
            ) {
                LazyColumn(
                    state = lazyListState,
                    //contentPadding = LocalPlayerAwareWindowInsets.current
                    //    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize()
                ) {
                    item(
                        key = "header",
                        contentType = 0
                    ) {
                        NoirPageTitle(
                            text = chip.name,
                            progress = titleProgress,
                            modifier = Modifier.cascadeEntry(0)
                        )
                    }

                    moodResult.sections.forEach {
                        if (it.items.isEmpty() || it.items.firstOrNull()?.key == null) return@forEach
                        item {
                            NoirSectionHeader(
                                title = it.title,
                                eyebrow = it.label?.takeIf { label -> label.isNotBlank() }
                            )
                        }
                        item {
                            val shelfScale = rememberNoirLayout().shelfScale
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(it.items) { item ->
                                    when (item) {
                                        is Environment.SongItem -> NoirMediaCard(
                                            cover = item.thumbnail?.url.thumbnail(480),
                                            title = item.info?.name.orEmpty(),
                                            subtitle = item.authors?.mapNotNull { a -> a.name }
                                                ?.filter { n -> n.isNotBlank() }?.joinToString(", "),
                                            onClick = {
                                                binder?.player?.forcePlay(item.asMediaItem)
                                            },
                                            modifier = Modifier.width(150.dp * shelfScale)
                                        )

                                        is Environment.AlbumItem -> NoirMediaCard(
                                            cover = item.thumbnail?.url.thumbnail(480),
                                            title = item.info?.name.orEmpty(),
                                            subtitle = listOfNotNull(
                                                item.authors?.mapNotNull { a -> a.name }
                                                    ?.filter { n -> n.isNotBlank() }
                                                    ?.joinToString(", ")?.takeIf { j -> j.isNotEmpty() },
                                                item.year
                                            ).joinToString(" · "),
                                            sharedKey = "chip-album:${item.key}",
                                            browseId = item.key,
                                            onClick = {
                                                navController.navigate("${NavRoutes.album.name}/${item.key}")
                                            },
                                            modifier = Modifier.width(150.dp * shelfScale)
                                        )

                                        is Environment.ArtistItem -> NoirMediaCard(
                                            cover = item.thumbnail?.url.thumbnail(480),
                                            title = item.info?.name.orEmpty(),
                                            subtitle = item.subscribersCountText,
                                            shape = CircleShape,
                                            onClick = {
                                                navController.navigate("${NavRoutes.artist.name}/${item.key}")
                                            },
                                            modifier = Modifier.width(150.dp * shelfScale)
                                        )

                                        is Environment.PlaylistItem -> NoirMediaCard(
                                            cover = item.thumbnail?.url.thumbnail(480),
                                            title = item.info?.name.orEmpty(),
                                            subtitle = item.channel?.name,
                                            onClick = {
                                                openPlaylistOrMix(navController, binder, item.key)
                                            },
                                            modifier = Modifier.width(150.dp * shelfScale)
                                        )

                                        is Environment.VideoItem -> NoirMediaCard(
                                            cover = item.thumbnail?.url.thumbnail(480),
                                            title = item.info?.name.orEmpty(),
                                            subtitle = item.authors?.mapNotNull { a -> a.name }
                                                ?.filter { n -> n.isNotBlank() }?.joinToString(", "),
                                            aspect = 16f / 9f,
                                            onClick = {
                                                binder?.stopRadio()
                                                binder?.player?.forcePlay(item.asMediaItem)
                                            },
                                            modifier = Modifier.width(240.dp * shelfScale)
                                        )

                                        null -> {}
                                    }

                                }
                            }
                        }
                    }

                    item(key = "bottom") {
                        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                    }

                }
            }
        } ?: chipPage?.let { page ->
            if (page.isFailure) LoadingErrorScreen(onRetry = { retry++ })
            else LoadingEmptyScreen()
        } ?: ShimmerHost {
            HeaderPlaceholder(modifier = Modifier.shimmer())
            repeat(4) {
                TextPlaceholder(modifier = sectionTextModifier)
                Row {
                    repeat(6) {
                        AlbumItemPlaceholder(
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true
                        )
                    }
                }
            }
        }
    }
}
