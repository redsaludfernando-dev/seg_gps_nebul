package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.domain.model.AlertType
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Genera CSV con KPIs de rendimiento por trabajador.
 * - Distancia (Haversine entre puntos GPS consecutivos)
 * - Velocidad media y máxima
 * - Tiempo activo y tiempo inactivo (gaps > 3 min entre puntos)
 * - Alertas por tipo (agua, gasolina, insumo, avería, finalizado, mensaje)
 * - Manzanas asignadas
 */
@OptIn(ExperimentalTime::class)
class MetricsExporter {

    @Serializable
    private data class UserMeta(
        val id: String,
        val dni: String,
        val full_name: String,
        val role: String
    )

    @Serializable
    private data class SessionMeta(
        val id: String,
        val name: String,
        val brigade_code: String?,
        val started_at: String,
        val ended_at: String?
    )

    private data class WorkerStats(
        val userId: String,
        val fullName: String,
        val role: String,
        val dni: String,
        var gpsPoints: Int = 0,
        var distanceKm: Double = 0.0,
        var maxSpeedKmh: Double = 0.0,
        var firstTs: Long? = null,
        var lastTs: Long? = null,
        var idleMs: Long = 0,
        val alertCounts: MutableMap<String, Int> = AlertType.entries.associate { it.value to 0 }.toMutableMap(),
        var manzanasAssigned: Int = 0
    ) {
        val totalAlerts: Int get() = alertCounts.values.sum()
        val activeMinutes: Long get() {
            val f = firstTs; val l = lastTs
            return if (f != null && l != null && l > f) (l - f) / 60_000 else 0
        }
        val idleMinutes: Long get() = idleMs / 60_000
        val movingMinutes: Long get() = (activeMinutes - idleMinutes).coerceAtLeast(0)
        val avgSpeedKmh: Double get() =
            if (movingMinutes > 0) distanceKm / (movingMinutes / 60.0) else 0.0
    }

    /** Métricas detalladas de UNA jornada: cabecera + fila por trabajador + total. */
    suspend fun buildSessionMetrics(sessionId: String): Result<Pair<String, String>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val session = supabaseClient.postgrest["sessions"]
                    .select { filter { eq("id", sessionId) } }
                    .decodeList<SessionMeta>().firstOrNull()
                    ?: error("Sesión no encontrada")

                val users = supabaseClient.postgrest["users"]
                    .select()
                    .decodeList<UserMeta>()
                    .associateBy { it.id }

                val tracks = supabaseClient.postgrest["gps_tracks"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<GpsRow>()

                val alerts = supabaseClient.postgrest["alerts"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<AlertRow>()

                val blocks = supabaseClient.postgrest["block_assignments"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<BlockRow>()

                val statsByUser = aggregate(tracks, alerts, blocks, users)

                val csv = renderSessionCsv(session, statsByUser.values.toList())
                val safeName = session.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                Pair(csv, "metricas_${safeName}_${sessionId.take(8)}.csv")
            }
        }

    /** Métricas globales: una fila (session × trabajador) por toda la historia. */
    suspend fun buildAllMetrics(): Result<Pair<String, String>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val sessions = supabaseClient.postgrest["sessions"]
                    .select()
                    .decodeList<SessionMeta>()
                    .sortedByDescending { it.started_at }

                val users = supabaseClient.postgrest["users"]
                    .select()
                    .decodeList<UserMeta>()
                    .associateBy { it.id }

                val csv = buildString {
                    appendLine("# Métricas globales · todas las jornadas")
                    appendLine("# Generado: ${fmtTs(Clock.System.now().toString())}")
                    appendLine()
                    appendLine(headerRow(prependSession = true))

                    sessions.forEach { s ->
                        val tracks = supabaseClient.postgrest["gps_tracks"]
                            .select { filter { eq("session_id", s.id) } }
                            .decodeList<GpsRow>()
                        val alerts = supabaseClient.postgrest["alerts"]
                            .select { filter { eq("session_id", s.id) } }
                            .decodeList<AlertRow>()
                        val blocks = supabaseClient.postgrest["block_assignments"]
                            .select { filter { eq("session_id", s.id) } }
                            .decodeList<BlockRow>()

                        val byUser = aggregate(tracks, alerts, blocks, users)
                        val durMin = s.ended_at?.let {
                            (Instant.parse(it).toEpochMilliseconds() - Instant.parse(s.started_at).toEpochMilliseconds()) / 60_000
                        } ?: 0L
                        byUser.values.forEach { st ->
                            appendLine(workerRow(st, sessionPrefix = listOf(
                                s.id, q(s.name), s.brigade_code ?: "", fmtTs(s.started_at),
                                s.ended_at?.let { fmtTs(it) } ?: "EN_CURSO", durMin
                            )))
                        }
                    }
                }

                Pair(csv, "metricas_globales_${nowMs()}.csv")
            }
        }

    // ── Agregación ───────────────────────────────────────────────────────────

    private fun aggregate(
        tracks: List<GpsRow>,
        alerts: List<AlertRow>,
        blocks: List<BlockRow>,
        users: Map<String, UserMeta>
    ): Map<String, WorkerStats> {
        val byUser = mutableMapOf<String, WorkerStats>()

        fun statsFor(uid: String): WorkerStats {
            val u = users[uid]
            return byUser.getOrPut(uid) {
                WorkerStats(
                    userId   = uid,
                    fullName = u?.full_name ?: uid.take(8),
                    role     = u?.role     ?: "",
                    dni      = u?.dni      ?: ""
                )
            }
        }

        // Tracks por usuario, ordenados por timestamp
        tracks.groupBy { it.user_id }.forEach { (uid, pts) ->
            val sorted = pts.sortedBy { it.captured_at }
            val st = statsFor(uid)
            st.gpsPoints = sorted.size
            if (sorted.isNotEmpty()) {
                st.firstTs = Instant.parse(sorted.first().captured_at).toEpochMilliseconds()
                st.lastTs  = Instant.parse(sorted.last().captured_at).toEpochMilliseconds()
            }
            for (i in 1 until sorted.size) {
                val a = sorted[i - 1]; val b = sorted[i]
                val dt = (Instant.parse(b.captured_at).toEpochMilliseconds() - Instant.parse(a.captured_at).toEpochMilliseconds()).coerceAtLeast(0)
                if (dt > 3 * 60_000) {
                    // gap largo → tiempo inactivo, no cuenta para distancia
                    st.idleMs += dt
                    continue
                }
                val km = haversineKm(a.latitude, a.longitude, b.latitude, b.longitude)
                st.distanceKm += km
                if (dt > 0) {
                    val kmh = km / (dt / 3_600_000.0)
                    if (kmh in 0.0..120.0) {           // filtra outliers GPS
                        if (kmh > st.maxSpeedKmh) st.maxSpeedKmh = kmh
                    }
                }
            }
        }

        alerts.forEach { a ->
            val st = statsFor(a.sender_id)
            st.alertCounts[a.alert_type] = (st.alertCounts[a.alert_type] ?: 0) + 1
        }

        blocks.forEach { b ->
            val st = statsFor(b.assigned_to)
            st.manzanasAssigned += 1
        }

        return byUser
    }

    // ── Render CSV ───────────────────────────────────────────────────────────

    private fun renderSessionCsv(session: SessionMeta, stats: List<WorkerStats>): String =
        buildString {
            val durMin = session.ended_at?.let {
                (Instant.parse(it).toEpochMilliseconds() - Instant.parse(session.started_at).toEpochMilliseconds()) / 60_000
            } ?: 0L
            appendLine("## METRICAS_JORNADA")
            appendLine("id,nombre,brigada,inicio_utc,fin_utc,duracion_min,trabajadores")
            appendLine(rowOf(
                session.id, q(session.name), session.brigade_code ?: "",
                fmtTs(session.started_at),
                session.ended_at?.let { fmtTs(it) } ?: "EN_CURSO",
                durMin, stats.size
            ))

            appendLine()
            appendLine("## POR_TRABAJADOR")
            appendLine(headerRow(prependSession = false))
            stats.sortedBy { it.fullName }.forEach { appendLine(workerRow(it)) }

            // Total agregado
            if (stats.isNotEmpty()) {
                val total = WorkerStats(
                    userId = "", fullName = "TOTAL", role = "", dni = ""
                )
                stats.forEach { s ->
                    total.gpsPoints += s.gpsPoints
                    total.distanceKm += s.distanceKm
                    if (s.maxSpeedKmh > total.maxSpeedKmh) total.maxSpeedKmh = s.maxSpeedKmh
                    total.idleMs += s.idleMs
                    s.alertCounts.forEach { (k, v) ->
                        total.alertCounts[k] = (total.alertCounts[k] ?: 0) + v
                    }
                    total.manzanasAssigned += s.manzanasAssigned
                }
                // promediar minutos activos a partir del rango de la sesión
                total.firstTs = Instant.parse(session.started_at).toEpochMilliseconds()
                total.lastTs  = session.ended_at?.let { Instant.parse(it).toEpochMilliseconds() }
                    ?: stats.mapNotNull { it.lastTs }.maxOrNull()
                appendLine(workerRow(total))
            }
        }

    private fun headerRow(prependSession: Boolean): String {
        val base = "trabajador,dni,rol," +
            "gps_puntos,distancia_km,velocidad_media_kmh,velocidad_max_kmh," +
            "duracion_activa_min,tiempo_inactivo_min," +
            "alertas_agua,alertas_gasolina,alertas_insumo,alertas_averia," +
            "alertas_finalizado,alertas_mensaje,total_alertas," +
            "manzanas_asignadas"
        return if (prependSession)
            "session_id,jornada,brigada,inicio_utc,fin_utc,duracion_min,$base"
        else base
    }

    private fun workerRow(s: WorkerStats, sessionPrefix: List<Any?> = emptyList()): String {
        val cells = mutableListOf<Any?>()
        cells.addAll(sessionPrefix)
        cells.addAll(listOf(
            q(s.fullName), s.dni, s.role,
            s.gpsPoints, fmt2(s.distanceKm), fmt2(s.avgSpeedKmh), fmt2(s.maxSpeedKmh),
            s.activeMinutes, s.idleMinutes,
            s.alertCounts["agua"] ?: 0,
            s.alertCounts["gasolina"] ?: 0,
            s.alertCounts["insumo_quimico"] ?: 0,
            s.alertCounts["averia_maquina"] ?: 0,
            s.alertCounts["trabajo_finalizado"] ?: 0,
            s.alertCounts["broadcast_text"] ?: 0,
            s.totalAlerts,
            s.manzanasAssigned
        ))
        return rowOf(*cells.toTypedArray())
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2).pow(2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun fmtTs(iso: String): String = iso.take(19).replace("T", " ")

    private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    private fun fmt2(d: Double): String {
        val rounded = (d * 100).let { kotlin.math.round(it) } / 100.0
        return rounded.toString()
    }

    private fun q(s: String): String = "\"${s.replace("\"", "\"\"")}\""

    private fun rowOf(vararg values: Any?): String =
        values.joinToString(",") { it?.toString() ?: "" }
}
