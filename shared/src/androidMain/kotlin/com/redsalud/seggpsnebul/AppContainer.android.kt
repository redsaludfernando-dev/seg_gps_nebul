package com.redsalud.seggpsnebul

import com.redsalud.seggpsnebul.connectivity.ConnectivityObserver
import com.redsalud.seggpsnebul.connectivity.createConnectivityObserver
import com.redsalud.seggpsnebul.data.local.DatabaseDriverFactory
import com.redsalud.seggpsnebul.data.local.LocalDataSource
import com.redsalud.seggpsnebul.data.local.SegGpsDatabase
import com.redsalud.seggpsnebul.data.remote.AlertSyncRepository
import com.redsalud.seggpsnebul.data.remote.AuthRepository
import com.redsalud.seggpsnebul.data.remote.AuthResult
import com.redsalud.seggpsnebul.data.remote.GpsSyncRepository
import com.redsalud.seggpsnebul.data.remote.RealtimeRepository
import com.redsalud.seggpsnebul.data.remote.SyncManager
import com.redsalud.seggpsnebul.data.remote.UsersSyncRepository
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

actual object AppContainer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var _db: SegGpsDatabase
    private lateinit var _localDataSource: LocalDataSource
    private lateinit var _authRepository: AuthRepository
    private lateinit var _gpsSyncRepository: GpsSyncRepository
    private lateinit var _connectivityObserver: ConnectivityObserver
    private lateinit var _alertSyncRepository: AlertSyncRepository
    private lateinit var _syncManager: SyncManager
    private lateinit var _usersSyncRepository: UsersSyncRepository

    // ── Exposed to androidMain consumers (worker screens, RoleViewModel, etc.) ──
    val db: SegGpsDatabase get() = _db
    val localDataSource: LocalDataSource get() = _localDataSource
    val authRepository: AuthRepository get() = _authRepository
    val gpsSyncRepository: GpsSyncRepository get() = _gpsSyncRepository
    val alertSyncRepository: AlertSyncRepository get() = _alertSyncRepository
    val syncManager: SyncManager get() = _syncManager
    val usersSyncRepository: UsersSyncRepository get() = _usersSyncRepository

    // ── expect members ────────────────────────────────────────────────────────
    actual val currentUser = MutableStateFlow<User?>(null)
    actual val currentSessionId = MutableStateFlow<String?>(null)
    actual val realtimeRepository = RealtimeRepository()
    actual val connectivityObserver: ConnectivityObserver get() = _connectivityObserver

    actual suspend fun loginAdmin(email: String, password: String): AuthResult =
        _authRepository.loginAdmin(email, password)

    actual suspend fun loginWorker(dni: String, pin: String): AuthResult =
        _authRepository.loginWorker(dni, pin)

    actual suspend fun registerWorker(dni: String, fullName: String, role: UserRole, pin: String): AuthResult =
        _authRepository.registerWorker(dni, fullName, role, pin)

    // ── Init (called from MainActivity) ──────────────────────────────────────
    fun init(driverFactory: DatabaseDriverFactory) {
        _db = SegGpsDatabase(driverFactory.createDriver())
        _localDataSource = LocalDataSource(_db)
        _usersSyncRepository = UsersSyncRepository()
        _authRepository = AuthRepository(_localDataSource, _usersSyncRepository)
        _gpsSyncRepository = GpsSyncRepository(_localDataSource)
        _alertSyncRepository = AlertSyncRepository(_localDataSource)
        _syncManager = SyncManager(_localDataSource, _gpsSyncRepository, _alertSyncRepository)
        _connectivityObserver = createConnectivityObserver()
        _connectivityObserver.start()
        observeConnectivity()
    }

    private fun observeConnectivity() {
        scope.launch {
            // collectLatest cancels the previous block when isOnline changes,
            // so the periodic loop stops automatically when going offline.
            _connectivityObserver.isOnline.collectLatest { online ->
                if (!online) return@collectLatest
                realtimeRepository.connect()
                currentSessionId.value?.let { realtimeRepository.subscribeToSession(it) }
                _syncManager.syncAll()
                while (isActive) {
                    delay(60_000)
                    _syncManager.syncAll()
                }
            }
        }
    }
}
