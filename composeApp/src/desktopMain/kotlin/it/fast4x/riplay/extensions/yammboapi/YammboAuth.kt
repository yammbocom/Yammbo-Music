package it.fast4x.riplay.extensions.yammboapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

// Duplicate of androidMain/YammboAuth.kt — desktop target. Only difference:
// tokenName defaults to "yambo_music_desktop" so the backend can distinguish
// desktop sessions (useful for analytics, forced logout, audit trail).
// Future refactor: move to commonMain once desktop is stable (expect/actual
// is not needed here since these are pure data classes).

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("token_name") val tokenName: String = "yambo_music_desktop"
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class AuthResponse(
    val status: String? = null,
    val message: String? = null,
    val user: YammboUser? = null,
    val errors: Map<String, List<String>>? = null
) {
    val isSuccess: Boolean get() = status == "success" && user?.accessToken != null
    val firstError: String? get() =
        errors?.values?.firstOrNull()?.firstOrNull() ?: message
}

@Serializable
data class RegisterResponse(
    val status: String? = null,
    val message: String? = null,
    val user: YammboUser? = null,
    @SerialName("bootstrapData") val bootstrapDataRaw: JsonElement? = null,
    val errors: Map<String, List<String>>? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }

    val bootstrapData: BootstrapData? get() = try {
        bootstrapDataRaw?.let { raw ->
            try {
                val str = raw.jsonPrimitive.content
                json.decodeFromString<BootstrapData>(str)
            } catch (_: Exception) {
                json.decodeFromJsonElement(BootstrapData.serializer(), raw)
            }
        }
    } catch (_: Exception) { null }

    val resolvedUser: YammboUser? get() = user ?: bootstrapData?.user
    val isSuccess: Boolean get() = status == "success" || status == "needs_email_verification"
    val firstError: String? get() =
        errors?.values?.firstOrNull()?.firstOrNull() ?: message
}

@Serializable
data class BootstrapData(
    val user: YammboUser? = null
)

@Serializable
data class YammboUser(
    val id: Int? = null,
    val email: String? = null,
    val name: String? = null,
    @SerialName("avatar") val avatarUrl: String? = null,
    val image: String? = null,
    @SerialName("access_token") val accessToken: String? = null
)

@Serializable
data class SubscriptionStatusResponse(
    val subscribed: Boolean = false,
    val plan: String? = null,
    @SerialName("renews_at") val renewsAt: String? = null
)

@Serializable
data class ForgotPasswordResponse(
    val status: String? = null,
    val message: String? = null,
    val errors: Map<String, List<String>>? = null
) {
    val isSuccess: Boolean get() = errors == null && (status != null || message != null)
    val successMessage: String? get() = status ?: message
    val firstError: String? get() =
        errors?.values?.firstOrNull()?.firstOrNull() ?: if (!isSuccess) message else null
}

@Serializable
data class TvLinkGenerateResponse(
    val code: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 600,
    @SerialName("confirm_url") val confirmUrl: String? = null
)

@Serializable
data class TvLinkPollResponse(
    val status: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    val user: TvLinkUser? = null
)

@Serializable
data class TvLinkUser(
    val id: Int? = null,
    val email: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null
)

@Serializable
data class TvLinkPollRequest(val code: String)
