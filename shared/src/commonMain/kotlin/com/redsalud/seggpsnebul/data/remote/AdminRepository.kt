package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.data.PinHasher
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ─── DTOs (admin read-only en lo posible — sin PIN en respuestas) ───────────

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

@Serializable
data class AllowedUserDto(
    val dni: String,
    val phone_number: String,
    val loaded_at: Long? = null
)

data class SessionStats(
    val sessionId: String,
    val trackCount: Int,
    val alertCount: Int,
    val blockCount: Int
)

// DTOs internos para INSERT/UPDATE — no se exponen a la UI

@Serializable
private data class UserInsertDto(
    val id: String,
    val dni: String,
    val phone_number: String,
    val full_name: String,
    val role: String,
    val pin: String,
    val is_active: Boolean,
    val created_at: Long
)

@Serializable
private data class AllowedUserInsertDto(
    val dni: String,
    val phone_number: String,
    val loaded_at: Long
)

// ─── Repository ───────────────────────────────────────────────────────────────

class AdminRepository {

    // ── Lectura ─────────────────────────────────────────────────────────────

    suspend fun fetchSessions(): Result<List<SessionAdminDto>> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["sessions"]
                .select()
                .decodeList<SessionAdminDto>()
                .sortedByDescending { it.started_at }
        }
    }

    suspend fun fetchUsers(): Result<List<UserAdminDto>> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["users"]
                .select()
                .decodeList<UserAdminDto>()
                .sortedBy { it.full_name }
        }
    }

    suspend fun fetchAllowedUsers(): Result<List<AllowedUserDto>> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["allowed_users"]
                .select()
                .decodeList<AllowedUserDto>()
                .sortedBy { it.dni }
        }
    }

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

    // ── Allowed users (whitelist DNIs) ───────────────────────────────────────

    @OptIn(ExperimentalTime::class)
    suspend fun addAllowedUser(dni: String, phone: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            supabaseClient.postgrest["allowed_users"].upsert(
                AllowedUserInsertDto(dni.trim(), phone.trim(), now)
            ) { defaultToNull = false }
            Unit
        }
    }

    suspend fun deleteAllowedUser(dni: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["allowed_users"]
                .delete { filter { eq("dni", dni) } }
            Unit
        }
    }

    // ── Trabajadores ────────────────────────────────────────────────────────

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    suspend fun createUser(
        dni: String,
        phone: String,
        fullName: String,
        role: String,
        pin: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN debe ser 4 dígitos" }
            require(dni.isNotBlank()) { "DNI vacío" }
            require(fullName.isNotBlank()) { "Nombre vacío" }

            // Asegurar que el DNI esté en allowed_users (FK desde users.dni)
            addAllowedUser(dni, phone).getOrThrow()

            supabaseClient.postgrest["users"].insert(
                UserInsertDto(
                    id = Uuid.random().toString(),
                    dni = dni.trim(),
                    phone_number = phone.trim(),
                    full_name = fullName.trim(),
                    role = role,
                    pin = PinHasher.hash(pin),
                    is_active = true,
                    created_at = Clock.System.now().toEpochMilliseconds()
                )
            ) { defaultToNull = false }
            Unit
        }
    }

    suspend fun updateUser(
        userId: String,
        fullName: String,
        role: String,
        phone: String
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["users"].update({
                set("full_name", fullName.trim())
                set("role", role)
                set("phone_number", phone.trim())
            }) { filter { eq("id", userId) } }
            Unit
        }
    }

    suspend fun resetUserPin(userId: String, newPin: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(newPin.length == 4 && newPin.all { it.isDigit() }) { "PIN debe ser 4 dígitos" }
            supabaseClient.postgrest["users"].update({
                set("pin", PinHasher.hash(newPin))
                set<String?>("device_id", null)   // forzar re-vinculación de dispositivo
            }) { filter { eq("id", userId) } }
            Unit
        }
    }

    suspend fun setUserActive(userId: String, active: Boolean): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["users"].update({
                set("is_active", active)
            }) { filter { eq("id", userId) } }
            Unit
        }
    }

    /** Borrado físico del trabajador. Falla si tiene gps_tracks/alerts/sessions referenciados (FK). */
    suspend fun deleteUser(userId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["users"]
                .delete { filter { eq("id", userId) } }
            Unit
        }
    }

    // ── GPS / Jornadas ──────────────────────────────────────────────────────

    suspend fun deleteGpsTracksFromServer(sessionId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["gps_tracks"]
                .delete { filter { eq("session_id", sessionId) } }
            Unit
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun closeSession(sessionId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            supabaseClient.postgrest["sessions"].update({
                set("is_active", false)
                set("ended_at", now)
            }) { filter { eq("id", sessionId) } }
            Unit
        }
    }
}
