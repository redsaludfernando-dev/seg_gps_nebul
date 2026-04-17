package com.redsalud.seggpsnebul.screens.admin

import com.redsalud.seggpsnebul.AppContainer
import com.redsalud.seggpsnebul.data.remote.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AdminViewModel {
    private val scope        = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val adminRepo    = AdminRepository()
    private val csvExporter  = CsvExporter()
    private val zonasRepo    = ZonasRepository()
    private val geovisorRepo = GeovisorRepository()

    // ── Jornadas / Usuarios ───────────────────────────────────────────────────
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

    // ── Conectividad ──────────────────────────────────────────────────────────
    val realtimeUp = AppContainer.realtimeRepository.isConnected
    val isOnline   = AppContainer.connectivityObserver.isOnline

    // ── Sync (no-op en web) ───────────────────────────────────────────────────
    val pendingTracks = MutableStateFlow(0)
    val pendingAlerts = MutableStateFlow(0)
    val pendingBlocks = MutableStateFlow(0)
    val isSyncing     = MutableStateFlow(false)
    val lastSyncAt    = MutableStateFlow<Long?>(null)
    val syncLastError = MutableStateFlow<String?>(null)

    // ── Zonas ─────────────────────────────────────────────────────────────────
    private val _zonas = MutableStateFlow<List<ZonaDto>>(emptyList())
    val zonas: StateFlow<List<ZonaDto>> = _zonas.asStateFlow()

    // ── Geovisor ─────────────────────────────────────────────────────────────
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    private val _livePositions = MutableStateFlow<List<WorkerPositionDto>>(emptyList())
    val livePositions: StateFlow<List<WorkerPositionDto>> = _livePositions.asStateFlow()

    private val _trackSegments = MutableStateFlow<List<TrackSegment>>(emptyList())
    val trackSegments: StateFlow<List<TrackSegment>> = _trackSegments.asStateFlow()

    private val _showZonas     = MutableStateFlow(true)
    val showZonas: StateFlow<Boolean> = _showZonas.asStateFlow()

    private val _showPositions = MutableStateFlow(true)
    val showPositions: StateFlow<Boolean> = _showPositions.asStateFlow()

    private val _showTracks    = MutableStateFlow(true)
    val showTracks: StateFlow<Boolean> = _showTracks.asStateFlow()

    init { loadAll(); loadZonas() }

    // ── Carga de datos ────────────────────────────────────────────────────────

    fun loadAll() {
        scope.launch {
            _isLoading.value = true
            val sd = async { adminRepo.fetchSessions() }
            val ud = async { adminRepo.fetchUsers() }
            sd.await().onSuccess { _sessions.value = it }
                      .onFailure { _message.value = "Error cargando jornadas: ${it.message}" }
            ud.await().onSuccess { _users.value = it }
                      .onFailure { _message.value = "Error cargando usuarios: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun loadSessionStats(sessionId: String) {
        if (_sessionStats.value.containsKey(sessionId)) return
        scope.launch {
            adminRepo.fetchSessionStats(sessionId)
                .onSuccess { _sessionStats.value = _sessionStats.value + (sessionId to it) }
        }
    }

    fun exportSession(sessionId: String) {
        scope.launch {
            _isLoading.value = true
            csvExporter.buildCsv(sessionId)
                .onSuccess { (csv, fn) -> platformSaveCsv(sessionId, csv, fn) }
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

    // ── Geovisor ─────────────────────────────────────────────────────────────

    fun selectSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
        if (sessionId == null) return
        loadSessionStats(sessionId)
        scope.launch { loadGeovisorData(sessionId) }
    }

    private suspend fun loadGeovisorData(sessionId: String) {
        _isLoading.value = true
        val posD    = scope.async { geovisorRepo.fetchLivePositions(sessionId) }
        val tracksD = scope.async { geovisorRepo.fetchTracks(sessionId) }
        posD.await()   .onSuccess { _livePositions.value = it }
        tracksD.await().onSuccess { _trackSegments.value = it }
        _isLoading.value = false
    }

    fun toggleLayer(layer: String) {
        when (layer) {
            "zonas"     -> _showZonas.value     = !_showZonas.value
            "positions" -> _showPositions.value = !_showPositions.value
            "tracks"    -> _showTracks.value    = !_showTracks.value
        }
    }

    fun dispose() { scope.cancel() }

    // ── CSV platform ──────────────────────────────────────────────────────────
    private suspend fun platformSaveCsv(sessionId: String, csv: String, filename: String) {
        runCatching { saveCsvToPlatform(sessionId, csv, filename) }
            .onSuccess { _message.value = "CSV guardado: $it" }
            .onFailure { _message.value = "Error guardando CSV: ${it.message}" }
    }
}

expect suspend fun saveCsvToPlatform(sessionId: String, csv: String, filename: String): String
