package com.redsalud.seggpsnebul.screens.role

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.AppContainer
import com.redsalud.seggpsnebul.data.local.Alerts
import com.redsalud.seggpsnebul.domain.model.AlertType
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.map.PmTilesManager
import com.redsalud.seggpsnebul.screens.map.MapLibreView
import com.redsalud.seggpsnebul.screens.map.PmTilesState

private enum class JefeTab { MAPA, ALERTAS, BRIGADA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JefeScreen(vm: RoleViewModel, onLogout: () -> Unit) {
    val pmState       by vm.pmTilesState.collectAsState()
    val sessionActive by vm.sessionActive.collectAsState()
    val userPositions by vm.userPositions.collectAsState()
    val myPosition    by vm.myPosition.collectAsState()
    val pendingAlerts by vm.pendingAlerts.collectAsState()
    val allBlocks     by vm.allBlocks.collectAsState()
    val isOnline      by vm.isOnline.collectAsState()
    val message       by vm.message.collectAsState()
    val zonas         by vm.zonas.collectAsState()

    var selectedTab    by remember { mutableStateOf(JefeTab.MAPA) }
    var showStartDialog by remember { mutableStateOf(false) }
    var showEndDialog   by remember { mutableStateOf(false) }
    var showBroadcast   by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<User?>(null) }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message!!)
            vm.clearMessage()
        }
    }

    // Auto-switch to Alertas tab when new pending alerts arrive
    val pendingCount = pendingAlerts.size
    LaunchedEffect(pendingCount) {
        if (pendingCount > 0 && selectedTab == JefeTab.MAPA) selectedTab = JefeTab.ALERTAS
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(vm.currentUser.fullName) },
                actions = {
                    if (pendingCount > 0) {
                        BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                            Text("Alertas")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    SyncStatusIndicator()
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onLogout) { Text("Salir") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                JefeTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick  = { selectedTab = tab },
                        text     = {
                            when (tab) {
                                JefeTab.MAPA    -> Text("Mapa")
                                JefeTab.ALERTAS -> Text(if (pendingCount > 0) "Alertas ($pendingCount)" else "Alertas")
                                JefeTab.BRIGADA -> Text("Brigada")
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                JefeTab.MAPA -> {
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
                    // Session controls + broadcast
                    Surface(tonalElevation = 4.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!sessionActive) {
                                Button(onClick = { showStartDialog = true }, Modifier.weight(1f)) {
                                    Text("INICIAR JORNADA")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { showBroadcast = true },
                                    modifier = Modifier.weight(1f)
                                ) { Text("📢 Broadcast") }
                                OutlinedButton(
                                    onClick = { showEndDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("FINALIZAR") }
                            }
                        }
                    }
                }

                JefeTab.ALERTAS -> {
                    LazyColumn(
                        Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        if (pendingAlerts.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                    Text("Sin alertas pendientes", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        } else {
                            items(pendingAlerts, key = { it.id }) { alert ->
                                PendingAlertCard(
                                    alert      = alert,
                                    onAttend   = { vm.markAlertAttended(alert.id) }
                                )
                            }
                        }
                    }
                }

                JefeTab.BRIGADA -> {
                    val allUsers = remember { AppContainer.localDataSource.getAllActiveUsers() }
                    LazyColumn(
                        Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(allUsers, key = { it.id }) { member ->
                            val assignedBlock = allBlocks.lastOrNull { it.assigned_to == member.id }
                            BrigadeMemberCard(
                                user          = member,
                                assignedBlock = assignedBlock?.block_name,
                                canAssign     = sessionActive,
                                onAssign      = { showAssignDialog = member }
                            )
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
            text    = { Text("¿Estás seguro? Se detendrá el seguimiento GPS de todos.") },
            confirmButton = {
                TextButton(onClick = { vm.endSession(); showEndDialog = false }) { Text("Finalizar") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showBroadcast) {
        BroadcastDialog(
            onSend    = { msg -> vm.sendAlert(AlertType.BROADCAST_TEXT, message = msg); showBroadcast = false },
            onDismiss = { showBroadcast = false }
        )
    }

    showAssignDialog?.let { member ->
        AssignBlockDialog(
            memberName = member.fullName,
            onConfirm  = { blockName, notes ->
                vm.assignBlock(member.id, blockName, notes.takeIf { it.isNotBlank() })
                showAssignDialog = null
            },
            onDismiss  = { showAssignDialog = null }
        )
    }
}

@Composable
private fun PendingAlertCard(alert: Alerts, onAttend: () -> Unit) {
    val type = AlertType.fromValue(alert.alert_type)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${type?.emoji ?: "❓"} ${type?.label ?: alert.alert_type}",
                    style = MaterialTheme.typography.titleSmall
                )
                alert.message?.takeIf { it.isNotBlank() }?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall)
                }
                Text("Enviado por: ${alert.sender_id.take(8)}…", style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = onAttend, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("Atender")
            }
        }
    }
}

@Composable
private fun BrigadeMemberCard(
    user: User,
    assignedBlock: String?,
    canAssign: Boolean,
    onAssign: () -> Unit
) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(user.fullName, style = MaterialTheme.typography.titleSmall)
                Text(user.role.displayName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                assignedBlock?.let {
                    Text("📋 $it", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (canAssign) {
                OutlinedButton(
                    onClick          = onAssign,
                    contentPadding   = PaddingValues(horizontal = 12.dp)
                ) { Text("Asignar") }
            }
        }
    }
}

@Composable
private fun BroadcastDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("📢 Mensaje a toda la brigada") },
        text    = {
            OutlinedTextField(
                value         = text,
                onValueChange = { text = it },
                placeholder   = { Text("Escribe tu mensaje…") },
                maxLines      = 4
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSend(text.trim()) }, enabled = text.isNotBlank()) {
                Text("Enviar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AssignBlockDialog(memberName: String, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var blockName by remember { mutableStateOf("") }
    var notes     by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Asignar manzana a $memberName") },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value         = blockName,
                    onValueChange = { blockName = it },
                    label         = { Text("Manzana") },
                    placeholder   = { Text("Ej: Manzana A-12") },
                    singleLine    = true
                )
                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = { Text("Notas (opcional)") },
                    maxLines      = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (blockName.isNotBlank()) onConfirm(blockName.trim(), notes.trim()) },
                enabled  = blockName.isNotBlank()
            ) { Text("Asignar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
