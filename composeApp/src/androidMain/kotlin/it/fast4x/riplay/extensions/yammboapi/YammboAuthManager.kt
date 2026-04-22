package it.fast4x.riplay.extensions.yammboapi

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class YammboAuthManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "yambo_music_prefs"
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

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUser(response: AuthResponse) {
        val user = response.user
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, user?.accessToken)
            putInt(KEY_USER_ID, user?.id ?: 0)
            putString(KEY_USER_EMAIL, user?.email.orEmpty())
            putString(KEY_USER_NAME, user?.name.orEmpty())
            putString(KEY_USER_AVATAR, user?.avatarUrl ?: user?.image.orEmpty())
            putBoolean(KEY_IS_LOGGED_IN, true)
        }
    }

    fun saveUser(response: RegisterResponse) {
        val user = response.resolvedUser
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, user?.accessToken)
            putInt(KEY_USER_ID, user?.id ?: 0)
            putString(KEY_USER_EMAIL, user?.email.orEmpty())
            putString(KEY_USER_NAME, user?.name.orEmpty())
            putString(KEY_USER_AVATAR, user?.avatarUrl ?: user?.image.orEmpty())
            putBoolean(KEY_IS_LOGGED_IN, true)
        }
    }

    fun saveTvLinkUser(token: String, user: TvLinkUser?) {
        prefs.edit {
            putString(KEY_ACCESS_TOKEN, token)
            putInt(KEY_USER_ID, user?.id ?: 0)
            putString(KEY_USER_EMAIL, user?.email.orEmpty())
            val displayName = listOfNotNull(user?.firstName, user?.lastName)
                .joinToString(" ")
                .trim()
                .ifEmpty { user?.email.orEmpty() }
            putString(KEY_USER_NAME, displayName)
            putString(KEY_USER_AVATAR, "")
            putBoolean(KEY_IS_LOGGED_IN, true)
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        && !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrEmpty()

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "").orEmpty()

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "").orEmpty()

    fun getUserAvatar(): String = prefs.getString(KEY_USER_AVATAR, "").orEmpty()

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)

    fun isSubscriptionActive(): Boolean = prefs.getBoolean(KEY_SUBSCRIPTION_ACTIVE, false)

    fun getSubscriptionPlan(): String = prefs.getString(KEY_SUBSCRIPTION_PLAN, "").orEmpty()

    fun saveSubscriptionStatus(response: SubscriptionStatusResponse) {
        prefs.edit {
            putBoolean(KEY_SUBSCRIPTION_ACTIVE, response.subscribed)
            putString(KEY_SUBSCRIPTION_PLAN, response.plan.orEmpty())
            putString(KEY_SUBSCRIPTION_RENEWS_AT, response.renewsAt.orEmpty())
        }
    }

    fun getDismissedNotificationIds(): Set<String> {
        val raw = prefs.getString(KEY_DISMISSED_NOTIFICATION_IDS, "") ?: ""
        return raw.split(",").filter { it.isNotEmpty() }.toSet()
    }

    fun dismissNotification(id: String) {
        val current = getDismissedNotificationIds()
        val updated = current + id
        prefs.edit {
            putString(KEY_DISMISSED_NOTIFICATION_IDS, updated.joinToString(","))
        }
    }

    fun logout() {
        prefs.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            remove(KEY_USER_AVATAR)
            putBoolean(KEY_IS_LOGGED_IN, false)
            putBoolean(KEY_SUBSCRIPTION_ACTIVE, false)
            remove(KEY_SUBSCRIPTION_PLAN)
            remove(KEY_SUBSCRIPTION_RENEWS_AT)
        }
    }
}
