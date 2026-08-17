package com.veritas.ai.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Monitors network connectivity and provides a callback-based API.
 * Registers a ConnectivityManager.NetworkCallback for reliable detection.
 */
class OfflineManager(private val context: Context) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "VeritasOffline"
    }

    var isOnline: Boolean = false
        private set

    var onConnectivityChanged: ((Boolean) -> Unit)? = null

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            updateState(true)
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            updateState(false)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            updateState(hasInternet)
        }
    }

    init {
        // Initial check
        isOnline = checkCurrentState()
        Log.d(TAG, "Initial state: ${if (isOnline) "online" else "offline"}")
    }

    override fun onResume(owner: LifecycleOwner) {
        registerCallback()
        // Re-check on resume
        isOnline = checkCurrentState()
        onConnectivityChanged?.invoke(isOnline)
    }

    override fun onPause(owner: LifecycleOwner) {
        try { unregisterCallback() } catch (_: Exception) {}
    }

    fun registerCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    fun unregisterCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }

    private fun checkCurrentState(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
        }
    }

    private fun updateState(online: Boolean) {
        if (isOnline != online) {
            isOnline = online
            Log.d(TAG, "Connectivity changed: ${if (online) "online" else "offline"}")
            onConnectivityChanged?.invoke(online)
        }
    }
}