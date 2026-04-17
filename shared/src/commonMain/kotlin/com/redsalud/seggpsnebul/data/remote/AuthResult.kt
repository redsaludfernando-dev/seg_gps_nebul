package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.domain.model.User

sealed class AuthResult {
    data class WorkerSuccess(val user: User) : AuthResult()
    data class AdminSuccess(val email: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
