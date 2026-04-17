package com.redsalud.seggpsnebul.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ZonasRepository {

    suspend fun fetchZonas(): Result<List<ZonaDto>> = withContext(Dispatchers.Default) {
        runCatching {
            supabaseClient.postgrest["zonas"]
                .select()
                .decodeList<ZonaDto>()
        }
    }

    suspend fun upsertZonas(zonas: List<ZonaDto>): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            // defaultToNull = false: no enviar null para campos omitidos (respeta el DEFAULT de la BD)
            supabaseClient.postgrest["zonas"].upsert(zonas) { defaultToNull = false }
            Unit
        }
    }

    /** Elimina todas las zonas (preparación para reemplazar con nuevas). */
    suspend fun deleteAllZonas(): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            // PostgREST requiere un filtro para DELETE; "id != vacío" cubre todo
            supabaseClient.postgrest["zonas"].delete {
                filter { neq("id", "") }
            }
            Unit
        }
    }
}
