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
import kotlin.math.*

private enum class ChoferTab { SOLICITUDES, MAPA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoferScreen(vm: RoleViewModel, onLogout: () -> Unit) {
    val pmState       by vm.pmTilesState.collectAsState()
    val pendingAlerts by vm.pendingAlerts.collectAsState()
    val userPositions by vm.userPositions.collectAsState()
    val myPosition    by vm.myPosition.collectAsState()
    val isOnline      by vm.isOnline.collectAsState()
    val message       by vm.message.collectAsState()
    val zonas         by vm.zonas.collectAsState()

    var selectedTab by remember { mutableStateOf(ChoferTab.SOLICITUDES) }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHost.showSnackbar(message!!)
            vm.clearMessage()
        }
    }

    // Supply requests only (chofer target), sorted by distance
    val supplyAlerts by remember(pendingAlerts, myPosition) {
        derivedStateOf {
            pendingAlerts
                .filter { AlertType.fromValue(it.alert_type) in AlertType.SUPPLY_TYPES }
                .sortedBy { alert ->
                    val myLat = myPosition?.latitude
                    val myLon = myPosition?.longitude
                    val aLat = alert.latitude
                    val aLon = alert.longitude
                    if (myLat != null && myLon != null && aLat != null && aLon != null)
                        haversineKm(myLat, myLon, aLat, aLon)
                    else
                        Double.MAX_VALUE
                }
        }
    }

    // Auto-switch to SOLICITUDES when new supply alert arrives
    val supplyCount = supplyAlerts.size
    LaunchedEffect(supplyCount) {
        if (supplyCount > 0) selectedTab = ChoferTab.SOLICITUDES
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(vm.currentUser.fullName) },
                actions = {
                    if (supplyCount > 0) {
                        BadgedBox(badge = { Badge { Text("$supplyCount") } }) {
                            Text("Pendiente")
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
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ChoferTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick  = { selectedTab = tab },
                        text     = {
                            when (tab) {
                                ChoferTab.SOLICITUDES -> Text(if (supplyCount > 0) "Solicitudes ($supplyCount)" else "Solicitudes")
                                ChoferTab.MAPA        -> Text("Mapa")
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                ChoferTab.SOLICITUDES -> {
                    if (supplyAlerts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sin solicitudes pendientes", style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(8.dp))
                                Text("Esperando solicitudes de la brigada…", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement  = Arrangement.spacedBy(8.dp),
                            contentPadding       = PaddingValues(vertical = 12.dp)
                        ) {
                            items(supplyAlerts, key = { it.id }) { alert ->
                                SupplyRequestCard(
                                    alert       = alert,
                                    myLat       = myPosition?.latitude,
                                    myLon       = myPosition?.longitude,
                                    onAttend    = { vm.markAlertAttended(alert.id) }
                                )
                            }
                        }
                    }
                }

                ChoferTab.MAPA -> {
                    Box(Modifier.fillMaxSize()) {
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
                }
            }
        }
    }
}

@Composable
private fun SupplyRequestCard(
    alert: Alerts,
    myLat: Double?,
    myLon: Double?,
    onAttend: () -> Unit
) {
    val type = AlertType.fromValue(alert.alert_type)
    val aLat = alert.latitude
    val aLon = alert.longitude
    val distanceText = if (myLat != null && myLon != null && aLat != null && aLon != null) {
        val km = haversineKm(myLat, myLon, aLat, aLon)
        if (km < 1.0) "${(km * 1000).roundToInt()} m" else "${"%.1f".format(km)} km"
    } else null

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
                distanceText?.let {
                    Text("Distancia: $it", style = MaterialTheme.typography.bodySmall)
                }
                alert.message?.takeIf { it.isNotBlank() }?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                distanceText?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick          = onAttend,
                    contentPadding   = PaddingValues(horizontal = 12.dp)
                ) { Text("Atendido") }
            }
        }
    }
}

/** Haversine formula — returns distance in km */
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r    = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a    = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
