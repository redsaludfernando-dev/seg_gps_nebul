package com.redsalud.seggpsnebul

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.redsalud.seggpsnebul.domain.model.UserRole
import com.redsalud.seggpsnebul.location.ImuProvider
import com.redsalud.seggpsnebul.location.LocationFusionEngine
import com.redsalud.seggpsnebul.notifications.AlertNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class GpsTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var imuProvider: ImuProvider
    private val fusionEngine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)

    // GPS callback — runs on main looper (same thread as ImuProvider callbacks)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val fused = fusionEngine.onGpsFix(
                    latDeg     = loc.latitude,
                    lonDeg     = loc.longitude,
                    accuracyM  = loc.accuracy,
                    speedMs    = if (loc.hasSpeed())   loc.speed   else null,
                    bearingDeg = if (loc.hasBearing()) loc.bearing else null
                )
                scope.launch {
                    recordTrack(fused.latitude, fused.longitude, fused.accuracy.toDouble())
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        // ImuProvider delivers callbacks on main looper — no extra synchronisation needed
        imuProvider = ImuProvider(applicationContext).apply {
            onSample = { sample -> fusionEngine.onImuSample(sample) }
        }
        createNotificationChannel()
        AlertNotificationHelper.createChannel(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        imuProvider.start()
        requestLocationUpdates()
        listenForAlerts()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        imuProvider.stop()
        fusedClient.removeLocationUpdates(locationCallback)
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_INTERVAL_MS)
            .setMinUpdateIntervalMillis(GPS_MIN_INTERVAL_MS)
            .setMaxUpdateDelayMillis(GPS_MAX_DELAY_MS)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private suspend fun recordTrack(lat: Double, lon: Double, accuracy: Double) {
        val user    = AppContainer.currentUser.value ?: return
        val session = AppContainer.localDataSource.getActiveSession() ?: return
        AppContainer.localDataSource.insertGpsTrack(
            id        = UUID.randomUUID().toString(),
            userId    = user.id,
            sessionId = session.id,
            latitude  = lat,
            longitude = lon,
            accuracy  = accuracy,
            capturedAt = System.currentTimeMillis()
        )
        if (AppContainer.connectivityObserver.isOnline.value) {
            AppContainer.gpsSyncRepository.syncPendingTracks()
        }
    }

    /**
     * Collects Realtime alert events and shows a heads-up notification for:
     * - Jefe de Brigada: all alerts
     * - Chofer: only alerts targeting "chofer" or "all"
     * Alerts sent by the current user are silently ignored.
     */
    private fun listenForAlerts() {
        scope.launch {
            AppContainer.realtimeRepository.newAlerts.collect { event ->
                val user = AppContainer.currentUser.value ?: return@collect
                if (event.senderId == user.id) return@collect   // don't notify sender
                val notify = when (user.role) {
                    UserRole.JEFE_BRIGADA -> true
                    UserRole.CHOFER       -> event.targetRole == "chofer" || event.targetRole == "all"
                    else                  -> false
                }
                if (notify) {
                    AlertNotificationHelper.show(applicationContext, event.alertType, event.message)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "GPS Nebulizacion", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("GPS activo")
        .setContentText("Registrando posicion cada 5 segundos")
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        private const val NOTIF_ID           = 1001
        private const val CHANNEL_ID         = "gps_tracking"
        private const val GPS_INTERVAL_MS    = 5_000L   // request a fix every 5 s
        private const val GPS_MIN_INTERVAL_MS = 4_000L  // accept fixes no faster than 4 s
        private const val GPS_MAX_DELAY_MS   = 8_000L   // batch tolerance
    }
}
