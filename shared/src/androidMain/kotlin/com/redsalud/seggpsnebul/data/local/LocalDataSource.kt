package com.redsalud.seggpsnebul.data.local

import com.redsalud.seggpsnebul.domain.model.User
import com.redsalud.seggpsnebul.domain.model.UserRole

class LocalDataSource(private val db: SegGpsDatabase) {

    // allowed_users

    fun isDniAllowed(dni: String): Boolean =
        db.segGpsDatabaseQueries.selectAllowedUserByDni(dni).executeAsOneOrNull() != null

    fun insertAllowedUser(dni: String, phoneNumber: String, loadedAt: Long) {
        db.segGpsDatabaseQueries.insertAllowedUser(dni, phoneNumber, loadedAt)
    }

    fun clearAllowedUsers() {
        db.segGpsDatabaseQueries.deleteAllAllowedUsers()
    }

    fun getPhoneForDni(dni: String): String? =
        db.segGpsDatabaseQueries.selectAllowedUserByDni(dni).executeAsOneOrNull()?.phone_number

    // users

    fun insertUser(
        id: String,
        dni: String,
        phoneNumber: String,
        fullName: String,
        role: String,
        pin: String,
        deviceId: String?,
        createdAt: Long
    ) {
        db.segGpsDatabaseQueries.insertUser(
            id, dni, phoneNumber, fullName, role, pin, deviceId, createdAt
        )
    }

    fun getUserByDni(dni: String): User? =
        db.segGpsDatabaseQueries.selectUserByDni(dni).executeAsOneOrNull()?.toUser()

    fun getAllActiveUsers(): List<User> =
        db.segGpsDatabaseQueries.selectAllUsers().executeAsList().map { it.toUser() }

    fun updateUserPin(id: String, newPin: String) {
        db.segGpsDatabaseQueries.updateUserPin(newPin, id)
    }

    fun setUserActive(id: String, active: Boolean) {
        db.segGpsDatabaseQueries.updateUserActiveStatus(if (active) 1L else 0L, id)
    }

    // sessions

    fun getActiveSession(): Sessions? =
        db.segGpsDatabaseQueries.selectActiveSession().executeAsOneOrNull()

    // gps_tracks

    fun insertGpsTrack(
        id: String,
        userId: String,
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Double?,
        capturedAt: Long
    ) {
        db.segGpsDatabaseQueries.insertGpsTrack(id, userId, sessionId, latitude, longitude, accuracy, capturedAt)
    }

    fun getPendingGpsTracks(): List<Gps_tracks> =
        db.segGpsDatabaseQueries.selectPendingGpsTracks().executeAsList()

    fun markGpsTrackSynced(id: String) {
        db.segGpsDatabaseQueries.markGpsTrackSynced(id)
    }

    fun getGpsTracksByUserSession(userId: String, sessionId: String): List<Gps_tracks> =
        db.segGpsDatabaseQueries.selectGpsTracksByUserSession(userId, sessionId).executeAsList()

    // sessions

    fun insertSession(
        id: String,
        name: String,
        brigadeCode: String?,
        startedBy: String,
        startedAt: Long
    ) {
        db.segGpsDatabaseQueries.insertSession(id, name, brigadeCode, startedBy, startedAt)
    }

    fun closeSession(id: String, endedAt: Long) {
        db.segGpsDatabaseQueries.closeSession(endedAt, id)
    }

    fun getActiveSessions(): List<Sessions> =
        db.segGpsDatabaseQueries.selectActiveSessions().executeAsList()

    fun getAllSessions(): List<Sessions> =
        db.segGpsDatabaseQueries.selectAllSessions().executeAsList()

    // alerts

    fun insertAlert(
        id: String,
        senderId: String,
        sessionId: String,
        alertType: String,
        message: String?,
        targetRole: String,
        latitude: Double?,
        longitude: Double?,
        createdAt: Long
    ) {
        db.segGpsDatabaseQueries.insertAlert(
            id, senderId, sessionId, alertType, message, targetRole, latitude, longitude, createdAt
        )
    }

    fun getPendingAlerts(): List<Alerts> =
        db.segGpsDatabaseQueries.selectPendingAlerts().executeAsList()

    fun getAlertsBySession(sessionId: String): List<Alerts> =
        db.segGpsDatabaseQueries.selectAlertsBySession(sessionId).executeAsList()

    fun getUnattendedAlertsBySession(sessionId: String): List<Alerts> =
        db.segGpsDatabaseQueries.selectUnattendedAlertsBySession(sessionId).executeAsList()

    fun markAlertSynced(id: String) {
        db.segGpsDatabaseQueries.markAlertSynced(id)
    }

    fun markAlertAttended(id: String, attendedBy: String, respondedAt: Long) {
        db.segGpsDatabaseQueries.markAlertAttended(attendedBy, respondedAt, id)
    }

    fun markAlertOnWay(id: String, responseBy: String, respondedAt: Long) {
        db.segGpsDatabaseQueries.markAlertOnWay(responseBy, respondedAt, id)
    }

    fun getActiveAlerts(): List<Alerts> =
        db.segGpsDatabaseQueries.selectActiveAlerts().executeAsList()

    fun upsertRemoteAlert(
        id: String,
        senderId: String,
        sessionId: String,
        alertType: String,
        message: String?,
        targetRole: String,
        latitude: Double?,
        longitude: Double?,
        isAttended: Boolean,
        attendedBy: String?,
        createdAt: Long,
        responseStatus: String?,
        responseBy: String?,
        respondedAt: Long?
    ) {
        db.segGpsDatabaseQueries.upsertRemoteAlert(
            id, senderId, sessionId, alertType, message, targetRole,
            latitude, longitude, if (isAttended) 1L else 0L, attendedBy, createdAt,
            responseStatus, responseBy, respondedAt
        )
    }

    // block_assignments

    fun insertBlockAssignment(
        id: String,
        sessionId: String?,
        assignedTo: String,
        assignedBy: String,
        blockName: String,
        notes: String?,
        assignedAt: Long
    ) {
        db.segGpsDatabaseQueries.insertBlockAssignment(
            id, sessionId, assignedTo, assignedBy, blockName, notes, assignedAt
        )
    }

    /** Inserta o reemplaza una asignación traída desde Supabase (marcada como 'synced'). */
    fun upsertRemoteBlockAssignment(
        id: String,
        sessionId: String?,
        assignedTo: String,
        assignedBy: String,
        blockName: String,
        notes: String?,
        assignedAt: Long
    ) {
        db.segGpsDatabaseQueries.upsertRemoteBlockAssignment(
            id, sessionId, assignedTo, assignedBy, blockName, notes, assignedAt
        )
    }

    /** Última manzana asignada al usuario dentro de una jornada concreta. */
    fun getMyBlockAssignment(sessionId: String, assignedTo: String): Block_assignments? =
        db.segGpsDatabaseQueries.selectMyBlockAssignmentBySession(sessionId, assignedTo).executeAsOneOrNull()

    /** Última manzana asignada al usuario, con o sin jornada (admin puede asignar offline). */
    fun getMyLatestBlockAssignment(assignedTo: String): Block_assignments? =
        db.segGpsDatabaseQueries.selectMyLatestBlockAssignment(assignedTo).executeAsOneOrNull()

    /** Todas las manzanas asignadas a este trabajador (cualquier jornada). */
    fun getBlockAssignmentsForUser(assignedTo: String): List<Block_assignments> =
        db.segGpsDatabaseQueries.selectBlockAssignmentsForUser(assignedTo).executeAsList()

    fun getBlockAssignmentsBySession(sessionId: String): List<Block_assignments> =
        db.segGpsDatabaseQueries.selectBlockAssignmentsBySession(sessionId).executeAsList()

    fun getPendingBlockAssignments(): List<Block_assignments> =
        db.segGpsDatabaseQueries.selectPendingBlockAssignments().executeAsList()

    fun markBlockAssignmentSynced(id: String) {
        db.segGpsDatabaseQueries.markBlockAssignmentSynced(id)
    }

    fun getAllUsers(): List<Users> =
        db.segGpsDatabaseQueries.selectAllUsersAny().executeAsList()

    fun getGpsTracksBySession(sessionId: String): List<Gps_tracks> =
        db.segGpsDatabaseQueries.selectGpsTracksBySession(sessionId).executeAsList()

    fun markSessionExported(id: String) {
        db.segGpsDatabaseQueries.markSessionExported(id)
    }

    // mapper

    private fun Users.toUser() = User(
        id = id,
        dni = dni,
        phoneNumber = phone_number,
        fullName = full_name,
        role = UserRole.fromString(role),
        pin = pin,
        deviceId = device_id,
        isActive = is_active == 1L
    )
}
