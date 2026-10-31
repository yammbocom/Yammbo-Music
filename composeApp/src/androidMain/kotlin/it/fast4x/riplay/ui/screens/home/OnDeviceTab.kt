package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.yambo.music.R
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.enums.AlbumSortBy
import it.fast4x.riplay.enums.AlbumsType
import it.fast4x.riplay.enums.ArtistSortBy
import it.fast4x.riplay.enums.ArtistsType
import it.fast4x.riplay.enums.BuiltInPlaylist
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PlaylistType
import it.fast4x.riplay.enums.SortOrder
import it.fast4x.riplay.extensions.preferences.albumTypeKey
import it.fast4x.riplay.extensions.preferences.artistTypeKey
import it.fast4x.riplay.extensions.preferences.builtInPlaylistKey
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.playlistTypeKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.putEnum
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

// Size of a flow's list, null until the first emission (no "0" flash).
@Composable
private fun rememberListCount(source: () -> Flow<List<Any?>>): Int? {
    val flow = remember {
        source().map { it.size }.distinctUntilChanged().flowOn(Dispatchers.Default)
    }
    val state: Int? by flow.collectAsState(initial = null)
    return state
}

@Composable
fun OnDeviceTab(
    onSongsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    onPlaylistsClick: () -> Unit
) {
    val context = LocalContext.current
    val colors = colorPalette()

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    val songsCount = rememberListCount { Database.songsOnDevice() }
    val artistsCount = rememberListCount {
        Database.artistsOnDevice(ArtistSortBy.DateAdded, SortOrder.Descending)
    }
    val albumsCount = rememberListCount {
        Database.albumsOnDevice(AlbumSortBy.DateAdded, SortOrder.Descending)
    }

    Box(
        modifier = Modifier
            .background(colors.background0)
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
            Column(modifier = Modifier.noirContentWidth(NoirContentMax.List)) {
                // Same title as the other library tabs; the long subtitle wraps instead of
                // being cut like an eyebrow would.
                BasicText(
                    text = stringResource(R.string.my_music_on_device_title),
                    style = typography().xxl.tight.copy(
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp)
                )
                BasicText(
                    text = stringResource(R.string.my_music_on_device_subtitle),
                    style = typography().xs.copy(color = colors.textSecondary),
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OnDeviceListRow(
                        iconId = R.drawable.musical_notes,
                        label = stringResource(R.string.local_songs),
                        subtitle = stringResource(R.string.on_device_hint_songs),
                        count = songsCount,
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
                        count = artistsCount,
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
                        count = albumsCount,
                        onClick = {
                            context.preferences.edit {
                                putEnum(albumTypeKey, AlbumsType.OnDevice)
                            }
                            onAlbumsClick()
                        }
                    )
                    // No cheap count of device folders exists, so the playlists row shows none.
                    OnDeviceListRow(
                        iconId = R.drawable.library,
                        label = stringResource(R.string.playlists),
                        subtitle = stringResource(R.string.on_device_hint_playlists),
                        count = null,
                        onClick = {
                            context.preferences.edit {
                                putEnum(playlistTypeKey, PlaylistType.OnDevicePlaylist)
                            }
                            onPlaylistsClick()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
            }
        }
    }
}

// Noir card row: 24dp radius, background1 and a hairline border (same surface as MyMusicTab).
@Composable
private fun OnDeviceListRow(
    iconId: Int,
    label: String,
    subtitle: String,
    count: Int?,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    val cardShape = RoundedCornerShape(24.dp)
    val tileShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(cardShape)
            .background(colors.background1)
            .border(1.dp, colors.text.copy(alpha = 0.08f), cardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Monochrome icon tile
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(tileShape)
                .background(colors.background0)
                .border(1.dp, colors.text.copy(alpha = 0.08f), tileShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.text),
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

        if (count != null) {
            BasicText(
                text = count.toString(),
                style = typography().xxs.mono.copy(color = colors.textSecondary, fontSize = 13.sp),
                maxLines = 1
            )
            Spacer(modifier = Modifier.size(8.dp))
        }

        // Chevron
        Image(
            painter = painterResource(id = R.drawable.chevron_forward),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.textSecondary),
            modifier = Modifier.size(18.dp)
        )
    }
}
