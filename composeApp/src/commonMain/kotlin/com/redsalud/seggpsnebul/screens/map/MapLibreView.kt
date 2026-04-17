package com.redsalud.seggpsnebul.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.redsalud.seggpsnebul.data.remote.ZonaDto

data class UserPosition(
    val userId: String,
    val fullName: String,
    val role: String,
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long,
    val activeAlert: String? = null,
    val assignedBlock: String? = null
)

expect @Composable fun MapLibreView(
    modifier: Modifier,
    pmtilesPath: String?,
    userPositions: List<UserPosition>,
    myPosition: UserPosition?,
    zonas: List<ZonaDto> = emptyList()
)
