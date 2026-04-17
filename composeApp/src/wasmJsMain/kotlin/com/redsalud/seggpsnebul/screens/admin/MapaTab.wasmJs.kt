package com.redsalud.seggpsnebul.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsalud.seggpsnebul.data.remote.*
import kotlinx.coroutines.delay

// ─── Composable principal ─────────────────────────────────────────────────────

actual @Composable fun MapaTab(vm: AdminViewModel) {
    val zonas           by vm.zonas.collectAsState()
    val sessions        by vm.sessions.collectAsState()
    val selectedSession by vm.selectedSessionId.collectAsState()
    val positions       by vm.livePositions.collectAsState()
    val tracks          by vm.trackSegments.collectAsState()
    val isLoading       by vm.isLoading.collectAsState()
    val sessionStats    by vm.sessionStats.collectAsState()
    val showZones       by vm.showZonas.collectAsState()
    val showPositions   by vm.showPositions.collectAsState()
    val showTracks      by vm.showTracks.collectAsState()

    // Inicializar / destruir el mapa al entrar / salir del tab
    DisposableEffect(Unit) {
        initAdminMap()
        onDispose { destroyAdminMap() }
    }

    // Actualizar fuentes cuando cambian los datos
    LaunchedEffect(zonas)     { delay(300); updateMapZonas(buildZonasGeoJson(zonas)) }
    LaunchedEffect(positions) { updateMapPositions(buildPositionsGeoJson(positions)) }
    LaunchedEffect(tracks)    { updateMapTracks(buildTracksGeoJson(tracks)) }

    // Visibilidad de capas
    LaunchedEffect(showZones) {
        setMapLayerVisible("zonas-fill", showZones)
        setMapLayerVisible("zonas-line", showZones)
        setMapLayerVisible("zonas-label", showZones)
    }
    LaunchedEffect(showPositions) {
        setMapLayerVisible("pos-circle", showPositions)
        setMapLayerVisible("pos-label", showPositions)
    }
    LaunchedEffect(showTracks) { setMapLayerVisible("tracks-line", showTracks) }

    // Seleccionar sesión activa por defecto
    LaunchedEffect(sessions) {
        if (selectedSession == null && sessions.isNotEmpty()) {
            val active = sessions.firstOrNull { it.is_active } ?: sessions.first()
            vm.selectSession(active.id)
        }
    }

    // Overlay de Compose encima del mapa
    Box(Modifier.fillMaxSize()) {
        // Panel lateral izquierdo
        Card(
            modifier = Modifier
                .width(270.dp)
                .fillMaxHeight()
                .padding(8.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            )
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Título
                item {
                    Text("Geovisor", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text("Brigadas GPS — Rioja",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Selector de sesión
                item {
                    SessionSelectorSection(sessions, selectedSession) { vm.selectSession(it) }
                }

                // Estadísticas de la sesión seleccionada
                if (selectedSession != null) {
                    item {
                        LaunchedEffect(selectedSession) { vm.loadSessionStats(selectedSession!!) }
                        StatsSection(
                            stats = sessionStats[selectedSession],
                            workerCount = positions.size
                        )
                    }
                }

                // Lista de trabajadores en campo
                if (positions.isNotEmpty()) {
                    item { Text("Trabajadores en campo", style = MaterialTheme.typography.labelMedium) }
                    items(positions) { pos -> WorkerPositionRow(pos) }
                }

                // Controles de capas
                item {
                    LayerTogglesSection(showZones, showPositions, showTracks, vm::toggleLayer)
                }

                // Actualizar
                item {
                    OutlinedButton(
                        onClick = { vm.selectSession(selectedSession) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedSession != null && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Actualizar")
                    }
                }
            }
        }
    }
}

// ─── Sub-composables del panel ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionSelectorSection(
    sessions: List<com.redsalud.seggpsnebul.data.remote.SessionAdminDto>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = sessions.firstOrNull { it.id == selected }

    Text("Sesión", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value    = current?.name ?: "Seleccionar…",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(s.name, style = MaterialTheme.typography.bodySmall)
                            Text(if (s.is_active) "En curso" else "Cerrada",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.is_active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(s.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun StatsSection(
    stats: com.redsalud.seggpsnebul.data.remote.SessionStats?,
    workerCount: Int
) {
    Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Estadísticas", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(2.dp))
            StatRow("Trabajadores en campo", "$workerCount")
            if (stats != null) {
                StatRow("Puntos GPS", "${stats.trackCount}")
                StatRow("Alertas", "${stats.alertCount}")
                StatRow("Manzanas asignadas", "${stats.blockCount}")
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(value, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun WorkerPositionRow(pos: WorkerPositionDto) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            Modifier.size(10.dp),
            shape = MaterialTheme.shapes.small,
            color = roleColorCompose(pos.role)
        ) {}
        Column(Modifier.weight(1f)) {
            Text(pos.fullName, style = MaterialTheme.typography.bodySmall)
            Text(roleLabel(pos.role), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(timeAgo(pos.capturedAt), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LayerTogglesSection(
    showZones: Boolean, showPositions: Boolean, showTracks: Boolean,
    toggle: (String) -> Unit
) {
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Capas", style = MaterialTheme.typography.labelMedium)
            LayerToggle("Manzanas",     showZones,     { toggle("zonas") })
            LayerToggle("Posiciones",   showPositions, { toggle("positions") })
            LayerToggle("Rutas históricas", showTracks,   { toggle("tracks") })
        }
    }
}

@Composable
private fun LayerToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Switch(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.height(24.dp))
    }
}

// ─── GeoJSON builders ─────────────────────────────────────────────────────────

private fun buildZonasGeoJson(zonas: List<ZonaDto>): String {
    val features = zonas.joinToString(",") { z ->
        val nombre = z.nombre.replace("\\", "\\\\").replace("\"", "\\\"")
        """{"type":"Feature","geometry":${z.geojson},"properties":{"nombre":"$nombre","color":"${z.color}"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun buildPositionsGeoJson(positions: List<WorkerPositionDto>): String {
    val features = positions.joinToString(",") { p ->
        val name = p.fullName.replace("\"", "\\\"")
        val initials = p.fullName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
        val color = roleColor(p.role)
        val role  = roleLabel(p.role)
        val ago   = timeAgo(p.capturedAt)
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]},"properties":{"userId":"${p.userId}","fullName":"$name","role":"$role","color":"$color","initials":"$initials","timeAgo":"$ago"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

private fun buildTracksGeoJson(tracks: List<TrackSegment>): String {
    val features = tracks.joinToString(",") { seg ->
        val coords = seg.points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        val color  = roleColor(seg.role)
        val name   = seg.fullName.replace("\"", "\\\"")
        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{"userId":"${seg.userId}","fullName":"$name","color":"$color"}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

// ─── Utilidades ───────────────────────────────────────────────────────────────

private fun roleColor(role: String) = when (role) {
    "nebulizador"  -> "#2ecc71"
    "jefe_brigada" -> "#3498db"
    "anotador"     -> "#9b59b6"
    "chofer"       -> "#f39c12"
    else           -> "#e74c3c"
}

@Composable
private fun roleColorCompose(role: String) =
    androidx.compose.ui.graphics.Color(
        when (role) {
            "nebulizador"  -> 0xFF2ecc71
            "jefe_brigada" -> 0xFF3498db
            "anotador"     -> 0xFF9b59b6
            "chofer"       -> 0xFFf39c12
            else           -> 0xFFe74c3c
        }
    )

private fun roleLabel(role: String) = when (role) {
    "nebulizador"  -> "Nebulizador"
    "jefe_brigada" -> "Jefe de Brigada"
    "anotador"     -> "Anotador"
    "chofer"       -> "Chofer"
    else           -> role
}

private fun timeAgo(capturedAt: Long): String {
    val now    = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    val diffMs = now - capturedAt
    return when {
        diffMs < 60_000     -> "hace ${diffMs / 1_000}s"
        diffMs < 3_600_000  -> "hace ${diffMs / 60_000}min"
        else                -> "hace ${diffMs / 3_600_000}h"
    }
}

// ─── MapLibre GL JS interop ───────────────────────────────────────────────────

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun initAdminMap(): Unit = js("""
(function() {
    var canvas = document.getElementById('ComposeTarget');
    if (canvas) { canvas.style.background = 'transparent'; canvas.style.pointerEvents = 'none'; }

    var div = document.createElement('div');
    div.id = 'admin-map-div';
    div.style.cssText = 'position:fixed;inset:0;z-index:0;';
    document.body.insertBefore(div, document.body.firstChild);

    var map = new maplibregl.Map({
        container: 'admin-map-div',
        style: {
            version: 8,
            sources: { osm: { type: 'raster',
                tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
                tileSize: 256, attribution: '© OpenStreetMap contributors' }},
            layers: [{ id: 'osm-raster', type: 'raster', source: 'osm' }]
        },
        center: [-77.160, -6.058], zoom: 14
    });

    window._aMap = map;
    window._aMapReady = false;

    map.addControl(new maplibregl.NavigationControl(), 'bottom-right');
    map.addControl(new maplibregl.ScaleControl({ unit: 'metric' }), 'bottom-right');

    map.on('load', function() {
        window._aMapReady = true;

        map.addSource('zonas-src',     { type: 'geojson', data: { type: 'FeatureCollection', features: [] }});
        map.addSource('tracks-src',    { type: 'geojson', data: { type: 'FeatureCollection', features: [] }});
        map.addSource('pos-src',       { type: 'geojson', data: { type: 'FeatureCollection', features: [] }});

        // Capas de zonas
        map.addLayer({ id: 'zonas-fill',  type: 'fill', source: 'zonas-src',
            paint: { 'fill-color': ['get','color'], 'fill-opacity': 0.18 }});
        map.addLayer({ id: 'zonas-line',  type: 'line', source: 'zonas-src',
            paint: { 'line-color': ['get','color'], 'line-width': 2.2, 'line-opacity': 0.85 }});
        map.addLayer({ id: 'zonas-label', type: 'symbol', source: 'zonas-src',
            layout: { 'text-field': ['get','nombre'], 'text-size': 11, 'text-anchor': 'center', 'text-font': ['Open Sans Regular','Arial Unicode MS Regular'] },
            paint:  { 'text-color': '#333', 'text-halo-color': '#fff', 'text-halo-width': 1.5 }});

        // Rutas históricas
        map.addLayer({ id: 'tracks-line', type: 'line', source: 'tracks-src',
            paint: { 'line-color': ['get','color'], 'line-width': 2, 'line-opacity': 0.7, 'line-dasharray': [2,1] }});

        // Posiciones actuales
        map.addLayer({ id: 'pos-circle', type: 'circle', source: 'pos-src',
            paint: { 'circle-radius': 10, 'circle-color': ['get','color'],
                     'circle-stroke-color': '#fff', 'circle-stroke-width': 2.5 }});
        map.addLayer({ id: 'pos-label', type: 'symbol', source: 'pos-src',
            layout: { 'text-field': ['get','initials'], 'text-size': 9,
                      'text-offset': [0, 0], 'text-anchor': 'center',
                      'text-font': ['Open Sans Bold','Arial Unicode MS Bold'] },
            paint: { 'text-color': '#fff' }});

        // Popup al clickear posición
        map.on('click', 'pos-circle', function(e) {
            var p = e.features[0].properties;
            new maplibregl.Popup({ offset: 14 })
                .setLngLat(e.lngLat)
                .setHTML('<div style="font-family:sans-serif;min-width:160px"><b>' + p.fullName + '</b><br><span style="color:#555;font-size:12px">' + p.role + '</span><br><span style="color:#888;font-size:11px">' + p.timeAgo + '</span></div>')
                .addTo(map);
        });
        map.on('mouseenter', 'pos-circle', function() { map.getCanvas().style.cursor = 'pointer'; });
        map.on('mouseleave', 'pos-circle', function() { map.getCanvas().style.cursor = ''; });

        // Aplicar datos pendientes
        if (window._pendingZonas)     { map.getSource('zonas-src').setData(window._pendingZonas);  window._pendingZonas = null; }
        if (window._pendingPositions) { map.getSource('pos-src').setData(window._pendingPositions); window._pendingPositions = null; }
        if (window._pendingTracks)    { map.getSource('tracks-src').setData(window._pendingTracks); window._pendingTracks = null; }
    });
})()
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateMapZonas(geojson: String): Unit = js("""
(function(g) {
    var d = JSON.parse(g);
    if (window._aMapReady) window._aMap.getSource('zonas-src').setData(d);
    else window._pendingZonas = d;
})(geojson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateMapPositions(geojson: String): Unit = js("""
(function(g) {
    var d = JSON.parse(g);
    if (window._aMapReady) window._aMap.getSource('pos-src').setData(d);
    else window._pendingPositions = d;
})(geojson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun updateMapTracks(geojson: String): Unit = js("""
(function(g) {
    var d = JSON.parse(g);
    if (window._aMapReady) window._aMap.getSource('tracks-src').setData(d);
    else window._pendingTracks = d;
})(geojson)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun setMapLayerVisible(layerId: String, visible: Boolean): Unit = js("""
(function(id, vis) {
    if (!window._aMapReady) return;
    window._aMap.setLayoutProperty(id, 'visibility', vis ? 'visible' : 'none');
})(layerId, visible)
""")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun destroyAdminMap(): Unit = js("""
(function() {
    window._aMapReady = false;
    if (window._aMap) { window._aMap.remove(); window._aMap = null; }
    var d = document.getElementById('admin-map-div');
    if (d) d.parentNode.removeChild(d);
    var c = document.getElementById('ComposeTarget');
    if (c) { c.style.background = ''; c.style.pointerEvents = ''; }
    window._pendingZonas = null; window._pendingPositions = null; window._pendingTracks = null;
})()
""")
