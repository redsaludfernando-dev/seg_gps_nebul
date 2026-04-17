package com.redsalud.seggpsnebul.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ZonaDto(
    val id: String,
    val nombre: String,
    val color: String = "#e74c3c",
    val geojson: JsonElement   // GeoJSON Polygon geometry (almacenado como JSONB en Supabase)
)
