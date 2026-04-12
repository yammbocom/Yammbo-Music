package it.fast4x.riplay.enums

import androidx.compose.runtime.Composable

enum class LinkType {
    Yammbo,
    Main,
    Alternative;

    val textName: String
        @Composable
        get() = when( this ) {
            Yammbo -> "Yammbo Music"
            Main -> "YouTube"
            Alternative -> "YouTube Music"
        }
}