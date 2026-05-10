@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.data.local.LocalDataSource
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
private data class GpsTrackDto(
    val id: String,
    val user_id: String,
    val session_id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val captured_at: String   // ISO 8601 — Supabase espera TIMESTAMPTZ
)

class GpsSyncRepository(private val localDataSource: LocalDataSource) {

    suspend fun syncPendingTracks() = withContext(Dispatchers.IO) {
        runCatching {
            val pending = localDataSource.getPendingGpsTracks()
            if (pending.isEmpty()) return@runCatching
            val dtos = pending.map { t ->
                GpsTrackDto(
                    id          = t.id,
                    user_id     = t.user_id,
                    session_id  = t.session_id,
                    latitude    = t.latitude,
                    longitude   = t.longitude,
                    accuracy    = t.accuracy,
                    captured_at = Instant.fromEpochMilliseconds(t.captured_at).toString()
                )
            }
            supabaseClient.postgrest["gps_tracks"].upsert(dtos)
            pending.forEach { localDataSource.markGpsTrackSynced(it.id) }
        }
    }
}
