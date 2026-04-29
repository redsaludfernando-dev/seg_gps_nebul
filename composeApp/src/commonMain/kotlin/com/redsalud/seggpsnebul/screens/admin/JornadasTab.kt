package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.remote.AlertAdminDto
import com.redsalud.seggpsnebul.data.remote.AssignmentDto
import com.redsalud.seggpsnebul.data.remote.SessionAdminDto
import com.redsalud.seggpsnebul.data.remote.SessionStats
import com.redsalud.seggpsnebul.data.remote.UserAdminDto

@Composable
fun JornadasTab(vm: AdminViewModel) {
    val sessions by vm.sessions.collectAsState()
    val statsMap by vm.sessionStats.collectAsState()

    var detailFor by remember { mutableStateOf<SessionAdminDto?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("${sessions.size} jornadas", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { vm.loadAll() }) { Text("Actualizar") }
        }

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin jornadas registradas.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val stats = statsMap[session.id]
                    LaunchedEffect(session.id) { vm.loadSessionStats(session.id) }
                    SessionCard(
                        session  = session,
                        stats    = stats,
                        onExport = { vm.exportSession(session.id) },
                        onDeleteGps = { vm.deleteGpsFromServer(session.id) },
                        onClose  = { vm.closeSession(session.id) },
                        onDetail = { detailFor = session }
                    )
                }
            }
        }
    }

    detailFor?.let { s ->
        SessionDetailDialog(
            session   = s,
            vm        = vm,
            onDismiss = { detailFor = null }
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionAdminDto,
    stats: SessionStats?,
    onExport: () -> Unit,
    onDeleteGps: () -> Unit,
    onClose: () -> Unit,
    onDetail: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCloseConfirm  by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(session.name, style = MaterialTheme.typography.titleSmall)
                    Text(fmtTs(session.started_at), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val duration = session.ended_at?.let { (it - session.started_at) / 60_000 }
                    if (duration != null) {
                        Text("Duración: ${duration} min", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!session.brigade_code.isNullOrBlank()) {
                        Text("Brigada: ${session.brigade_code}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Badge(
                        containerColor = if (session.is_active) MaterialTheme.colorScheme.primaryContainer
                                         else MaterialTheme.colorScheme.surfaceVariant
                    ) { Text(if (session.is_active) "En curso" else "Cerrada",
                        Modifier.padding(horizontal = 4.dp)) }
                    if (session.export_done) {
                        Spacer(Modifier.height(2.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("Exportada", Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
            }

            if (stats != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatChip("GPS", stats.trackCount)
                    StatChip("Alertas", stats.alertCount)
                    StatChip("Manzanas", stats.blockCount)
                }
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onDetail, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("Ver detalle")
                }
                OutlinedButton(onClick = onExport, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("CSV")
                }
                if (session.is_active) {
                    OutlinedButton(
                        onClick        = { showCloseConfirm = true },
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("Cerrar jornada") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Borrar GPS", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirmar borrado") },
            text  = { Text("¿Eliminar todos los puntos GPS de esta jornada del servidor? El CSV ya debe estar exportado.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteGps(); showDeleteConfirm = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") } }
        )
    }
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("Cerrar jornada") },
            text  = { Text("Marca la jornada como cerrada y registra la hora de fin. Los trabajadores ya no podrán enviar GPS para esta sesión.") },
            confirmButton = {
                TextButton(onClick = { onClose(); showCloseConfirm = false }) { Text("Cerrar") }
            },
            dismissButton = { TextButton(onClick = { showCloseConfirm = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StatChip(label: String, count: Int) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text("$label: $count", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

// ─── Diálogo de detalle de jornada ───────────────────────────────────────────

@Composable
private fun SessionDetailDialog(
    session: SessionAdminDto,
    vm: AdminViewModel,
    onDismiss: () -> Unit
) {
    val alerts      by vm.alerts.collectAsState()
    val assignments by vm.assignments.collectAsState()
    val users       by vm.users.collectAsState()

    LaunchedEffect(session.id) {
        vm.loadAlerts(session.id)
        vm.loadAssignments(session.id)
    }

    val usersById = remember(users) { users.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Detalle: ${session.name}") },
        text    = {
            LazyColumn(
                Modifier.heightIn(max = 540.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Inicio: ${fmtTs(session.started_at)}", style = MaterialTheme.typography.bodySmall)
                    session.ended_at?.let { ts ->
                        Text("Fin: ${fmtTs(ts)}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                item {
                    Text(
                        "Asignaciones (${assignments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (assignments.isEmpty()) {
                    item {
                        Text("Sin asignaciones registradas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(assignments, key = { it.id }) { a ->
                        AssignmentRowReadOnly(a, usersById)
                    }
                }

                item {
                    Text(
                        "Alertas (${alerts.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (alerts.isEmpty()) {
                    item {
                        Text("Sin alertas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(alerts, key = { it.id }) { al ->
                        AlertRow(
                            al        = al,
                            usersById = usersById,
                            onAttend  = { uid ->
                                vm.markAlertAttended(al.id, session.id, uid)
                            },
                            onDelete  = { vm.deleteAlert(al.id, session.id) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun AssignmentRowReadOnly(a: AssignmentDto, usersById: Map<String, UserAdminDto>) {
    val worker = usersById[a.assigned_to]?.full_name ?: a.assigned_to.take(6)
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(8.dp)) {
            Text(a.block_name, style = MaterialTheme.typography.bodyMedium)
            Text("→ $worker", style = MaterialTheme.typography.labelSmall)
            a.notes?.takeIf { it.isNotBlank() }?.let { n ->
                Text(n, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AlertRow(
    al: AlertAdminDto,
    usersById: Map<String, UserAdminDto>,
    onAttend: (attendedBy: String) -> Unit,
    onDelete: () -> Unit
) {
    val sender = usersById[al.sender_id]?.full_name ?: al.sender_id.take(6)
    val attender = al.attended_by?.let { usersById[it]?.full_name ?: it.take(6) }

    Card(
        colors = if (al.is_attended)
            CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
        else
            CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(alertTypeLabel(al.alert_type), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                Text(fmtTs(al.created_at), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("De: $sender", style = MaterialTheme.typography.labelSmall)
            al.message?.takeIf { it.isNotBlank() }?.let { m ->
                Text(m, style = MaterialTheme.typography.bodySmall)
            }
            if (al.is_attended && attender != null) {
                Text("Atendida por: $attender", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!al.is_attended) {
                    // Sin un user.id "admin" real, marcamos atendida con el primer user disponible
                    val firstUserId = usersById.values.firstOrNull()?.id
                    if (firstUserId != null) {
                        TextButton(
                            onClick = { onAttend(firstUserId) },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Marcar atendida", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                TextButton(
                    onClick        = onDelete,
                    colors         = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) { Text("Borrar", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

private fun alertTypeLabel(t: String) = when (t) {
    "agua"               -> "Solicita agua"
    "gasolina"           -> "Solicita gasolina"
    "insumo_quimico"     -> "Solicita insumo químico"
    "averia_maquina"     -> "Avería de máquina"
    "trabajo_finalizado" -> "Trabajo finalizado"
    "broadcast_text"     -> "Mensaje"
    else                 -> t
}
