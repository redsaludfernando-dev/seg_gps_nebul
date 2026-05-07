package com.redsalud.seggpsnebul.screens.role

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.local.Alerts
import com.redsalud.seggpsnebul.domain.model.AlertType
import com.redsalud.seggpsnebul.map.PmTilesManager
import com.redsalud.seggpsnebul.screens.map.MapLibreView
import com.redsalud.seggpsnebul.screens.map.PmTilesState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnotadorScreen(vm: RoleViewModel, onLogout: () -> Unit) {
    val pmState       by vm.pmTilesState.collectAsState()
    val sessionActive by vm.sessionActive.collectAsState()
    val userPositions by vm.userPositions.collectAsState()
    val myPosition    by vm.myPosition.collectAsState()
    val myBlock       by vm.myBlock.collectAsState()
    val allAlerts     by vm.allAlerts.collectAsState()
    val zonas         by vm.zonas.collectAsState()
    
    val message       by vm.message.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }
    var showEndDialog   by remember { mutableStateOf(false) }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message!!)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(vm.currentUser.fullName) },
                actions = {
                    SyncStatusIndicator()
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onLogout) { Text("Salir") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Map
            Box(Modifier.weight(1f)) {
                when (pmState) {
                    is PmTilesState.NotDownloaded -> MapDownloadPrompt(vm)
                    is PmTilesState.Downloading   -> MapDownloadProgress((pmState as PmTilesState.Downloading).progress)
                    is PmTilesState.Error         -> MapErrorCard((pmState as PmTilesState.Error).msg, vm)
                    is PmTilesState.Ready -> MapLibreView(
                        modifier      = Modifier.fillMaxSize(),
                        pmtilesPath   = PmTilesManager.localPath(),
                        userPositions = userPositions,
                        myPosition    = myPosition,
                        zonas         = zonas
                    )
                }
            }

            // Action panel
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    // Assigned block card
                    myBlock?.let { block ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Manzana asignada", style = MaterialTheme.typography.labelSmall)
                                    Text(block.block_name, style = MaterialTheme.typography.titleMedium)
                                    block.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                        Text(notes, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text("📋", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    } ?: Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            "Sin manzana asignada",
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Session control
                    if (!sessionActive) {
                        Button(
                            onClick  = { showStartDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("INICIAR JORNADA") }
                    } else {
                        AlertButtons(
                            modifier = Modifier.fillMaxWidth(),
                            types    = AlertType.WORKER_ALERTS,
                            onSend   = { vm.sendAlert(it, lat = myPosition?.latitude, lon = myPosition?.longitude) }
                        )
                        OutlinedButton(
                            onClick  = { showEndDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("FINALIZAR JORNADA") }
                    }

                    // Recent alerts (solo visibles durante jornada activa)
                    if (sessionActive && allAlerts.isNotEmpty()) {
                        Text("Historial de alertas", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(allAlerts.take(10)) { alert ->
                                AlertHistoryItem(alert)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartDialog) {
        StartSessionDialog(
            onConfirm = { name -> vm.startSession(name); showStartDialog = false },
            onDismiss = { showStartDialog = false }
        )
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title   = { Text("Finalizar jornada") },
            text    = { Text("¿Estás seguro de que deseas finalizar la jornada?") },
            confirmButton = {
                TextButton(onClick = { vm.endSession(); showEndDialog = false }) { Text("Finalizar") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AlertHistoryItem(alert: Alerts) {
    val type = AlertType.fromValue(alert.alert_type)
    val attended = alert.is_attended == 1L
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text  = "${type?.emoji ?: "❓"} ${type?.label ?: alert.alert_type}",
            style = MaterialTheme.typography.bodySmall
        )
        if (attended) {
            Text("✓ Atendida", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
        } else {
            Text("Pendiente", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}
