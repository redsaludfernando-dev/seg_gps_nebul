package com.redsalud.seggpsnebul.location

/**
 * Orchestrates GPS + IMU sensor fusion.
 *
 * Maintains an [EKF][KalmanLocationFilter] and a [ComplementaryLocationFilter]
 * in parallel. Only the [mode] filter drives [lastFused]; the other is updated
 * silently so it stays warm and can be switched to without a cold start.
 *
 * ## Thread-safety
 * All public methods must be called from the **same thread** (either both
 * from the main looper or both from a single background thread).  The Android
 * integration in [GpsTrackingService] calls everything on the main looper.
 *
 * @param mode  Which filter to use as the authoritative output.
 */
class LocationFusionEngine(val mode: Mode = Mode.EKF) {

    enum class Mode { EKF, COMPLEMENTARY }

    private val ekf  = KalmanLocationFilter()
    private val comp = ComplementaryLocationFilter()

    /** Last fused position — null until the first GPS fix is received. */
    var lastFused: LocationPoint? = null
        private set

    val isInitialized: Boolean
        get() = ekf.isInitialized || comp.isInitialized

    // ─── Feed IMU ─────────────────────────────────────────────────────────────

    /**
     * Forward an IMU sample to both filters (predict step).
     * Call at ~50 Hz from [ImuProvider].
     */
    fun onImuSample(sample: ImuSample) {
        ekf.predict(sample.aNorth, sample.aEast, sample.dt)
        comp.predict(sample.aNorth, sample.aEast, sample.dt)
    }

    // ─── Feed GPS ─────────────────────────────────────────────────────────────

    /**
     * Forward a GPS fix to both filters (update step) and return the fused
     * [LocationPoint] from the active [mode].
     *
     * @param latDeg     GPS latitude (degrees)
     * @param lonDeg     GPS longitude (degrees)
     * @param accuracyM  1-sigma horizontal accuracy (meters)
     * @param speedMs    GPS speed (m/s); null if unavailable
     * @param bearingDeg GPS bearing (degrees CW from North); null if unavailable
     */
    fun onGpsFix(
        latDeg: Double,
        lonDeg: Double,
        accuracyM: Float,
        speedMs: Float? = null,
        bearingDeg: Float? = null
    ): LocationPoint {
        // Update both filters regardless of active mode (keeps warm for switching)
        ekf.update(latDeg, lonDeg, accuracyM, speedMs, bearingDeg)
        comp.onGpsFix(latDeg, lonDeg, accuracyM, speedMs, bearingDeg)

        val fused = when (mode) {
            Mode.EKF -> {
                val (lat, lon) = ekf.getPosition()
                LocationPoint(lat, lon, ekf.getEstimatedAccuracy())
            }
            Mode.COMPLEMENTARY -> {
                val (lat, lon) = comp.getPosition()
                // Estimated accuracy: GPS accuracy reduced by blend factor
                val alpha = (1.0 - accuracyM / 60.0).coerceIn(0.30, 0.90).toFloat()
                LocationPoint(lat, lon, accuracyM * (1.0f - alpha))
            }
        }
        lastFused = fused
        return fused
    }

    /**
     * Reset both filters.  The next [onGpsFix] call will re-initialize
     * the reference origin — call this when a new session starts.
     */
    fun reset() {
        ekf.reset()
        comp.reset()
        lastFused = null
    }
}
