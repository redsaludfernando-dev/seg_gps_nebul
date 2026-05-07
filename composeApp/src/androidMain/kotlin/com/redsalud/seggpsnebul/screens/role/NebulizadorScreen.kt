package com.redsalud.seggpsnebul.screens.role

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.domain.model.AlertType
import com.redsalud.seggpsnebul.map.PmTilesManager
import com.redsalud.seggpsnebul.screens.map.MapLibreView
import com.redsalud.seggpsnebul.screens.map.PmTilesState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NebulizadorScreen(vm: RoleViewModel, onLogout: () -> Unit) {
    val pmState       by vm.pmTilesState.collectAsState()
    val sessionActive by vm.sessionActive.collectAsState()
    val userPositions by vm.userPositions.collectAsState()
    val myPosition    by vm.myPosition.collectAsState()
    val zonas         by vm.zonas.collectAsState()
    val alertMarkers  by vm.alertMarkers.collectAsState()

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
                    if (sessionActive) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("En jornada") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onLogout) { Text("Salir") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Map — takes most space
            Box(Modifier.weight(1f)) {
                when (pmState) {
                    is PmTilesState.NotDownloaded -> MapDownloadPrompt(vm)
                    is PmTilesState.Downloading   -> MapDownloadProgress((pmState as PmTilesState.Downloading).progress)
                    is PmTilesState.Error         -> MapErrorCard((pmState as PmTilesState.Error).msg, vm)
                    is PmTilesState.Ready -> MapLibreView(
                        modifier        = Modifier.fillMaxSize(),
                        pmtilesPath     = PmTilesManager.localPath(),
                        userPositions   = userPositions,
                        myPosition      = myPosition,
                        zonas           = zonas,
                        alerts          = alertMarkers,
                        onAlertOnWay    = { vm.markAlertOnWay(it) },
                        onAlertAttended = { vm.markAlertAttended(it) }
                    )
                }
            }

            // Action panel
            Surface(tonalElevation = 4.dp) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Session button
                    if (!sessionActive) {
                        Button(
                            onClick  = { showStartDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) { Text("INICIAR JORNADA") }
                    } else {
                        // Supply alerts (worker types)
                        AlertButtons(
                            modifier = Modifier.fillMaxWidth(),
                            types    = AlertType.WORKER_ALERTS,
                            onSend   = { vm.sendAlert(it, lat = myPosition?.latitude, lon = myPosition?.longitude) }
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick  = { showEndDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("FINALIZAR JORNADA") }
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
internal fun StartSessionDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Iniciar jornada") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ingresa el nombre de la jornada:")
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    placeholder   = { Text("Ej: Jornada Mañana 29/03") },
                    singleLine    = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled  = name.isNotBlank()
            ) { Text("Iniciar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
