package com.redsalud.seggpsnebul.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.redsalud.seggpsnebul.data.remote.ZonaDto

actual @Composable fun MapLibreView(
    modifier: Modifier,
    pmtilesPath: String?,
    userPositions: List<UserPosition>,
    myPosition: UserPosition?,
    zonas: List<ZonaDto>
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Mapa disponible solo en aplicación Android.")
    }
}
