package com.veritas.ai.deep

import android.net.Uri
import android.util.Log

/**
 * Routes deep links (veritas:// and https://veritas-ai.pages.dev) to
 * in-app actions. The routing result is consumed by MainActivity to
 * navigate the WebView or trigger native actions.
 *
 * Supported deep link patterns:
 *   veritas://new-chat          -> start new conversation
 *   veritas://settings           -> open settings
 *   veritas://tool/<name>        -> activate specific tool
 *   veritas://chat/<id>          -> open specific chat
 *   https://veritas-ai.pages.dev/... - pass through to WebView
 */
object DeepLinkRouter {

    private const val TAG = "VeritasDeepLink"
    private const val SCHEME = "veritas"
    private const val WEB_HOST = "veritas-ai.pages.dev"

    sealed class Route {
        data class OpenChat(val chatId: String) : Route()
        data class ActivateTool(val toolName: String) : Route()
        object NewChat : Route()
        object Settings : Route()
        data class WebUrl(val url: String) : Route()
        object Unknown : Route()
    }

    fun parse(uri: Uri?): Route {
        if (uri == null) return Route.Unknown

        val url = uri.toString()
        Log.d(TAG, "Parsing deep link: $url")

        // Custom scheme: veritas://
        if (uri.scheme == SCHEME) {
            return when (uri.host) {
                "new-chat", "newchat" -> Route.NewChat
                "settings", "config" -> Route.Settings
                "tool" -> Route.ActivateTool(uri.lastPathSegment ?: "")
                "chat" -> Route.OpenChat(uri.lastPathSegment ?: "")
                else -> Route.Unknown
            }
        }

        // HTTPS: https://veritas-ai.pages.dev/*
        if (uri.scheme == "https" && uri.host == WEB_HOST) {
            // Pass the full URL to WebView
            return Route.WebUrl(url)
        }

        return Route.Unknown
    }

    /**
     * Converts a Route to a JavaScript snippet that the WebView executes
     * to perform the corresponding action in the web app.
     */
    fun toJavaScript(route: Route): String? {
        return when (route) {
            is Route.NewChat -> {
                """
                (function() {
                    // Try to click the new chat button if it exists
                    var btn = document.querySelector('[data-action="new-chat"]');
                    if (!btn) btn = document.querySelector('button');
                    if (btn) btn.click();
                    // Fallback: navigate to root
                    if (window.location.pathname !== '/') window.location.href = '/';
                })();
                """.trimIndent()
            }
            is Route.OpenChat -> {
                """
                (function() {
                    window.location.href = '/#chat-${route.chatId}';
                })();
                """.trimIndent()
            }
            is Route.ActivateTool -> {
                """
                (function() {
                    var el = document.querySelector('[data-tool="${route.toolName}"]');
                    if (el) el.click();
                })();
                """.trimIndent()
            }
            is Route.WebUrl -> {
                """
                (function() {
                    window.location.href = '${route.url}';
                })();
                """.trimIndent()
            }
            else -> null
        }
    }
}