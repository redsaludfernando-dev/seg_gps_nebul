package com.redsalud.seggpsnebul

import com.redsalud.seggpsnebul.connectivity.ConnectivityObserver
import com.redsalud.seggpsnebul.connectivity.createConnectivityObserver
import com.redsalud.seggpsnebul.data.remote.AuthResult
import com.redsalud.seggpsnebul.data.remote.RealtimeRepository
import com.redsalud.seggpsnebul.data.remote.supabaseClient
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

actual object AppContainer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    actual val currentUser = MutableStateFlow<User?>(null)
    actual val currentSessionId = MutableStateFlow<String?>(null)
    actual val connectivityObserver: ConnectivityObserver = createConnectivityObserver()
    actual val realtimeRepository = RealtimeRepository()

    fun init() {
        connectivityObserver.start()
        scope.launch {
            connectivityObserver.isOnline.collect { online ->
                if (online) realtimeRepository.connect()
            }
        }
    }

    actual suspend fun loginAdmin(email: String, password: String): AuthResult = try {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthResult.AdminSuccess(email)
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Error de autenticación")
    }

    actual suspend fun loginWorker(dni: String, pin: String): AuthResult =
        AuthResult.Error("Login de trabajador no disponible en versión web")

    actual suspend fun registerWorker(dni: String, fullName: String, role: UserRole, pin: String): AuthResult =
        AuthResult.Error("Registro de trabajador no disponible en versión web")

    actual suspend fun tryRestoreSession(): RestoreResult {
        // Supabase Auth restaura el JWT desde localStorage automaticamente al
        // crearse el cliente. Esperamos a que termine la carga inicial — si
        // existe una sesion valida, la consideramos sesion de admin (web no
        // soporta login worker).
        return runCatching {
            supabaseClient.auth.awaitInitialization()
            if (supabaseClient.auth.currentSessionOrNull() != null) RestoreResult.Admin
            else RestoreResult.None
        }.getOrDefault(RestoreResult.None)
    }

    actual suspend fun signOutAdmin() {
        runCatching { supabaseClient.auth.signOut() }
    }

    actual suspend fun signOutWorker() {
        // No-op: web no tiene sesion worker.
    }
}
