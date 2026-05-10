package com.redsalud.seggpsnebul

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.screens.WorkerHome
import com.redsalud.seggpsnebul.screens.admin.AdminScreen
import com.redsalud.seggpsnebul.screens.auth.AuthScreen
import kotlinx.coroutines.launch

private sealed interface Dest {
    data object Loading : Dest
    data object Auth : Dest
    data class WorkerHome(val user: User) : Dest
    data object AdminHome : Dest
}

@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var dest by remember { mutableStateOf<Dest>(Dest.Loading) }
            val scope = rememberCoroutineScope()

            // Restaurar sesion previa al arrancar (admin JWT o worker cacheado).
            LaunchedEffect(Unit) {
                dest = when (val r = AppContainer.tryRestoreSession()) {
                    is RestoreResult.Worker -> {
                        AppContainer.currentUser.value = r.user
                        Dest.WorkerHome(r.user)
                    }
                    RestoreResult.Admin     -> Dest.AdminHome
                    RestoreResult.None      -> Dest.Auth
                }
            }

            when (val d = dest) {
                is Dest.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
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
                        scope.launch {
                            AppContainer.signOutWorker()
                            dest = Dest.Auth
                        }
                    }
                )
                is Dest.AdminHome -> AdminScreen(
                    onLogout = {
                        scope.launch {
                            AppContainer.signOutAdmin()
                            dest = Dest.Auth
                        }
                    }
                )
            }
        }
    }
}
