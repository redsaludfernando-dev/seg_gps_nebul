package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// El geovisor admin es exclusivo del panel web.
actual @Composable fun MapaTab(vm: AdminViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Geovisor disponible en el panel web.")
    }
}
