package com.redsalud.seggpsnebul.screens

import androidx.compose.runtime.Composable
import com.redsalud.seggpsnebul.domain.model.User

expect @Composable fun WorkerHome(user: User, onLogout: () -> Unit)
