package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.StaggeredEntry
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


// Strictly monochrome: every card shares the same subtle dark gradient between the palette's
// surfaces (like the Browse watermark cards) and is told apart by its icon and label only.
@Composable
private fun Modifier.bentoCardSurface(shape: RoundedCornerShape): Modifier {
    val colors = colorPalette()
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(colors.background2, colors.background1)
            )
        )
        .border(width = 1.dp, color = colors.background2, shape = shape)
}

@Composable
fun MyMusicTab(
    onSongsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onRadioClick: () -> Unit,
    onDeviceClick: () -> Unit
) {
    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HeaderWithIcon(
                title = stringResource(R.string.my_music),
                iconId = R.drawable.musical_notes,
                enabled = true,
                showIcon = true,
                modifier = Modifier,
                onClick = {}
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cards stagger in (40 ms each) on first composition. Indexes
                // are sequential so the hero leads and the OnDevice banner
                // finishes the chain.
                StaggeredEntry(index = 0) {
                    BentoHeroCard(
                        iconId = R.drawable.musical_notes,
                        label = stringResource(R.string.local_songs),
                        hint = stringResource(R.string.my_music_hint_songs),
                        onClick = onSongsClick
                    )
                }

                // Right under Canciones: at the bottom of the list it sat behind the mini player.
                StaggeredEntry(index = 1) {
                    BentoWideCard(
                        iconId = R.drawable.radio,
                        label = stringResource(R.string.favorite_radios),
                        hint = stringResource(R.string.favorite_radios_hint),
                        onClick = onRadioClick
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredEntry(index = 2) {
                            BentoSquareCard(
                                iconId = R.drawable.person,
                                label = stringResource(R.string.artists),
                                onClick = onArtistsClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StaggeredEntry(index = 3) {
                            BentoSquareCard(
                                iconId = R.drawable.album,
                                label = stringResource(R.string.albums),
                                onClick = onAlbumsClick,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                StaggeredEntry(index = 4) {
                    BentoWideCard(
                        iconId = R.drawable.library,
                        label = stringResource(R.string.playlists),
                        hint = stringResource(R.string.my_music_hint_playlists),
                        onClick = onPlaylistsClick
                    )
                }

                StaggeredEntry(index = 5) {
                    OnDeviceBannerCard(onClick = onDeviceClick)
                }
            }

            // Room for the mini player, which floats over the bottom of the page
            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
        }
    }
}

@Composable
private fun BentoHeroCard(
    iconId: Int,
    label: String,
    hint: String,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .bentoCardSurface(RoundedCornerShape(24.dp))
            .pressable(onClick = onClick)
            .padding(20.dp)
    ) {
        // Decorative oversized watermark icon at right
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.text.copy(alpha = 0.09f)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(120.dp)
        )

        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.Center
        ) {
            CategoryBadge(iconId = iconId, badgeSize = 44.dp, iconSize = 22.dp)
            Spacer(modifier = Modifier.height(12.dp))
            BasicText(
                text = label,
                style = typography().l.semiBold.copy(color = colors.text),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = hint,
                style = typography().xs.copy(color = colors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BentoSquareCard(
    iconId: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .bentoCardSurface(RoundedCornerShape(22.dp))
            .pressable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.Bottom
        ) {
            BasicText(
                text = label,
                style = typography().m.semiBold.copy(color = colors.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        CategoryBadge(
            iconId = iconId,
            badgeSize = 44.dp,
            iconSize = 22.dp,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

@Composable
private fun BentoWideCard(
    iconId: Int,
    label: String,
    hint: String,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .bentoCardSurface(RoundedCornerShape(22.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryBadge(iconId = iconId, badgeSize = 52.dp, iconSize = 26.dp)
            Spacer(modifier = Modifier.size(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = label,
                    style = typography().m.semiBold.copy(color = colors.text),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = hint,
                    style = typography().xs.copy(color = colors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Inverted badge: a text-colored circle with a background-colored icon.
@Composable
private fun CategoryBadge(
    iconId: Int,
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(colors.text),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.background0),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun OnDeviceBannerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
            .bentoCardSurface(RoundedCornerShape(22.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.text),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.download),
                    contentDescription = stringResource(R.string.on_device),
                    colorFilter = ColorFilter.tint(colors.background0),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                BasicText(
                    text = stringResource(R.string.my_music_on_device_title),
                    style = typography().m.semiBold.copy(color = colors.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = stringResource(R.string.my_music_on_device_subtitle),
                    style = typography().xs.copy(color = colors.textSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
