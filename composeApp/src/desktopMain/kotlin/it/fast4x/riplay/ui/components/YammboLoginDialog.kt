package it.fast4x.riplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.fast4x.riplay.extensions.yammboapi.YammboSession
import java.awt.Desktop
import java.net.URI

// Lightweight login/register modal for desktop. Not themed to the full app
// design system yet (Fase 4 handles rebranding). Dark background matches the
// existing desktop color scheme in main.kt.
@Composable
fun YammboLoginDialog(onDismiss: () -> Unit) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    val status by YammboSession.authStatus.collectAsState()
    val state by YammboSession.state.collectAsState()

    // Auto-close on successful login.
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) {
            YammboSession.resetAuthStatus()
            onDismiss()
        }
    }

    val accent = Color(0xFFFF6B6B)
    val bg = Color(0xFF121212)
    val fieldBg = Color(0xFF1E1E1E)
    val textColor = Color.White
    val muted = Color(0xFFB0B0B0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .background(bg, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRegisterMode) "Crear cuenta Yammbo" else "Iniciar sesión",
                    color = textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "music.yammbo.com",
                    color = muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(24.dp))

                YammboField(
                    value = email,
                    onChange = { email = it },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    fieldBg = fieldBg,
                    accent = accent,
                    textColor = textColor,
                    muted = muted
                )
                Spacer(Modifier.height(12.dp))
                YammboField(
                    value = password,
                    onChange = { password = it },
                    label = "Contraseña",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    fieldBg = fieldBg,
                    accent = accent,
                    textColor = textColor,
                    muted = muted
                )
                if (isRegisterMode) {
                    Spacer(Modifier.height(12.dp))
                    YammboField(
                        value = confirmation,
                        onChange = { confirmation = it },
                        label = "Confirmar contraseña",
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        fieldBg = fieldBg,
                        accent = accent,
                        textColor = textColor,
                        muted = muted
                    )
                }

                val errorText = (status as? YammboSession.AuthStatus.Error)?.message
                if (errorText != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorText,
                        color = Color(0xFFFF8A8A),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (isRegisterMode) {
                            YammboSession.register(email.trim(), password, confirmation)
                        } else {
                            YammboSession.login(email.trim(), password)
                        }
                    },
                    enabled = status !is YammboSession.AuthStatus.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White,
                        disabledContainerColor = accent.copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (status is YammboSession.AuthStatus.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = if (isRegisterMode) "Crear cuenta" else "Entrar",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isRegisterMode) "¿Ya tienes cuenta?" else "¿Sin cuenta?",
                        color = muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isRegisterMode) "Inicia sesión" else "Regístrate",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            isRegisterMode = !isRegisterMode
                            YammboSession.resetAuthStatus()
                        }
                    )
                }

                Spacer(Modifier.height(6.dp))

                TextButton(
                    onClick = {
                        runCatching {
                            Desktop.getDesktop()
                                .browse(URI("https://music.yammbo.com/forgot-password"))
                        }
                    }
                ) {
                    Text(
                        text = "¿Olvidaste tu contraseña?",
                        color = muted,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(2.dp))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cerrar",
                        color = muted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun YammboField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    fieldBg: Color,
    accent: Color,
    textColor: Color,
    muted: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = muted, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fieldBg,
            unfocusedContainerColor = fieldBg,
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            cursorColor = accent
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
