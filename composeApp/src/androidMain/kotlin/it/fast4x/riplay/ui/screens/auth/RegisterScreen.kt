package it.fast4x.riplay.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.yammboapi.VpnState
import it.fast4x.riplay.ui.components.themed.ConfirmationDialog
import it.fast4x.riplay.ui.components.themed.OnboardingConnect
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.utils.colorPalette
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    navController: NavController,
    authManager: YammboAuthManager
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showVpnNotice by remember { mutableStateOf(false) }
    var vpnNoticeShown by remember { mutableStateOf(false) }

    val colors = colorPalette()

    val fillAllFieldsText = stringResource(R.string.auth_fill_all_fields)
    val passwordsNotMatchText = stringResource(R.string.auth_passwords_not_match)
    val passwordMinLengthText = stringResource(R.string.auth_password_min_length)
    val registrationFailedText = stringResource(R.string.auth_registration_failed)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background0)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = rememberNoirLayout().sidePadding(NoirContentMax.Form, 24.dp))
    ) {
        AuthBackButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        AuthLogo(modifier = Modifier.align(Alignment.Start))

        Spacer(modifier = Modifier.height(18.dp))

        AuthHeadline(text = stringResource(R.string.auth_create_account))

        Spacer(modifier = Modifier.height(24.dp))

        AuthField(
            label = stringResource(R.string.auth_email),
            value = email,
            onValueChange = { email = it },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        AuthField(
            label = stringResource(R.string.auth_password),
            value = password,
            onValueChange = { password = it },
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))

        AuthField(
            label = stringResource(R.string.auth_confirm_password),
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            isPassword = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            AuthMessage(text = msg)
        }

        Spacer(modifier = Modifier.height(24.dp))

        AuthPrimaryButton(
            text = stringResource(R.string.auth_register),
            onClick = {
                when {
                    email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                        errorMessage = fillAllFieldsText
                        return@AuthPrimaryButton
                    }
                    password != confirmPassword -> {
                        errorMessage = passwordsNotMatchText
                        return@AuthPrimaryButton
                    }
                    password.length < 6 -> {
                        errorMessage = passwordMinLengthText
                        return@AuthPrimaryButton
                    }
                }
                // A country's free access is decided by where the person is, and through a
                // VPN the server only sees the exit node. Said once, and only to a phone
                // whose SIM belongs to a country the offer covers.
                if (!vpnNoticeShown && VpnState.isActive() &&
                    VpnState.simCountry() in VpnState.OFFER_SIM_COUNTRIES
                ) {
                    vpnNoticeShown = true
                    showVpnNotice = true
                    return@AuthPrimaryButton
                }

                isLoading = true
                errorMessage = null
                coroutineScope.launch {
                    YammboApiService.register(email.trim(), password, confirmPassword)
                        .onSuccess { response ->
                            val accessToken = response.resolvedUser?.accessToken
                            if (response.isSuccess && !accessToken.isNullOrEmpty()) {
                                authManager.saveUser(response)
                                // Offer "Trae tu música" on Home; persisted, Home reads it from prefs.
                                OnboardingConnect.markNewAccount()
                                val userId = authManager.getUserId()
                                if (userId > 0) {
                                    YammboApiService.checkSubscription(userId).onSuccess { subResponse ->
                                        authManager.saveSubscriptionStatus(subResponse)
                                    }
                                }
                                navController.navigate(NavRoutes.home.name) {
                                    popUpTo(NavRoutes.login.name) { inclusive = true }
                                }
                            } else if (response.needsEmailVerification) {
                                // No token until the mailed code is confirmed; back from
                                // there returns to login, not to a filled-in sign-up form.
                                val verifyEmail = response.resolvedUser?.email ?: email.trim()
                                navController.navigate(verifyEmailRoute(verifyEmail, newAccount = true)) {
                                    popUpTo(NavRoutes.register.name) { inclusive = true }
                                }
                            } else {
                                errorMessage = response.firstError ?: registrationFailedText
                            }
                        }
                        .onFailure { e ->
                            errorMessage = e.message ?: registrationFailedText
                        }
                    isLoading = false
                }
            },
            enabled = !isLoading,
            loading = isLoading
        )

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showVpnNotice) {
        ConfirmationDialog(
            text = stringResource(R.string.vpn_notice_free_country),
            confirmText = stringResource(R.string.confirm),
            cancelText = stringResource(R.string.cancel),
            onDismiss = { showVpnNotice = false },
            onConfirm = { showVpnNotice = false },
        )
    }
}
