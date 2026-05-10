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
    /**
     * Nombre de la manzana asignada al usuario actual. Si no es null y existe
     * en `zonas` con ese nombre, se muestra un FAB extra que enfoca el mapa
     * sobre el polígono.
     */
    assignedBlockName: String? = null,
    onAlertOnWay: (String) -> Unit = {},
    onAlertAttended: (String) -> Unit = {}
)
