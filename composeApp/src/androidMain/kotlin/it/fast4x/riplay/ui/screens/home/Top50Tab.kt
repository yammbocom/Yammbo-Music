package it.fast4x.riplay.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.LocalSelectedQueue
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.data.models.defaultQueue
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.LocalGlobalSheetState
import it.fast4x.riplay.ui.components.SwipeablePlaylistItem
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.riplay.ui.items.SongItem
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.color
import it.fast4x.riplay.ui.styling.px
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.LazyListContainer
import it.fast4x.riplay.utils.addNext
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.forcePlayFromBeginning
import it.fast4x.riplay.utils.typography
import kotlin.time.Duration.Companion.days


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun Top50Tab(
    navController: NavController
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalGlobalSheetState.current
    val selectedQueue = LocalSelectedQueue.current

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSizePx = thumbnailSizeDp.px

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    var songs by persistList<Song>("top50/songs")

    val now = System.currentTimeMillis()
    val allTime = 18250.days.inWholeMilliseconds

    LaunchedEffect(Unit) {
        Database.songsMostPlayedByPeriod(allTime, now, 50)
            .collect { songs = it }
    }

    Box(
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
        val lazyListState = rememberLazyListState()

        LazyListContainer(
            state = lazyListState,
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                item(key = "header") {
                    HeaderWithIcon(
                        title = stringResource(R.string.top_50),
                        iconId = R.drawable.trending,
                        enabled = true,
                        showIcon = true,
                        modifier = Modifier,
                        onClick = {}
                    )
                }

                item(key = "hero") {
                    Top50HeroCard(
                        songCount = songs.size,
                        onPlayAll = {
                            if (songs.isNotEmpty()) {
                                binder?.stopRadio()
                                binder?.player?.forcePlayFromBeginning(
                                    songs.map(Song::asMediaItem)
                                )
                            }
                        },
                        onShuffleAll = {
                            if (songs.isNotEmpty()) {
                                binder?.stopRadio()
                                binder?.player?.forcePlayFromBeginning(
                                    songs.shuffled().map(Song::asMediaItem)
                                )
                            }
                        }
                    )
                }

                if (songs.isEmpty()) {
                    item(key = "empty") {
                        Top50EmptyState()
                    }
                } else {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id }
                    ) { index, song ->
                        SwipeablePlaylistItem(
                            mediaItem = song.asMediaItem,
                            onPlayNext = {
                                binder?.player?.addNext(
                                    song.asMediaItem,
                                    queue = selectedQueue ?: defaultQueue()
                                )
                            }
                        ) {
                            SongItem(
                                song = song.asMediaItem,
                                thumbnailSizeDp = thumbnailSizeDp,
                                thumbnailSizePx = thumbnailSizePx,
                                onThumbnailContent = {
                                    // Bold rank number overlay
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .width(thumbnailSizeDp)
                                            .align(Alignment.Center)
                                    ) {
                                        BasicText(
                                            text = "${index + 1}",
                                            style = typography().l.semiBold.color(
                                                if (index < 3) colorPalette().accent else colorPalette().text
                                            ).copy(
                                                fontSize = if (index < 3) 22.sp else 18.sp,
                                                textAlign = TextAlign.Center
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onLongClick = {
                                            menuState.display {
                                                NonQueuedMediaItemMenu(
                                                    navController = navController,
                                                    onDismiss = { menuState.hide() },
                                                    mediaItem = song.asMediaItem,
                                                    onInfo = {
                                                        navController.navigate(
                                                            "${NavRoutes.videoOrSongInfo.name}/${song.id}"
                                                        )
                                                    },
                                                    disableScrollingText = disableScrollingText,
                                                )
                                            }
                                        },
                                        onClick = {
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayAtIndex(
                                                songs.map(Song::asMediaItem),
                                                index
                                            )
                                        }
                                    )
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }

                item(key = "bottom-spacer") {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun Top50HeroCard(
    songCount: Int,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit
) {
    val colors = colorPalette()
    val canPlay = songCount > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Gradient hero strip with chart icon + counts
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.85f),
                            colors.accent.copy(alpha = 0.45f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            // Decorative oversized trending icon
            Image(
                painter = painterResource(id = R.drawable.trending),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.18f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(112.dp)
            )

            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = stringResource(R.string.top_50),
                    style = typography().xxl.semiBold.copy(color = colors.onAccent)
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = stringResource(R.string.top_50_most_played),
                    style = typography().xs.copy(
                        color = colors.onAccent.copy(alpha = 0.88f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (songCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = "$songCount/50",
                        style = typography().s.semiBold.copy(
                            color = colors.onAccent.copy(alpha = 0.95f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action row: Play all + Shuffle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroActionButton(
                iconId = R.drawable.play,
                label = stringResource(R.string.play_all),
                primary = true,
                enabled = canPlay,
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            )
            HeroActionButton(
                iconId = R.drawable.shuffle,
                label = stringResource(R.string.shuffle),
                primary = false,
                enabled = canPlay,
                onClick = onShuffleAll,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun HeroActionButton(
    iconId: Int,
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    val bg = when {
        !enabled -> colors.background2.copy(alpha = 0.5f)
        primary -> colors.accent
        else -> colors.background3
    }
    val fg = when {
        !enabled -> colors.textDisabled
        primary -> colors.onAccent
        else -> colors.text
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(fg),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicText(
            text = label,
            style = typography().s.semiBold.copy(color = fg),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Top50EmptyState() {
    val colors = colorPalette()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.musical_notes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.accent),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        BasicText(
            text = stringResource(R.string.top_50_empty_title),
            style = typography().m.semiBold.copy(
                color = colors.text,
                textAlign = TextAlign.Center
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        BasicText(
            text = stringResource(R.string.top_50_empty_subtitle),
            style = typography().xs.copy(
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
