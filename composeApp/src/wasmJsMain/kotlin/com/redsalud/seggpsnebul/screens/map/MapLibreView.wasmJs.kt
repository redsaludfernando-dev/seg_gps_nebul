package com.redsalud.seggpsnebul.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

actual @Composable fun MapLibreView(
    modifier: Modifier,
    pmtilesPath: String?,
    userPositions: List<UserPosition>,
    myPosition: UserPosition?
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Mapa disponible solo en aplicación Android.")
    }
}
