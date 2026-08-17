package com.veritas.ai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.veritas.ai.auth.AuthManager
import com.veritas.ai.notifications.NotificationPollWorker
import com.veritas.ai.ui.theme.*
import kotlinx.coroutines.launch
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(NotificationPollWorker.isNotificationsEnabled(context)) }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onConfirm = { current, newPass ->
                (context as? ComponentActivity)?.lifecycleScope?.launch {
                    auth.changePassword(current, newPass)
                        .onSuccess { Toast.makeText(context, "Contrasena actualizada", Toast.LENGTH_SHORT).show() }
                        .onFailure { e -> Toast.makeText(context, e.message ?: "Error", Toast.LENGTH_SHORT).show() }
                }
                showChangePassword = false
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Cerrar sesion") },
            text = { Text("Se cerrara tu sesion en este dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    (context as? ComponentActivity)?.lifecycleScope?.launch {
                        NotificationPollWorker.cancel(context)
                        NotificationPollWorker.unregisterDevice(context)
                        auth.logout()
                        context.startActivity(Intent(context, LoginActivity::class.java))
                        (context as? ComponentActivity)?.finishAffinity()
                    }
                }) { Text("Cerrar sesion", color = VeritasError) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Limpiar cache") },
            text = { Text("Se borraran cookies, cache y datos de sitios. Tu sesion se mantendra.") },
            confirmButton = {
                TextButton(onClick = {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    Toast.makeText(context, "Cache limpiado", Toast.LENGTH_SHORT).show()
                    showClearCacheConfirm = false
                }) { Text("Limpiar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VeritasDarkBg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = VeritasAccent)
            }
            Spacer(Modifier.width(12.dp))
            Text("Ajustes", style = MaterialTheme.typography.titleLarge)
        }

        HorizontalDivider(color = VeritasSurfaceLight, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Profile section
            SectionLabel("PERFIL")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VeritasSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Email", color = VeritasTextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        auth.currentUserEmail ?: "No autenticado",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Security
            SectionLabel("SEGURIDAD")
            SettingsRow(
                icon = Icons.Default.Lock,
                title = "Cambiar contrasena",
                subtitle = "Actualizar tu contrasena de acceso"
            ) { showChangePassword = true }

            Spacer(Modifier.height(24.dp))

            // Data
            SectionLabel("DATOS")
            SettingsRow(
                icon = Icons.Default.DeleteSweep,
                title = "Limpiar cache",
                subtitle = "Borrar datos de navegacion y cache"
            ) { showClearCacheConfirm = true }

            Spacer(Modifier.height(24.dp))

            // Notifications
            SectionLabel("NOTIFICACIONES")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VeritasSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            notificationsEnabled = !notificationsEnabled
                            NotificationPollWorker.setNotificationsEnabled(context, notificationsEnabled)
                            Toast
                                .makeText(
                                    context,
                                    if (notificationsEnabled) "Notificaciones activadas" else "Notificaciones desactivadas",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notificaciones push", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Polling cada 15 min via Cloudflare Workers",
                            color = VeritasTextMuted, fontSize = 12.sp
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { checked ->
                            notificationsEnabled = checked
                            NotificationPollWorker.setNotificationsEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = VeritasAccent)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // About
            SectionLabel("ACERCA DE")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VeritasSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AboutRow("Version", "3.0.0 (build 5)")
                    AboutRow("Plataforma", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    AboutRow("Paquete", "com.veritas.ai")
                    AboutRow("Backend", "Cloudflare Workers + D1")
                }
            }

            Spacer(Modifier.height(32.dp))

            // Logout
            TextButton(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesion", color = VeritasError, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
 Text(
        text,
        color = VeritasAccent,
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        letterSpacing = 0.1.sp
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VeritasSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = VeritasTextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, color = VeritasTextMuted, fontSize = 12.sp)
            }
            Text(">", color = VeritasBorder, fontSize = 18.sp)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = VeritasTextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = VeritasTextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contrasena") },
        text = {
            Column {
                OutlinedTextField(
                    value = current, onValueChange = { current = it },
                    label = { Text("Contrasena actual") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newPass, onValueChange = { newPass = it },
                    label = { Text("Nueva (min 8)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current, newPass) }) {
                Text("Actualizar", color = VeritasAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = VeritasAccent,
    unfocusedBorderColor = VeritasBorder,
    cursorColor = VeritasAccent,
    focusedTextColor = VeritasTextPrimary,
    unfocusedTextColor = VeritasTextPrimary
)

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeritasTheme {
                SettingsScreen(onBack = { onBackPressed() })
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
    }
}
