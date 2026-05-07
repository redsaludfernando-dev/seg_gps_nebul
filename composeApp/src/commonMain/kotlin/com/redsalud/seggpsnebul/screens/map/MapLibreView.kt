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

/**
 * Marker de alerta en el mapa, comun a todos los roles.
 * status: "pending" | "on_way" | "attended"
 */
data class AlertMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val alertType: String,
    val status: String,
    val senderName: String,
    val responderName: String?,
    val createdAt: Long
)

expect @Composable fun MapLibreView(
    modifier: Modifier,
    pmtilesPath: String?,
    userPositions: List<UserPosition>,
    myPosition: UserPosition?,
    zonas: List<ZonaDto> = emptyList(),
    alerts: List<AlertMarker> = emptyList(),
    onAlertOnWay: (String) -> Unit = {},
    onAlertAttended: (String) -> Unit = {}
)
