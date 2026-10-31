package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yambo.music.R
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.typography


@Composable
fun SortMenu (
    title: String? = null,
    onDismiss: () -> Unit,
    onTitle: (() -> Unit)? = null,
    onDatePlayed: (() -> Unit)? = null,
    onPlayTime: (() -> Unit)? = null,
    onRelativePlayTime: (() -> Unit)? = null,
    onName: (() -> Unit)? = null,
    onSongNumber: (() -> Unit)? = null,
    onPosition: (() -> Unit)? = null,
    onArtist: (() -> Unit)? = null,
    onArtistAndAlbum: (() -> Unit)? = null,
    onAlbum: (() -> Unit)? = null,
    onAlbumYear: (() -> Unit)? = null,
    onYear: (() -> Unit)? = null,
    onDateAdded: (() -> Unit)? = null,
    onDateLiked: (() -> Unit)? = null,
    onDuration: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var height by remember {
        mutableStateOf(0.dp)
    }
    val density = LocalDensity.current

    Menu(
        modifier = modifier
            .onPlaced { height = with(density) { it.size.height.toDp() } }

    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .padding(end = 12.dp)
        ) {
            if (title != null) {
                BasicText(
                    text = title,
                    style = typography().m.semiBold,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 24.dp)
                )
            }
        }

        Spacer(
            modifier = Modifier
                .height(8.dp)
        )

        onTitle?.let {
            MenuChoiceEntry(
                icon = R.drawable.text,
                text = stringResource(R.string.sort_title),
                selected = false,
                onClick = {
                    onDismiss()
                    onTitle()
                }
            )
        }
        onDatePlayed?.let {
            MenuChoiceEntry(
                icon = R.drawable.up_right_arrow,
                text = stringResource(R.string.sort_date_played),
                selected = false,
                onClick = {
                    onDismiss()
                    onDatePlayed()
                }
            )
        }
        onDateLiked?.let {
            MenuChoiceEntry(
                icon = R.drawable.heart,
                text = stringResource(R.string.sort_date_liked),
                selected = false,
                onClick = {
                    onDismiss()
                    onDateLiked()
                }
            )
        }
        onPlayTime?.let {
            MenuChoiceEntry(
                icon = R.drawable.trending,
                text = stringResource(R.string.sort_listening_time),
                selected = false,
                onClick = {
                    onDismiss()
                    onPlayTime()
                }
            )
        }
        onRelativePlayTime?.let {
            MenuChoiceEntry(
                icon = R.drawable.trending,
                text = stringResource(R.string.sort_relative_listening_time),
                selected = false,
                onClick = {
                    onDismiss()
                    onRelativePlayTime()
                }
            )
        }
        onName?.let {
            MenuChoiceEntry(
                icon = R.drawable.text,
                text = stringResource(R.string.sort_name),
                selected = false,
                onClick = {
                    onDismiss()
                    onName()
                }
            )
        }
        onSongNumber?.let {
            MenuChoiceEntry(
                icon = R.drawable.medical,
                text = stringResource(R.string.sort_songs_number),
                selected = false,
                onClick = {
                    onDismiss()
                    onSongNumber()
                }
            )
        }
        onPosition?.let {
            MenuChoiceEntry(
                icon = R.drawable.position,
                text = stringResource(R.string.sort_position),
                selected = false,
                onClick = {
                    onDismiss()
                    onPosition()
                }
            )
        }
        onArtist?.let {
            MenuChoiceEntry(
                icon = R.drawable.artist,
                text = stringResource(R.string.sort_artist),
                selected = false,
                onClick = {
                    onDismiss()
                    onArtist()
                }
            )
        }
        onAlbum?.let {
            MenuChoiceEntry(
                icon = R.drawable.music_album,
                text = stringResource(R.string.sort_album),
                selected = false,
                onClick = {
                    onDismiss()
                    onAlbum()
                }
            )
        }
        onArtistAndAlbum?.let {
            MenuChoiceEntry(
                icon = R.drawable.artist,
                text = "${stringResource(R.string.sort_artist)}, ${stringResource(R.string.sort_album)}",
                selected = false,
                onClick = {
                    onDismiss()
                    onArtistAndAlbum()
                }
            )
        }
        onAlbumYear?.let {
            MenuChoiceEntry(
                icon = R.drawable.calendar,
                text = stringResource(R.string.sort_album_year),
                selected = false,
                onClick = {
                    onDismiss()
                    onAlbumYear()
                }
            )
        }
        onYear?.let {
            MenuChoiceEntry(
                icon = R.drawable.calendar,
                text = stringResource(R.string.sort_year),
                selected = false,
                onClick = {
                    onDismiss()
                    onYear()
                }
            )
        }
        onDateAdded?.let {
            MenuChoiceEntry(
                icon = R.drawable.time,
                text = stringResource(R.string.sort_date_added),
                selected = false,
                onClick = {
                    onDismiss()
                    onDateAdded()
                }
            )
        }

        onDuration?.let {
            MenuChoiceEntry(
                icon = R.drawable.time,
                text = stringResource(R.string.sort_duration),
                selected = false,
                onClick = {
                    onDismiss()
                    onDuration()
                }
            )
        }
    }
}

@Composable
fun FilterMenu (
    title: String? = null,
    onDismiss: () -> Unit,
    onAll: (() -> Unit)? = null,
    onOnlineSongs: (() -> Unit)? = null,
    onYoutubeLibrary: (() -> Unit)? = null,
    onVideos: (() -> Unit)? = null,
    onLocal: (() -> Unit)? = null,
    onFavorites: (() -> Unit)? = null,
    onUnmatched: (() -> Unit)? = null,
    onCached: (() -> Unit)? = null,
    onExplicit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var height by remember {
        mutableStateOf(0.dp)
    }
    val density = LocalDensity.current

    Menu(
        modifier = modifier
            .onPlaced { height = with(density) { it.size.height.toDp() } }

    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .padding(end = 12.dp)
        ) {
            if (title != null) {
                BasicText(
                    text = title,
                    style = typography().m.semiBold,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 24.dp)
                )
            }
        }

        Spacer(
            modifier = Modifier
                .height(8.dp)
        )

        onAll?.let {
            MenuChoiceEntry(
                icon = R.drawable.musical_notes,
                text = stringResource(R.string.all),
                selected = false,
                onClick = {
                    onDismiss()
                    onAll()
                }
            )
        }
        onOnlineSongs?.let {
            MenuChoiceEntry(
                icon = R.drawable.internet,
                text = stringResource(R.string.online_songs),
                selected = false,
                onClick = {
                    onDismiss()
                    onOnlineSongs()
                }
            )
        }
        onYoutubeLibrary?.let {
            MenuChoiceEntry(
                icon = R.drawable.internet,
                text = stringResource(R.string.ytm_library),
                selected = false,
                onClick = {
                    onDismiss()
                    onYoutubeLibrary()
                }
            )
        }

        onVideos?.let {
            MenuChoiceEntry(
                icon = R.drawable.video,
                text = stringResource(R.string.videos),
                selected = false,
                onClick = {
                    onDismiss()
                    onVideos()
                }
            )
        }
        onUnmatched?.let {
            MenuChoiceEntry(
                icon = R.drawable.alert,
                text = stringResource(R.string.unmatched),
                selected = false,
                onClick = {
                    onDismiss()
                    onUnmatched()
                }
            )
        }
        onFavorites?.let {
            MenuChoiceEntry(
                icon = R.drawable.heart,
                text = stringResource(R.string.favorites),
                selected = false,
                onClick = {
                    onDismiss()
                    onFavorites()
                }
            )
        }
        onLocal?.let {
            MenuChoiceEntry(
                icon = R.drawable.devices,
                text = stringResource(R.string.on_device),
                selected = false,
                onClick = {
                    onDismiss()
                    onLocal()
                }
            )
        }

        onExplicit?.let {
            MenuChoiceEntry(
                icon = R.drawable.explicit,
                text = stringResource(R.string.explicit),
                selected = false,
                onClick = {
                    onDismiss()
                    onExplicit()
                }
            )
        }
    }
}