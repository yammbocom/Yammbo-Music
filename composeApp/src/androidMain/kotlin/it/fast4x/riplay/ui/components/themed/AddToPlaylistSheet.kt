package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yambo.music.R
import it.fast4x.riplay.commonutils.MONTHLY_PREFIX
import it.fast4x.riplay.commonutils.PINNED_PREFIX
import it.fast4x.riplay.commonutils.cleanPrefix
import it.fast4x.riplay.commonutils.thumbnail
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Playlist
import it.fast4x.riplay.data.models.PlaylistPreview
import it.fast4x.riplay.ui.styling.medium
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.isNetworkConnected
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

private const val LIKED_MUSIC_COVER = "https://www.gstatic.com/youtube/media/ytm/images/pbg/liked-music-@1200.png"

/** Top row of the add-to-playlist sheets: back, the title and, when given, a search toggle. */
@Composable
fun AddToPlaylistHeader(onBack: () -> Unit, onSearch: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
    ) {
        IconButton(
            onClick = onBack,
            icon = R.drawable.chevron_back,
            color = colorPalette().text,
            modifier = Modifier
                .padding(12.dp)
                .size(20.dp)
        )
        BasicText(
            text = stringResource(R.string.add_to_playlist),
            style = typography().m.semiBold.copy(color = colorPalette().text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        onSearch?.let {
            IconButton(
                onClick = it,
                icon = R.drawable.search,
                color = colorPalette().text,
                modifier = Modifier
                    .padding(12.dp)
                    .size(20.dp)
            )
        }
    }
}

/** "New playlist" as the first row of the list, shaped like the playlists under it. */
@Composable
fun NewPlaylistEntry(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colorPalette().text)
        ) {
            Image(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette().background0),
                modifier = Modifier.size(22.dp)
            )
        }
        BasicText(
            text = stringResource(R.string.new_playlist),
            style = typography().s.semiBold.copy(color = colorPalette().text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Name prompt for a new playlist; [onCreate] gets the trimmed name. */
@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    InputTextDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.new_playlist),
        value = "",
        placeholder = stringResource(R.string.playlist_name),
        setValue = onCreate,
        confirmText = stringResource(R.string.create_playlist_action),
        singleLine = true
    )
}

/**
 * The playlists a song can go to, in three titled groups: pinned, YouTube Music (only the ones
 * that can be edited, and only online) and the own ones. Monthly playlists are left out, they
 * fill themselves. [containing] marks the playlists that already have the song; what a tap on
 * one of those does is up to [onPick]. A long press opens the playlist.
 */
@Composable
fun PlaylistPickerSections(
    playlists: List<PlaylistPreview>,
    containing: Collection<Long>,
    onPick: (PlaylistPreview) -> Unit,
    onOpen: (PlaylistPreview) -> Unit,
) {
    val context = LocalContext.current
    val online = isNetworkConnected(context)

    val pinned = playlists.filter {
        it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                if (online) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
    }
    val youtube = if (online) playlists.filter {
        it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
    } else emptyList()
    val own = playlists.filter {
        !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                !it.playlist.isYoutubePlaylist
    }

    listOf(
        R.string.pinned_playlists to pinned,
        R.string.ytm_playlists to youtube,
        R.string.playlists to own
    ).forEach { (title, group) ->
        if (group.isEmpty()) return@forEach
        BasicText(
            text = stringResource(title),
            style = typography().xs.semiBold.secondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
        )
        group.forEach { preview ->
            PlaylistPickEntry(
                preview = preview,
                isIn = preview.playlist.id in containing,
                onClick = { onPick(preview) },
                onLongClick = { onOpen(preview) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistPickEntry(
    preview: PlaylistPreview,
    isIn: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        PlaylistPickCover(preview.playlist)

        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = cleanPrefix(preview.playlist.name),
                style = typography().s.semiBold.copy(color = colorPalette().text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = "${preview.songCount} " + stringResource(R.string.songs),
                style = typography().xxs.medium.secondary,
                maxLines = 1
            )
        }

        // Filled check when the song is already there, an outlined plus when it can be added.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (isIn) Modifier.background(colorPalette().text)
                    else Modifier.border(1.5.dp, colorPalette().textSecondary, CircleShape)
                )
        ) {
            Image(
                painter = painterResource(if (isIn) R.drawable.checkmark else R.drawable.add),
                contentDescription = null,
                colorFilter = ColorFilter.tint(if (isIn) colorPalette().background0 else colorPalette().textSecondary),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** The playlist's cover, else its first song's artwork, else a plain playlist glyph. */
@Composable
private fun PlaylistPickCover(playlist: Playlist) {
    val firstSongCover by remember(playlist.id) {
        Database.playlistThumbnailUrls(playlist.id).map { it.firstOrNull()?.thumbnail(144) }
    }.collectAsState(initial = null, context = Dispatchers.IO)
    val cover = when {
        playlist.browseId == "LM" -> LIKED_MUSIC_COVER
        !playlist.thumbnailUrl.isNullOrBlank() -> playlist.thumbnailUrl
        else -> firstSongCover
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colorPalette().background2)
    ) {
        Image(
            painter = painterResource(R.drawable.playlist),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colorPalette().textSecondary),
            modifier = Modifier.size(20.dp)
        )
        if (cover != null)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cover)
                    .setHeader("User-Agent", "Mozilla/5.0")
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
    }
}
