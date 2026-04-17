package com.redsalud.seggpsnebul

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.screens.WorkerHome
import com.redsalud.seggpsnebul.screens.admin.AdminScreen
import com.redsalud.seggpsnebul.screens.auth.AuthScreen

private sealed interface Dest {
    data object Auth : Dest
    data class WorkerHome(val user: User) : Dest
    data object AdminHome : Dest
}

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var dest by remember { mutableStateOf<Dest>(Dest.Auth) }
            when (val d = dest) {
                is Dest.Auth -> AuthScreen(
                    onWorkerLoggedIn = { user ->
                        AppContainer.currentUser.value = user
                        dest = Dest.WorkerHome(user)
                    },
                    onAdminLoggedIn = { dest = Dest.AdminHome }
                )
                is Dest.WorkerHome -> WorkerHome(
                    user = d.user,
                    onLogout = {
                        AppContainer.currentUser.value = null
                        dest = Dest.Auth
                    }
                )
                is Dest.AdminHome -> AdminScreen(
                    onLogout = { dest = Dest.Auth }
                )
            }
        }
    }
}
