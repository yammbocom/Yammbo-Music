package it.fast4x.riplay.ui.screens.searchresult

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.fast4x.riplay.extensions.persist.persist
import it.fast4x.environment.Environment
import it.fast4x.environment.utils.plus
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.ui.components.ShimmerHost
import it.fast4x.riplay.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.center
import it.fast4x.riplay.ui.styling.secondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.enums.ContentType
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.utils.LazyListContainer

@ExperimentalAnimationApi
@Composable
inline fun <T : Environment.Item> ItemsPage(
    tag: String,
    crossinline headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = "No items found",
    noinline itemsPageProvider: (suspend (String?) -> Result<Environment.ItemsPage<T>?>?)? = null,
    filterContentType: ContentType = ContentType.UserGenerated
) {
    val updatedItemsPageProvider by rememberUpdatedState(itemsPageProvider)

    val lazyListState = rememberLazyListState()

    var itemsPage by persist<Environment.ItemsPage<T>?>(tag)

    // Keep each fresh result set pinned to the top. Tabs whose header renders nothing leave a
    // 0-height "header" item, so the list can anchor to the bottom "loading" row and the first
    // page shows up scrolled to the end (user had to scroll up). Scroll to the top once per
    // composition, when the first page of a given query/tab (tag) loads.
    //
    // NOTE: this uses `remember`, NOT `rememberSaveable`. rememberSaveable restored the flag as
    // `true` when the tab's composable was re-created after navigating away and back (e.g. opening
    // a podcast/album and pressing back), so the re-pin was skipped and the list re-anchored to the
    // bottom again. Plain remember resets on every fresh composition, so we re-pin on return too,
    // while staying stable across recompositions (pagination appends don't re-fire it).
    var didPinTop by remember(tag) { mutableStateOf(false) }
    LaunchedEffect(tag) {
        if (didPinTop) return@LaunchedEffect
        // Wait for the first page, then re-assert the top over a few frames. Tabs with an empty
        // (0-height) header let the LazyColumn anchor to the bottom "loading" row, so the first
        // page shows up scrolled to the end; a single scrollToItem loses the race against that
        // anchor-preservation, which can re-assert over several frames as layout settles.
        snapshotFlow { itemsPage?.items?.size ?: 0 }.filter { it > 0 }.first()
        repeat(5) {
            withFrameNanos { }
            if (lazyListState.firstVisibleItemIndex != 0 ||
                lazyListState.firstVisibleItemScrollOffset != 0
            ) {
                lazyListState.scrollToItem(0)
            }
        }
        didPinTop = true
    }

    LaunchedEffect(lazyListState, updatedItemsPageProvider) {
        val currentItemsPageProvider = updatedItemsPageProvider ?: return@LaunchedEffect

        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
            .collect { shouldLoadMore ->
                if (!shouldLoadMore) return@collect

                withContext(Dispatchers.IO) {
                    currentItemsPageProvider(itemsPage?.continuation)
                }?.onSuccess {
                    if (it == null) {
                        if (itemsPage == null) {
                            itemsPage = Environment.ItemsPage(null, null)
                        }
                    } else {
                        itemsPage += it
                    }
                }?.exceptionOrNull()?.printStackTrace()
            }
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
    ) {
        LazyListContainer(
            state = lazyListState,
        ) {
            LazyColumn(
                state = lazyListState,
                //contentPadding = LocalPlayerAwareWindowInsets.current
                //    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                modifier = modifier
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = "header",
                ) {
                    headerContent(null)
                }

                items(
                    items = itemsPage?.items?.filter { item ->
                        when (item) {
                            is Environment.VideoItem-> {
                                val mediaItem = (item as Environment.VideoItem)
                                when (filterContentType) {
                                    ContentType.All -> true
                                    ContentType.Official -> !mediaItem.isUserGeneratedContent
                                    ContentType.UserGenerated -> mediaItem.isUserGeneratedContent
                                }
                            }
                            is Environment.SongItem-> {
                                val mediaItem = (item as Environment.SongItem)
                                when (filterContentType) {
                                    ContentType.All -> true
                                    ContentType.Official -> !mediaItem.isUserGeneratedContent
                                    ContentType.UserGenerated -> mediaItem.isUserGeneratedContent
                                }
                            }
                            else -> true
                        }
                    } ?: emptyList(),
                    key = Environment.Item::key,
                    itemContent = itemContent
                )

                if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) {
                    item(key = "empty") {
                        BasicText(
                            text = emptyItemsText,
                            style = typography().xs.secondary.center,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 32.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                if (!(itemsPage != null && itemsPage?.continuation == null)) {
                    item(key = "loading") {
                        val isFirstLoad = itemsPage?.items.isNullOrEmpty()
                        ShimmerHost(
                            modifier = Modifier
                                .run {
                                    if (isFirstLoad) fillParentMaxSize() else this
                                }
                        ) {
                            repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                                itemPlaceholderContent()
                            }
                        }
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

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)


    }
}
