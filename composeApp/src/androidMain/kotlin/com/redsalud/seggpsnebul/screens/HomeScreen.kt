package com.redsalud.seggpsnebul.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import com.redsalud.seggpsnebul.screens.role.*

@Composable
fun HomeScreen(user: User?, onLogout: () -> Unit) {
    if (user == null) return

    val vm = remember(user.id) { RoleViewModel(user) }
    DisposableEffect(user.id) { onDispose { vm.dispose() } }

    when (user.role) {
        UserRole.NEBULIZADOR  -> NebulizadorScreen(vm = vm, onLogout = onLogout)
        UserRole.ANOTADOR     -> AnotadorScreen(vm = vm, onLogout = onLogout)
        UserRole.JEFE_BRIGADA -> JefeScreen(vm = vm, onLogout = onLogout)
        UserRole.CHOFER       -> ChoferScreen(vm = vm, onLogout = onLogout)
    }
}
