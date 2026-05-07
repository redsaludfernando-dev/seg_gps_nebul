package com.redsalud.seggpsnebul.data.remote

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class AlertEvent(
    val id: String,
    val sessionId: String,
    val alertType: String,
    val targetRole: String,
    val senderId: String,
    val message: String?
)

/** Evento puro: el admin acaba de crear/cambiar una asignacion para este usuario. */
data class AssignmentEvent(val assignedTo: String)

class RealtimeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _newAlerts = MutableSharedFlow<AlertEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    val newAlerts: SharedFlow<AlertEvent> = _newAlerts.asSharedFlow()

    private val _newAssignments = MutableSharedFlow<AssignmentEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    val newAssignments: SharedFlow<AssignmentEvent> = _newAssignments.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    /** True once the Realtime WebSocket connects AND a channel subscribes successfully. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Track current subscription to avoid duplicates
    private var subscribedSessionId: String? = null
    private var subscribeJob: Job? = null
    private var subscribedAssignmentsUserId: String? = null
    private var assignmentsSubscribeJob: Job? = null

    /** Connect the Realtime WebSocket. Call once when going online. */
    suspend fun connect() {
        runCatching {
            supabaseClient.realtime.connect()
            _isConnected.value = true
        }.onFailure { _isConnected.value = false }
    }

    /**
     * Subscribe to INSERT events on the `alerts` table for the given session.
     * Cancels any previous subscription automatically.
     */
    fun subscribeToSession(sessionId: String) {
        if (subscribedSessionId == sessionId) return
        subscribeJob?.cancel()
        subscribedSessionId = sessionId

        subscribeJob = scope.launch {
            runCatching {
                val channel = supabaseClient.channel("alerts_$sessionId")

                // Wire up the flow before subscribing
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table  = "alerts"
                    filter("session_id", FilterOperator.EQ, sessionId)
                }.onEach { insert ->
                    val rec = insert.record
                    _newAlerts.tryEmit(
                        AlertEvent(
                            id         = rec["id"]?.jsonPrimitive?.content       ?: return@onEach,
                            sessionId  = sessionId,
                            alertType  = rec["alert_type"]?.jsonPrimitive?.content ?: return@onEach,
                            targetRole = rec["target_role"]?.jsonPrimitive?.content ?: "all",
                            senderId   = rec["sender_id"]?.jsonPrimitive?.content  ?: "",
                            message    = rec["message"]?.jsonPrimitive?.contentOrNull
                        )
                    )
                }.launchIn(this)

                channel.subscribe()
                _isConnected.value = true
            }.onFailure { _isConnected.value = false }
        }
    }

    /**
     * Suscribe a INSERT/UPDATE/DELETE de `block_assignments` filtrado por
     * assigned_to = userId. Emite [AssignmentEvent] sin payload — el consumidor
     * dispara un PULL para tener la fila completa con su sync_status correcto.
     */
    fun subscribeToAssignmentsForUser(userId: String) {
        if (subscribedAssignmentsUserId == userId) return
        assignmentsSubscribeJob?.cancel()
        subscribedAssignmentsUserId = userId

        assignmentsSubscribeJob = scope.launch {
            runCatching {
                val channel = supabaseClient.channel("assignments_$userId")

                channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "block_assignments"
                    filter("assigned_to", FilterOperator.EQ, userId)
                }.onEach {
                    _newAssignments.tryEmit(AssignmentEvent(assignedTo = userId))
                }.launchIn(this)

                channel.subscribe()
                _isConnected.value = true
            }.onFailure { _isConnected.value = false }
        }
    }

    /** Drop all Realtime channels and cancel subscriptions. */
    fun unsubscribeAll() {
        _isConnected.value = false
        subscribeJob?.cancel()
        assignmentsSubscribeJob?.cancel()
        subscribedSessionId = null
        subscribedAssignmentsUserId = null
        scope.launch { runCatching { supabaseClient.realtime.removeAllChannels() } }
    }
}
