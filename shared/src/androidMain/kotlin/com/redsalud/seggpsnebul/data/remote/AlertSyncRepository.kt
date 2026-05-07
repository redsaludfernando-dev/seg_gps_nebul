package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.data.local.LocalDataSource
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// ─── DTOs ────────────────────────────────────────────────────────────────────

@Serializable
private data class SessionDto(
    val id: String,
    val name: String,
    val brigade_code: String?,
    val started_by: String,
    val started_at: String,
    val ended_at: String?,
    val is_active: Boolean,
    val export_done: Boolean
)

@Serializable
private data class AlertDto(
    val id: String,
    val sender_id: String,
    val session_id: String,
    val alert_type: String,
    val message: String?,
    val target_role: String,
    val latitude: Double?,
    val longitude: Double?,
    val is_attended: Boolean,
    val attended_by: String?,
    val created_at: String
)

@Serializable
private data class BlockAssignmentDto(
    val id: String,
    val session_id: String?,
    val assigned_to: String,
    val assigned_by: String,
    val block_name: String,
    val notes: String?,
    val assigned_at: String
)

// ─── Repository ───────────────────────────────────────────────────────────────

class AlertSyncRepository(private val localDataSource: LocalDataSource) {

    /**
     * Upserts all local sessions to Supabase.
     * Must be called BEFORE syncing GPS tracks, alerts, or blocks (FK constraints).
     */
    suspend fun syncPendingSessions() = withContext(Dispatchers.IO) {
        runCatching {
            val sessions = localDataSource.getAllSessions()
            if (sessions.isEmpty()) return@runCatching
            val dtos = sessions.map { s ->
                SessionDto(
                    id           = s.id,
                    name         = s.name,
                    brigade_code = s.brigade_code,
                    started_by   = s.started_by,
                    started_at   = Instant.fromEpochMilliseconds(s.started_at).toString(),
                    ended_at     = s.ended_at?.let { Instant.fromEpochMilliseconds(it).toString() },
                    is_active    = s.is_active == 1L,
                    export_done  = s.export_done == 1L
                )
            }
            supabaseClient.postgrest["sessions"].upsert(dtos)
        }
    }

    /** Syncs pending (unsynced) alerts to Supabase. */
    suspend fun syncPendingAlerts() = withContext(Dispatchers.IO) {
        runCatching {
            val pending = localDataSource.getPendingAlerts()
            if (pending.isEmpty()) return@runCatching
            val dtos = pending.map { a ->
                AlertDto(
                    id          = a.id,
                    sender_id   = a.sender_id,
                    session_id  = a.session_id,
                    alert_type  = a.alert_type,
                    message     = a.message,
                    target_role = a.target_role,
                    latitude    = a.latitude,
                    longitude   = a.longitude,
                    is_attended = a.is_attended == 1L,
                    attended_by = a.attended_by,
                    created_at  = Instant.fromEpochMilliseconds(a.created_at).toString()
                )
            }
            supabaseClient.postgrest["alerts"].upsert(dtos)
            pending.forEach { localDataSource.markAlertSynced(it.id) }
        }
    }

    /** Syncs pending block assignments to Supabase. */
    suspend fun syncPendingBlockAssignments() = withContext(Dispatchers.IO) {
        runCatching {
            val pending = localDataSource.getPendingBlockAssignments()
            if (pending.isEmpty()) return@runCatching
            val dtos = pending.map { b ->
                BlockAssignmentDto(
                    id          = b.id,
                    session_id  = b.session_id,
                    assigned_to = b.assigned_to,
                    assigned_by = b.assigned_by,
                    block_name  = b.block_name,
                    notes       = b.notes,
                    assigned_at = Instant.fromEpochMilliseconds(b.assigned_at).toString()
                )
            }
            supabaseClient.postgrest["block_assignments"].upsert(dtos)
            pending.forEach { localDataSource.markBlockAssignmentSynced(it.id) }
        }
    }

    /** Convenience: sync all pending data in the correct dependency order. */
    suspend fun syncAll() {
        syncPendingSessions()
        syncPendingAlerts()
        syncPendingBlockAssignments()
    }
}
