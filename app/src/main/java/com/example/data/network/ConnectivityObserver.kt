package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OperatingMode(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    CONNECTING("Connecting"),
    SWITCHING("Switching Mode")
}

class ConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _mode = MutableStateFlow(checkCurrentMode())
    val mode: StateFlow<OperatingMode> = _mode.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _mode.value = OperatingMode.CONNECTING
            _mode.value = checkCurrentMode()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _mode.value = if (validated) OperatingMode.ONLINE else OperatingMode.CONNECTING
        }

        override fun onLost(network: Network) {
            _mode.value = checkCurrentMode()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            _mode.value = checkCurrentMode()
        }
    }

    fun refresh(): OperatingMode {
        val current = checkCurrentMode()
        _mode.value = current
        return current
    }

    private fun checkCurrentMode(): OperatingMode {
        val activeNetwork = connectivityManager.activeNetwork ?: return OperatingMode.OFFLINE
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return OperatingMode.OFFLINE
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            hasInternet && validated -> OperatingMode.ONLINE
            hasInternet -> OperatingMode.CONNECTING
            else -> OperatingMode.OFFLINE
        }
    }
}
