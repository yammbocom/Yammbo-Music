package it.fast4x.riplay.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.items.RadioFavoriteRow
import it.fast4x.riplay.ui.items.rememberNowPlayingMediaId
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlayAtIndex

/**
 * Mi Música > Radios favoritas: every station the listener hearted, newest first. A station is
 * just a Song row whose id starts with `radio:`, so the heart in the player is all it takes to
 * land here. Tapping one queues the whole list so next/previous hop between favourites.
 */
@UnstableApi
@Composable
fun FavoriteRadiosTab() {
    val binder = LocalPlayerServiceBinder.current
    val navigationBarPosition by rememberPreference(navigationBarPositionKey, NavigationBarPosition.Bottom)
    val favorites by Database.favoriteRadios().collectAsState(initial = emptyList())
    val nowPlayingId = rememberNowPlayingMediaId()

    Column(
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header", contentType = "header") {
                LibraryTitleBlock(
                    title = stringResource(R.string.favorite_radios),
                    subtitle = libraryCountText(R.plurals.mymusic_count_radios, favorites.size)
                )
            }

            if (favorites.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    LibraryEmptyState(
                        iconId = R.drawable.radio,
                        title = stringResource(R.string.mymusic_empty_radios_title),
                        message = stringResource(R.string.mymusic_empty_radios_hint)
                    )
                }
            }

            itemsIndexed(
                favorites,
                key = { _, song -> song.id },
                contentType = { _, _ -> "radio" }
            ) { index, song ->
                RadioFavoriteRow(
                    song = song,
                    isPlaying = nowPlayingId == song.id,
                    onClick = {
                        binder?.let { service ->
                            service.stopRadio()
                            service.player.forcePlayAtIndex(favorites.map { it.asMediaItem }, index)
                        }
                    }
                )
            }

            item(key = "footer", contentType = "footer") {
                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
            }
        }
    }
}
