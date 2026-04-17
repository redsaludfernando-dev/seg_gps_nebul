package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.remote.ZonaDto
import com.redsalud.seggpsnebul.util.KmlParser
import kotlinx.coroutines.delay

@Composable
fun ZonasTab(vm: AdminViewModel) {
    val zonas     by vm.zonas.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    var preview    by remember { mutableStateOf<List<ZonaDto>?>(null) }
    var isWaiting  by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }

    // Polling hasta que el usuario seleccione un archivo KML
    LaunchedEffect(isWaiting) {
        if (!isWaiting) return@LaunchedEffect
        parseError = null
        repeat(150) {                       // timeout ≈ 30 s
            val kml = consumePendingKml()
            if (kml != null) {
                val parsed = KmlParser.parseToZonas(kml)
                if (parsed.isEmpty()) parseError = "No se encontraron polígonos en el KML"
                else preview = parsed
                isWaiting = false
                return@LaunchedEffect
            }
            delay(200)
        }
        isWaiting = false                   // timeout sin selección
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Cabecera ─────────────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    if (zonas.isEmpty()) "Sin manzanas publicadas"
                    else "${zonas.size} manzanas en Supabase",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (zonas.isNotEmpty() && preview == null) {
                        OutlinedButton(
                            onClick = { vm.deleteAllZonas() },
                            colors  = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Limpiar") }
                    }
                    Button(
                        onClick  = { triggerKmlFilePicker(); isWaiting = true },
                        enabled  = !isLoading && !isWaiting
                    ) {
                        if (isWaiting) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Esperando…")
                        } else {
                            Text("Subir KML")
                        }
                    }
                }
            }
        }

        // ── Error de parseo ───────────────────────────────────────────────────
        parseError?.let { err ->
            item {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer)) {
                    Text(err, Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // ── Preview de zonas parseadas ────────────────────────────────────────
        preview?.let { list ->
            item {
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Vista previa — ${list.size} manzanas",
                            style = MaterialTheme.typography.titleSmall)
                        list.take(5).forEach { z ->
                            Text("• ${z.nombre}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (list.size > 5) {
                            Text("… y ${list.size - 5} más",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.uploadZonas(list); preview = null },
                                enabled = !isLoading
                            ) {
                                if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary)
                                else Text("Publicar ${list.size} manzanas")
                            }
                            OutlinedButton(onClick = { preview = null }) { Text("Cancelar") }
                        }
                    }
                }
            }
        }

        // ── Lista de zonas publicadas ─────────────────────────────────────────
        if (zonas.isEmpty() && preview == null && !isWaiting) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sin manzanas publicadas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Exporta tu KML desde Google My Maps o QGIS y súbelo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        items(zonas, key = { it.id }) { zona ->
            ZonaCard(zona)
        }
    }
}

@Composable
private fun ZonaCard(zona: ZonaDto) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Indicador de color
            Surface(
                modifier = Modifier.size(14.dp),
                shape    = MaterialTheme.shapes.small,
                color    = parseColor(zona.color)
            ) {}
            Text(zona.nombre, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

/** Convierte un hex string a un Color de Compose. */
@Composable
private fun parseColor(hex: String): androidx.compose.ui.graphics.Color =
    runCatching {
        val cleaned = hex.trimStart('#')
        val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
        androidx.compose.ui.graphics.Color(argb.toLong(16).toInt())
    }.getOrDefault(MaterialTheme.colorScheme.primary)
