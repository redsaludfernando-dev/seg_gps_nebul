package com.redsalud.seggpsnebul.connectivity

import kotlinx.coroutines.flow.StateFlow

interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()
}

expect fun createConnectivityObserver(): ConnectivityObserver
