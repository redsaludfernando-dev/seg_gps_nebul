package com.redsalud.seggpsnebul.util

import com.redsalud.seggpsnebul.data.remote.ZonaDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val ZONE_COLORS = listOf(
    "#e74c3c", "#3498db", "#2ecc71", "#f39c12",
    "#9b59b6", "#1abc9c", "#e67e22", "#34495e",
    "#16a085", "#d35400", "#8e44ad", "#2980b9"
)

object KmlParser {

    /**
     * Parsea un archivo KML y devuelve una lista de ZonaDto listos para subir a Supabase.
     * Extrae cada <Placemark> que contenga un <Polygon>.
     */
    fun parseToZonas(kml: String): List<ZonaDto> {
        val placemarks = kml.split("<Placemark").drop(1)
        return placemarks.mapIndexedNotNull { idx, block ->
            val name   = extractTag(block, "name")?.trim() ?: "Manzana ${idx + 1}"
            val coords = extractCoordinates(block) ?: return@mapIndexedNotNull null
            if (coords.size < 3) return@mapIndexedNotNull null
            ZonaDto(
                id      = "zona_$idx",
                nombre  = name,
                color   = ZONE_COLORS[idx % ZONE_COLORS.size],
                geojson = buildPolygonGeometry(coords)
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractTag(text: String, tag: String): String? {
        val open  = text.indexOf("<$tag>")
        val close = text.indexOf("</$tag>")
        if (open == -1 || close == -1 || close <= open) return null
        return text.substring(open + tag.length + 2, close)
    }

    /** Extrae coordenadas del primer <outerBoundaryIs> o directamente de <coordinates>. */
    private fun extractCoordinates(block: String): List<Pair<Double, Double>>? {
        val coordText = extractTag(block, "coordinates") ?: return null
        return coordText.trim()
            .split(Regex("\\s+"))
            .mapNotNull { triplet ->
                val parts = triplet.split(",")
                if (parts.size < 2) null
                else {
                    val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
                    val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                    Pair(lon, lat)
                }
            }
            .takeIf { it.isNotEmpty() }
    }

    /** Construye un objeto GeoJSON Polygon a partir de pares (lon, lat). */
    private fun buildPolygonGeometry(coords: List<Pair<Double, Double>>): JsonElement {
        // Asegurar anillo cerrado (primer punto == último punto)
        val ring = buildList {
            addAll(coords)
            if (coords.first() != coords.last()) add(coords.first())
        }
        val ringJson = ring.joinToString(",") { (lon, lat) -> "[$lon,$lat]" }
        return Json.parseToJsonElement("""{"type":"Polygon","coordinates":[[$ringJson]]}""")
    }
}
