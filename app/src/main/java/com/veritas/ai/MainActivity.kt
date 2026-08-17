package com.veritas.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.*
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.veritas.ai.auth.AuthManager
import com.veritas.ai.camera.CameraHelper
import com.veritas.ai.deep.DeepLinkRouter
import com.veritas.ai.notifications.NotificationHelper
import com.veritas.ai.notifications.NotificationPollWorker
import com.veritas.ai.offline.OfflineManager
import com.veritas.ai.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Veritas AI v3.0 - Main WebView with Compose overlays.
 *
 * Architecture:
 * - WebView + SwipeRefreshLayout as base (AndroidView)
 * - Offline banner and Camera FAB as Compose overlays
 * - No Google/FCM dependency — pure Cloudflare polling for notifications
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val VERITAS_URL = "https://veritas-ai.pages.dev"
        private const val VERITAS_ORIGIN = "https://veritas-ai.pages.dev"
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
    }

    private lateinit var auth: AuthManager
    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var offlineManager: OfflineManager
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    // Activity result launchers
    private lateinit var cameraLauncher: ActivityResultLauncher<Unit>
    private lateinit var galleryLauncher: ActivityResultLauncher<Unit>

    // Pending actions to execute after page load
    private var pendingJs: String? = null
    private var pendingDeepLink: String? = null

    // Compose state for overlays
    private var isOffline by mutableStateOf(false)

    // Runtime permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result handled by system; channels already created */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = AuthManager.getInstance(this)

        // Auth gate: redirect to login if no valid session
        if (!auth.isLoggedIn) {
            redirectToLogin()
            return
        }

        // Validate session server-side in background
        lifecycleScope.launch {
            val valid = auth.validateSession()
            if (!valid) {
                auth.clearSession()
                redirectToLogin()
            }
        }

        // Register activity result launchers
        cameraLauncher = registerForActivityResult(CameraHelper.Capture()) { uri ->
            uri?.let { injectImageToChat(it) }
        }
        galleryLauncher = registerForActivityResult(CameraHelper.PickImage()) { uri ->
            uri?.let { injectImageToChat(it) }
        }

        // Setup offline manager
        offlineManager = OfflineManager(this)
        lifecycle.addObserver(offlineManager)
        offlineManager.onConnectivityChanged = { online ->
            runOnUiThread { isOffline = !online }
        }

        // Ensure notification channels exist and polling is running
        NotificationHelper.createNotificationChannels(this)
        lifecycleScope.launch { NotificationPollWorker.schedule(this@MainActivity) }

        // Request POST_NOTIFICATIONS permission on Android 13+ (required for showing notifications)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Fullscreen immersive
        setupImmersiveMode()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Build SwipeRefreshLayout + WebView
        swipeRefreshLayout = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(AndroidColor.parseColor("#50C878"))
            setOnRefreshListener {
                if (offlineManager.isOnline) webView.reload() else isRefreshing = false
            }
        }

        webView = WebView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }

        configureWebView(webView)
        swipeRefreshLayout.addView(webView)

        // Handle incoming extras
        handleIntentExtras(intent)

        // Compose root: WebView as base, Compose overlays on top
        setContent {
            VeritasTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // WebView base layer
                    AndroidView(
                        factory = { swipeRefreshLayout },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Offline banner overlay (Compose)
                    if (isOffline) {
                        OfflineBannerComposable()
                    }

                    // Camera FAB overlay (Compose)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 72.dp)
                    ) {
                        CameraFabComposable(onClick = { showCameraOptions() })
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl(VERITAS_URL)
        }
    }

    // --- Compose Overlays ---

    @Composable
    private fun OfflineBannerComposable() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VeritasSurfaceLight)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "!",
                color = VeritasDarkBg,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(VeritasWarning)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Sin conexion a Internet",
                color = VeritasWarning,
                fontSize = 13.sp
            )
        }
    }

    @Composable
    private fun CameraFabComposable(onClick: () -> Unit) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .shadow(8.dp, CircleShape)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(VeritasAccent)
                    .clickable(onClick = onClick)
            ) {
                Box(
                    modifier = Modifier.padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Camara",
                        tint = VeritasAccentDark,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    private fun showCameraOptions() {
        val options = arrayOf("Tomar foto", "Galeria", "Subir archivo")
        android.app.AlertDialog.Builder(this)
            .setTitle("Agregar al analisis")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(Unit)
                    1 -> galleryLauncher.launch(Unit)
                    2 -> openFileChooserFromFileFab()
                }
            }
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openFileChooserFromFileFab() {
        uploadMessage = object : ValueCallback<Array<Uri>> {
            override fun onReceiveValue(value: Array<Uri>?) {
                value?.firstOrNull()?.let { injectImageToChat(it) }
            }
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
    }

    private fun injectImageToChat(uri: Uri) {
        CameraHelper.uriToDataUrl(this, uri) { dataUrl ->
            if (dataUrl != null) {
                val js = """
                    (function() {
                        var input = document.querySelector('input[type="file"], textarea, [contenteditable]');
                        if (!input) input = document.querySelector('#chat-input, .chat-input, [data-role="input"]');
                        if (input) {
                            window.dispatchEvent(new CustomEvent('veritas:image-captured', {
                                detail: { dataUrl: '$dataUrl' }
                            }));
                        }
                        console.log('[Veritas Android] Imagen capturada: ${uri.lastPathSegment}');
                    })();
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            }
        }
    }

    // --- Intent extras handling ---

    private fun handleIntentExtras(intent: Intent) {
        val deepLink = intent.getStringExtra("deep_link")
        if (deepLink != null) {
            val route = DeepLinkRouter.parse(Uri.parse(deepLink))
            if (route is DeepLinkRouter.Route.Settings) {
                startActivity(Intent(this, SettingsActivity::class.java))
                return
            }
            val js = DeepLinkRouter.toJavaScript(route)
            if (js != null) pendingJs = js
            return
        }

        val sharedText = intent.getStringExtra("shared_text")
        if (!sharedText.isNullOrBlank()) {
            val safeText = sharedText.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
            pendingJs = """
                (function() {
                    var input = document.querySelector('textarea, [contenteditable]');
                    if (!input) input = document.querySelector('#chat-input, .chat-input, [data-role="input"]');
                    if (input && typeof input.value !== 'undefined') {
                        input.value = '$safeText';
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                    } else if (input) {
                        input.textContent = '$safeText';
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                    console.log('[Veritas Android] Texto compartido inyectado');
                })();
            """.trimIndent()
        }
    }

    // --- Navigation ---

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun buildTokenInjectionScript(): String {
        val token = auth.getToken() ?: return ""
        val email = auth.currentUserEmail ?: return ""
        val safeToken = token.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "")
        val safeEmail = email.replace("\\", "\\\\").replace("'", "\\'")
        return """
            (function() {
                try {
                    localStorage.setItem('veritas_token', '${safeToken}');
                    localStorage.setItem('veritas_user', '${safeEmail}');
                    console.log('[Veritas Android] Token inyectado para: ${safeEmail}');
                } catch(e) {
                    console.error('[Veritas Android] Error inyectando token:', e);
                }
            })();
        """.trimIndent()
    }

    private fun buildBridgeScript(): String {
        return """
            (function() {
                window.__veritasAndroid = true;
                document.documentElement.style.setProperty('--safe-area-top', '0px');
                document.documentElement.style.setProperty('--safe-area-bottom', '0px');

                const _origFetch = window.fetch;
                window.fetch = function(url, options) {
                    options = options || {};
                    if (url && String(url).includes('/api/')) {
                        options.headers = options.headers || {};
                        if (options.headers instanceof Headers) {
                            if (!options.headers.has('Authorization')) {
                                options.headers.set('Authorization', 'Bearer ' + localStorage.getItem('veritas_token'));
                            }
                        } else {
                            options.headers = Object.assign({}, options.headers);
                            if (!options.headers['Authorization']) {
                                options.headers['Authorization'] = 'Bearer ' + localStorage.getItem('veritas_token');
                            }
                        }
                    }
                    return _origFetch.call(this, url, options);
                };

                var _origRemoveItem = localStorage.removeItem;
                localStorage.removeItem = function(key) {
                    _origRemoveItem.call(this, key);
                    if (key === 'veritas_token' || key === 'veritas_session') {
                        console.log('[Veritas Android] Web logout detected');
                        try { AndroidBridge.onWebLogout(); } catch(e) {}
                    }
                };

                console.log('[Veritas Android] Bridge + fetch interceptor listo');
            })();
        """.trimIndent()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView) {
        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.userAgentString = settings.userAgentString + " VeritasAI/3.0.0-Android"
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)

        CookieManager.getInstance().apply {
            setAcceptThirdPartyCookies(wv, true)
            setAcceptCookie(true)
        }

        wv.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun openSettings() {
                runOnUiThread { this@MainActivity.openSettings() }
            }

            @JavascriptInterface
            fun openCamera() {
                runOnUiThread { cameraLauncher.launch(Unit) }
            }

            @JavascriptInterface
            fun onWebLogout() {
                runOnUiThread {
                    lifecycleScope.launch {
                        NotificationPollWorker.cancel(this@MainActivity)
                        NotificationPollWorker.unregisterDevice(this@MainActivity)
                        auth.clearSession()
                        redirectToLogin()
                    }
                }
            }
        }, "AndroidBridge")

        wv.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith(VERITAS_ORIGIN)) return false
                if (url.contains("oauth") || url.contains("callback")
                    || url.startsWith("https://github.com/login/oauth")) {
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url == VERITAS_URL || url == "$VERITAS_URL/") {
                    view.evaluateJavascript(buildTokenInjectionScript(), null)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                swipeRefreshLayout.isRefreshing = false

                if (url == VERITAS_URL || url == "$VERITAS_URL/") {
                    view.evaluateJavascript(buildTokenInjectionScript(), null)
                    view.evaluateJavascript(buildBridgeScript(), null)

                    pendingJs?.let {
                        view.postDelayed({ view.evaluateJavascript(it, null) }, 500)
                        pendingJs = null
                    }

                    pendingDeepLink?.let {
                        val route = DeepLinkRouter.parse(Uri.parse(it))
                        DeepLinkRouter.toJavaScript(route)?.let { js ->
                            view.postDelayed({ view.evaluateJavascript(js, null) }, 500)
                        }
                        pendingDeepLink = null
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }

            override fun onReceivedSslError(
                view: WebView?, handler: SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.cancel()
            }
        }

        wv.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback

                val intent = fileChooserParams.createIntent().apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }

                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                } catch (e: Exception) {
                    uploadMessage = null
                    return false
                }
                return true
            }

            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                message?.let {
                    android.util.Log.d("VeritasWebView", "${it.messageLevel()}: ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                }
                return true
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Use Activity Result API where possible")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            uploadMessage?.onReceiveValue(results ?: arrayOf())
            uploadMessage = null
        }
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
        if (!auth.isLoggedIn) {
            redirectToLogin()
        }
        isOffline = !offlineManager.isOnline
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}