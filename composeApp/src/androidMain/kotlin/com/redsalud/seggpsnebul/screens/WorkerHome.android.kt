package com.redsalud.seggpsnebul.screens

import androidx.compose.runtime.Composable
import com.redsalud.seggpsnebul.domain.model.User

actual @Composable fun WorkerHome(user: User, onLogout: () -> Unit) {
    HomeScreen(user = user, onLogout = onLogout)
}
