package it.fast4x.riplay.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    navController: NavController,
    authManager: YammboAuthManager
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val colors = colorPalette()
    val typo = typography()

    val fillAllFieldsText = stringResource(R.string.auth_fill_all_fields)
    val loginFailedText = stringResource(R.string.auth_login_failed)
    val noAccountText = stringResource(R.string.auth_no_account)
    val createAccountText = stringResource(R.string.auth_create_account)

    fun submit() {
        if (email.isBlank() || password.isBlank()) {
            errorMessage = fillAllFieldsText
            return
        }
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            YammboApiService.login(email.trim(), password)
                .onSuccess { response ->
                    if (response.isSuccess) {
                        authManager.saveUser(response)
                        // Check subscription status after login
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
                        navController.navigate(
                            verifyEmailRoute(response.email ?: email.trim(), newAccount = false)
                        )
                    } else {
                        errorMessage = response.firstError ?: loginFailedText
                    }
                }
                .onFailure { e ->
                    errorMessage = e.message ?: loginFailedText
                }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background0)
    ) {
        AuthCoverBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = rememberNoirLayout().sidePadding(NoirContentMax.Form, 24.dp))
        ) {
            Spacer(modifier = Modifier.height(190.dp))

            AuthLogo(modifier = Modifier.align(Alignment.Start))

            Spacer(modifier = Modifier.height(18.dp))

            AuthHeadline(text = stringResource(R.string.auth_login_headline))

            Spacer(modifier = Modifier.height(6.dp))

            AuthSubtitle(text = stringResource(R.string.auth_login_subtitle))

            Spacer(modifier = Modifier.height(18.dp))

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
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            BasicText(
                text = stringResource(R.string.auth_forgot_password),
                style = typo.xxs.copy(
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End
                ),
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable {
                        navController.navigate(forgotPasswordRoute(email.trim()))
                    }
                    .padding(vertical = 10.dp, horizontal = 2.dp)
            )

            errorMessage?.let { msg ->
                AuthMessage(text = msg, modifier = Modifier.padding(bottom = 14.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            AuthPrimaryButton(
                text = stringResource(R.string.auth_login),
                onClick = { submit() },
                enabled = !isLoading,
                loading = isLoading
            )

            Spacer(modifier = Modifier.height(18.dp))

            BasicText(
                text = buildAnnotatedString {
                    append(noAccountText)
                    append(" ")
                    withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) {
                        append(createAccountText)
                    }
                },
                style = typo.xxs.copy(
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(NavRoutes.register.name)
                    }
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
