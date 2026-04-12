package it.fast4x.riplay.extensions.yammboapi

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber

object YammboApiService {

    private const val BASE_URL = "https://music.yammbo.com/api/v1/auth"

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> = runCatching {
        client.post("$BASE_URL/login") {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json")
            setBody(LoginRequest(email = email, password = password))
        }.body<AuthResponse>()
    }.onFailure {
        Timber.e("YammboApi login error: ${it.message}")
    }

    suspend fun register(
        email: String,
        password: String,
        passwordConfirmation: String
    ): Result<RegisterResponse> = runCatching {
        client.post("$BASE_URL/register") {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json")
            setBody(
                RegisterRequest(
                    email = email,
                    password = password,
                    passwordConfirmation = passwordConfirmation
                )
            )
        }.body<RegisterResponse>()
    }.onFailure {
        Timber.e("YammboApi register error: ${it.message}")
    }

    suspend fun forgotPassword(email: String): Result<ForgotPasswordResponse> = runCatching {
        client.post("$BASE_URL/password/email") {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json")
            setBody(ForgotPasswordRequest(email = email))
        }.body<ForgotPasswordResponse>()
    }.onFailure {
        Timber.e("YammboApi forgotPassword error: ${it.message}")
    }

    suspend fun checkSubscription(userId: Int): Result<SubscriptionStatusResponse> = runCatching {
        client.get("https://music.yammbo.com/app-music/subscription-status") {
            header("Accept", "application/json")
            url { parameters.append("user_id", userId.toString()) }
        }.body<SubscriptionStatusResponse>()
    }.onFailure {
        Timber.e("YammboApi checkSubscription error: ${it.message}")
    }

    suspend fun logout(token: String): Result<AuthResponse> = runCatching {
        client.get("$BASE_URL/logout") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/json")
        }.body<AuthResponse>()
    }.onFailure {
        Timber.e("YammboApi logout error: ${it.message}")
    }
}
