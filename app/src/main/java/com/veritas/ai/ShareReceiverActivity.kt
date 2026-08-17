package com.veritas.ai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.veritas.ai.auth.AuthManager

/**
 * Receives shared content (text, URLs) from other apps.
 * Passes the content to MainActivity which injects it into the WebView chat.
 *
 * Supported intent types: text/plain, text/html
 * The shared text is passed via "shared_text" extra to MainActivity.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = AuthManager.getInstance(this)

        // Extract shared text
        val sharedText = when {
            intent.action == Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.action == Intent.ACTION_PROCESS_TEXT -> intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            else -> null
        }

        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, "No hay contenido para compartir", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Route to appropriate activity
        val target = if (auth.isLoggedIn) MainActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, target).apply {
            putExtra("shared_text", sharedText)
            // Clear the task so back press goes to home, not here
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }
}