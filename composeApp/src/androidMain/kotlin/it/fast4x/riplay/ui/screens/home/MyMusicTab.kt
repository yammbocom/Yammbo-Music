package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
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
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


// Per-section tint colors. Light alpha overlays on top of background gradient so the
// active theme stays in charge — these only nudge each card so the user can tell them
// apart at a glance (vs the symmetric grid we used before).
private val SongsTint = Color(0xFFEF5350)      // red — match brand accent
private val ArtistsTint = Color(0xFF5C6BC0)    // indigo
private val AlbumsTint = Color(0xFFAB47BC)     // purple
private val PlaylistsTint = Color(0xFFFFA726)  // amber

@Composable
fun MyMusicTab(
    onSongsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
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
                // Hero — Canciones (full-width, prominent)
                BentoHeroCard(
                    iconId = R.drawable.musical_notes,
                    label = stringResource(R.string.local_songs),
                    hint = stringResource(R.string.my_music_hint_songs),
                    tint = SongsTint,
                    onClick = onSongsClick
                )

                // Two squares — Artistas + Álbumes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoSquareCard(
                        iconId = R.drawable.person,
                        label = stringResource(R.string.artists),
                        tint = ArtistsTint,
                        onClick = onArtistsClick,
                        modifier = Modifier.weight(1f)
                    )
                    BentoSquareCard(
                        iconId = R.drawable.album,
                        label = stringResource(R.string.albums),
                        tint = AlbumsTint,
                        onClick = onAlbumsClick,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Wide — Listas de reproducción
                BentoWideCard(
                    iconId = R.drawable.library,
                    label = stringResource(R.string.playlists),
                    hint = stringResource(R.string.my_music_hint_playlists),
                    tint = PlaylistsTint,
                    onClick = onPlaylistsClick
                )

                // Accent banner — En mi dispositivo
                OnDeviceBannerCard(onClick = onDeviceClick)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BentoHeroCard(
    iconId: Int,
    label: String,
    hint: String,
    tint: Color,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.32f),
                        colors.background3.copy(alpha = 0.95f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        // Decorative oversized icon at right
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint.copy(alpha = 0.35f)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(120.dp)
        )

        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalArrangement = Arrangement.Center
        ) {
            CategoryBadge(iconId = iconId, tint = tint, badgeSize = 44.dp, iconSize = 22.dp)
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
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = colorPalette()
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.22f),
                        colors.background3.copy(alpha = 0.95f)
                    )
                )
            )
            .clickable(onClick = onClick)
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
            tint = tint,
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
    tint: Color,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.28f),
                        colors.background3.copy(alpha = 0.95f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryBadge(iconId = iconId, tint = tint, badgeSize = 52.dp, iconSize = 26.dp)
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

@Composable
private fun CategoryBadge(
    iconId: Int,
    tint: Color,
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
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
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = 0.92f),
                        colors.accent.copy(alpha = 0.55f)
                    )
                )
            )
            .clickable(onClick = onClick)
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
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.download),
                    contentDescription = stringResource(R.string.on_device),
                    colorFilter = ColorFilter.tint(colors.onAccent),
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
                    style = typography().m.semiBold.copy(color = colors.onAccent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = stringResource(R.string.my_music_on_device_subtitle),
                    style = typography().xs.copy(color = colors.onAccent.copy(alpha = 0.88f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
