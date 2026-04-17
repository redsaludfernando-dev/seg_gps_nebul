package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.remote.SessionAdminDto
import com.redsalud.seggpsnebul.data.remote.UserAdminDto
import com.redsalud.seggpsnebul.screens.role.SyncStatusIndicator
import kotlinx.datetime.Instant

private enum class AdminTab { TRABAJADORES, JORNADAS, SYNC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onLogout: () -> Unit) {
    val vm = remember { AdminViewModel() }
    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    var tab by remember { mutableStateOf(AdminTab.TRABAJADORES) }

    val isLoading by vm.isLoading.collectAsState()
    val message   by vm.message.collectAsState()
    val snackbar  = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message != null) { snackbar.showSnackbar(message!!); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Panel Administrador") },
                actions = {
                    SyncStatusIndicator()
                    Spacer(Modifier.width(4.dp))
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onLogout) { Text("Salir") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab.ordinal) {
                AdminTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick  = { tab = t },
                        text     = {
                            Text(when (t) {
                                AdminTab.TRABAJADORES -> "Trabajadores"
                                AdminTab.JORNADAS     -> "Jornadas"
                                AdminTab.SYNC         -> "Sync"
                            })
                        }
                    )
                }
            }
            when (tab) {
                AdminTab.TRABAJADORES -> TrabajadoresTab(vm)
                AdminTab.JORNADAS     -> JornadasTab(vm)
                AdminTab.SYNC         -> SyncTab(vm)
            }
        }
    }
}

// ─── Tab: Trabajadores ────────────────────────────────────────────────────────

@Composable
private fun TrabajadoresTab(vm: AdminViewModel) {
    val users by vm.users.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("${users.size} trabajadores", style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { vm.loadAll() }) { Text("Actualizar") }
        }

        if (users.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin datos. Verifica la conexión.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding       = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement  = Arrangement.spacedBy(8.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    WorkerCard(user, onToggleActive = { vm.toggleUserActive(user.id, user.isActive) })
                }
            }
        }
    }
}

@Composable
private fun WorkerCard(user: UserAdminDto, onToggleActive: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(user.full_name, style = MaterialTheme.typography.titleSmall)
                Text(user.role.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("DNI: ${user.dni}  ·  ${user.phone_number}",
                    style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Badge(
                    containerColor = if (user.isActive) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(if (user.isActive) "Activo" else "Inactivo",
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick          = onToggleActive,
                    contentPadding   = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (user.isActive) "Desactivar" else "Activar",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ─── Tab: Jornadas ────────────────────────────────────────────────────────────

@Composable
private fun JornadasTab(vm: AdminViewModel) {
    val sessions by vm.sessions.collectAsState()
    val statsMap by vm.sessionStats.collectAsState()

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
                        onDelete = { vm.deleteGpsFromServer(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionAdminDto,
    stats: com.redsalud.seggpsnebul.data.remote.SessionStats?,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                        Text("Duración: ${duration}min", style = MaterialTheme.typography.labelSmall)
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

            // Stats row
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

            // Action buttons
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick          = onExport,
                    contentPadding   = PaddingValues(horizontal = 12.dp)
                ) { Text("Exportar CSV") }
                OutlinedButton(
                    onClick          = { showDeleteConfirm = true },
                    contentPadding   = PaddingValues(horizontal = 12.dp),
                    colors           = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Borrar GPS servidor") }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Confirmar borrado") },
            text    = { Text("¿Eliminar todos los puntos GPS de esta jornada del servidor? No se puede deshacer. El CSV ya debe estar exportado.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun StatChip(label: String, count: Int) {
    Surface(
        shape         = MaterialTheme.shapes.small,
        color         = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text("$label: $count", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall)
    }
}

// ─── Tab: Sync ────────────────────────────────────────────────────────────────

@Composable
private fun SyncTab(vm: AdminViewModel) {
    val isOnline      by vm.isOnline.collectAsState()
    val realtimeUp    by vm.realtimeUp.collectAsState()
    val isSyncing     by vm.isSyncing.collectAsState()
    val pendingTracks by vm.pendingTracks.collectAsState()
    val pendingAlerts by vm.pendingAlerts.collectAsState()
    val pendingBlocks by vm.pendingBlocks.collectAsState()
    val lastSyncAt    by vm.lastSyncAt.collectAsState()
    val lastError     by vm.syncLastError.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SyncStatusCard(
                label  = "Conectividad",
                value  = if (isOnline) "En línea" else "Sin red",
                good   = isOnline
            )
        }
        item {
            SyncStatusCard(
                label  = "Realtime WebSocket",
                value  = if (realtimeUp) "Conectado" else "Desconectado (polling activo)",
                good   = realtimeUp
            )
        }
        item {
            SyncStatusCard(label = "GPS pendientes", value = "$pendingTracks puntos", good = pendingTracks == 0)
        }
        item {
            SyncStatusCard(label = "Alertas pendientes", value = "$pendingAlerts", good = pendingAlerts == 0)
        }
        item {
            SyncStatusCard(label = "Manzanas pendientes", value = "$pendingBlocks", good = pendingBlocks == 0)
        }
        item {
            SyncStatusCard(
                label = "Última sincronización",
                value = lastSyncAt?.let { fmtTs(it) } ?: "Nunca",
                good  = lastSyncAt != null
            )
        }
        if (lastError != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("Error: $lastError", Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        item {
            Button(
                onClick  = { vm.triggerSync() },
                enabled  = isOnline && !isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Sincronizando...")
                } else {
                    Text("Sincronizar ahora")
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(label: String, value: String, good: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, style = MaterialTheme.typography.labelMedium,
                    color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Badge(
                    containerColor = if (good) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.errorContainer
                ) { Text(if (good) "✓" else "!", Modifier.padding(horizontal = 2.dp)) }
            }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

private fun fmtTs(ms: Long): String =
    Instant.fromEpochMilliseconds(ms).toString()
        .replace("T", " ").take(16).replace("Z", "")
