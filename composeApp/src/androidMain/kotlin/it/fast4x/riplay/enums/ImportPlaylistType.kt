package it.fast4x.riplay.enums

import androidx.compose.runtime.Composable
import com.yambo.music.R
import it.fast4x.riplay.utils.GenericMenuItem

enum class ImportPlaylistType {
    Riplay,
    ExportifyNet,
    SpotifyLink;

    val titleId: Int
        get() = when(this) {
            Riplay -> R.string.import_playlist_riplay
            ExportifyNet -> R.string.import_playlist_exportify_net
            SpotifyLink -> R.string.spotify_import_menu
        }

    val iconId: Int
        get() = when(this) {
            Riplay -> R.drawable.app_logo
            ExportifyNet -> R.drawable.resource_import
            SpotifyLink -> R.drawable.link
        }

    val menuItem: GenericMenuItem
        @Composable
        get() = GenericMenuItem( this.ordinal, titleId, iconId )

}
