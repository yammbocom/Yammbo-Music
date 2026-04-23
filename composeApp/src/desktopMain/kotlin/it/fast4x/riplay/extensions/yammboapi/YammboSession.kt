package it.fast4x.riplay.extensions.yammboapi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Process-wide Yammbo session for desktop.
// - Holds the logged-in user + subscription snapshot as StateFlow so the UI
//   (Compose) can collect it and re-render automatically.
// - Wraps YammboAuthManager (disk) + YammboApiService (network). All network
//   calls happen on Dispatchers.IO via the internal scope.
// - Singleton because the app only has one active user at a time and we want
//   the sidebar/account widget to stay in sync with the login dialog.
object YammboSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auth = YammboAuthManager()

    data class AccountState(
        val loggedIn: Boolean = false,
        val userId: Int = 0,
        val email: String = "",
        val name: String = "",
        val avatar: String = "",
        val subscriptionActive: Boolean = false,
        val plan: String = ""
    )

    sealed interface AuthStatus {
        object Idle : AuthStatus
        object Loading : AuthStatus
        object Success : AuthStatus
        data class Error(val message: String) : AuthStatus
    }

    private val _state = MutableStateFlow(readFromDisk())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private val _authStatus = MutableStateFlow<AuthStatus>(AuthStatus.Idle)
    val authStatus: StateFlow<AuthStatus> = _authStatus.asStateFlow()

    private fun readFromDisk(): AccountState = AccountState(
        loggedIn = auth.isLoggedIn(),
        userId = auth.getUserId(),
        email = auth.getUserEmail(),
        name = auth.getUserName(),
        avatar = auth.getUserAvatar(),
        subscriptionActive = auth.isSubscriptionActive(),
        plan = auth.getSubscriptionPlan()
    )

    fun refreshFromDisk() {
        _state.value = readFromDisk()
    }

    fun getAccessToken(): String? = auth.getAccessToken()

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authStatus.value = AuthStatus.Error("Email y contraseña requeridos")
            return
        }
        _authStatus.value = AuthStatus.Loading
        scope.launch {
            val result = YammboApiService.login(email, password)
            result.fold(
                onSuccess = { response ->
                    if (response.isSuccess) {
                        auth.saveUser(response)
                        refreshFromDisk()
                        _authStatus.value = AuthStatus.Success
                        // Background subscription check — UI updates via state flow
                        refreshSubscriptionStatus()
                    } else {
                        _authStatus.value = AuthStatus.Error(
                            response.firstError ?: "Credenciales inválidas"
                        )
                    }
                },
                onFailure = {
                    _authStatus.value = AuthStatus.Error(
                        "Error de red: ${it.message ?: "no se pudo conectar"}"
                    )
                }
            )
        }
    }

    fun register(email: String, password: String, confirmation: String) {
        if (email.isBlank() || password.isBlank()) {
            _authStatus.value = AuthStatus.Error("Email y contraseña requeridos")
            return
        }
        if (password != confirmation) {
            _authStatus.value = AuthStatus.Error("Las contraseñas no coinciden")
            return
        }
        _authStatus.value = AuthStatus.Loading
        scope.launch {
            val result = YammboApiService.register(email, password, confirmation)
            result.fold(
                onSuccess = { response ->
                    if (response.isSuccess) {
                        auth.saveUser(response)
                        refreshFromDisk()
                        _authStatus.value = AuthStatus.Success
                        refreshSubscriptionStatus()
                    } else {
                        _authStatus.value = AuthStatus.Error(
                            response.firstError ?: "No se pudo registrar"
                        )
                    }
                },
                onFailure = {
                    _authStatus.value = AuthStatus.Error(
                        "Error de red: ${it.message ?: "no se pudo conectar"}"
                    )
                }
            )
        }
    }

    fun refreshSubscriptionStatus() {
        val userId = auth.getUserId()
        if (userId <= 0) return
        scope.launch {
            YammboApiService.checkSubscription(userId).onSuccess { status ->
                auth.saveSubscriptionStatus(status)
                refreshFromDisk()
            }
        }
    }

    fun logout() {
        val token = auth.getAccessToken()
        if (!token.isNullOrEmpty()) {
            scope.launch {
                // Best-effort server revocation; ignore result.
                YammboApiService.logout(token)
            }
        }
        auth.logout()
        refreshFromDisk()
        _authStatus.value = AuthStatus.Idle
    }

    fun resetAuthStatus() {
        _authStatus.value = AuthStatus.Idle
    }
}
