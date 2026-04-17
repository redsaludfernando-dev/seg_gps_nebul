package com.redsalud.seggpsnebul.screens.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.map.PmTilesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(user: User?, onLogout: () -> Unit) {
    val vm = remember(user) { MapViewModel(user) }
    DisposableEffect(vm) { onDispose { vm.dispose() } }

    val pmState by vm.pmTilesState.collectAsState()
    val userPositions by vm.userPositions.collectAsState()
    val myPosition by vm.myPosition.collectAsState()
    val isOnline by vm.isOnline.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.fullName ?: "Administrador") },
                actions = {
                    if (!isOnline) {
                        Badge { Text("Sin conexion") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onLogout) { Text("Salir") }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = pmState) {
                is PmTilesState.NotDownloaded -> DownloadPrompt(vm)
                is PmTilesState.Downloading   -> DownloadProgress(s.progress)
                is PmTilesState.Error         -> ErrorCard(s.msg, vm)
                is PmTilesState.Ready -> {
                    MapLibreView(
                        modifier = Modifier.fillMaxSize(),
                        pmtilesPath = PmTilesManager.localPath(),
                        userPositions = userPositions,
                        myPosition = myPosition
                    )
                    if (userPositions.isNotEmpty()) {
                        UserCountBadge(userPositions.size, Modifier.align(Alignment.BottomEnd).padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadPrompt(vm: MapViewModel) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Mapa de Rioja no descargado", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.downloadPmTiles() }) { Text("Descargar mapa (~50 MB)") }
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Descargando mapa...", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        if (progress > 0f) LinearProgressIndicator(progress = { progress }, Modifier.width(200.dp))
        else CircularProgressIndicator()
    }
}

@Composable
private fun ErrorCard(msg: String, vm: MapViewModel) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error: $msg", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { vm.downloadPmTiles() }) { Text("Reintentar") }
    }
}

@Composable
private fun UserCountBadge(count: Int, modifier: Modifier) {
    Card(modifier) {
        Text("$count en campo", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}
