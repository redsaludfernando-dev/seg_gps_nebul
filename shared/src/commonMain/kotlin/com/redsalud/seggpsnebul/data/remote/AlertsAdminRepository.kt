package com.redsalud.seggpsnebul.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class AlertAdminDto(
    val id: String,
    val sender_id: String,
    val session_id: String,
    val alert_type: String,
    val message: String? = null,
    val target_role: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val is_attended: Boolean,
    val attended_by: String? = null,
    val created_at: String
)

class AlertsAdminRepository {

    suspend fun fetchBySession(sessionId: String): Result<List<AlertAdminDto>> =
        withContext(Dispatchers.Default) {
            runCatching {
                supabaseClient.postgrest["alerts"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<AlertAdminDto>()
                    .sortedByDescending { it.created_at }
            }
        }

    suspend fun markAttended(alertId: String, attendedBy: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                supabaseClient.postgrest["alerts"].update({
                    set("is_attended", true)
                    set("attended_by", attendedBy)
                }) { filter { eq("id", alertId) } }
                Unit
            }
        }

    suspend fun delete(alertId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["alerts"]
                .delete { filter { eq("id", alertId) } }
            Unit
        }
    }
}
