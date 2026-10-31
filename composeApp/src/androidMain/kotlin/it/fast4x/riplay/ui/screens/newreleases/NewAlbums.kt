package it.fast4x.riplay.ui.screens.newreleases

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.Environment
import it.fast4x.environment.requests.discoverPage
import it.fast4x.riplay.utils.contentCountryCode
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.CollapsingTitleEffect
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.themed.LoaderScreen
import it.fast4x.riplay.ui.components.themed.LoadingEmptyScreen
import it.fast4x.riplay.ui.components.themed.LoadingErrorScreen
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.LazyListContainer
import kotlinx.coroutines.withTimeoutOrNull

@ExperimentalTextApi
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun NewAlbums(
    navController: NavController
) {
    var discoverPage by persist<Result<Environment.DiscoverPage>>("home/discoveryAlbums")

    var retry by rememberSaveable { mutableStateOf(0) }

    LoaderScreen(show = discoverPage == null)

    LaunchedEffect(retry) {
        // Drop a persisted failure so the loader shows again while retrying
        if (retry > 0 || discoverPage?.isFailure == true) discoverPage = null
        // A request that hangs becomes a failure with retry instead of an endless loader
        discoverPage = withTimeoutOrNull(20_000) {
            Environment.discoverPage(contentCountryCode())
        } ?: Result.failure(Exception("NewAlbums timeout"))
    }

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    val lazyGridState = rememberLazyGridState()
    val titleProgress = rememberGridTitleCollapse(lazyGridState)
    CollapsingTitleEffect(stringResource(R.string.new_albums), titleProgress)

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
    ) {

        /***************/
        discoverPage?.getOrNull()?.takeIf { it.newReleaseAlbums.isNotEmpty() }?.let { page ->
            val albums = page.newReleaseAlbums.distinct()
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
                ) {
                    item(
                        key = "header",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        NoirPageTitle(
                            text = stringResource(R.string.new_albums),
                            progress = titleProgress,
                            modifier = Modifier
                                .cascadeEntry(0),
                            horizontalPadding = 0.dp
                        )
                    }

                    itemsIndexed(
                        items = albums,
                        key = { _, album -> album.key }
                    ) { index, album ->
                        val cover = album.thumbnail?.url.thumbnail(480)
                        NoirMediaCard(
                            cover = cover,
                            title = album.info?.name.orEmpty(),
                            subtitle = listOfNotNull(
                                album.authors?.mapNotNull { it.name }?.filter { it.isNotBlank() }
                                    ?.joinToString(", ")?.takeIf { it.isNotEmpty() },
                                album.year
                            ).joinToString(" · "),
                            sharedKey = "new-album:${album.key}",
                            browseId = album.key,
                            onClick = {
                                navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                            },
                            modifier = Modifier.cascadeEntry(index + 1)
                        )
                    }
                    item(
                        key = "footer",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                    }
                }
            }

        } ?: discoverPage?.let { result ->
            if (result.isFailure) LoadingErrorScreen(onRetry = { retry++ })
            else LoadingEmptyScreen()
        }
        /***************/


    }

}
