package it.fast4x.riplay.utils

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.environment.models.NavigationEndpoint
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.service.PlayerService

/**
 * Personal and auto-generated mixes (RDAMVM, RDAMPL, RDEM, RDTMAK...) have no browsable page:
 * browsing "VLRD..." answers 200 with no contents, so a playlist screen opened on them has
 * nothing to show. Curated mixes (RDCLAK...) are real playlists and do browse fine.
 */
fun isPersonalMix(key: String): Boolean {
    val id = key.removePrefix("VL")
    return id.startsWith("RD") && !id.startsWith("RDCLAK")
}

/**
 * Plays a mix as a radio, or opens the playlist screen for anything else.
 * [key] may come with or without the "VL" browse prefix.
 */
@OptIn(UnstableApi::class)
fun openPlaylistOrMix(
    navController: NavController,
    binder: PlayerService.Binder?,
    key: String,
) {
    if (isPersonalMix(key)) {
        binder?.stopRadio()
        binder?.playRadio(NavigationEndpoint.Endpoint.Watch(playlistId = key.removePrefix("VL")))
    } else {
        navController.navigate("${NavRoutes.playlist.name}/$key")
    }
}
