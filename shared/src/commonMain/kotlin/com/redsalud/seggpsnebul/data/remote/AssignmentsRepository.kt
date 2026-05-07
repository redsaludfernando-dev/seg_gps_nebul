package com.redsalud.seggpsnebul.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class AssignmentDto(
    val id: String,
    val session_id: String?,
    val assigned_to: String,
    val assigned_by: String,
    val block_name: String,
    val notes: String? = null,
    val assigned_at: String
)

@Serializable
private data class AssignmentInsertDto(
    val id: String,
    val session_id: String?,
    val assigned_to: String,
    val assigned_by: String,
    val block_name: String,
    val notes: String?
)

class AssignmentsRepository {

    suspend fun fetchBySession(sessionId: String): Result<List<AssignmentDto>> =
        withContext(Dispatchers.Default) {
            runCatching {
                supabaseClient.postgrest["block_assignments"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<AssignmentDto>()
                    .sortedByDescending { it.assigned_at }
            }
        }

    suspend fun fetchAll(): Result<List<AssignmentDto>> =
        withContext(Dispatchers.Default) {
            runCatching {
                supabaseClient.postgrest["block_assignments"]
                    .select()
                    .decodeList<AssignmentDto>()
                    .sortedByDescending { it.assigned_at }
            }
        }

    /** Trae todas las manzanas asignadas a un trabajador (incluidas las sin jornada). */
    suspend fun fetchByUser(userId: String): Result<List<AssignmentDto>> =
        withContext(Dispatchers.Default) {
            runCatching {
                supabaseClient.postgrest["block_assignments"]
                    .select { filter { eq("assigned_to", userId) } }
                    .decodeList<AssignmentDto>()
                    .sortedByDescending { it.assigned_at }
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun create(
        sessionId: String?,
        assignedTo: String,
        assignedBy: String,
        blockName: String,
        notes: String?
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            require(blockName.isNotBlank()) { "Manzana vacía" }
            supabaseClient.postgrest["block_assignments"].insert(
                AssignmentInsertDto(
                    id = Uuid.random().toString(),
                    session_id = sessionId,
                    assigned_to = assignedTo,
                    assigned_by = assignedBy,
                    block_name = blockName.trim(),
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() }
                )
            ) { defaultToNull = false }
            Unit
        }
    }

    suspend fun update(
        id: String,
        assignedTo: String,
        blockName: String,
        notes: String?
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["block_assignments"].update({
                set("assigned_to", assignedTo)
                set("block_name", blockName.trim())
                set("notes", notes?.trim()?.takeIf { it.isNotEmpty() })
            }) { filter { eq("id", id) } }
            Unit
        }
    }

    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["block_assignments"]
                .delete { filter { eq("id", id) } }
            Unit
        }
    }
}
