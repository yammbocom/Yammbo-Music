package it.fast4x.riplay.enums

import androidx.compose.runtime.Composable
import it.fast4x.riplay.extensions.preferences.navigationBarPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.utils.isTVDevice

enum class NavigationBarPosition {
    Left,
    Right,
    Top,
    Bottom;

    companion object {

        @Composable
        fun current(): NavigationBarPosition {
            // On Android TV, force sidebar nav (Left) regardless of stored preference
            if (isTVDevice()) return Left
            return rememberPreference(navigationBarPositionKey, Bottom).value
        }
    }

    @Composable
    fun isCurrent(): Boolean = current() == this
}