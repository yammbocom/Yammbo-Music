package it.fast4x.riplay.ui.screens.auth

import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 60

const val FORGOT_PASSWORD_ROUTE = "forgotPassword?email={email}"

// The email typed so far on the login screen travels along so it need not be typed twice.
fun forgotPasswordRoute(email: String) =
    if (email.isBlank()) "forgotPassword" else "forgotPassword?email=${Uri.encode(email)}"

// Step 1 asks for the email and mails a code; step 2 takes the code plus the new password
// and signs the person straight in, like verify-code does.
@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    authManager: YammboAuthManager,
    initialEmail: String = ""
) {
    val coroutineScope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    // Not saveable: a request in flight does not survive process death.
    var isLoading by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var infoMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var resendCooldown by rememberSaveable { mutableIntStateOf(0) }

    val colors = colorPalette()
    val typo = typography()

    val invalidEmailText = stringResource(R.string.auth_forgot_invalid_email)
    val codeIncompleteText = stringResource(R.string.auth_forgot_code_incomplete)
    val enterPasswordText = stringResource(R.string.auth_forgot_enter_password)
    val mismatchText = stringResource(R.string.auth_forgot_passwords_mismatch)
    val networkErrorText = stringResource(R.string.auth_forgot_network_error)
    val tooManyAttemptsText = stringResource(R.string.auth_too_many_attempts)

    // Laravel's per-IP throttle answers a bare English "Too Many Attempts.".
    fun readable(message: String?): String? =
        if (message?.startsWith("Too Many Attempts") == true) tooManyAttemptsText else message

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    fun isValidEmail(value: String) = Patterns.EMAIL_ADDRESS.matcher(value).matches()

    // Mails the code. From step 1 it moves on to step 2; from step 2 it is a resend.
    fun requestCode(fromStep: Int) {
        if (isLoading || isResending) return
        val target = email.trim()
        if (!isValidEmail(target)) {
            errorMessage = invalidEmailText
            return
        }
        if (fromStep == 1) isLoading = true else isResending = true
        errorMessage = null
        infoMessage = null
        coroutineScope.launch {
            YammboApiService.requestPasswordResetCode(target)
                .onSuccess { response ->
                    if (response.isSuccess) {
                        email = target
                        code = ""
                        step = 2
                        infoMessage = response.message
                        resendCooldown = RESEND_COOLDOWN_SECONDS
                    } else {
                        errorMessage = readable(response.firstError) ?: networkErrorText
                    }
                }
                .onFailure { errorMessage = networkErrorText }
            isLoading = false
            isResending = false
        }
    }

    fun reset() {
        if (isLoading) return
        when {
            code.length != CODE_LENGTH -> { errorMessage = codeIncompleteText; return }
            password.isEmpty() -> { errorMessage = enterPasswordText; return }
            password != confirmation -> { errorMessage = mismatchText; return }
        }
        isLoading = true
        errorMessage = null
        infoMessage = null
        coroutineScope.launch {
            YammboApiService.resetPasswordWithCode(email.trim(), code, password, confirmation)
                .onSuccess { response ->
                    if (response.isSuccess) {
                        completeAuthSignIn(response, authManager, navController, newAccount = false)
                    } else {
                        errorMessage = response.errors?.get("password")?.firstOrNull()
                            ?: readable(response.firstError)
                            ?: networkErrorText
                    }
                }
                .onFailure { errorMessage = networkErrorText }
            isLoading = false
        }
    }

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

        AuthHeadline(text = stringResource(R.string.auth_forgot_title))

        Spacer(modifier = Modifier.height(6.dp))

        AuthSubtitle(
            text = if (step == 1) stringResource(R.string.auth_forgot_subtitle_email)
            else stringResource(R.string.auth_forgot_subtitle_code, email)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (step == 1) {
            AuthField(
                label = stringResource(R.string.auth_email),
                value = email,
                onValueChange = { email = it; errorMessage = null },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            AuthField(
                label = stringResource(R.string.auth_forgot_code_label),
                value = code,
                // Keeps only the digits, so pasting a whole mail line still yields the code.
                onValueChange = { input ->
                    code = input.filter { it.isDigit() }.take(CODE_LENGTH)
                    errorMessage = null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            AuthField(
                label = stringResource(R.string.auth_forgot_new_password),
                value = password,
                onValueChange = { password = it; errorMessage = null },
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            AuthField(
                label = stringResource(R.string.auth_forgot_repeat_password),
                value = confirmation,
                onValueChange = { confirmation = it; errorMessage = null },
                isPassword = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            val canResend = resendCooldown == 0 && !isResending && !isLoading
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicText(
                    text = stringResource(R.string.auth_forgot_change_email),
                    style = typo.xxs.mono.copy(
                        color = colors.text,
                        fontSize = 13.sp,
                        letterSpacing = 0.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLoading) {
                            step = 1
                            code = ""
                            password = ""
                            confirmation = ""
                            errorMessage = null
                            infoMessage = null
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                BasicText(
                    text = if (resendCooldown > 0)
                        stringResource(R.string.auth_forgot_resend_in, resendCooldown)
                    else stringResource(R.string.auth_forgot_resend_code),
                    style = typo.xxs.mono.copy(
                        color = if (canResend) colors.text else colors.textSecondary,
                        fontSize = 13.sp,
                        letterSpacing = 0.sp
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canResend) { requestCode(fromStep = 2) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            AuthMessage(text = msg)
        }

        infoMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            AuthMessage(text = msg, isError = false)
        }

        Spacer(modifier = Modifier.height(24.dp))

        AuthPrimaryButton(
            text = if (step == 1) stringResource(R.string.auth_forgot_send_code)
            else stringResource(R.string.auth_forgot_change_password),
            onClick = { if (step == 1) requestCode(fromStep = 1) else reset() },
            enabled = !isLoading,
            loading = isLoading
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}
