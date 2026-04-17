package com.redsalud.seggpsnebul.screens.map

import com.redsalud.seggpsnebul.AppContainer
import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.map.PmTilesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface PmTilesState {
    data object NotDownloaded : PmTilesState
    data class Downloading(val progress: Float) : PmTilesState
    data object Ready : PmTilesState
    data class Error(val msg: String) : PmTilesState
}

class MapViewModel(private val currentUser: User?) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _pmTilesState = MutableStateFlow<PmTilesState>(
        if (PmTilesManager.isDownloaded()) PmTilesState.Ready else PmTilesState.NotDownloaded
    )
    val pmTilesState: StateFlow<PmTilesState> = _pmTilesState.asStateFlow()

    val isOnline: StateFlow<Boolean> = AppContainer.connectivityObserver.isOnline

    private val _userPositions = MutableStateFlow<List<UserPosition>>(emptyList())
    val userPositions: StateFlow<List<UserPosition>> = _userPositions.asStateFlow()

    private val _myPosition = MutableStateFlow<UserPosition?>(null)
    val myPosition: StateFlow<UserPosition?> = _myPosition.asStateFlow()

    init {
        if (_pmTilesState.value == PmTilesState.NotDownloaded) {
            downloadPmTiles()
        }
        pollPositions()
    }

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

    private fun pollPositions() {
        scope.launch {
            while (isActive) {
                loadPositions()
                delay(15_000L)
            }
        }
    }

    private fun loadPositions() {
        val session = AppContainer.localDataSource.getActiveSession() ?: return
        val allUsers = AppContainer.localDataSource.getAllActiveUsers()
        val positions = mutableListOf<UserPosition>()
        for (user in allUsers) {
            val tracks = AppContainer.localDataSource.getGpsTracksByUserSession(user.id, session.id)
            val latest = tracks.maxByOrNull { it.captured_at } ?: continue
            val pos = UserPosition(user.id, user.fullName, user.role.value,
                latest.latitude, latest.longitude, latest.captured_at)
            if (user.id == currentUser?.id) {
                _myPosition.value = pos
            } else {
                positions.add(pos)
            }
        }
        _userPositions.value = positions
    }

    fun dispose() {
        scope.cancel()
    }
}
