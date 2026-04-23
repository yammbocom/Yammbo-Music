package it.fast4x.riplay.extensions.yammboapi

import java.util.prefs.Preferences

// Desktop counterpart of androidMain/YammboAuthManager.kt.
// Same public API so the shared UI/auth code can call it identically.
// Storage: java.util.prefs.Preferences (per-user, OS-native: Registry on Windows,
// plist on macOS, ~/.java/.userPrefs on Linux). This is plaintext — the Sanctum
// access_token is not OS-encrypted here. Upgrade path: DPAPI/Keychain/libsecret
// wrappers in a later phase if we handle anything more sensitive than a revocable
// session token.
class YammboAuthManager {

    companion object {
        // Preferences node name. Mirrors the Android SharedPreferences file name
        // so log lines and docs stay consistent across platforms.
        private const val NODE = "com/yambo/music/prefs"

        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_SUBSCRIPTION_ACTIVE = "subscription_active"
        private const val KEY_SUBSCRIPTION_PLAN = "subscription_plan"
        private const val KEY_SUBSCRIPTION_RENEWS_AT = "subscription_renews_at"
        private const val KEY_DISMISSED_NOTIFICATION_IDS = "dismissed_notification_ids"
    }

    private val prefs: Preferences = Preferences.userRoot().node(NODE)

    private fun flush() {
        try { prefs.flush() } catch (_: Exception) { /* best-effort */ }
    }

    fun saveUser(response: AuthResponse) {
        val user = response.user
        prefs.put(KEY_ACCESS_TOKEN, user?.accessToken.orEmpty())
        prefs.putInt(KEY_USER_ID, user?.id ?: 0)
        prefs.put(KEY_USER_EMAIL, user?.email.orEmpty())
        prefs.put(KEY_USER_NAME, user?.name.orEmpty())
        prefs.put(KEY_USER_AVATAR, user?.avatarUrl ?: user?.image.orEmpty())
        prefs.putBoolean(KEY_IS_LOGGED_IN, true)
        flush()
    }

    fun saveUser(response: RegisterResponse) {
        val user = response.resolvedUser
        prefs.put(KEY_ACCESS_TOKEN, user?.accessToken.orEmpty())
        prefs.putInt(KEY_USER_ID, user?.id ?: 0)
        prefs.put(KEY_USER_EMAIL, user?.email.orEmpty())
        prefs.put(KEY_USER_NAME, user?.name.orEmpty())
        prefs.put(KEY_USER_AVATAR, user?.avatarUrl ?: user?.image.orEmpty())
        prefs.putBoolean(KEY_IS_LOGGED_IN, true)
        flush()
    }

    fun saveTvLinkUser(token: String, user: TvLinkUser?) {
        prefs.put(KEY_ACCESS_TOKEN, token)
        prefs.putInt(KEY_USER_ID, user?.id ?: 0)
        prefs.put(KEY_USER_EMAIL, user?.email.orEmpty())
        val displayName = listOfNotNull(user?.firstName, user?.lastName)
            .joinToString(" ")
            .trim()
            .ifEmpty { user?.email.orEmpty() }
        prefs.put(KEY_USER_NAME, displayName)
        prefs.put(KEY_USER_AVATAR, "")
        prefs.putBoolean(KEY_IS_LOGGED_IN, true)
        flush()
    }

    fun getAccessToken(): String? =
        prefs.get(KEY_ACCESS_TOKEN, null).takeIf { !it.isNullOrEmpty() }

    fun isLoggedIn(): Boolean =
        prefs.getBoolean(KEY_IS_LOGGED_IN, false) && !getAccessToken().isNullOrEmpty()

    fun getUserEmail(): String = prefs.get(KEY_USER_EMAIL, "") ?: ""

    fun getUserName(): String = prefs.get(KEY_USER_NAME, "") ?: ""

    fun getUserAvatar(): String = prefs.get(KEY_USER_AVATAR, "") ?: ""

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)

    fun isSubscriptionActive(): Boolean = prefs.getBoolean(KEY_SUBSCRIPTION_ACTIVE, false)

    fun getSubscriptionPlan(): String = prefs.get(KEY_SUBSCRIPTION_PLAN, "") ?: ""

    fun saveSubscriptionStatus(response: SubscriptionStatusResponse) {
        prefs.putBoolean(KEY_SUBSCRIPTION_ACTIVE, response.subscribed)
        prefs.put(KEY_SUBSCRIPTION_PLAN, response.plan.orEmpty())
        prefs.put(KEY_SUBSCRIPTION_RENEWS_AT, response.renewsAt.orEmpty())
        flush()
    }

    fun getDismissedNotificationIds(): Set<String> {
        val raw = prefs.get(KEY_DISMISSED_NOTIFICATION_IDS, "") ?: ""
        return raw.split(",").filter { it.isNotEmpty() }.toSet()
    }

    fun dismissNotification(id: String) {
        val current = getDismissedNotificationIds()
        val updated = current + id
        prefs.put(KEY_DISMISSED_NOTIFICATION_IDS, updated.joinToString(","))
        flush()
    }

    fun logout() {
        prefs.remove(KEY_ACCESS_TOKEN)
        prefs.remove(KEY_USER_ID)
        prefs.remove(KEY_USER_EMAIL)
        prefs.remove(KEY_USER_NAME)
        prefs.remove(KEY_USER_AVATAR)
        prefs.putBoolean(KEY_IS_LOGGED_IN, false)
        prefs.putBoolean(KEY_SUBSCRIPTION_ACTIVE, false)
        prefs.remove(KEY_SUBSCRIPTION_PLAN)
        prefs.remove(KEY_SUBSCRIPTION_RENEWS_AT)
        flush()
    }
}
