package com.redsalud.seggpsnebul.screens.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonObject
import com.redsalud.seggpsnebul.map.LocalTileServer
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private val RIOJA_CENTER = LatLng(-6.058, -77.160)
private const val DEFAULT_ZOOM = 14.0
private const val SOURCE_ID    = "users-source"
private const val LAYER_CIRCLES = "users-circles"

actual @Composable fun MapLibreView(
    modifier: Modifier,
    pmtilesPath: String?,
    userPositions: List<UserPosition>,
    myPosition: UserPosition?
) {
    val tileServer = remember {
        pmtilesPath?.let { LocalTileServer(it).also { s -> s.start() } }
    }
    DisposableEffect(Unit) { onDispose { tileServer?.stop() } }

    // Non-composable callbacks (click listener) need a stable reference to current positions
    val positionsRef = remember { mutableStateOf<List<UserPosition>>(emptyList()) }
    LaunchedEffect(userPositions, myPosition) {
        positionsRef.value = buildList {
            myPosition?.let { add(it) }
            addAll(userPositions)
        }
    }

    var selectedUser by remember { mutableStateOf<UserPosition?>(null) }
    val mapHolder   = remember { MapHolder() }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).apply {
                    getMapAsync { map ->
                        mapHolder.map = map
                        map.setStyle(Style.Builder().fromJson(buildStyleJson(tileServer?.port))) { style ->
                            map.cameraPosition = CameraPosition.Builder()
                                .target(RIOJA_CENTER).zoom(DEFAULT_ZOOM).build()

                            // Add empty source + circle layer for user positions
                            style.addSource(
                                GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList()))
                            )
                            style.addLayer(
                                CircleLayer(LAYER_CIRCLES, SOURCE_ID).withProperties(
                                    circleRadius(9f),
                                    circleColor(get("color")),
                                    circleStrokeWidth(2f),
                                    circleStrokeColor("#ffffff")
                                )
                            )

                            // Render current positions immediately if already available
                            positionsRef.value.takeIf { it.isNotEmpty() }?.let {
                                style.getSourceAs<GeoJsonSource>(SOURCE_ID)
                                    ?.setGeoJson(buildFeatureCollection(it))
                            }

                            // Tap on a circle → show popup
                            map.addOnMapClickListener { latLng ->
                                val screenPt = map.projection.toScreenLocation(latLng)
                                val features = map.queryRenderedFeatures(screenPt, LAYER_CIRCLES)
                                if (features.isNotEmpty()) {
                                    val uid = features[0].getStringProperty("userId") ?: ""
                                    selectedUser = positionsRef.value.find { it.userId == uid }
                                    selectedUser != null
                                } else {
                                    selectedUser = null
                                    false
                                }
                            }
                        }
                        onCreate(null)
                    }
                }
            },
            update = { _ ->
                // Called on every recomposition — updates markers with latest positions
                mapHolder.map?.style?.getSourceAs<GeoJsonSource>(SOURCE_ID)
                    ?.setGeoJson(buildFeatureCollection(positionsRef.value))
            },
            modifier = Modifier.fillMaxSize()
        )

        // Compose popup overlay — shown above the map when a user is tapped
        selectedUser?.let { user ->
            UserPopupCard(
                user      = user,
                modifier  = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onDismiss = { selectedUser = null }
            )
        }
    }
}

// ─── Popup card ──────────────────────────────────────────────────────────────

@Composable
private fun UserPopupCard(user: UserPosition, modifier: Modifier, onDismiss: () -> Unit) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("✕", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                text  = roleLabel(user.role),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "Última posición: ${relativeTime(user.capturedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            user.activeAlert?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "⚠ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            user.assignedBlock?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "Manzana: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

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

private fun roleColor(role: String) = when (role) {
    "nebulizador"  -> "#2ecc71"
    "jefe_brigada" -> "#3498db"
    "anotador"     -> "#9b59b6"
    "chofer"       -> "#f39c12"
    else           -> "#e74c3c"   // yo / desconocido
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
        diff < 60_000     -> "hace ${diff / 1_000} s"
        diff < 3_600_000  -> "hace ${diff / 60_000} min"
        else              -> {
            val h = diff / 3_600_000
            "hace ${h}h"
        }
    }
}

// ─── Map style ───────────────────────────────────────────────────────────────

private fun buildStyleJson(tileServerPort: Int?): String {
    if (tileServerPort == null) {
        // Fallback when no PMTiles available: plain background
        return """{"version":8,"sources":{},"layers":[{"id":"bg","type":"background","paint":{"background-color":"#f5f0e8"}}]}"""
    }
    val tilesUrl = "pmtiles://http://localhost:$tileServerPort/tiles.pmtiles"
    return omtStyle(tilesUrl)
}

private fun omtStyle(tilesUrl: String) = """
{
  "version": 8,
  "name": "Rioja GPS",
  "sources": {
    "omt": {
      "type": "vector",
      "url": "$tilesUrl",
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
      "paint": { "fill-color": "#d8d0c8", "fill-outline-color": "#b8b0a8" } },
    { "id": "place_city",  "type": "symbol", "source": "omt", "source-layer": "place",
      "filter": ["in", "class", "city", "town", "village"],
      "layout": { "text-field": ["get", "name:es"], "text-size": 12,
                  "text-font": ["Open Sans Regular"] },
      "paint": { "text-color": "#333", "text-halo-color": "#fff", "text-halo-width": 1 } }
  ]
}
""".trimIndent()
