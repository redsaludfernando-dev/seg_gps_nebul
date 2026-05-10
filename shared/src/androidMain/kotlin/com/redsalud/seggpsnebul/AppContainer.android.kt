package com.redsalud.seggpsnebul

import android.content.Context
import android.content.SharedPreferences
import com.redsalud.seggpsnebul.connectivity.ConnectivityObserver
import com.redsalud.seggpsnebul.connectivity.createConnectivityObserver
import com.redsalud.seggpsnebul.data.local.DatabaseDriverFactory
import com.redsalud.seggpsnebul.data.local.LocalDataSource
import com.redsalud.seggpsnebul.data.local.SegGpsDatabase
import com.redsalud.seggpsnebul.data.remote.AlertSyncRepository
import com.redsalud.seggpsnebul.data.remote.AssignmentsRepository
import com.redsalud.seggpsnebul.data.remote.AuthRepository
import com.redsalud.seggpsnebul.data.remote.AuthResult
import com.redsalud.seggpsnebul.data.remote.GpsSyncRepository
import com.redsalud.seggpsnebul.data.remote.RealtimeRepository
import com.redsalud.seggpsnebul.data.remote.SyncManager
import com.redsalud.seggpsnebul.data.remote.UsersSyncRepository
import com.redsalud.seggpsnebul.data.remote.supabaseClient
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole
import io.github.jan.supabase.auth.auth
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
    private lateinit var _assignmentsRepository: AssignmentsRepository
    private lateinit var _prefs: SharedPreferences

    // ── Exposed to androidMain consumers (worker screens, RoleViewModel, etc.) ──
    val db: SegGpsDatabase get() = _db
    val localDataSource: LocalDataSource get() = _localDataSource
    val authRepository: AuthRepository get() = _authRepository
    val gpsSyncRepository: GpsSyncRepository get() = _gpsSyncRepository
    val alertSyncRepository: AlertSyncRepository get() = _alertSyncRepository
    val syncManager: SyncManager get() = _syncManager
    val usersSyncRepository: UsersSyncRepository get() = _usersSyncRepository
    val assignmentsRepository: AssignmentsRepository get() = _assignmentsRepository

    // ── expect members ────────────────────────────────────────────────────────
    actual val currentUser = MutableStateFlow<User?>(null)
    actual val currentSessionId = MutableStateFlow<String?>(null)
    actual val realtimeRepository = RealtimeRepository()
    actual val connectivityObserver: ConnectivityObserver get() = _connectivityObserver

    actual suspend fun loginAdmin(email: String, password: String): AuthResult {
        val r = _authRepository.loginAdmin(email, password)
        // Supabase Auth ya persiste la sesion automaticamente (Settings/SharedPrefs).
        // Limpiamos cualquier worker cacheado: sesiones admin y worker son excluyentes.
        if (r is AuthResult.AdminSuccess) clearCachedWorker()
        return r
    }

    actual suspend fun loginWorker(dni: String, pin: String): AuthResult {
        val r = _authRepository.loginWorker(dni, pin)
        if (r is AuthResult.WorkerSuccess) cacheWorkerId(r.user.id)
        return r
    }

    actual suspend fun registerWorker(dni: String, fullName: String, role: UserRole, pin: String): AuthResult {
        val r = _authRepository.registerWorker(dni, fullName, role, pin)
        if (r is AuthResult.WorkerSuccess) cacheWorkerId(r.user.id)
        return r
    }

    actual suspend fun tryRestoreSession(): RestoreResult {
        // 1) Sesion Supabase Auth (admin) tiene prioridad — el SDK la restaura
        //    desde Settings/SharedPreferences en su init. currentSessionOrNull()
        //    devuelve null si no hay JWT valido.
        runCatching {
            if (supabaseClient.auth.currentSessionOrNull() != null) {
                clearCachedWorker()
                return RestoreResult.Admin
            }
        }
        // 2) Worker cacheado en SharedPreferences → resolver en SQLite local.
        val cachedId = _prefs.getString(KEY_WORKER_ID, null) ?: return RestoreResult.None
        val u = _localDataSource.getUserById(cachedId) ?: run {
            clearCachedWorker(); return RestoreResult.None
        }
        if (!u.isActive) { clearCachedWorker(); return RestoreResult.None }
        // No seteamos currentUser aqui — App.kt lo hace una vez navegado a WorkerHome.
        return RestoreResult.Worker(u)
    }

    actual suspend fun signOutAdmin() {
        runCatching { supabaseClient.auth.signOut() }
        currentUser.value = null
    }

    actual suspend fun signOutWorker() {
        clearCachedWorker()
        currentUser.value = null
        currentSessionId.value = null
    }

    private fun cacheWorkerId(id: String) {
        _prefs.edit().putString(KEY_WORKER_ID, id).apply()
    }

    private fun clearCachedWorker() {
        _prefs.edit().remove(KEY_WORKER_ID).apply()
    }

    // ── Init (called from MainActivity) ──────────────────────────────────────
    fun init(context: Context, driverFactory: DatabaseDriverFactory) {
        _prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _db = SegGpsDatabase(driverFactory.createDriver())
        _localDataSource = LocalDataSource(_db)
        _usersSyncRepository = UsersSyncRepository()
        _authRepository = AuthRepository(_localDataSource, _usersSyncRepository)
        _gpsSyncRepository = GpsSyncRepository(_localDataSource)
        _alertSyncRepository = AlertSyncRepository(_localDataSource)
        _assignmentsRepository = AssignmentsRepository()
        _syncManager = SyncManager(
            localDataSource       = _localDataSource,
            gpsSyncRepository     = _gpsSyncRepository,
            alertSyncRepository   = _alertSyncRepository,
            assignmentsRepository = _assignmentsRepository,
            currentUserIdProvider = { currentUser.value?.id }
        )
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
                currentUser.value?.id?.let { realtimeRepository.subscribeToAssignmentsForUser(it) }
                _syncManager.syncAll()
                while (isActive) {
                    delay(60_000)
                    _syncManager.syncAll()
                }
            }
        }
    }

    private const val PREFS_NAME    = "seg_gps_nebul_session"
    private const val KEY_WORKER_ID = "cached_worker_id"
}
