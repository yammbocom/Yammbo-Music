package it.fast4x.riplay.extensions.yammboapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    @SerialName("token_name") val tokenName: String = "yambo_music_android"
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

// Login response: {"status":"success","user":{"id":...,"email":...,"access_token":"..."},...}
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

// Register response may wrap in bootstrapData: {"status":"success","bootstrapData":{"user":{...}}}
// bootstrapData can be either a JSON object or a stringified JSON string
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
                // Try as stringified JSON first
                val str = raw.jsonPrimitive.content
                json.decodeFromString<BootstrapData>(str)
            } catch (_: Exception) {
                // Fall back to direct object
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

// Forgot password response: {"status":"We have emailed your password reset link."} or error
// Laravel may return success text in either `status` or `message` field
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
