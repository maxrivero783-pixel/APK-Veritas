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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.ai.auth.AuthManager
import com.veritas.ai.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SplashScreenContent(deepLink: String?) {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }

    // Staggered animation state
    var showLogo by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100); showLogo = true
        delay(400); showTitle = true
        delay(200); showSubtitle = true
    }

    // Navigate after splash delay
    LaunchedEffect(Unit) {
        delay(1800)
        val target = when {
            deepLink != null -> Intent(context, RouteActivity::class.java).apply {
                putExtra("deep_link", deepLink)
            }
            auth.isLoggedIn -> Intent(context, MainActivity::class.java)
            else -> Intent(context, LoginActivity::class.java)
        }
        context.startActivity(target)
        (context as? ComponentActivity)?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VeritasDarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(VeritasAccent)
                    .then(
                        Modifier.scale(
                            animateFloatAsState(
                                if (showLogo) 1f else 0.6f,
                                tween(600, easing = FastOutSlowInEasing), label = "ls"
                            ).value
                        )
                    )
                    .alpha(
                        animateFloatAsState(
                            if (showLogo) 1f else 0f,
                            tween(600, easing = FastOutSlowInEasing), label = "la"
                        ).value
                    )
            )

            Spacer(Modifier.height(20.dp))

            // Title
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }
            ) {
                Text(
                    "VERITAS",
                    color = VeritasAccent,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            // Subtitle
            AnimatedVisibility(
                visible = showSubtitle,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }
            ) {
                Text(
                    "AI  ·  OSINT  ·  Analisis",
                    color = VeritasTextMuted,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        val deepLink = intent.getStringExtra("deep_link")
        setContent {
            VeritasTheme {
                SplashScreenContent(deepLink = deepLink)
            }
        }
    }

    override fun onBackPressed() {}
}