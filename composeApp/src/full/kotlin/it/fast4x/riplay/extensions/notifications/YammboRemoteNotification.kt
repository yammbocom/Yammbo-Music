package it.fast4x.riplay.extensions.notifications

import android.content.Context
import com.yambo.music.BuildConfig
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.themed.NotificationPopupData

/** Full flavor: fetches the in-app notification popup config from Firebase Remote Config. */
fun refreshYammboRemoteNotification(
    context: Context,
    authManager: YammboAuthManager,
    onResult: (NotificationPopupData?) -> Unit
) {
    try {
        val remoteConfig = com.google.firebase.remoteconfig.FirebaseRemoteConfig.getInstance()
        remoteConfig.setDefaultsAsync(mapOf(
            "notification_active" to false,
            "notification_id" to "",
            "notification_title" to "",
            "notification_message" to "",
            "notification_video_url" to "",
            "notification_button_text" to "",
            "notification_button_url" to "",
            "notification_min_version" to ""
        ))
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val isActive = remoteConfig.getBoolean("notification_active")
                val notifId = remoteConfig.getString("notification_id")
                val title = remoteConfig.getString("notification_title")
                val message = remoteConfig.getString("notification_message")
                val minVersion = remoteConfig.getString("notification_min_version")
                if (isActive && notifId.isNotEmpty() && title.isNotEmpty()) {
                    val dismissed = authManager.getDismissedNotificationIds()
                    if (notifId !in dismissed) {
                        val appVersion = BuildConfig.VERSION_NAME
                        val showForVersion = minVersion.isEmpty() ||
                            compareVersions(appVersion, minVersion) < 0
                        if (showForVersion) {
                            onResult(
                                NotificationPopupData(
                                    id = notifId,
                                    title = title,
                                    message = message,
                                    videoUrl = remoteConfig.getString("notification_video_url").ifEmpty { null },
                                    buttonText = remoteConfig.getString("notification_button_text").ifEmpty { null },
                                    buttonUrl = remoteConfig.getString("notification_button_url").ifEmpty { null }
                                )
                            )
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        timber.log.Timber.e("Remote Config error: ${e.message}")
    }
}

private fun compareVersions(v1: String, v2: String): Int {
    val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1.compareTo(p2)
    }
    return 0
}
