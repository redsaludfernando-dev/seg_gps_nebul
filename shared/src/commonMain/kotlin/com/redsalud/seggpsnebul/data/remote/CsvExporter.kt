@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.redsalud.seggpsnebul.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.redsalud.seggpsnebul.domain.model.AlertType

// ─── Fetch DTOs ───────────────────────────────────────────────────────────────

@Serializable
data class GpsRow(
    val id: String,
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val captured_at: String,
    val sync_status: String
)

@Serializable
data class AlertRow(
    val id: String,
    val sender_id: String,
    val alert_type: String,
    val message: String?,
    val latitude: Double?,
    val longitude: Double?,
    val is_attended: Boolean,
    val attended_by: String?,
    val created_at: String
)

@Serializable
data class BlockRow(
    val id: String,
    val assigned_to: String,
    val assigned_by: String,
    val block_name: String,
    val notes: String?,
    val assigned_at: String
)

@Serializable
data class SessionRow(
    val id: String,
    val name: String,
    val started_by: String,
    val started_at: String,
    val ended_at: String?
)

@Serializable
data class UserNameRow(
    val id: String,
    val full_name: String,
    val role: String
)

// ─── Builder ──────────────────────────────────────────────────────────────────

/**
 * Fetches session data from Supabase and builds a CSV string.
 * Platform-agnostic: does NOT write files or touch local DB.
 */
class CsvExporter {

    suspend fun buildCsv(sessionId: String): Result<Pair<String, String>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val session = supabaseClient.postgrest["sessions"]
                    .select { filter { eq("id", sessionId) } }
                    .decodeList<SessionRow>().firstOrNull()
                    ?: error("Sesión no encontrada: $sessionId")

                val users = supabaseClient.postgrest["users"]
                    .select()
                    .decodeList<UserNameRow>()
                    .associateBy { it.id }

                val tracks = supabaseClient.postgrest["gps_tracks"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<GpsRow>()
                    .sortedBy { it.captured_at }

                val alerts = supabaseClient.postgrest["alerts"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<AlertRow>()
                    .sortedBy { it.created_at }

                val blocks = supabaseClient.postgrest["block_assignments"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<BlockRow>()
                    .sortedBy { it.assigned_at }

                val csv = buildString {
                    val workerName = users[session.started_by]?.full_name ?: session.started_by
                    val workerRole = users[session.started_by]?.role ?: ""
                    val durationMin = session.ended_at?.let {
                    (Instant.parse(it).toEpochMilliseconds() - Instant.parse(session.started_at).toEpochMilliseconds()) / 60_000
                } ?: 0L

                    appendLine("## SESION")
                    appendLine("id,nombre,iniciada_por,cargo,inicio_utc,fin_utc,duracion_min,puntos_gps,alertas,manzanas")
                    appendLine(row(session.id, q(session.name), q(workerName), workerRole,
                        fmtTs(session.started_at), session.ended_at?.let { fmtTs(it) } ?: "EN_CURSO",
                        durationMin, tracks.size, alerts.size, blocks.size))

                    appendLine("\n## PUNTOS_GPS")
                    appendLine("id,usuario,cargo,latitud,longitud,precision_m,capturado_utc")
                    tracks.forEach { t ->
                        val u = users[t.user_id]
                        appendLine(row(t.id, q(u?.full_name ?: t.user_id), u?.role ?: "",
                            t.latitude, t.longitude, t.accuracy ?: "", fmtTs(t.captured_at)))
                    }

                    appendLine("\n## ALERTAS")
                    appendLine("id,tipo,descripcion,enviado_por,cargo,mensaje,latitud,longitud,creado_utc,atendida,atendida_por")
                    alerts.forEach { a ->
                        val sender = users[a.sender_id]
                        val attendedBy = a.attended_by?.let { users[it]?.full_name } ?: ""
                        appendLine(row(a.id, a.alert_type, q(AlertType.labelFor(a.alert_type)),
                            q(sender?.full_name ?: a.sender_id), sender?.role ?: "",
                            q(a.message ?: ""), a.latitude ?: "", a.longitude ?: "",
                            fmtTs(a.created_at), if (a.is_attended) "SI" else "NO", q(attendedBy)))
                    }

                    appendLine("\n## MANZANAS")
                    appendLine("id,asignado_a,cargo,asignado_por,manzana,notas,asignado_utc")
                    blocks.forEach { b ->
                        val to = users[b.assigned_to]
                        val by = users[b.assigned_by]
                        appendLine(row(b.id, q(to?.full_name ?: b.assigned_to), to?.role ?: "",
                            q(by?.full_name ?: b.assigned_by), q(b.block_name),
                            q(b.notes ?: ""), fmtTs(b.assigned_at)))
                    }
                }

                val safeName = session.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val filename  = "${safeName}_${sessionId.take(8)}.csv"
                Pair(csv, filename)
            }
        }

    private fun fmtTs(iso: String): String = iso.take(19).replace("T", " ")

    private fun q(s: String): String = "\"${s.replace("\"", "\"\"")}\""
    private fun row(vararg values: Any?): String = values.joinToString(",") { it?.toString() ?: "" }
}
