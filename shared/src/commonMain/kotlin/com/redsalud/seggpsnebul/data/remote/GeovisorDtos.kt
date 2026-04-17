package com.redsalud.seggpsnebul.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackRaw(
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val captured_at: Long
)

@Serializable
data class UserMinDto(
    val id: String,
    val full_name: String,
    val role: String
)

data class WorkerPositionDto(
    val userId: String,
    val fullName: String,
    val role: String,
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long
)

data class TrackSegment(
    val userId: String,
    val fullName: String,
    val role: String,
    val points: List<TrackPoint>
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val capturedAt: Long
)
