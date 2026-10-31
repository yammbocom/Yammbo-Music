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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.valentinilk.shimmer.shimmer
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.Environment
import it.fast4x.environment.requests.discoverPage
import it.fast4x.riplay.utils.contentCountryCode
import it.fast4x.riplay.LocalPlayerAwareWindowInsets
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.data.models.toUiMood
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.themed.HeaderPlaceholder
import it.fast4x.riplay.ui.components.CollapsingTitleEffect
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.cascadeEntry
import it.fast4x.riplay.ui.components.themed.NoirTile
import it.fast4x.riplay.ui.screens.newreleases.NoirPageTitle
import it.fast4x.riplay.ui.screens.newreleases.rememberGridTitleCollapse
import it.fast4x.riplay.ui.components.themed.TextPlaceholder
import it.fast4x.riplay.ui.items.AlbumItemPlaceholder
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.utils.LazyListContainer

@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun MoodsPage(
    navController: NavController
) {
    val windowInsets = LocalPlayerAwareWindowInsets.current

    var discoverPage by persist<Result<Environment.DiscoverPage>>("home/discoveryMoods")
    LaunchedEffect(Unit) {
        discoverPage = Environment.discoverPage(contentCountryCode())
    }
    val thumbnailSizeDp = Dimensions.thumbnails.album + 24.dp

    val moodAngGenresLazyGridState = rememberLazyGridState()
    val titleProgress = rememberGridTitleCollapse(moodAngGenresLazyGridState)
    CollapsingTitleEffect(stringResource(R.string.moods_and_genres), titleProgress)

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

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
        discoverPage?.getOrNull()?.let { moodResult ->
            LazyListContainer(
                state = moodAngGenresLazyGridState,
            ) {
                // 150dp cells on phones (as before), 180dp on tablets, capped at grid width.
                val noirLayout = rememberNoirLayout()
                LazyVerticalGrid(
                    state = moodAngGenresLazyGridState,
                    columns = GridCells.Adaptive(if (noirLayout.isCompact) 150.dp else 180.dp),
                    contentPadding = PaddingValues(horizontal = noirLayout.sidePadding(NoirContentMax.Grid)),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            text = stringResource(R.string.moods_and_genres),
                            progress = titleProgress,
                            horizontalPadding = 0.dp
                        )
                    }

                    discoverPage?.getOrNull()?.let { page ->
                        if (page.moods.isNotEmpty()) {

                            itemsIndexed(
                                items = page.moods.sortedBy { it.title },
                                key = { _, mood -> mood.endpoint.params ?: mood.title }
                            ) { index, it ->
                                NoirTile(
                                    text = it.title,
                                    onClick = {
                                        it.endpoint.browseId?.let { _ ->
                                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                                "mood",
                                                it.toUiMood()
                                            )
                                            navController.navigate(NavRoutes.mood.name)
                                        }
                                    },
                                    modifier = Modifier
                                        .animateItem()
                                        .cascadeEntry(index + 1)
                                        .fillMaxWidth()
                                        .height(84.dp)
                                )
                            }
                        }

                    }

                    item(key = "bottom", span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                    }

                }
            }
        } ?: discoverPage?.exceptionOrNull()?.let {
            BasicText(
                text = stringResource(R.string.page_not_been_loaded),
                style = typography().s.secondary.center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(all = 16.dp)
            )
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
