package it.fast4x.riplay.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.yammboapi.YammboApiService
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    navController: NavController
) {
    val coroutineScope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val colors = colorPalette()
    val typo = typography()

    val fillAllFieldsText = stringResource(R.string.auth_fill_all_fields)
    val passwordsNotMatchText = stringResource(R.string.auth_passwords_not_match)
    val passwordMinLengthText = stringResource(R.string.auth_password_min_length)
    val accountCreatedVerifyText = stringResource(R.string.auth_account_created_verify)
    val accountCreatedSuccessText = stringResource(R.string.auth_account_created_success)
    val registrationFailedText = stringResource(R.string.auth_registration_failed)

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.textDisabled,
        cursorColor = colors.accent,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.textSecondary,
        focusedTextColor = colors.text,
        unfocusedTextColor = colors.text
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background0)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Image(
            painter = painterResource(
                id = if (colors.isDark) R.drawable.yambo_logo_light else R.drawable.yambo_logo_dark
            ),
            contentDescription = "Yammbo Music",
            modifier = Modifier.size(160.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        BasicText(
            text = stringResource(R.string.auth_create_account),
            style = typo.l.copy(color = colors.text, textAlign = TextAlign.Center)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.auth_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text(stringResource(R.string.auth_confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            BasicText(
                text = msg,
                style = typo.xxs.copy(color = colors.red, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth()
            )
        }

        successMessage?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            BasicText(
                text = msg,
                style = typo.xxs.copy(color = colors.accent, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                when {
                    email.isBlank() || password.isBlank() || confirmPassword.isBlank() -> {
                        errorMessage = fillAllFieldsText
                        return@Button
                    }
                    password != confirmPassword -> {
                        errorMessage = passwordsNotMatchText
                        return@Button
                    }
                    password.length < 6 -> {
                        errorMessage = passwordMinLengthText
                        return@Button
                    }
                }
                isLoading = true
                errorMessage = null
                successMessage = null
                coroutineScope.launch {
                    YammboApiService.register(email.trim(), password, confirmPassword)
                        .onSuccess { response ->
                            if (response.isSuccess) {
                                successMessage = if (response.status == "needs_email_verification")
                                    accountCreatedVerifyText
                                else
                                    accountCreatedSuccessText
                                errorMessage = null
                            } else {
                                errorMessage = response.firstError ?: registrationFailedText
                                successMessage = null
                            }
                        }
                        .onFailure { e ->
                            errorMessage = e.message ?: registrationFailedText
                        }
                    isLoading = false
                }
            },
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = colors.onAccent,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.auth_register))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        BasicText(
            text = stringResource(R.string.auth_back_to_login),
            style = typo.xxs.copy(color = colors.accent, textAlign = TextAlign.Center),
            modifier = Modifier
                .clickable { navController.popBackStack() }
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(60.dp))
    }
}
