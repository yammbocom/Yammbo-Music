package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.yambo.music.R
import it.fast4x.riplay.enums.AlbumsType
import it.fast4x.riplay.enums.ArtistsType
import it.fast4x.riplay.enums.BuiltInPlaylist
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PlaylistType
import it.fast4x.riplay.extensions.preferences.albumTypeKey
import it.fast4x.riplay.extensions.preferences.artistTypeKey
import it.fast4x.riplay.extensions.preferences.builtInPlaylistKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.playlistTypeKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.putEnum
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.themed.HeaderWithIcon
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography


// Reuse per-category tints — same colors as Mi Música so a section's identity is
// visually consistent across the app, but the OnDevice layout (vertical rows with
// chevrons) is intentionally different from Mi Música's bento grid so users don't
// confuse the two screens.
private val SongsTint = Color(0xFFEF5350)
private val ArtistsTint = Color(0xFF5C6BC0)
private val AlbumsTint = Color(0xFFAB47BC)
private val PlaylistsTint = Color(0xFFFFA726)

@Composable
fun OnDeviceTab(
    onSongsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onPlaylistsClick: () -> Unit
) {
    val context = LocalContext.current

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
                title = stringResource(R.string.my_music_on_device_title),
                iconId = R.drawable.download,
                enabled = true,
                showIcon = true,
                modifier = Modifier,
                onClick = {}
            )

            BasicText(
                text = stringResource(R.string.my_music_on_device_subtitle),
                style = typography().xs.copy(color = colorPalette().textSecondary),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OnDeviceListRow(
                    iconId = R.drawable.musical_notes,
                    label = stringResource(R.string.local_songs),
                    subtitle = stringResource(R.string.on_device_hint_songs),
                    tint = SongsTint,
                    onClick = {
                        context.preferences.edit {
                            putEnum(builtInPlaylistKey, BuiltInPlaylist.OnDevice)
                        }
                        onSongsClick()
                    }
                )
                OnDeviceListRow(
                    iconId = R.drawable.person,
                    label = stringResource(R.string.artists),
                    subtitle = stringResource(R.string.on_device_hint_artists),
                    tint = ArtistsTint,
                    onClick = {
                        context.preferences.edit {
                            putEnum(artistTypeKey, ArtistsType.OnDevice)
                        }
                        onArtistsClick()
                    }
                )
                OnDeviceListRow(
                    iconId = R.drawable.album,
                    label = stringResource(R.string.albums),
                    subtitle = stringResource(R.string.on_device_hint_albums),
                    tint = AlbumsTint,
                    onClick = {
                        context.preferences.edit {
                            putEnum(albumTypeKey, AlbumsType.OnDevice)
                        }
                        onAlbumsClick()
                    }
                )
                OnDeviceListRow(
                    iconId = R.drawable.library,
                    label = stringResource(R.string.playlists),
                    subtitle = stringResource(R.string.on_device_hint_playlists),
                    tint = PlaylistsTint,
                    onClick = {
                        context.preferences.edit {
                            putEnum(playlistTypeKey, PlaylistType.OnDevicePlaylist)
                        }
                        onPlaylistsClick()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnDeviceListRow(
    iconId: Int,
    label: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    val colors = colorPalette()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.background2.copy(alpha = 0.65f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.size(14.dp))

        // Title + subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            BasicText(
                text = label,
                style = typography().m.semiBold.copy(color = colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = typography().xs.copy(color = colors.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        // Chevron
        Image(
            painter = painterResource(id = R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier.size(18.dp)
        )
    }
}
