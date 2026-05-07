package com.redsalud.seggpsnebul.screens.role

import com.redsalud.seggpsnebul.AppContainer
import com.redsalud.seggpsnebul.data.local.Alerts
import com.redsalud.seggpsnebul.data.local.Block_assignments
import com.redsalud.seggpsnebul.data.remote.ZonaDto
import com.redsalud.seggpsnebul.data.remote.ZonasRepository
import com.redsalud.seggpsnebul.domain.model.AlertType
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.map.PmTilesManager
import com.redsalud.seggpsnebul.screens.map.AlertMarker
import com.redsalud.seggpsnebul.screens.map.PmTilesState
import com.redsalud.seggpsnebul.screens.map.UserPosition
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RoleViewModel(val currentUser: User) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val zonasRepo = ZonasRepository()

    // ── PMTiles ──────────────────────────────────────────────────────────────
    private val _pmTilesState = MutableStateFlow<PmTilesState>(
        if (PmTilesManager.isDownloaded()) PmTilesState.Ready else PmTilesState.NotDownloaded
    )
    val pmTilesState: StateFlow<PmTilesState> = _pmTilesState.asStateFlow()

    // ── Connectivity ─────────────────────────────────────────────────────────
    val isOnline: StateFlow<Boolean> = AppContainer.connectivityObserver.isOnline

    // ── Session ───────────────────────────────────────────────────────────────
    private val _sessionActive = MutableStateFlow(
        AppContainer.localDataSource.getActiveSession() != null
    )
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _sessionId = MutableStateFlow(
        AppContainer.localDataSource.getActiveSession()?.id
    )
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    // ── Alerts ────────────────────────────────────────────────────────────────
    private val _pendingAlerts = MutableStateFlow<List<Alerts>>(emptyList())
    val pendingAlerts: StateFlow<List<Alerts>> = _pendingAlerts.asStateFlow()

    private val _allAlerts = MutableStateFlow<List<Alerts>>(emptyList())
    val allAlerts: StateFlow<List<Alerts>> = _allAlerts.asStateFlow()

    /** Todas las alertas activas (pendiente + on_way) de cualquier sesion. */
    private val _activeAlerts = MutableStateFlow<List<Alerts>>(emptyList())
    val activeAlerts: StateFlow<List<Alerts>> = _activeAlerts.asStateFlow()

    /** Alertas activas listas para pintar como markers en el mapa (con sender resuelto). */
    private val _alertMarkers = MutableStateFlow<List<AlertMarker>>(emptyList())
    val alertMarkers: StateFlow<List<AlertMarker>> = _alertMarkers.asStateFlow()

    // ── Block assignments ─────────────────────────────────────────────────────
    private val _myBlock = MutableStateFlow<Block_assignments?>(null)
    val myBlock: StateFlow<Block_assignments?> = _myBlock.asStateFlow()

    private val _allBlocks = MutableStateFlow<List<Block_assignments>>(emptyList())
    val allBlocks: StateFlow<List<Block_assignments>> = _allBlocks.asStateFlow()

    // ── Map positions ─────────────────────────────────────────────────────────
    private val _userPositions = MutableStateFlow<List<UserPosition>>(emptyList())
    val userPositions: StateFlow<List<UserPosition>> = _userPositions.asStateFlow()

    private val _myPosition = MutableStateFlow<UserPosition?>(null)
    val myPosition: StateFlow<UserPosition?> = _myPosition.asStateFlow()

    // ── Zonas (manzanas vectoriales) ──────────────────────────────────────────
    private val _zonas = MutableStateFlow<List<ZonaDto>>(emptyList())
    val zonas: StateFlow<List<ZonaDto>> = _zonas.asStateFlow()

    // ── Snackbar messages ─────────────────────────────────────────────────────
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        if (_pmTilesState.value == PmTilesState.NotDownloaded) downloadPmTiles()
        // Publish existing session to AppContainer so Realtime can subscribe
        _sessionId.value?.let { sid ->
            AppContainer.currentSessionId.value = sid
            if (AppContainer.connectivityObserver.isOnline.value) {
                AppContainer.realtimeRepository.subscribeToSession(sid)
            }
        }
        // Suscripcion realtime a manzanas para reaccionar al admin web sin esperar polling.
        if (AppContainer.connectivityObserver.isOnline.value) {
            AppContainer.realtimeRepository.subscribeToAssignmentsForUser(currentUser.id)
        }
        // Trigger an immediate PULL so la manzana asignada aparece nada mas entrar.
        scope.launch {
            if (AppContainer.connectivityObserver.isOnline.value) {
                AppContainer.syncManager.pullAssignmentsForCurrentUser()
                refresh()
            }
        }
        pollData()
        subscribeToRealtimeAlerts()
        subscribeToRealtimeAssignments()
        loadZonas()
    }

    private fun loadZonas() {
        scope.launch {
            zonasRepo.fetchZonas().onSuccess { _zonas.value = it }
        }
    }

    // ── Session control ───────────────────────────────────────────────────────

    fun startSession(name: String) {
        val sessionId = UUID.randomUUID().toString()
        AppContainer.localDataSource.insertSession(
            id          = sessionId,
            name        = name,
            brigadeCode = null,
            startedBy   = currentUser.id,
            startedAt   = Clock.System.now().toEpochMilliseconds()
        )
        _sessionId.value = sessionId
        _sessionActive.value = true
        AppContainer.currentSessionId.value = sessionId
        if (AppContainer.connectivityObserver.isOnline.value) {
            AppContainer.realtimeRepository.subscribeToSession(sessionId)
        }
        _message.value = "Jornada iniciada"
        refresh()
        scope.launch { AppContainer.syncManager.syncAll() }
    }

    fun endSession() {
        val sid = _sessionId.value ?: return
        AppContainer.localDataSource.closeSession(sid, Clock.System.now().toEpochMilliseconds())
        _sessionActive.value = false
        AppContainer.currentSessionId.value = null
        AppContainer.realtimeRepository.unsubscribeAll()
        _message.value = "Jornada finalizada"
        refresh()
        scope.launch { AppContainer.syncManager.syncAll() }
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    fun sendAlert(type: AlertType, message: String? = null, lat: Double? = null, lon: Double? = null) {
        val sid = _sessionId.value ?: run {
            _message.value = "No hay jornada activa"
            return
        }
        AppContainer.localDataSource.insertAlert(
            id         = UUID.randomUUID().toString(),
            senderId   = currentUser.id,
            sessionId  = sid,
            alertType  = type.value,
            message    = message,
            targetRole = type.targetRole,
            latitude   = lat,
            longitude  = lon,
            createdAt  = Clock.System.now().toEpochMilliseconds()
        )
        _message.value = "Alerta enviada: ${type.label}"
        refresh()
        scope.launch { AppContainer.syncManager.syncAll() }
    }

    fun markAlertAttended(alertId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        AppContainer.localDataSource.markAlertAttended(alertId, currentUser.id, now)
        refresh()
        scope.launch {
            if (AppContainer.connectivityObserver.isOnline.value) {
                AppContainer.alertSyncRepository.pushAlertAttended(alertId, currentUser.id, now)
            }
        }
    }

    /** "Ya voy": el usuario captura la alerta sin cerrarla aun. */
    fun markAlertOnWay(alertId: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        AppContainer.localDataSource.markAlertOnWay(alertId, currentUser.id, now)
        refresh()
        scope.launch {
            if (AppContainer.connectivityObserver.isOnline.value) {
                AppContainer.alertSyncRepository.pushAlertOnWay(alertId, currentUser.id, now)
            }
        }
    }

    // ── Block assignments ─────────────────────────────────────────────────────

    fun assignBlock(assignedTo: String, blockName: String, notes: String? = null) {
        val sid = _sessionId.value ?: run {
            _message.value = "No hay jornada activa"
            return
        }
        AppContainer.localDataSource.insertBlockAssignment(
            id         = UUID.randomUUID().toString(),
            sessionId  = sid,
            assignedTo = assignedTo,
            assignedBy = currentUser.id,
            blockName  = blockName,
            notes      = notes,
            assignedAt = Clock.System.now().toEpochMilliseconds()
        )
        _message.value = "Manzana asignada: $blockName"
        refresh()
    }

    // ── PMTiles ───────────────────────────────────────────────────────────────

    fun downloadPmTiles() {
        scope.launch {
            _pmTilesState.value = PmTilesState.Downloading(0f)
            PmTilesManager.download { progress ->
                _pmTilesState.value = PmTilesState.Downloading(progress)
            }.onSuccess {
                _pmTilesState.value = PmTilesState.Ready
            }.onFailure { e ->
                _pmTilesState.value = PmTilesState.Error(e.message ?: "Error desconocido")
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun pollData() {
        scope.launch {
            while (isActive) {
                // Trae alertas activas globales antes de refrescar (asi todos los roles
                // ven en su mapa lo que pase en otras sesiones).
                if (AppContainer.connectivityObserver.isOnline.value) {
                    AppContainer.alertSyncRepository.pullActiveAlerts()
                }
                refresh()
                // Poll faster when Realtime is down so alerts aren't missed
                val realtimeUp = AppContainer.realtimeRepository.isConnected.value
                delay(if (realtimeUp) 10_000L else 5_000L)
            }
        }
    }

    /** Instant refresh when Supabase Realtime pushes a new alert for this session. */
    private fun subscribeToRealtimeAlerts() {
        scope.launch {
            AppContainer.realtimeRepository.newAlerts.collect { event ->
                if (event.sessionId == _sessionId.value) {
                    refresh()
                }
            }
        }
    }

    /** Realtime de manzanas: cuando el admin asigna/edita, hacemos PULL + refresh. */
    private fun subscribeToRealtimeAssignments() {
        scope.launch {
            AppContainer.realtimeRepository.newAssignments.collect { event ->
                if (event.assignedTo == currentUser.id) {
                    AppContainer.syncManager.pullAssignmentsForCurrentUser()
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        val sid = _sessionId.value ?: AppContainer.localDataSource.getActiveSession()?.id

        // La manzana asignada se muestra incluso sin jornada activa: el admin puede
        // asignar manzanas offline y deben aparecer en cuanto el PULL las traiga.
        _myBlock.value = AppContainer.localDataSource.getMyLatestBlockAssignment(currentUser.id)

        // Alertas activas globales (cualquier sesion): se muestran en el mapa.
        // El sender no se ve a si mismo como marker — ya esta en su ubicacion.
        val active = AppContainer.localDataSource.getActiveAlerts()
        _activeAlerts.value = active
        val users  = AppContainer.localDataSource.getAllActiveUsers().associateBy { it.id }
        _alertMarkers.value = active.mapNotNull { a ->
            if (a.sender_id == currentUser.id) return@mapNotNull null
            val lat = a.latitude ?: return@mapNotNull null
            val lon = a.longitude ?: return@mapNotNull null
            AlertMarker(
                id            = a.id,
                latitude      = lat,
                longitude     = lon,
                alertType     = a.alert_type,
                status        = a.response_status ?: "pending",
                senderName    = users[a.sender_id]?.fullName ?: "—",
                responderName = a.response_by?.let { users[it]?.fullName },
                createdAt     = a.created_at
            )
        }

        if (sid == null) {
            _pendingAlerts.value = emptyList()
            _allAlerts.value     = emptyList()
            _allBlocks.value     = emptyList()
            _userPositions.value = emptyList()
            return
        }
        _sessionId.value = sid

        _pendingAlerts.value = AppContainer.localDataSource.getUnattendedAlertsBySession(sid)
        _allAlerts.value     = AppContainer.localDataSource.getAlertsBySession(sid)
        _allBlocks.value     = AppContainer.localDataSource.getBlockAssignmentsBySession(sid)

        val allUsers = AppContainer.localDataSource.getAllActiveUsers()
        val positions = mutableListOf<UserPosition>()
        val blockMap  = _allBlocks.value.associateBy { it.assigned_to }
        val alertMap  = _pendingAlerts.value.groupBy { it.sender_id }

        for (u in allUsers) {
            val tracks = AppContainer.localDataSource.getGpsTracksByUserSession(u.id, sid)
            val latest = tracks.maxByOrNull { it.captured_at } ?: continue
            val latestAlert = alertMap[u.id]?.maxByOrNull { it.created_at }
            val pos = UserPosition(
                userId        = u.id,
                fullName      = u.fullName,
                role          = u.role.value,
                latitude      = latest.latitude,
                longitude     = latest.longitude,
                capturedAt    = latest.captured_at,
                activeAlert   = latestAlert?.alert_type,
                assignedBlock = blockMap[u.id]?.block_name
            )
            if (u.id == currentUser.id) _myPosition.value = pos
            else positions.add(pos)
        }
        _userPositions.value = positions
    }

    fun clearMessage() { _message.value = null }

    fun dispose() { scope.cancel() }
}
