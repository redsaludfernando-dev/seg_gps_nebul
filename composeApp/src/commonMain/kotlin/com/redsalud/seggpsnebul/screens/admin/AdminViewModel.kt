package com.redsalud.seggpsnebul.screens.admin

import com.redsalud.seggpsnebul.AppContainer
import com.redsalud.seggpsnebul.data.remote.AdminRepository
import com.redsalud.seggpsnebul.data.remote.CsvExporter
import com.redsalud.seggpsnebul.data.remote.SessionAdminDto
import com.redsalud.seggpsnebul.data.remote.SessionStats
import com.redsalud.seggpsnebul.data.remote.UserAdminDto
import com.redsalud.seggpsnebul.data.remote.ZonaDto
import com.redsalud.seggpsnebul.data.remote.ZonasRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AdminViewModel {
    private val scope         = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val adminRepo     = AdminRepository()
    private val csvExporter   = CsvExporter()
    private val zonasRepo     = ZonasRepository()

    // ── Data lists ────────────────────────────────────────────────────────────
    private val _sessions = MutableStateFlow<List<SessionAdminDto>>(emptyList())
    val sessions: StateFlow<List<SessionAdminDto>> = _sessions.asStateFlow()

    private val _users = MutableStateFlow<List<UserAdminDto>>(emptyList())
    val users: StateFlow<List<UserAdminDto>> = _users.asStateFlow()

    private val _sessionStats = MutableStateFlow<Map<String, SessionStats>>(emptyMap())
    val sessionStats: StateFlow<Map<String, SessionStats>> = _sessionStats.asStateFlow()

    // ── UI state ──────────────────────────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ── Connectivity / realtime ───────────────────────────────────────────────
    val realtimeUp = AppContainer.realtimeRepository.isConnected
    val isOnline   = AppContainer.connectivityObserver.isOnline

    // ── Sync (no-op for web; Android admin is online-only too) ───────────────
    val pendingTracks = MutableStateFlow(0)
    val pendingAlerts = MutableStateFlow(0)
    val pendingBlocks = MutableStateFlow(0)
    val isSyncing     = MutableStateFlow(false)
    val lastSyncAt    = MutableStateFlow<Long?>(null)
    val syncLastError = MutableStateFlow<String?>(null)

    // ── Zonas ─────────────────────────────────────────────────────────────────
    private val _zonas = MutableStateFlow<List<ZonaDto>>(emptyList())
    val zonas: StateFlow<List<ZonaDto>> = _zonas.asStateFlow()

    init { loadAll(); loadZonas() }

    fun loadAll() {
        scope.launch {
            _isLoading.value = true
            val sessionsDeferred = async { adminRepo.fetchSessions() }
            val usersDeferred    = async { adminRepo.fetchUsers() }
            sessionsDeferred.await()
                .onSuccess { _sessions.value = it }
                .onFailure { _message.value = "Error cargando jornadas: ${it.message}" }
            usersDeferred.await()
                .onSuccess { _users.value = it }
                .onFailure { _message.value = "Error cargando usuarios: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun loadSessionStats(sessionId: String) {
        if (_sessionStats.value.containsKey(sessionId)) return
        scope.launch {
            adminRepo.fetchSessionStats(sessionId)
                .onSuccess { stats ->
                    _sessionStats.value = _sessionStats.value + (sessionId to stats)
                }
        }
    }

    fun exportSession(sessionId: String) {
        scope.launch {
            _isLoading.value = true
            csvExporter.buildCsv(sessionId)
                .onSuccess { (csv, filename) -> platformSaveCsv(sessionId, csv, filename) }
                .onFailure { _message.value = "Error exportando: ${it.message}" }
            _isLoading.value = false
            loadAll()
        }
    }

    fun deleteGpsFromServer(sessionId: String) {
        scope.launch {
            adminRepo.deleteGpsTracksFromServer(sessionId)
                .onSuccess { _message.value = "GPS eliminados del servidor" }
                .onFailure { _message.value = "Error: ${it.message}" }
            loadSessionStats(sessionId)
        }
    }

    fun toggleUserActive(userId: String, currentlyActive: Boolean) {
        scope.launch {
            adminRepo.setUserActive(userId, !currentlyActive)
                .onSuccess { loadAll() }
                .onFailure { _message.value = "Error: ${it.message}" }
        }
    }

    fun triggerSync() { /* no-op */ }

    fun clearMessage() { _message.value = null }

    // ── Zonas ─────────────────────────────────────────────────────────────────

    fun loadZonas() {
        scope.launch {
            zonasRepo.fetchZonas()
                .onSuccess { _zonas.value = it }
                .onFailure { _message.value = "Error cargando zonas: ${it.message}" }
        }
    }

    fun uploadZonas(list: List<ZonaDto>) {
        scope.launch {
            _isLoading.value = true
            zonasRepo.deleteAllZonas()
                .mapCatching { zonasRepo.upsertZonas(list).getOrThrow() }
                .onSuccess { _message.value = "${list.size} manzanas publicadas"; loadZonas() }
                .onFailure { _message.value = "Error publicando zonas: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun deleteAllZonas() {
        scope.launch {
            _isLoading.value = true
            zonasRepo.deleteAllZonas()
                .onSuccess { _zonas.value = emptyList(); _message.value = "Manzanas eliminadas" }
                .onFailure { _message.value = "Error: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun dispose() { scope.cancel() }

    // ── Platform-specific CSV save ────────────────────────────────────────────
    private suspend fun platformSaveCsv(sessionId: String, csv: String, filename: String) {
        runCatching { saveCsvToPlatform(sessionId, csv, filename) }
            .onSuccess { _message.value = "CSV guardado: $it" }
            .onFailure { _message.value = "Error guardando CSV: ${it.message}" }
    }
}

/** Platform-specific: write file (Android) or trigger browser download (web). */
expect suspend fun saveCsvToPlatform(sessionId: String, csv: String, filename: String): String
