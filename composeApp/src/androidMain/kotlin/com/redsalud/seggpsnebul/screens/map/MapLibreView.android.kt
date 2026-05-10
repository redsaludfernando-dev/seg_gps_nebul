package com.redsalud.seggpsnebul.screens.map

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.JsonObject
import com.redsalud.seggpsnebul.data.remote.ZonaDto
import com.redsalud.seggpsnebul.map.LocalTileServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon as GjPolygon

private val RIOJA_CENTER      = LatLng(-6.058, -77.160)
private const val DEFAULT_ZOOM  = 14.0
private const val SOURCE_ID     = "users-source"
private const val LAYER_CIRCLES = "users-circles"
private const val ZONES_SOURCE  = "zones-source"
private const val ZONES_FILL    = "zones-fill"
private const val ZONES_LINE    = "zones-line"
private const val ZONES_LABELS_SOURCE = "zones-labels-source"
private const val ZONES_LABELS_LAYER  = "zones-labels"
private const val ALERTS_SOURCE       = "alerts-source"
private const val ALERTS_LAYER        = "alerts-circles"

actual @Composable fun MapLibreView(
    modifier: Modifier,
    pmtilesPath: String?,
    userPositions: List<UserPosition>,
    myPosition: UserPosition?,
    zonas: List<ZonaDto>,
    alerts: List<AlertMarker>,
    assignedBlockName: String?,
    onAlertOnWay: (String) -> Unit,
    onAlertAttended: (String) -> Unit
) {
    var tileServer by remember { mutableStateOf<LocalTileServer?>(null) }
    val mapHolder  = remember { MapHolder() }

    DisposableEffect(pmtilesPath) {
        onDispose { tileServer?.stop(); tileServer = null }
    }

    // Start the tile server on IO — avoids blocking the main thread and ensures
    // the server is truly accepting connections before MapLibre requests tiles.
    LaunchedEffect(pmtilesPath) {
        tileServer = null
        if (pmtilesPath == null) return@LaunchedEffect
        tileServer = withContext(Dispatchers.IO) {
            runCatching { LocalTileServer(pmtilesPath).also { it.start() } }.getOrNull()
        }
    }

    val positionsRef = remember { mutableStateOf<List<UserPosition>>(emptyList()) }
    val zonasRef     = remember { mutableStateOf<List<ZonaDto>>(emptyList()) }
    val alertsRef    = remember { mutableStateOf<List<AlertMarker>>(emptyList()) }

    LaunchedEffect(userPositions, myPosition) {
        positionsRef.value = buildList { myPosition?.let { add(it) }; addAll(userPositions) }
    }
    LaunchedEffect(zonas)  { zonasRef.value  = zonas }
    LaunchedEffect(alerts) { alertsRef.value = alerts }

    var selectedUser  by remember { mutableStateOf<UserPosition?>(null) }
    var selectedAlert by remember { mutableStateOf<AlertMarker?>(null) }
    var selectedZone  by remember { mutableStateOf<String?>(null) }

    val context     = LocalContext.current
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // If the server became ready after the map was already loaded, upgrade the style
    // and re-activate LocationComponent (LocationComponent is style-bound).
    LaunchedEffect(tileServer) {
        val server = tileServer ?: return@LaunchedEffect
        val map    = mapHolder.map ?: return@LaunchedEffect
        map.setStyle(Style.Builder().fromJson(buildStyleJson(server))) { style ->
            applyMapLayers(style, positionsRef, zonasRef, alertsRef)
            enableLocationComponent(context, map, style)
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    getMapAsync { map ->
                        mapHolder.map = map
                        map.cameraPosition = CameraPosition.Builder()
                            .target(RIOJA_CENTER).zoom(DEFAULT_ZOOM).build()
                        map.setStyle(Style.Builder().fromJson(buildStyleJson(tileServer))) { style ->
                            applyMapLayers(style, positionsRef, zonasRef, alertsRef)
                            enableLocationComponent(ctx, map, style)
                        }
                        map.addOnMapClickListener { latLng ->
                            val pt = map.projection.toScreenLocation(latLng)
                            // Alertas > usuarios > zonas en orden de prioridad al hacer click.
                            val alertHits = map.queryRenderedFeatures(pt, ALERTS_LAYER)
                            if (alertHits.isNotEmpty()) {
                                val aid = alertHits[0].getStringProperty("alertId") ?: ""
                                selectedAlert = alertsRef.value.find { it.id == aid }
                                selectedUser  = null
                                selectedZone  = null
                                return@addOnMapClickListener selectedAlert != null
                            }
                            val userHits = map.queryRenderedFeatures(pt, LAYER_CIRCLES)
                            if (userHits.isNotEmpty()) {
                                val uid = userHits[0].getStringProperty("userId") ?: ""
                                selectedUser  = positionsRef.value.find { it.userId == uid }
                                selectedAlert = null
                                selectedZone  = null
                                return@addOnMapClickListener selectedUser != null
                            }
                            val zoneHits = map.queryRenderedFeatures(pt, ZONES_FILL)
                            if (zoneHits.isNotEmpty()) {
                                selectedZone  = zoneHits[0].getStringProperty("nombre")
                                selectedUser  = null
                                selectedAlert = null
                                return@addOnMapClickListener selectedZone != null
                            }
                            selectedUser  = null
                            selectedAlert = null
                            selectedZone  = null
                            false
                        }
                        onCreate(null)
                    }
                }
            },
            update = { _ ->
                val style = mapHolder.map?.style ?: return@AndroidView
                style.getSourceAs<GeoJsonSource>(SOURCE_ID)
                    ?.setGeoJson(buildFeatureCollection(positionsRef.value))
                style.getSourceAs<GeoJsonSource>(ZONES_SOURCE)
                    ?.setGeoJson(buildZonesCollection(zonasRef.value))
                style.getSourceAs<GeoJsonSource>(ZONES_LABELS_SOURCE)
                    ?.setGeoJson(buildZoneLabelsCollection(zonasRef.value))
                style.getSourceAs<GeoJsonSource>(ALERTS_SOURCE)
                    ?.setGeoJson(buildAlertsCollection(alertsRef.value))
            },
            modifier = Modifier.fillMaxSize()
        )

        // FAB "Zoom a mi manzana": aparece SIEMPRE que el trabajador tenga manzana
        // asignada. El lookup en `zonas` se hace al pulsar — si no se encuentra
        // (KML no publicado, nombre distinto), mostramos Toast con el motivo en
        // vez de ocultar el boton silenciosamente.
        val rawAssigned = assignedBlockName?.trim()?.takeIf { it.isNotEmpty() }
        if (rawAssigned != null) {
            FloatingActionButton(
                onClick = {
                    val map = mapHolder.map ?: return@FloatingActionButton
                    val zonasNow = zonasRef.value
                    if (zonasNow.isEmpty()) {
                        Toast.makeText(context,
                            "Manzanas KML aún no cargadas. Verifica conexión.",
                            Toast.LENGTH_SHORT).show()
                        return@FloatingActionButton
                    }
                    // Match por nombre (case-insensitive) o como prefijo si el
                    // nombre publicado es "MZ 312" y el assignment dice "Manzana 1".
                    val zona = zonasNow.firstOrNull {
                        it.nombre.trim().equals(rawAssigned, ignoreCase = true)
                    } ?: zonasNow.firstOrNull {
                        val n = it.nombre.trim()
                        n.startsWith(rawAssigned, ignoreCase = true) ||
                            rawAssigned.startsWith(n, ignoreCase = true)
                    }
                    if (zona == null) {
                        Toast.makeText(context,
                            "Manzana \"$rawAssigned\" no esta publicada en el KML",
                            Toast.LENGTH_LONG).show()
                        return@FloatingActionButton
                    }
                    val bounds = polygonBounds(zona)
                    if (bounds != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                    } else {
                        Toast.makeText(context, "Geometria de la manzana invalida",
                            Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 88.dp),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text("🎯", style = MaterialTheme.typography.titleLarge)
            }
        }

        // FAB "mi ubicacion": prefiere myPosition (track del foreground service);
        // si no hay fix todavia (sin jornada activa o GPS recien arrancado),
        // pide un getCurrentLocation one-shot al FusedLocationProviderClient.
        FloatingActionButton(
            onClick  = {
                val map = mapHolder.map ?: return@FloatingActionButton
                val mp  = myPosition
                if (mp != null) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(mp.latitude, mp.longitude), 17.0))
                    return@FloatingActionButton
                }
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Toast.makeText(context, "Falta permiso de ubicación", Toast.LENGTH_SHORT).show()
                    return@FloatingActionButton
                }
                @Suppress("MissingPermission")
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17.0)
                            )
                        } else {
                            Toast.makeText(context, "Sin señal GPS aún. Prueba al aire libre.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error obteniendo ubicación", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text("📍", style = MaterialTheme.typography.titleLarge)
        }

        selectedUser?.let { user ->
            UserPopupCard(
                user      = user,
                modifier  = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                onDismiss = { selectedUser = null }
            )
        }

        selectedAlert?.let { alert ->
            AlertPopupCard(
                alert     = alert,
                modifier  = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                onOnWay   = { onAlertOnWay(alert.id); selectedAlert = null },
                onAttended = { onAlertAttended(alert.id); selectedAlert = null },
                onDismiss = { selectedAlert = null }
            )
        }

        selectedZone?.let { name ->
            ZonePopupCard(
                name      = name,
                modifier  = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                onDismiss = { selectedZone = null }
            )
        }
    }
}

/**
 * Activa el puck de "mi ubicacion" estilo Google Maps. Sin permiso ACCESS_FINE_LOCATION
 * el componente queda inactivo silenciosamente. Es seguro llamarlo varias veces (cada
 * vez que se reaplica el style) — MapLibre re-vincula el componente al nuevo style.
 */
private fun enableLocationComponent(
    context: android.content.Context,
    map: MapLibreMap,
    style: Style
) {
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted) return
    runCatching {
        val options = LocationComponentOptions.builder(context)
            .pulseEnabled(true)
            .accuracyAnimationEnabled(true)
            .build()
        val activation = LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            .useDefaultLocationEngine(true)
            .build()
        with(map.locationComponent) {
            @Suppress("MissingPermission")
            activateLocationComponent(activation)
            @Suppress("MissingPermission")
            isLocationComponentEnabled = true
            cameraMode = CameraMode.NONE
            renderMode = RenderMode.COMPASS
        }
    }
}

private fun applyMapLayers(
    style: Style,
    positionsRef: State<List<UserPosition>>,
    zonasRef: State<List<ZonaDto>>,
    alertsRef: State<List<AlertMarker>>
) {
    style.addSource(GeoJsonSource(ZONES_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        FillLayer(ZONES_FILL, ZONES_SOURCE).withProperties(
            fillColor(get("color")),
            fillOpacity(0.18f)
        )
    )
    style.addLayer(
        LineLayer(ZONES_LINE, ZONES_SOURCE).withProperties(
            lineColor(get("color")),
            lineWidth(2.2f),
            lineOpacity(0.85f)
        )
    )
    // Etiquetas de zona (MZ A, MZ B…) en el centroide del poligono.
    style.addSource(GeoJsonSource(ZONES_LABELS_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        SymbolLayer(ZONES_LABELS_LAYER, ZONES_LABELS_SOURCE).withProperties(
            textField(get("nombre")),
            textFont(arrayOf("Open Sans Regular")),
            textSize(13f),
            textColor("#1a1a1a"),
            textHaloColor("#ffffff"),
            textHaloWidth(1.6f),
            textAllowOverlap(true),
            textIgnorePlacement(true)
        )
    )
    style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(LAYER_CIRCLES, SOURCE_ID).withProperties(
            circleRadius(9f),
            circleColor(get("color")),
            circleStrokeWidth(2f),
            circleStrokeColor("#ffffff")
        )
    )
    // Alertas: marker mas grande con color por estado.
    style.addSource(GeoJsonSource(ALERTS_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer(ALERTS_LAYER, ALERTS_SOURCE).withProperties(
            circleRadius(13f),
            circleColor(get("color")),
            circleStrokeWidth(3f),
            circleStrokeColor("#ffffff"),
            circleOpacity(0.92f)
        )
    )
    positionsRef.value.takeIf { it.isNotEmpty() }?.let {
        style.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(buildFeatureCollection(it))
    }
    zonasRef.value.takeIf { it.isNotEmpty() }?.let { zonas ->
        style.getSourceAs<GeoJsonSource>(ZONES_SOURCE)?.setGeoJson(buildZonesCollection(zonas))
        style.getSourceAs<GeoJsonSource>(ZONES_LABELS_SOURCE)?.setGeoJson(buildZoneLabelsCollection(zonas))
    }
    alertsRef.value.takeIf { it.isNotEmpty() }?.let {
        style.getSourceAs<GeoJsonSource>(ALERTS_SOURCE)?.setGeoJson(buildAlertsCollection(it))
    }
}

// ─── Popup ────────────────────────────────────────────────────────────────────

@Composable
private fun UserPopupCard(user: UserPosition, modifier: Modifier, onDismiss: () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("✕")
                }
            }
            Text(roleLabel(user.role), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("Última posición: ${relativeTime(user.capturedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            user.activeAlert?.let {
                Spacer(Modifier.height(4.dp))
                Text("⚠ $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            user.assignedBlock?.let {
                Spacer(Modifier.height(2.dp))
                Text("Manzana: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AlertPopupCard(
    alert: AlertMarker,
    modifier: Modifier,
    onOnWay: () -> Unit,
    onAttended: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("⚠ ${alertTypeLabel(alert.alertType)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("✕")
                }
            }
            Text("De: ${alert.senderName}", style = MaterialTheme.typography.bodyMedium)
            Text(relativeTime(alert.createdAt), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (alert.status == "on_way") {
                Text("🟠 En camino: ${alert.responderName ?: "—"}",
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (alert.status != "on_way") {
                    OutlinedButton(onClick = onOnWay, modifier = Modifier.weight(1f)) {
                        Text("Ya voy")
                    }
                }
                Button(onClick = onAttended, modifier = Modifier.weight(1f)) {
                    Text("Atendida")
                }
            }
        }
    }
}

@Composable
private fun ZonePopupCard(name: String, modifier: Modifier, onDismiss: () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically) {
            Column {
                Text("Manzana", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                Text("✕")
            }
        }
    }
}

private fun alertTypeLabel(type: String) = when (type) {
    "agua"               -> "Agua mineral"
    "gasolina"           -> "Gasolina"
    "insumo_quimico"     -> "Insumo químico"
    "averia_maquina"     -> "Avería de máquina"
    "trabajo_finalizado" -> "Trabajo finalizado"
    "broadcast_text"     -> "Mensaje a brigada"
    else                 -> type
}

// ─── Builders ─────────────────────────────────────────────────────────────────

private class MapHolder { var map: MapLibreMap? = null }

private fun buildFeatureCollection(positions: List<UserPosition>): FeatureCollection {
    val features = positions.map { u ->
        val props = JsonObject().apply {
            addProperty("userId",    u.userId)
            addProperty("fullName",  u.fullName)
            addProperty("role",      u.role)
            addProperty("capturedAt", u.capturedAt)
            addProperty("color",     roleColor(u.role))
        }
        Feature.fromGeometry(Point.fromLngLat(u.longitude, u.latitude), props)
    }
    return FeatureCollection.fromFeatures(features)
}

private fun buildZonesCollection(zonas: List<ZonaDto>): FeatureCollection {
    val features = zonas.mapNotNull { zona ->
        runCatching {
            // zona.geojson.toString() → cadena JSON del Polygon geometry
            val geomStr = zona.geojson.toString()
            val nombre  = zona.nombre.replace("\\", "\\\\").replace("\"", "\\\"")
            val json    = """{"type":"Feature","geometry":$geomStr,"properties":{"nombre":"$nombre","color":"${zona.color}"}}"""
            Feature.fromJson(json)
        }.getOrNull()
    }
    return FeatureCollection.fromFeatures(features)
}

private fun buildAlertsCollection(alerts: List<AlertMarker>): FeatureCollection {
    val features = alerts.map { a ->
        val props = JsonObject().apply {
            addProperty("alertId",   a.id)
            addProperty("alertType", a.alertType)
            addProperty("status",    a.status)
            addProperty("color",     alertStatusColor(a.status))
        }
        Feature.fromGeometry(Point.fromLngLat(a.longitude, a.latitude), props)
    }
    return FeatureCollection.fromFeatures(features)
}

private fun alertStatusColor(status: String) = when (status) {
    "on_way" -> "#f39c12"   // ambar — alguien en camino
    else     -> "#e74c3c"   // rojo — pendiente
}

/**
 * Calcula el bounding box de una zona (Polygon GeoJSON).
 * Devuelve null si la geometria no es parseable o esta vacia.
 */
private fun polygonBounds(zona: ZonaDto): LatLngBounds? = runCatching {
    val poly = GjPolygon.fromJson(zona.geojson.toString())
    val ring = poly.outer().coordinates()
    if (ring.size < 3) return@runCatching null
    var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
    var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
    for (p in ring) {
        if (p.latitude()  < minLat) minLat = p.latitude()
        if (p.latitude()  > maxLat) maxLat = p.latitude()
        if (p.longitude() < minLng) minLng = p.longitude()
        if (p.longitude() > maxLng) maxLng = p.longitude()
    }
    LatLngBounds.Builder()
        .include(LatLng(minLat, minLng))
        .include(LatLng(maxLat, maxLng))
        .build()
}.getOrNull()

/** Genera un Feature de tipo Point en el centroide de cada poligono, con la propiedad nombre. */
private fun buildZoneLabelsCollection(zonas: List<ZonaDto>): FeatureCollection {
    val features = zonas.mapNotNull { zona ->
        runCatching {
            val poly = GjPolygon.fromJson(zona.geojson.toString())
            val ring = poly.outer().coordinates()
            if (ring.size < 3) return@runCatching null
            // Centroide simple: media de las coordenadas del anillo exterior. Para poligonos
            // convexos pequeños como manzanas urbanas es indistinguible del centroide real.
            var sumLng = 0.0; var sumLat = 0.0
            for (p in ring) { sumLng += p.longitude(); sumLat += p.latitude() }
            val cx = sumLng / ring.size
            val cy = sumLat / ring.size
            val props = JsonObject().apply { addProperty("nombre", zona.nombre) }
            Feature.fromGeometry(Point.fromLngLat(cx, cy), props)
        }.getOrNull()
    }
    return FeatureCollection.fromFeatures(features)
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun roleColor(role: String) = when (role) {
    "nebulizador"  -> "#2ecc71"
    "jefe_brigada" -> "#3498db"
    "anotador"     -> "#9b59b6"
    "chofer"       -> "#f39c12"
    else           -> "#e74c3c"
}

private fun roleLabel(role: String) = when (role) {
    "nebulizador"  -> "Nebulizador"
    "jefe_brigada" -> "Jefe de Brigada"
    "anotador"     -> "Anotador"
    "chofer"       -> "Chofer / Abastecedor"
    else           -> role
}

private fun relativeTime(capturedAt: Long): String {
    val diff = System.currentTimeMillis() - capturedAt
    return when {
        diff < 60_000    -> "hace ${diff / 1_000} s"
        diff < 3_600_000 -> "hace ${diff / 60_000} min"
        else             -> "hace ${diff / 3_600_000}h"
    }
}

// ─── Map style ────────────────────────────────────────────────────────────────

private fun buildStyleJson(tileServer: LocalTileServer?): String {
    if (tileServer == null || tileServer.port == 0) {
        return """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#f5f0e8"}}]}"""
    }
    val tilesUrl = "http://localhost:${tileServer.port}/tiles/{z}/{x}/{y}.pbf"
    return omtStyle(tilesUrl, tileServer.minZoom, tileServer.maxZoom)
}

private fun omtStyle(tilesUrl: String, minZoom: Int, maxZoom: Int) = """
{
  "version": 8,
  "name": "Rioja GPS",
  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
  "sources": {
    "omt": {
      "type": "vector",
      "tiles": ["$tilesUrl"],
      "minzoom": $minZoom,
      "maxzoom": $maxZoom,
      "scheme": "xyz",
      "attribution": "© OpenStreetMap"
    }
  },
  "layers": [
    { "id": "background",  "type": "background", "paint": { "background-color": "#f5f0e8" } },
    { "id": "water",       "type": "fill",   "source": "omt", "source-layer": "water",
      "paint": { "fill-color": "#a0c8e0" } },
    { "id": "waterway",    "type": "line",   "source": "omt", "source-layer": "waterway",
      "paint": { "line-color": "#a0c8e0", "line-width": 1 } },
    { "id": "landuse_res", "type": "fill",   "source": "omt", "source-layer": "landuse",
      "filter": ["==", "class", "residential"],
      "paint": { "fill-color": "#ede8e0" } },
    { "id": "park",        "type": "fill",   "source": "omt", "source-layer": "park",
      "paint": { "fill-color": "#d2e8c8" } },
    { "id": "road_casing", "type": "line",   "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "primary", "secondary", "tertiary", "trunk", "motorway"],
      "paint": { "line-color": "#c8b090",
                 "line-width": ["interpolate", ["linear"], ["zoom"], 10, 1, 16, 6] } },
    { "id": "road_fill",   "type": "line",   "source": "omt", "source-layer": "transportation",
      "paint": { "line-color": "#ffffff",
                 "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.5, 16, 4] } },
    { "id": "building",    "type": "fill",   "source": "omt", "source-layer": "building",
      "minzoom": 13,
      "paint": { "fill-color": "#d8d0c8", "fill-outline-color": "#b8b0a8" } }
  ]
}
""".trimIndent()
