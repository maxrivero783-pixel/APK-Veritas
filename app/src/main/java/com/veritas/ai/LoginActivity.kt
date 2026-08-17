package com.veritas.ai

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.veritas.ai.auth.AuthManager
import com.veritas.ai.notifications.NotificationHelper
import com.veritas.ai.notifications.NotificationPollWorker
import com.veritas.ai.ui.theme.*
import kotlinx.coroutines.launch

@Composable
private fun LoginScreen() {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showLogo = true
    }

    // Auto-navigate if already logged in
    LaunchedEffect(auth.isLoggedIn) {
        if (auth.isLoggedIn) {
            context.startActivity(Intent(context, MainActivity::class.java))
            (context as? ComponentActivity)?.finish()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VeritasDarkBg)
            .padding(horizontal = 32.dp, vertical = 60.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Logo
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(VeritasAccent)
                .alpha(
                    animateFloatAsState(
                        if (showLogo) 1f else 0f,
                        tween(400), label = "logo"
                    ).value
                )
        )

        Spacer(Modifier.height(12.dp))

        // Title
        Text(
            "VERITAS",
            color = VeritasAccent,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(4.dp))

        // Subtitle
        Text(
            "IA con herramientas, memoria, criterio y trazabilidad",
            color = VeritasTextMuted,
            fontSize = 12.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Email field
        Text("Email", color = VeritasTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            placeholder = { Text("tu@email.com", color = VeritasTextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VeritasAccent,
                unfocusedBorderColor = VeritasBorder,
                cursorColor = VeritasAccent,
                focusedTextColor = VeritasTextPrimary,
                unfocusedTextColor = VeritasTextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Password field
        Text("Contrasena", color = VeritasTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            placeholder = { Text("Minimo 8 caracteres", color = VeritasTextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VeritasAccent,
                unfocusedBorderColor = VeritasBorder,
                cursorColor = VeritasAccent,
                focusedTextColor = VeritasTextPrimary,
                unfocusedTextColor = VeritasTextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // Error message
        errorMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(
                msg,
                color = VeritasError,
                fontSize = 13.sp
            )
        }

        // Loading indicator
        if (isLoading) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(color = VeritasAccent, modifier = Modifier.size(40.dp))
        }

        Spacer(Modifier.height(20.dp))

        // Submit button
        Button(
            onClick = {
                when {
                    email.isBlank() || !email.contains("@") -> errorMessage = "Email invalido"
                    password.length < 8 -> errorMessage = "Minimo 8 caracteres"
                    else -> {
                        isLoading = true
                        (context as? ComponentActivity)?.lifecycleScope?.launch {
                            val result = if (isRegisterMode) auth.register(email, password)
                                           else auth.login(email, password)
                            isLoading = false
                            result.onSuccess {
                                // Create notification channel + register device + start polling
                                NotificationHelper.createNotificationChannels(context)
                                (context as? ComponentActivity)?.lifecycleScope?.launch {
                                    NotificationPollWorker.registerDevice(context)
                                    NotificationPollWorker.schedule(context)
                                }
                                context.startActivity(Intent(context, MainActivity::class.java))
                                (context as? ComponentActivity)?.finish()
                            }.onFailure { e ->
                                errorMessage = e.message ?: "Error desconocido"
                                password = ""
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = VeritasAccent,
                contentColor = VeritasAccentDark,
                disabledContainerColor = VeritasAccent.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isLoading) "Conectando..."
                else if (isRegisterMode) "Crear cuenta"
                else "Iniciar sesion",
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))

        // Toggle mode
        TextButton(
            onClick = { isRegisterMode = !isRegisterMode; errorMessage = null }
        ) {
            Text(
                if (isRegisterMode) "Ya tienes cuenta? Inicia sesion"
                else "No tienes cuenta? Registrate",
                color = VeritasAccent
            )
        }

        Spacer(Modifier.weight(1f))

        // Version
        Text(
            "v3.0.0 — Android",
            color = VeritasTextMuted.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        setContent {
            VeritasTheme {
                LoginScreen()
            }
        }
    }
}