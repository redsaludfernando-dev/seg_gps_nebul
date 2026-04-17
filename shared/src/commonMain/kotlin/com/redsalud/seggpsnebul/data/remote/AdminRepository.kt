package com.redsalud.seggpsnebul.data.remote

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── DTOs (admin read-only, no PIN) ──────────────────────────────────────────

@Serializable
data class SessionAdminDto(
    val id: String,
    val name: String,
    val brigade_code: String?,
    val started_by: String,
    val started_at: Long,
    val ended_at: Long?,
    val is_active: Boolean,
    val export_done: Boolean
)

@Serializable
data class UserAdminDto(
    val id: String,
    val dni: String,
    val full_name: String,
    val role: String,
    val phone_number: String,
    @SerialName("is_active") val isActive: Boolean
)

data class SessionStats(
    val sessionId: String,
    val trackCount: Int,
    val alertCount: Int,
    val blockCount: Int
)

// ─── Repository ───────────────────────────────────────────────────────────────

class AdminRepository {

    /** All sessions from Supabase, newest first. */
    suspend fun fetchSessions(): Result<List<SessionAdminDto>> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["sessions"]
                .select()
                .decodeList<SessionAdminDto>()
                .sortedByDescending { it.started_at }
        }
    }

    /** All workers from Supabase (no PINs). */
    suspend fun fetchUsers(): Result<List<UserAdminDto>> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["users"]
                .select()
                .decodeList<UserAdminDto>()
                .sortedBy { it.full_name }
        }
    }

    /** Row counts for GPS tracks, alerts, and block assignments for a session. */
    suspend fun fetchSessionStats(sessionId: String): Result<SessionStats> = withContext(Dispatchers.Default) {
        runCatching {
            val tracks = supabaseClient.postgrest["gps_tracks"]
                .select {
                    count(Count.EXACT)
                    filter { eq("session_id", sessionId) }
                }
                .countOrNull() ?: 0L

            val alerts = supabaseClient.postgrest["alerts"]
                .select {
                    count(Count.EXACT)
                    filter { eq("session_id", sessionId) }
                }
                .countOrNull() ?: 0L

            val blocks = supabaseClient.postgrest["block_assignments"]
                .select {
                    count(Count.EXACT)
                    filter { eq("session_id", sessionId) }
                }
                .countOrNull() ?: 0L

            SessionStats(sessionId, tracks.toInt(), alerts.toInt(), blocks.toInt())
        }
    }

    /** Activates or deactivates a worker on the server. */
    suspend fun setUserActive(userId: String, active: Boolean): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["users"]
                .update({ set("is_active", active) }) { filter { eq("id", userId) } }
            Unit
        }
    }

    /** Deletes all GPS tracks for a session from Supabase (call after export). */
    suspend fun deleteGpsTracksFromServer(sessionId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["gps_tracks"]
                .delete { filter { eq("session_id", sessionId) } }
            Unit
        }
    }
}
