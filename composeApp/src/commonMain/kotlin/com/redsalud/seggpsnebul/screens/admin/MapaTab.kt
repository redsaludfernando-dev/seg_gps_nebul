package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.runtime.Composable

/** onBack: volver al tab anterior (gestiona AdminScreen). */
expect @Composable fun MapaTab(vm: AdminViewModel, onBack: () -> Unit)
