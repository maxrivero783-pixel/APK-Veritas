package com.veritas.ai

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.veritas.ai.auth.AuthManager

class RouteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = AuthManager.getInstance(this)
        val deepLink = intent.getStringExtra("deep_link")

        if (!auth.isLoggedIn) {
            // Pass deep link through to login, which will forward after auth
            startActivity(Intent(this, LoginActivity::class.java).apply {
                deepLink?.let { putExtra("deep_link", it) }
            })
        } else {
            // Navigate to main with optional deep link payload
            startActivity(Intent(this, MainActivity::class.java).apply {
                deepLink?.let { putExtra("deep_link", it) }
            })
        }
        finish()
    }
}