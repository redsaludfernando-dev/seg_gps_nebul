package com.redsalud.seggpsnebul.screens.auth

import com.redsalud.seggpsnebul.AppContainer
import com.redsalud.seggpsnebul.data.remote.AuthResult
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

class AuthViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun loginWorker(dni: String, pin: String, onSuccess: (User) -> Unit) {
        if (dni.isBlank() || pin.isBlank()) {
            _state.value = AuthUiState(error = "Complete todos los campos")
            return
        }
        scope.launch {
            _state.value = AuthUiState(isLoading = true)
            when (val r = AppContainer.loginWorker(dni.trim(), pin.trim())) {
                is AuthResult.WorkerSuccess -> { _state.value = AuthUiState(); onSuccess(r.user) }
                is AuthResult.Error        -> _state.value = AuthUiState(error = r.message)
                is AuthResult.AdminSuccess -> Unit
            }
        }
    }

    fun loginAdmin(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthUiState(error = "Complete todos los campos")
            return
        }
        scope.launch {
            _state.value = AuthUiState(isLoading = true)
            when (val r = AppContainer.loginAdmin(email.trim(), password.trim())) {
                is AuthResult.AdminSuccess -> { _state.value = AuthUiState(); onSuccess() }
                is AuthResult.Error        -> _state.value = AuthUiState(error = r.message)
                is AuthResult.WorkerSuccess -> Unit
            }
        }
    }

    fun registerWorker(
        dni: String,
        fullName: String,
        role: UserRole,
        pin: String,
        confirmPin: String,
        onSuccess: (User) -> Unit
    ) {
        when {
            dni.isBlank() || fullName.isBlank() || pin.isBlank() ->
                _state.value = AuthUiState(error = "Complete todos los campos")
            pin.length != 4 || pin.any { !it.isDigit() } ->
                _state.value = AuthUiState(error = "El PIN debe tener exactamente 4 dígitos")
            pin != confirmPin ->
                _state.value = AuthUiState(error = "Los PINs no coinciden")
            else -> scope.launch {
                _state.value = AuthUiState(isLoading = true)
                when (val r = AppContainer.registerWorker(dni.trim(), fullName.trim(), role, pin)) {
                    is AuthResult.WorkerSuccess -> { _state.value = AuthUiState(); onSuccess(r.user) }
                    is AuthResult.Error         -> _state.value = AuthUiState(error = r.message)
                    is AuthResult.AdminSuccess  -> Unit
                }
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
