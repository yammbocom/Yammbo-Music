package it.fast4x.riplay.extensions.notifications

import android.content.Context
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.themed.NotificationPopupData

/** FOSS flavor: no Firebase / remote config, so there is never a remote notification. */
fun refreshYammboRemoteNotification(
    context: Context,
    authManager: YammboAuthManager,
    onResult: (NotificationPopupData?) -> Unit
) {
    // Intentionally empty: the FOSS build has no proprietary remote-config backend.
}
