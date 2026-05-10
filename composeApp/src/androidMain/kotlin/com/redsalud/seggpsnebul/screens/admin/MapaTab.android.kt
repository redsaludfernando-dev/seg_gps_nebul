@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.remote.AlertAdminDto
import com.redsalud.seggpsnebul.data.remote.SessionAdminDto
import com.redsalud.seggpsnebul.data.remote.UserAdminDto
import com.redsalud.seggpsnebul.data.remote.WorkerPositionDto
import com.redsalud.seggpsnebul.map.PmTilesManager
import com.redsalud.seggpsnebul.screens.map.AlertMarker
import com.redsalud.seggpsnebul.screens.map.MapLibreView
import com.redsalud.seggpsnebul.screens.map.UserPosition
import kotlinx.coroutines.delay
import kotlin.time.Instant

/**
 * Geovisor admin nativo en Android. Reutiliza el mismo MapLibreView que los
 * trabajadores y le pasa datos del AdminViewModel (livePositions, zonas,
 * activeAlerts). El selector de jornada va en una barra superior, no en
 * sidebar lateral como en web.
 */
@OptIn(ExperimentalMaterial3Api::class)
actual @Composable fun MapaTab(vm: AdminViewModel, onBack: () -> Unit) {
    val zonas        by vm.zonas.collectAsState()
    val sessions     by vm.sessions.collectAsState()
    val selectedId   by vm.selectedSessionId.collectAsState()
    val positions    by vm.livePositions.collectAsState()
    val activeAlerts by vm.activeAlerts.collectAsState()
    val users        by vm.users.collectAsState()
    val sessionStats by vm.sessionStats.collectAsState()

    // Auto-seleccionar la jornada activa la primera vez que cargan.
    LaunchedEffect(sessions) {
        if (selectedId == null && sessions.isNotEmpty()) {
            val active = sessions.firstOrNull { it.is_active } ?: sessions.first()
            vm.selectSession(active.id)
        }
    }

    // Refrescar alertas activas cada 8s — equivalente al web.
    LaunchedEffect(Unit) {
        while (true) {
            vm.loadActiveAlerts()
            delay(8_000)
        }
    }

    // Refrescar posiciones en vivo cada 12s mientras haya jornada seleccionada.
    LaunchedEffect(selectedId) {
        val sid = selectedId ?: return@LaunchedEffect
        while (true) {
            vm.selectSession(sid)   // re-pull livePositions + tracks
            delay(12_000)
        }
    }

    val usersById = remember(users) { users.associateBy { it.id } }
    val workerPositions = remember(positions) { positions.map { it.toUserPosition() } }
    val alertMarkers = remember(activeAlerts, usersById) {
        activeAlerts.toAlertMarkers(usersById)
    }
    val stats = sessionStats[selectedId]

    Column(Modifier.fillMaxSize()) {
        // Barra superior con selector de jornada + stats compactas.
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SessionDropdown(
                        sessions   = sessions,
                        selectedId = selectedId,
                        onSelect   = { vm.selectSession(it) },
                        modifier   = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { selectedId?.let { vm.selectSession(it) } },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("↻") }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatPill("👷 ${workerPositions.size}")
                    StatPill("📍 ${stats?.trackCount ?: "—"}")
                    StatPill("⚠ ${stats?.alertCount ?: "—"}")
                    StatPill("🏘 ${stats?.blockCount ?: "—"}")
                }
            }
        }

        Box(Modifier.weight(1f)) {
            MapLibreView(
                modifier        = Modifier.fillMaxSize(),
                pmtilesPath     = PmTilesManager.localPath().takeIf { PmTilesManager.isDownloaded() },
                userPositions   = workerPositions,
                myPosition      = null,
                zonas           = zonas,
                alerts          = alertMarkers,
                onAlertOnWay    = { vm.adminAlertOnWay(it) },
                onAlertAttended = { vm.adminAlertAttended(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDropdown(
    sessions: List<SessionAdminDto>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val current = sessions.firstOrNull { it.id == selectedId }
    val label = current?.let {
        (if (it.is_active) "🟢 " else "⚪ ") + it.name
    } ?: "Selecciona jornada"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Jornada") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.forEach { s ->
                DropdownMenuItem(
                    text = { Text((if (s.is_active) "🟢 " else "⚪ ") + s.name) },
                    onClick = { onSelect(s.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

private fun WorkerPositionDto.toUserPosition() = UserPosition(
    userId     = userId,
    fullName   = fullName,
    role       = role,
    latitude   = latitude,
    longitude  = longitude,
    capturedAt = capturedAt
)

private fun List<AlertAdminDto>.toAlertMarkers(usersById: Map<String, UserAdminDto>): List<AlertMarker> =
    mapNotNull { a ->
        val lat = a.latitude ?: return@mapNotNull null
        val lon = a.longitude ?: return@mapNotNull null
        val createdMs = runCatching { Instant.parse(a.created_at).toEpochMilliseconds() }
            .getOrElse { return@mapNotNull null }
        AlertMarker(
            id            = a.id,
            latitude      = lat,
            longitude     = lon,
            alertType     = a.alert_type,
            status        = a.response_status ?: "pending",
            senderName    = usersById[a.sender_id]?.full_name ?: "—",
            responderName = a.response_by?.let { usersById[it]?.full_name },
            createdAt     = createdMs
        )
    }
