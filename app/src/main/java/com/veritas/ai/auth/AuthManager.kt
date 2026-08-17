package com.veritas.ai.auth

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages authentication state for Veritas AI Android.
 *
 * Persistence strategy (web <-> Android):
 * - Both platforms call the SAME backend endpoints (/api/auth/login, /api/auth/register)
 * - Each gets its own session token (7 days, stored in D1 sessions table)
 * - Both tokens map to the SAME user_email in D1 -> same chats, memories, skills, settings
 * - Android encrypts the token with AES-256-GCM via Android Keystore before storing
 * - On WebView load, the token is injected into localStorage so web JS works unchanged
 *
 * Result: log in on Android with the same email/password as on web,
 * and both sessions see the same data. No shared token needed.
 */
class AuthManager private constructor(context: Context) {

    companion object {
        private const val TAG = "VeritasAuth"
        private const val BASE_URL = "https://veritas-ai.pages.dev"
        private const val KEYSTORE_ALIAS = "veritas_session_key"
        private const val PREFS_FILE = "veritas_auth"
        private const val KEY_TOKEN_ENC = "token_enc"
        private const val KEY_TOKEN_IV = "token_iv"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_EXPIRES_AT = "expires_at"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // --- Public state ---

    val isLoggedIn: Boolean get() = getToken() != null

    val currentUserEmail: String?
        get() {
            if (!isLoggedIn) return null
            return prefs.getString(KEY_USER_EMAIL, null)
        }

    fun getToken(): String? {
        val enc = prefs.getString(KEY_TOKEN_ENC, null) ?: return null
        val ivB64 = prefs.getString(KEY_TOKEN_IV, null) ?: return null
        val expires = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (expires <= System.currentTimeMillis()) {
            clearSession()
            return null
        }
        return try {
            decrypt(enc, ivB64)
        } catch (e: Exception) {
            Log.w(TAG, "Token decryption failed, clearing", e)
            clearSession()
            null
        }
    }

    // --- API calls ---

    suspend fun login(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        authRequest("/api/auth/login", email, password)
    }

    suspend fun register(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        authRequest("/api/auth/register", email, password)
    }

    private suspend fun authRequest(endpoint: String, email: String, password: String): Result<String> {
        return try {
            val json = JSONObject().apply {
                put("email", email.trim().lowercase())
                put("password", password)
            }
            val resp = httpPost(endpoint, json)
            val body = resp.second

            if (!body.optBoolean("ok", false)) {
                val msg = when (body.optString("error", "")) {
                    "email_taken" -> "Ese email ya esta registrado"
                    "weak_password" -> "Contrasena: minimo 8 caracteres"
                    "invalid_email" -> "Email invalido"
                    "invalid_credentials" -> "Email o contrasena incorrectos"
                    "registration_disabled" -> "Registro deshabilitado"
                    else -> body.optString("message", "Error de autenticacion")
                }
                return Result.failure(AuthException(msg))
            }

            val token = body.getString("token")
            val expiresAt = body.getLong("expires_at")
            val userEmail = body.getString("user")
            saveSession(token, userEmail, expiresAt)
            Log.d(TAG, "Auth OK: $userEmail via $endpoint")
            Result.success(userEmail)
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Auth error", e)
            Result.failure(AuthException("Error de conexion: ${e.localizedMessage}"))
        }
    }

    suspend fun validateSession(): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        try {
            val (code, _) = httpGet("/api/auth/me")
            if (code != 200) {
                clearSession()
                false
            } else true
        } catch (_: Exception) { false }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try { httpPost("/api/auth/logout", JSONObject()) } catch (_: Exception) {}
        clearSession()
        Log.d(TAG, "Session cleared")
    }

    /**
     * Change password via the backend. On success, the current session remains valid.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("current_password", currentPassword)
                put("new_password", newPassword)
            }
            val (code, body) = httpPost("/api/auth/change-password", json)
            if (code in 200..299 && body.optBoolean("ok", true)) {
                Log.d(TAG, "Password changed for ${currentUserEmail}")
                Result.success(Unit)
            } else {
                val msg = body.optString("error", body.optString("message", "No se pudo cambiar la contrasena"))
                Result.failure(AuthException(msg))
            }
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Change password error", e)
            Result.failure(AuthException("Error de conexion: ${e.localizedMessage}"))
        }
    }

    // --- Session storage ---

    private fun saveSession(token: String, email: String, expiresAt: Long) {
        val (enc, iv) = encrypt(token)
        prefs.edit()
            .putString(KEY_TOKEN_ENC, enc)
            .putString(KEY_TOKEN_IV, iv)
            .putString(KEY_USER_EMAIL, email)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    // --- HTTP ---

    private fun httpGet(path: String): Pair<Int, JSONObject> {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return parseResponse(conn)
    }

    private fun httpPost(path: String, body: JSONObject): Pair<Int, JSONObject> {
        val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        return parseResponse(conn)
    }

    private fun parseResponse(conn: HttpURLConnection): Pair<Int, JSONObject> {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        conn.disconnect()
        return code to JSONObject(text.ifBlank { "{}" })
    }

    // --- AES-256-GCM via Android Keystore ---

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (ks.containsAlias(KEYSTORE_ALIAS)) {
            val entry = ks.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
         .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
         .setKeySize(256)
         .build())
        return kg.generateKey()
    }

    private fun encrypt(plaintext: String): Pair<String, String> {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    private fun decrypt(encB64: String, ivB64: String): String {
        val key = getOrCreateKey()
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val enc = Base64.decode(encB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(enc), Charsets.UTF_8)
    }

    class AuthException(message: String) : Exception(message)
}
