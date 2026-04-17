package com.redsalud.seggpsnebul.screens.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class UserPosition(
    val userId: String,
    val fullName: String,
    val role: String,
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long,
    val activeAlert: String? = null,    // Phase 3: populated when an alert is active
    val assignedBlock: String? = null   // Phase 3: populated from block_assignments
)

expect @Composable fun MapLibreView(
    modifier: Modifier,
    pmtilesPath: String?,
    userPositions: List<UserPosition>,
    myPosition: UserPosition?
)
