@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.data.local.LocalDataSource
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
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
    val created_at: String,
    val response_status: String? = null,
    val response_by: String? = null,
    val responded_at: String? = null
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
                    id              = a.id,
                    sender_id       = a.sender_id,
                    session_id      = a.session_id,
                    alert_type      = a.alert_type,
                    message         = a.message,
                    target_role     = a.target_role,
                    latitude        = a.latitude,
                    longitude       = a.longitude,
                    is_attended     = a.is_attended == 1L,
                    attended_by     = a.attended_by,
                    created_at      = Instant.fromEpochMilliseconds(a.created_at).toString(),
                    response_status = a.response_status,
                    response_by     = a.response_by,
                    responded_at    = a.responded_at?.let { Instant.fromEpochMilliseconds(it).toString() }
                )
            }
            supabaseClient.postgrest["alerts"].upsert(dtos)
            pending.forEach { localDataSource.markAlertSynced(it.id) }
        }
    }

    /**
     * PULL: trae alertas activas de cualquier sesion abierta y las upserta
     * localmente como 'synced'. Permite que cualquier dispositivo (incluido
     * el admin web) vea y conteste alertas aunque no las haya creado.
     */
    suspend fun pullActiveAlerts() = withContext(Dispatchers.IO) {
        runCatching {
            // is_attended=false cubre {pendiente, en_camino} — cuando alguien la cierre
            // pasa a is_attended=true y deja de aparecer en este pull.
            val remote = supabaseClient.postgrest["alerts"]
                .select { filter { eq("is_attended", false) } }
                .decodeList<AlertDto>()
            for (a in remote) {
                val createdMs = runCatching { Instant.parse(a.created_at).toEpochMilliseconds() }
                    .getOrElse { continue }
                val respondedMs = a.responded_at?.let {
                    runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull()
                }
                localDataSource.upsertRemoteAlert(
                    id              = a.id,
                    senderId        = a.sender_id,
                    sessionId       = a.session_id,
                    alertType       = a.alert_type,
                    message         = a.message,
                    targetRole      = a.target_role,
                    latitude        = a.latitude,
                    longitude       = a.longitude,
                    isAttended      = a.is_attended,
                    attendedBy      = a.attended_by,
                    createdAt       = createdMs,
                    responseStatus  = a.response_status,
                    responseBy      = a.response_by,
                    respondedAt     = respondedMs
                )
            }
            Unit
        }
    }

    /** Marca una alerta con response_status='on_way' en Supabase. */
    suspend fun pushAlertOnWay(alertId: String, responderId: String, respondedAtMs: Long) =
        withContext(Dispatchers.IO) {
            runCatching {
                supabaseClient.postgrest["alerts"].update({
                    set("response_status", "on_way")
                    set("response_by", responderId)
                    set("responded_at", Instant.fromEpochMilliseconds(respondedAtMs).toString())
                }) { filter { eq("id", alertId) } }
                Unit
            }
        }

    /** Marca una alerta como atendida en Supabase. */
    suspend fun pushAlertAttended(alertId: String, attendedBy: String, respondedAtMs: Long) =
        withContext(Dispatchers.IO) {
            runCatching {
                supabaseClient.postgrest["alerts"].update({
                    set("is_attended", true)
                    set("attended_by", attendedBy)
                    set("response_status", "attended")
                    set("responded_at", Instant.fromEpochMilliseconds(respondedAtMs).toString())
                }) { filter { eq("id", alertId) } }
                Unit
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
