@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.redsalud.seggpsnebul.data.remote

import com.redsalud.seggpsnebul.data.local.LocalDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SyncManager(
    private val localDataSource: LocalDataSource,
    private val gpsSyncRepository: GpsSyncRepository,
    private val alertSyncRepository: AlertSyncRepository,
    private val assignmentsRepository: AssignmentsRepository,
    private val currentUserIdProvider: () -> String?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pendingTracks = MutableStateFlow(0)
    private val _pendingAlerts = MutableStateFlow(0)
    private val _pendingBlocks = MutableStateFlow(0)
    private val _isSyncing     = MutableStateFlow(false)
    private val _lastSyncAt    = MutableStateFlow<Long?>(null)
    private val _lastError     = MutableStateFlow<String?>(null)
    private val _retryCount    = MutableStateFlow(0)

    val pendingTracks: StateFlow<Int>    = _pendingTracks.asStateFlow()
    val pendingAlerts: StateFlow<Int>    = _pendingAlerts.asStateFlow()
    val pendingBlocks: StateFlow<Int>    = _pendingBlocks.asStateFlow()
    val isSyncing: StateFlow<Boolean>    = _isSyncing.asStateFlow()
    val lastSyncAt: StateFlow<Long?>     = _lastSyncAt.asStateFlow()
    val lastError: StateFlow<String?>    = _lastError.asStateFlow()

    val totalPending: StateFlow<Int> = combine(_pendingTracks, _pendingAlerts, _pendingBlocks) { t, a, b ->
        t + a + b
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    private companion object {
        const val MAX_RETRIES = 5
        const val INITIAL_DELAY_MS = 2_000L
        const val MAX_DELAY_MS = 60_000L
    }

    init { refreshCounts() }

    /** Re-reads pending counts from local DB (call after any write or sync). */
    fun refreshCounts() {
        scope.launch {
            _pendingTracks.value = localDataSource.getPendingGpsTracks().size
            _pendingAlerts.value = localDataSource.getPendingAlerts().size
            _pendingBlocks.value = localDataSource.getPendingBlockAssignments().size
        }
    }

    /**
     * Syncs all pending data to Supabase in FK-safe order.
     * Retries with exponential backoff on failure (max [MAX_RETRIES] attempts).
     * No-op if already syncing.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun syncAll(): Result<Unit> {
        if (_isSyncing.value) return Result.success(Unit)
        _isSyncing.value = true
        _lastError.value = null
        _retryCount.value = 0

        var lastResult: Result<Unit> = Result.failure(Exception("No se ejecuto"))
        var attempt = 0

        while (attempt <= MAX_RETRIES) {
            lastResult = runCatching {
                alertSyncRepository.syncPendingSessions()
                    .onFailure { throw Exception("Sesiones: ${it.message}") }
                gpsSyncRepository.syncPendingTracks()
                    .onFailure { throw Exception("GPS: ${it.message}") }
                alertSyncRepository.syncPendingAlerts()
                    .onFailure { throw Exception("Alertas: ${it.message}") }
                alertSyncRepository.syncPendingBlockAssignments()
                    .onFailure { throw Exception("Manzanas: ${it.message}") }
                pullAssignmentsForCurrentUser()
                    .onFailure { throw Exception("Pull manzanas: ${it.message}") }
                alertSyncRepository.pullActiveAlerts()
                    .onFailure { throw Exception("Pull alertas: ${it.message}") }
                _lastSyncAt.value = Clock.System.now().toEpochMilliseconds()
            }

            if (lastResult.isSuccess) {
                _lastError.value = null
                _retryCount.value = 0
                break
            }

            attempt++
            _retryCount.value = attempt
            _lastError.value = lastResult.exceptionOrNull()?.message

            if (attempt <= MAX_RETRIES) {
                val delayMs = (INITIAL_DELAY_MS * (1L shl (attempt - 1)))
                    .coerceAtMost(MAX_DELAY_MS)
                delay(delayMs)
            }
        }

        _isSyncing.value = false
        refreshCounts()
        return lastResult
    }

    /**
     * PULL: descarga las asignaciones del usuario actual desde Supabase y las
     * upserta localmente como 'synced'. No-op si no hay usuario logueado.
     *
     * Se llama dentro de [syncAll], pero también puede invocarse on-demand
     * (por ejemplo, cuando el RealtimeRepository emite un cambio en
     * block_assignments para este usuario).
     */
    suspend fun pullAssignmentsForCurrentUser(): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = currentUserIdProvider() ?: return@withContext Result.success(Unit)
        runCatching {
            val remote = assignmentsRepository.fetchByUser(userId).getOrThrow()
            for (a in remote) {
                val assignedAtMs = runCatching { Instant.parse(a.assigned_at).toEpochMilliseconds() }
                    .getOrElse { continue }
                localDataSource.upsertRemoteBlockAssignment(
                    id          = a.id,
                    sessionId   = a.session_id,
                    assignedTo  = a.assigned_to,
                    assignedBy  = a.assigned_by,
                    blockName   = a.block_name,
                    notes       = a.notes,
                    assignedAt  = assignedAtMs
                )
            }
            Unit
        }
    }
}
