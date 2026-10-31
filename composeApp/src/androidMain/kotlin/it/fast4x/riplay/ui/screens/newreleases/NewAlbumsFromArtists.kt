package it.fast4x.riplay.ui.screens.newreleases

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.environment.Environment
import it.fast4x.environment.requests.discoverPageNewAlbums
import it.fast4x.riplay.data.Database
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.extensions.preferences.showSearchTabKey
import it.fast4x.riplay.ui.components.themed.LoaderScreen
import it.fast4x.riplay.ui.components.themed.LoadingErrorScreen
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.utils.LazyListContainer
import kotlinx.coroutines.withTimeoutOrNull

@ExperimentalTextApi
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun NewAlbumsFromArtists(
    navController: NavController
) {
    // Own tag: NewAlbums persists a different type under "home/discoveryAlbums" and reading it
    // here would throw a ClassCastException
    var discoverPage by persist<Result<Environment.DiscoverPageAlbums>>("home/discoveryAlbumsFromArtists")
    var retry by rememberSaveable { mutableStateOf(0) }

    LoaderScreen(show = discoverPage == null)

    LaunchedEffect(retry) {
        // Drop a persisted failure so the loader shows again while retrying
        if (retry > 0 || discoverPage?.isFailure == true) discoverPage = null
        // A request that hangs becomes a failure with retry instead of an endless loader
        discoverPage = withTimeoutOrNull(20_000) {
            Environment.discoverPageNewAlbums()
        } ?: Result.failure(Exception("NewAlbumsFromArtists timeout"))
    }

    var preferitesArtists by persistList<Artist>("home/artists")
    LaunchedEffect(Unit) {
        Database.preferitesArtistsByName().collect { preferitesArtists = it }
    }

    val thumbnailSizeDp = Dimensions.thumbnails.album + 24.dp
    val thumbnailSizePx = thumbnailSizeDp.px

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    val showSearchTab by rememberPreference(showSearchTabKey, false)

    val lazyGridState = rememberLazyGridState()

    val disableScrollingText by rememberPreference(disableScrollingTextKey, true)


    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
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
        discoverPage?.getOrNull()?.let { page ->
            var newReleaseAlbumsFiltered by persistList<Environment.AlbumItem>("discovery/newalbumsartist")
            page.newReleaseAlbums.forEach { album ->
                preferitesArtists.forEach { artist ->
                    if (artist.name == album.authors?.first()?.name) {
                        newReleaseAlbumsFiltered += album
                    }
                }
            }

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
                    //.fillMaxSize()
                ) {
                    item(
                        key = "header",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        NoirPageTitle(
                            text = stringResource(R.string.new_albums_of_your_artists),
                            progress = { 0f },
                            horizontalPadding = 0.dp
                        )

                    }

                    if (newReleaseAlbumsFiltered.isNotEmpty()) {
                        itemsIndexed(
                            items = newReleaseAlbumsFiltered.distinct(),
                            key = { _, album -> album.key }) { index, album ->
                            NoirMediaCard(
                                cover = album.thumbnail?.url.thumbnail(480),
                                title = album.info?.name.orEmpty(),
                                subtitle = listOfNotNull(
                                    album.authors?.mapNotNull { it.name }?.filter { name -> name.isNotBlank() }
                                        ?.joinToString(", ")?.takeIf { it.isNotEmpty() },
                                    album.year
                                ).joinToString(" · "),
                                sharedKey = "new-album-artist:${album.key}",
                                browseId = album.key,
                                onClick = {
                                    navController.navigate(route = "${NavRoutes.album.name}/${album.key}")
                                },
                                modifier = Modifier.cascadeEntry(index)
                            )
                        }
                    } else {
                        item(
                            key = "noAlbums",
                            contentType = 0,
                        ) {
                            BasicText(
                                text = stringResource(R.string.loading_no_new_releases_artists),
                                style = typography().s.secondary.center,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(all = 16.dp)
                            )
                        }
                    }
                    item(
                        key = "footer",
                        contentType = 0,
                    ) {
                        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                    }
                }
            }

        } ?: discoverPage?.let {
            LoadingErrorScreen(onRetry = { retry++ })
        }
        /***************/


    }

}
