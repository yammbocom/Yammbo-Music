package it.fast4x.riplay.ui.screens.auth

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.rememberNoirLayout
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.yammboapi.AuthResponse
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.themed.OnboardingConnect
import it.fast4x.riplay.ui.styling.mono
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 60

const val VERIFY_EMAIL_ROUTE =
    "verifyEmail?email={email}&newAccount={newAccount}"

// newAccount: arriving straight from sign-up, so the server has just mailed a code.
// From a login of an unverified account nothing was mailed, the old code may have
// expired, and resending is offered right away.
fun verifyEmailRoute(email: String, newAccount: Boolean) =
    "verifyEmail?email=${Uri.encode(email)}&newAccount=$newAccount"

// Shared by every flow that ends with a token (verify-code, password reset by code):
// stores the user, refreshes the subscription and lands on Home.
internal suspend fun completeAuthSignIn(
    response: AuthResponse,
    authManager: YammboAuthManager,
    navController: NavController,
    newAccount: Boolean
) {
    authManager.saveUser(response)
    if (newAccount) OnboardingConnect.markNewAccount()
    val userId = authManager.getUserId()
    if (userId > 0) {
        YammboApiService.checkSubscription(userId).onSuccess { subResponse ->
            authManager.saveSubscriptionStatus(subResponse)
        }
    }
    navController.navigate(NavRoutes.home.name) {
        popUpTo(NavRoutes.login.name) { inclusive = true }
    }
}

@Composable
fun VerifyEmailScreen(
    navController: NavController,
    authManager: YammboAuthManager,
    email: String,
    newAccount: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    // Not saveable: a request in flight does not survive process death, and a restored
    // `true` would lock the screen.
    var isVerifying by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var infoMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var resendCooldown by rememberSaveable {
        mutableIntStateOf(if (newAccount) RESEND_COOLDOWN_SECONDS else 0)
    }
    var codeFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val colors = colorPalette()
    val typo = typography()

    val codeIncompleteText = stringResource(R.string.auth_verify_code_incomplete)
    val verifyFailedText = stringResource(R.string.auth_verify_failed)
    val resendSentText = stringResource(R.string.auth_resend_code_sent)
    val resendFailedText = stringResource(R.string.auth_resend_code_failed)
    val tooManyAttemptsText = stringResource(R.string.auth_too_many_attempts)
    val sentToText = stringResource(R.string.auth_verify_sent_to)

    // Our own limits answer in Spanish; Laravel's per-IP throttle answers a bare
    // English "Too Many Attempts." that should not reach the screen as is.
    fun readable(message: String?): String? =
        if (message?.startsWith("Too Many Attempts") == true) tooManyAttemptsText else message

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
        }
    }

    // The code is the only thing to do here: open the keyboard straight away.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    fun verify() {
        if (isVerifying) return
        if (code.length != CODE_LENGTH) {
            errorMessage = codeIncompleteText
            return
        }
        isVerifying = true
        errorMessage = null
        infoMessage = null
        coroutineScope.launch {
            YammboApiService.verifyEmailCode(email, code)
                .onSuccess { response ->
                    if (response.isSuccess) {
                        completeAuthSignIn(response, authManager, navController, newAccount)
                    } else {
                        errorMessage = readable(response.firstError) ?: verifyFailedText
                    }
                }
                .onFailure { e ->
                    errorMessage = e.message ?: verifyFailedText
                }
            isVerifying = false
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

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.text),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_auth_mail),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.background0),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        AuthHeadline(text = stringResource(R.string.auth_verify_email_title))

        Spacer(modifier = Modifier.height(8.dp))

        if (newAccount) {
            BasicText(
                text = buildAnnotatedString {
                    append(sentToText)
                    append("\n")
                    withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.SemiBold)) {
                        append(email)
                    }
                },
                style = typo.xs.copy(
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            )
        } else {
            AuthSubtitle(text = stringResource(R.string.auth_verify_email_pending, email))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // A single field takes the typing and the paste; the six boxes only draw its value.
        // The digit filter and the auto-verify on the sixth digit are unchanged.
        BasicTextField(
            // Caret pinned to the end: the field is invisible, so a tap on a middle box must not
            // insert a digit in the middle of the code.
            value = TextFieldValue(code, TextRange(code.length)),
            onValueChange = { field ->
                val input = field.text
                // Keeps only the digits, so pasting the whole subject line
                // ("123456 es tu código...") still yields the code.
                val digits = input.filter { it.isDigit() }.take(CODE_LENGTH)
                if (digits != code) {
                    code = digits
                    errorMessage = null
                    if (digits.length == CODE_LENGTH) verify()
                }
            },
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { verify() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { codeFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                // The real field sits invisibly over the boxes so a tap anywhere focuses it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(CODE_LENGTH) { index ->
                        val active = codeFocused &&
                            index == code.length.coerceAtMost(CODE_LENGTH - 1)
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(shape)
                                .background(if (active) colors.background2 else colors.background1)
                                .border(
                                    if (active) 2.dp else 1.dp,
                                    if (active) colors.text else authHairline(),
                                    shape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val digit = code.getOrNull(index)
                            if (digit != null) {
                                BasicText(
                                    text = digit.toString(),
                                    style = typo.l.mono.copy(
                                        color = colors.text,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            } else if (active) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(30.dp)
                                        .background(colors.text)
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.matchParentSize().alpha(0f)) { innerTextField() }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val canResend = resendCooldown == 0 && !isResending
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (newAccount) {
                BasicText(
                    text = stringResource(R.string.auth_verify_expires),
                    style = typo.xxs.copy(color = colors.textSecondary, fontSize = 13.sp)
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            BasicText(
                text = if (resendCooldown > 0)
                    stringResource(
                        R.string.auth_resend_code_in,
                        "%d:%02d".format(resendCooldown / 60, resendCooldown % 60)
                    )
                else stringResource(R.string.auth_resend_code),
                style = typo.xxs.mono.copy(
                    color = if (canResend) colors.text else colors.textSecondary,
                    fontSize = 13.sp,
                    letterSpacing = 0.sp
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canResend) {
                        isResending = true
                        errorMessage = null
                        infoMessage = null
                        coroutineScope.launch {
                            YammboApiService.resendEmailCode(email)
                                .onSuccess { response ->
                                    if (response.isSuccess) {
                                        code = ""
                                        infoMessage = response.message ?: resendSentText
                                        resendCooldown = RESEND_COOLDOWN_SECONDS
                                    } else {
                                        errorMessage = readable(response.firstError) ?: resendFailedText
                                    }
                                }
                                .onFailure { e ->
                                    errorMessage = e.message ?: resendFailedText
                                }
                            isResending = false
                        }
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            AuthMessage(text = msg)
        }

        infoMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            AuthMessage(text = msg, isError = false)
        }

        Spacer(modifier = Modifier.height(8.dp))

        BasicText(
            text = stringResource(R.string.auth_verify_spam_hint),
            style = typo.xxs.copy(color = colors.textSecondary, fontSize = 13.sp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.background1)
                .border(1.dp, authHairline(), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        AuthPrimaryButton(
            text = stringResource(R.string.auth_verify_button),
            onClick = { verify() },
            enabled = !isVerifying,
            loading = isVerifying
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}
