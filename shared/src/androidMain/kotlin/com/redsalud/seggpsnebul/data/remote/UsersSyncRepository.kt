package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.domain.model.User
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
private data class UserDto(
    val id: String,
    val dni: String,
    val phone_number: String,
    val full_name: String,
    val role: String,
    val pin: String,
    val device_id: String?,
    val is_active: Boolean,
    val created_at: Long
)

class UsersSyncRepository {

    suspend fun upsertUser(user: User, createdAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabaseClient.postgrest["users"].upsert(
                    UserDto(
                        id = user.id,
                        dni = user.dni,
                        phone_number = user.phoneNumber,
                        full_name = user.fullName,
                        role = user.role.value,
                        pin = user.pin,
                        device_id = user.deviceId,
                        is_active = user.isActive,
                        created_at = createdAt
                    )
                ) { defaultToNull = false }
                Unit
            }
        }
}
