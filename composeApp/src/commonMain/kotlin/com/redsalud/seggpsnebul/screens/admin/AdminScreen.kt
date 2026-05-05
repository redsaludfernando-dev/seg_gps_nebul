package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.screens.role.SyncStatusIndicator

private enum class AdminTab { MAPA, TRABAJADORES, JORNADAS, ASIGNACIONES, ZONAS, SYNC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onLogout: () -> Unit) {
    val vm = remember { AdminViewModel() }
    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    var tab by remember { mutableStateOf(AdminTab.MAPA) }

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
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                AdminTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick  = { tab = t },
                        text     = {
                            Text(when (t) {
                                AdminTab.MAPA         -> "Mapa"
                                AdminTab.TRABAJADORES -> "Trabajadores"
                                AdminTab.JORNADAS     -> "Jornadas"
                                AdminTab.ASIGNACIONES -> "Asignaciones"
                                AdminTab.ZONAS        -> "Manzanas"
                                AdminTab.SYNC         -> "Sync"
                            })
                        }
                    )
                }
            }
            when (tab) {
                AdminTab.MAPA         -> MapaTab(vm, onBack = { tab = AdminTab.JORNADAS })
                AdminTab.TRABAJADORES -> TrabajadoresTab(vm)
                AdminTab.JORNADAS     -> JornadasTab(vm)
                AdminTab.ASIGNACIONES -> AsignacionesTab(vm)
                AdminTab.ZONAS        -> ZonasTab(vm)
                AdminTab.SYNC         -> SyncTab(vm)
            }
        }
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

internal fun fmtTs(iso: String): String = iso.take(16).replace("T", " ")
