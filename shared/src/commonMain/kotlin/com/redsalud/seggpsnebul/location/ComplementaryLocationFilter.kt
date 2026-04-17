package com.redsalud.seggpsnebul.location

import kotlin.math.*

/**
 * Complementary filter for GPS + IMU position fusion.
 *
 * Simpler alternative to the EKF: integrates IMU acceleration between GPS
 * fixes (dead reckoning) and blends the result with each GPS measurement.
 *
 * ## When to prefer this over EKF
 * - Devices without reliable IMU sensors
 * - Extremely constrained CPU environments
 * - As a sanity-check / fallback if EKF diverges
 *
 * ## Blend weight α
 * `α = 1.0` → fully trust GPS position; `α = 0.0` → fully trust dead reckoning.
 * [onGpsFix] computes α automatically from GPS accuracy.
 */
class ComplementaryLocationFilter {

    private var lat = 0.0
    private var lon = 0.0
    private var vN  = 0.0   // velocity North (m/s)
    private var vE  = 0.0   // velocity East  (m/s)

    var isInitialized: Boolean = false
        private set

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Predict step (dead reckoning) — call at ~50 Hz.
     *
     * @param aN  North acceleration, world frame (m/s²)
     * @param aE  East acceleration, world frame (m/s²)
     * @param dt  Elapsed seconds since the previous sample
     */
    fun predict(aN: Double, aE: Double, dt: Double) {
        if (!isInitialized || dt <= 0.0) return

        // Clamp acceleration spikes (consumer IMU can produce transient outliers)
        vN = (vN + aN.coerceIn(-ACC_CLAMP, ACC_CLAMP) * dt).coerceIn(-SPEED_CLAMP, SPEED_CLAMP)
        vE = (vE + aE.coerceIn(-ACC_CLAMP, ACC_CLAMP) * dt).coerceIn(-SPEED_CLAMP, SPEED_CLAMP)

        val cosLat = cos(lat * PI / 180.0)
        lat += vN * dt / EARTH_RADIUS * RAD_TO_DEG
        lon += vE * dt / (EARTH_RADIUS * cosLat) * RAD_TO_DEG
    }

    /**
     * GPS correction step.
     *
     * GPS accuracy drives the blend weight α:
     * - accuracyM ≤  5 m → α ≈ 0.90 (trust GPS heavily)
     * - accuracyM = 20 m → α ≈ 0.67
     * - accuracyM ≥ 60 m → α = 0.30 (trust dead reckoning more)
     *
     * @param latGps     GPS latitude (degrees)
     * @param lonGps     GPS longitude (degrees)
     * @param accuracyM  GPS horizontal accuracy (meters)
     * @param speedMs    GPS speed (m/s); null if unavailable
     * @param bearingDeg GPS bearing (degrees CW from North); null if unavailable
     */
    fun onGpsFix(
        latGps: Double,
        lonGps: Double,
        accuracyM: Float,
        speedMs: Float? = null,
        bearingDeg: Float? = null
    ) {
        if (!isInitialized) {
            lat = latGps
            lon = lonGps
            isInitialized = true
            applyGpsVelocity(speedMs, bearingDeg, blend = 1.0)
            return
        }

        // α ∈ [0.30, 0.90]: trust GPS more when accuracy is good
        val alpha = (1.0 - accuracyM / 60.0).coerceIn(0.30, 0.90)
        lat = alpha * latGps + (1.0 - alpha) * lat
        lon = alpha * lonGps + (1.0 - alpha) * lon

        applyGpsVelocity(speedMs, bearingDeg, blend = alpha)
        if (speedMs == null || speedMs < 0.5f) {
            // No GPS velocity — partially damp the dead-reckoning drift
            vN *= (1.0 - alpha)
            vE *= (1.0 - alpha)
        }
    }

    fun getPosition(): Pair<Double, Double> = Pair(lat, lon)

    fun reset() {
        isInitialized = false
        vN = 0.0
        vE = 0.0
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun applyGpsVelocity(speedMs: Float?, bearingDeg: Float?, blend: Double) {
        if (speedMs == null || bearingDeg == null || speedMs < 0.5f) return
        val br = bearingDeg * PI / 180.0
        val gpsVN = speedMs * cos(br)
        val gpsVE = speedMs * sin(br)
        vN = blend * gpsVN + (1.0 - blend) * vN
        vE = blend * gpsVE + (1.0 - blend) * vE
    }

    companion object {
        private const val EARTH_RADIUS = 6_371_000.0
        private const val RAD_TO_DEG   = 180.0 / PI
        private const val ACC_CLAMP    = 6.0    // m/s² — rejects violent sensor spikes
        private const val SPEED_CLAMP  = 15.0   // m/s  — ~54 km/h max for dirt-road moto
    }
}
