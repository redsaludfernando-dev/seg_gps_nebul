package com.redsalud.seggpsnebul.screens.role

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown when PMTiles file is not yet downloaded. */
@Composable
fun MapDownloadPrompt(vm: RoleViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(32.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment    = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(12.dp)
            ) {
                Text("Mapa no disponible", style = MaterialTheme.typography.titleMedium)
                Text(
                    "El mapa offline de Rioja no está descargado aún.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { vm.downloadPmTiles() }) {
                    Text("Descargar mapa (~50 MB)")
                }
            }
        }
    }
}

/** Shown while PMTiles is downloading, with optional progress bar. */
@Composable
fun MapDownloadProgress(progress: Float) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(32.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.spacedBy(12.dp)
            ) {
                Text("Descargando mapa…", style = MaterialTheme.typography.titleMedium)
                if (progress > 0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Iniciando descarga…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Shown when PMTiles download failed. */
@Composable
fun MapErrorCard(msg: String, vm: RoleViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            Modifier.padding(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.spacedBy(12.dp)
            ) {
                Text("Error al descargar mapa", style = MaterialTheme.typography.titleMedium)
                Text(msg, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.downloadPmTiles() }) { Text("Reintentar") }
            }
        }
    }
}
