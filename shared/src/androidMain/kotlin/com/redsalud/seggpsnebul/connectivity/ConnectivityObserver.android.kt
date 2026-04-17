package com.redsalud.seggpsnebul.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.redsalud.seggpsnebul.location.DeviceIdProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createConnectivityObserver(): ConnectivityObserver = AndroidConnectivityObserver()

class AndroidConnectivityObserver : ConnectivityObserver {
    private val context: Context get() = DeviceIdProvider.getAppContext()
    private val cm: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    override val isOnline: StateFlow<Boolean> = _isOnline

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun checkCurrentConnectivity(): Boolean {
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _isOnline.value = true }
            override fun onLost(network: Network) { _isOnline.value = checkCurrentConnectivity() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _isOnline.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    override fun stop() {
        networkCallback?.let { cm.unregisterNetworkCallback(it) }
        networkCallback = null
    }
}
