package it.fast4x.riplay.enums

import com.yambo.music.R
import it.fast4x.riplay.utils.appContext

enum class PlaylistSwipeAction {
    NoAction,
    PlayNext,
    Favourite,
    Enqueue;

    val displayName: String
        get() = when (this) {
            NoAction -> appContext().resources.getString(R.string.none)
            PlayNext -> appContext().resources.getString(R.string.play_next)
            Favourite -> appContext().resources.getString(R.string.favorites)
            Enqueue  -> appContext().resources.getString(R.string.enqueue)
        }

    val icon: Int?
        get() = when (this) {
            NoAction -> null
            PlayNext -> R.drawable.play_skip_forward
            Favourite -> R.drawable.heart_outline
            Enqueue -> R.drawable.enqueue
        }
}
