package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import it.fast4x.riplay.enums.PopupType
import it.fast4x.riplay.extensions.fastshare.shareCollectionToDownloader
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.data.models.Artist
import it.fast4x.riplay.enums.MenuStyle
import it.fast4x.riplay.extensions.preferences.menuStyleKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.items.ArtistItem
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.styling.px

@Composable
fun ArtistsItemMenu(
    //navController: NavController,
    artist: Artist,
    onDismiss: () -> Unit,
    onBlacklist: (() -> Unit)? = null,
    disableScrollingText: Boolean,
) {
    val density = LocalDensity.current
    val downloadContext = LocalContext.current

    var height by remember {
        mutableStateOf(0.dp)
    }

    val menuStyle by rememberPreference(
        menuStyleKey,
        MenuStyle.List
    )

    if (menuStyle == MenuStyle.Grid) {

    } else {
        Menu(
            modifier = Modifier
                .onPlaced { height = with(density) { it.size.height.toDp() } }
        ) {
            val thumbnailSizeDp = Dimensions.thumbnails.song + 20.dp
            val thumbnailSizePx = thumbnailSizeDp.px
            val thumbnailArtistSizeDp = Dimensions.thumbnails.song + 10.dp
            val thumbnailArtistSizePx = thumbnailArtistSizeDp.px

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 12.dp)
            ) {
                ArtistItem(
                    artist = artist,
                    thumbnailSizePx = thumbnailSizePx,
                    thumbnailSizeDp = thumbnailSizeDp,
                    disableScrollingText = disableScrollingText,
                )
            }
            Spacer(
                modifier = Modifier
                    .height(8.dp)
            )
            // The whole thing to the downloader, as one link it can expand by itself.
            MenuEntry(
                icon = R.drawable.downloaded,
                text = stringResource(R.string.download_all_with_ytdlnis),
                onClick = {
                    onDismiss()
                    shareCollectionToDownloader(
                        context = downloadContext,
                        url = artist.shareYTUrl,
                        songs = emptyList(),
                        title = artist.name.orEmpty(),
                        onEmpty = {
                            SmartMessage(
                                downloadContext.resources.getString(R.string.nothing_to_download),
                                context = downloadContext,
                                type = PopupType.Info,
                            )
                        },
                        onAppMissing = {
                            SmartMessage(
                                downloadContext.resources.getString(R.string.ytdlnis_not_installed),
                                context = downloadContext,
                                type = PopupType.Error,
                            )
                        },
                    )
                }
            )

            onBlacklist?.let {
                MenuEntry(
                    icon = R.drawable.alert_circle,
                    text = stringResource(R.string.add_to_blacklist),
                    onClick = {
                        onDismiss()
                        onBlacklist()
                    }
                )
            }
        }
    }
}