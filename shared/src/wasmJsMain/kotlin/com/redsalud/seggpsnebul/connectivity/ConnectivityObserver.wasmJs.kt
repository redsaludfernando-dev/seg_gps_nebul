package com.redsalud.seggpsnebul.connectivity

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual fun createConnectivityObserver(): ConnectivityObserver = WasmConnectivityObserver()

class WasmConnectivityObserver : ConnectivityObserver {
    private val _isOnline = MutableStateFlow(window.navigator.onLine)
    override val isOnline: StateFlow<Boolean> = _isOnline
    private var job: Job? = null

    override fun start() {
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                _isOnline.value = window.navigator.onLine
                delay(5_000L)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}
