package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.data.PinHasher
import com.redsalud.seggpsnebul.data.local.LocalDataSource
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import com.redsalud.seggpsnebul.location.DeviceIdProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AuthRepository(
    private val local: LocalDataSource,
    private val usersSync: UsersSyncRepository
) {

    @Serializable
    private data class AllowedRow(
        val dni: String,
        @SerialName("phone_number") val phoneNumber: String
    )

    @Serializable
    private data class UserRow(
        val id: String,
        val dni: String,
        val phone_number: String,
        val full_name: String,
        val role: String,
        val pin: String,
        val device_id: String? = null,
        val is_active: Boolean
    )

    suspend fun loginWorker(dni: String, pin: String): AuthResult {
        // Try local first; fall back to Supabase (e.g., after reinstall)
        val user = local.getUserByDni(dni)
            ?: fetchAndCacheFromSupabase(dni)
            ?: return AuthResult.Error("DNI no registrado. Si ya se inscribió, asegúrese de tener conexión a internet.")
        return when {
            !PinHasher.verify(pin, user.pin) -> AuthResult.Error("PIN incorrecto")
            !user.isActive                   -> AuthResult.Error("Usuario desactivado. Contacte al administrador.")
            else                             -> AuthResult.WorkerSuccess(user)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun fetchAndCacheFromSupabase(dni: String): User? = try {
        val row = supabaseClient.postgrest["users"]
            .select { filter { eq("dni", dni) } }
            .decodeList<UserRow>()
            .firstOrNull() ?: return null
        if (!row.is_active) return null

        val deviceId = DeviceIdProvider.getDeviceId()
        local.insertUser(
            row.id, row.dni, row.phone_number, row.full_name,
            row.role, row.pin, deviceId,
            Clock.System.now().toEpochMilliseconds()
        )
        User(
            id          = row.id,
            dni         = row.dni,
            phoneNumber = row.phone_number,
            fullName    = row.full_name,
            role        = UserRole.fromString(row.role),
            pin         = row.pin,
            deviceId    = deviceId,
            isActive    = true
        )
    } catch (_: Exception) {
        null
    }

    suspend fun loginAdmin(email: String, password: String): AuthResult = try {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        AuthResult.AdminSuccess(email)
    } catch (e: Exception) {
        AuthResult.Error(e.message ?: "Error de autenticación")
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    suspend fun registerWorker(
        dni: String,
        fullName: String,
        role: UserRole,
        pin: String
    ): AuthResult {
        if (local.getUserByDni(dni) != null) {
            return AuthResult.Error("Este DNI ya está registrado en el dispositivo")
        }

        val phoneNumber = resolvePhoneNumber(dni)
            ?: return AuthResult.Error("DNI no autorizado. Contacte al administrador.")

        val id = Uuid.random().toString()
        val deviceId = DeviceIdProvider.getDeviceId()
        val now = Clock.System.now().toEpochMilliseconds()

        val hashedPin = PinHasher.hash(pin)
        local.insertUser(id, dni, phoneNumber, fullName, role.value, hashedPin, deviceId, now)

        val user = User(id, dni, phoneNumber, fullName, role, hashedPin, deviceId, isActive = true)

        usersSync.upsertUser(user, now)

        return AuthResult.WorkerSuccess(user)
    }

    private suspend fun resolvePhoneNumber(dni: String): String? {
        val cached = local.getPhoneForDni(dni)
        if (cached != null) return cached
        return try {
            val rows = supabaseClient.postgrest["allowed_users"]
                .select { filter { eq("dni", dni) } }
                .decodeList<AllowedRow>()
            rows.firstOrNull()?.phoneNumber
        } catch (_: Exception) {
            null
        }
    }
}
