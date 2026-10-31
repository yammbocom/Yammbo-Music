package it.fast4x.riplay.ui.screens.moodandchip

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.navigation.NavController
import com.valentinilk.shimmer.shimmer
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.Environment
import it.fast4x.environment.models.bodies.BrowseBodyWithLocale
import it.fast4x.environment.requests.BrowseResult
import it.fast4x.environment.requests.browse
import it.fast4x.riplay.LocalPlayerAwareWindowInsets
import it.fast4x.riplay.LocalPlayerServiceBinder
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.data.models.Mood
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.themed.HeaderPlaceholder
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.CollapsingTitleEffect
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.screens.newreleases.NoirGridSectionHeader
import it.fast4x.riplay.ui.screens.newreleases.NoirMediaCard
import it.fast4x.riplay.ui.screens.newreleases.NoirPageTitle
import it.fast4x.riplay.ui.screens.newreleases.rememberGridTitleCollapse
import it.fast4x.riplay.ui.components.themed.TextPlaceholder
import it.fast4x.riplay.ui.items.AlbumItemPlaceholder
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.themed.LoaderScreen
import it.fast4x.riplay.ui.components.themed.LoadingEmptyScreen
import it.fast4x.riplay.ui.components.themed.LoadingErrorScreen
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.openPlaylistOrMix
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal const val defaultBrowseId = "FEmusic_moods_and_genres_category"

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun MoodList(
    navController: NavController,
    mood: Mood
) {
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val browseId = mood.browseId ?: defaultBrowseId
    var moodPage by persist<Result<BrowseResult>>("playlist/$browseId${mood.params?.let { "/$it" } ?: ""}")

    val binder = LocalPlayerServiceBinder.current
    var retry by rememberSaveable { mutableStateOf(0) }

    LoaderScreen(show = moodPage == null)

    LaunchedEffect(retry) {
        // Drop a persisted failure so the loader shows again while retrying
        if (retry > 0 || moodPage?.isFailure == true) moodPage = null
        // A request that hangs becomes a failure with retry instead of an endless loader
        moodPage = withTimeoutOrNull(20_000) {
            Environment.browse(BrowseBodyWithLocale(browseId = browseId, params = mood.params))
        } ?: Result.failure(Exception("MoodList timeout"))
        Timber.d("MoodList moodPage $moodPage")
    }

    val thumbnailSizeDp = Dimensions.thumbnails.album
    val thumbnailSizePx = thumbnailSizeDp.px

    val lazyGridState = rememberLazyGridState()
    val titleProgress = rememberGridTitleCollapse(lazyGridState)
    CollapsingTitleEffect(mood.name, titleProgress)

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
        moodPage?.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let { moodResult ->
            LazyListContainer(
                state = lazyGridState,
            ) {
                // 150dp cells on phones (as before), 180dp on tablets, capped at grid width.
                val noirLayout = rememberNoirLayout()
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(if (noirLayout.isCompact) 150.dp else 180.dp),
                    contentPadding = PaddingValues(horizontal = noirLayout.sidePadding(NoirContentMax.Grid)),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize()
                ) {
                    item(
                        key = "header",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        NoirPageTitle(
                            text = mood.name,
                            progress = titleProgress,
                            modifier = Modifier.cascadeEntry(0),
                            horizontalPadding = 0.dp
                        )
                    }

                    moodResult.items.forEachIndexed { sectionIndex, section ->
                        val children = section.items.filter {
                            it.key != defaultBrowseId &&
                                (it is Environment.AlbumItem || it is Environment.ArtistItem || it is Environment.PlaylistItem)
                        }
                        if (children.isEmpty()) return@forEachIndexed

                        item(
                            key = "section:$sectionIndex",
                            contentType = 1,
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            NoirGridSectionHeader(title = section.title)
                        }

                        itemsIndexed(
                            items = children,
                            key = { index, child -> "$sectionIndex:$index:${child.key}" }
                        ) { index, childItem ->
                            val cover = childItem.thumbnail?.url.thumbnail(480)
                            when (childItem) {
                                is Environment.AlbumItem -> NoirMediaCard(
                                    cover = cover,
                                    title = childItem.info?.name.orEmpty(),
                                    subtitle = listOfNotNull(
                                        childItem.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }
                                            ?.joinToString(", ")?.takeIf { it.isNotEmpty() },
                                        childItem.year
                                    ).joinToString(" · "),
                                    sharedKey = "mood-album:${childItem.key}",
                                    browseId = childItem.info?.endpoint?.browseId,
                                    onClick = {
                                        childItem.info?.endpoint?.browseId?.let {
                                            navController.navigate(route = "${NavRoutes.album.name}/$it")
                                        }
                                    },
                                    modifier = Modifier.cascadeEntry(index + 1)
                                )

                                is Environment.ArtistItem -> NoirMediaCard(
                                    cover = cover,
                                    title = childItem.info?.name.orEmpty(),
                                    subtitle = childItem.subscribersCountText,
                                    shape = CircleShape,
                                    onClick = {
                                        childItem.info?.endpoint?.browseId?.let {
                                            navController.navigate(route = "${NavRoutes.artist.name}/$it")
                                        }
                                    },
                                    modifier = Modifier.cascadeEntry(index + 1)
                                )

                                is Environment.PlaylistItem -> NoirMediaCard(
                                    cover = cover,
                                    title = childItem.info?.name.orEmpty(),
                                    subtitle = childItem.channel?.name,
                                    onClick = {
                                        childItem.info?.endpoint?.let { endpoint ->
                                            endpoint.browseId?.let {
                                                openPlaylistOrMix(navController, binder, it)
                                            }
                                        }
                                    },
                                    modifier = Modifier.cascadeEntry(index + 1)
                                )

                                else -> {}
                            }
                        }
                    }

                    item(key = "bottom", span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                    }

                }
            }
        } ?: moodPage?.let { page ->
            if (page.isFailure) LoadingErrorScreen(onRetry = { retry++ })
            else LoadingEmptyScreen()
        } ?: ShimmerHost {
            HeaderPlaceholder(modifier = Modifier.shimmer())
            repeat(4) {
                TextPlaceholder(modifier = sectionTextModifier)
                Row {
                    repeat(6) {
                        AlbumItemPlaceholder(
                            thumbnailSizeDp = thumbnailSizeDp,
                            alternative = true
                        )
                    }
                }
            }
        }
    }
}
