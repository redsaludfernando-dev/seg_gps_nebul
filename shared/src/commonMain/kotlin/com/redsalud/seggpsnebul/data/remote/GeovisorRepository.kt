package com.redsalud.seggpsnebul.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeovisorRepository {

    suspend fun fetchLivePositions(sessionId: String): Result<List<WorkerPositionDto>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val tracks = supabaseClient.postgrest["gps_tracks"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<TrackRaw>()

                val users = supabaseClient.postgrest["users"]
                    .select()
                    .decodeList<UserMinDto>()
                    .associateBy { it.id }

                tracks
                    .sortedByDescending { it.captured_at }
                    .distinctBy { it.user_id }
                    .mapNotNull { t ->
                        val u = users[t.user_id] ?: return@mapNotNull null
                        WorkerPositionDto(t.user_id, u.full_name, u.role,
                            t.latitude, t.longitude, t.captured_at)
                    }
            }
        }

    suspend fun fetchTracks(sessionId: String): Result<List<TrackSegment>> =
        withContext(Dispatchers.Default) {
            runCatching {
                val tracks = supabaseClient.postgrest["gps_tracks"]
                    .select { filter { eq("session_id", sessionId) } }
                    .decodeList<TrackRaw>()
                    .sortedBy { it.captured_at }

                val users = supabaseClient.postgrest["users"]
                    .select()
                    .decodeList<UserMinDto>()
                    .associateBy { it.id }

                tracks
                    .groupBy { it.user_id }
                    .mapNotNull { (userId, pts) ->
                        val u = users[userId] ?: return@mapNotNull null
                        if (pts.size < 2) return@mapNotNull null
                        TrackSegment(userId, u.full_name, u.role,
                            pts.map { TrackPoint(it.latitude, it.longitude, it.captured_at) })
                    }
            }
        }
}
