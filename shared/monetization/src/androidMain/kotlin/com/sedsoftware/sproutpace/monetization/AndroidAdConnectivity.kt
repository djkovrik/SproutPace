package com.sedsoftware.sproutpace.monetization

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observe the default network only; captive portals and unknown state fail closed. */
internal class AndroidAdConnectivity(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mutableAvailable = MutableStateFlow(false)
    val available = mutableAvailable.asStateFlow()
    private var registered = false
    private var defaultNetwork: Network? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNetwork = network
            mutableAvailable.value = false
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (network == defaultNetwork) {
                mutableAvailable.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }

        override fun onLost(network: Network) {
            if (network == defaultNetwork) {
                defaultNetwork = null
                mutableAvailable.value = false
            }
        }

        override fun onUnavailable() {
            mutableAvailable.value = false
        }
    }

    init {
        try {
            manager?.registerDefaultNetworkCallback(callback)
            registered = manager != null
        } catch (_: SecurityException) {
            mutableAvailable.value = false
        }
    }

    fun close() {
        if (registered) manager?.unregisterNetworkCallback(callback)
        registered = false
        mutableAvailable.value = false
    }
}
