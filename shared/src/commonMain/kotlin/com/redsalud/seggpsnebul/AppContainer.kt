package com.redsalud.seggpsnebul

import com.redsalud.seggpsnebul.connectivity.ConnectivityObserver
import com.redsalud.seggpsnebul.data.remote.AuthResult
import com.redsalud.seggpsnebul.data.remote.RealtimeRepository
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Plataforma-agnostic container.
 * Android actual: incluye además localDataSource, syncManager, etc. (offline-first).
 * WasmJs actual:  solo Supabase online, sin BD local.
 */
expect object AppContainer {
    val currentUser: MutableStateFlow<User?>
    val currentSessionId: MutableStateFlow<String?>
    val connectivityObserver: ConnectivityObserver
    val realtimeRepository: RealtimeRepository

    suspend fun loginAdmin(email: String, password: String): AuthResult
    suspend fun loginWorker(dni: String, pin: String): AuthResult
    suspend fun registerWorker(dni: String, fullName: String, role: UserRole, pin: String): AuthResult
}
