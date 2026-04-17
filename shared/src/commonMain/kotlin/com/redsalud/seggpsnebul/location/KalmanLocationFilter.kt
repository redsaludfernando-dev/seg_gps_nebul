package com.redsalud.seggpsnebul.location

import kotlin.math.*

/**
 * Extended Kalman Filter (EKF) for GPS + IMU position fusion.
 *
 * ## State vector  x = [N, E, vN, vE]
 * - N, E   : North / East displacement in **meters** from the reference origin
 * - vN, vE : velocity in m/s in the North / East directions
 *
 * ## Coordinate frame
 * The filter works in a flat-earth ENU frame anchored at the first GPS fix
 * (the reference origin).  Results are converted back to lat/lon on demand.
 *
 * ## Usage
 * 1. Call [update] on the first GPS fix — this sets the reference origin.
 * 2. Call [predict] at ~50 Hz with world-frame accelerations from the IMU.
 * 3. Call [update] on each GPS fix (~5 s) to correct drift.
 * 4. Call [getPosition] at any time to obtain the current fused lat/lon.
 */
class KalmanLocationFilter {

    // ─── State ───────────────────────────────────────────────────────────────

    /** [N, E, vN, vE] in meters / m/s from the reference origin. */
    private var x = DoubleArray(4)

    /** 4×4 state covariance — initialized large so the first GPS fix dominates. */
    private var P = Mat4.diagonal(1000.0, 1000.0, 100.0, 100.0)

    /**
     * Process noise Q.
     * - Position rows (0,1): small — position changes smoothly with velocity.
     * - Velocity rows (2,3): larger — IMU noise accumulates in velocity.
     */
    private val Q = Mat4.diagonal(0.5, 0.5, 2.0, 2.0)

    /** Reference lat/lon (degrees) for ENU origin.  Set on first GPS fix. */
    private var lat0 = 0.0
    private var lon0 = 0.0
    private var cosLat0 = 1.0   // precomputed cos(lat0) for ENU ↔ degrees conversion

    var isInitialized: Boolean = false
        private set

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Predict step — call for each IMU sample (~50 Hz).
     *
     * @param aN  North acceleration in world frame (m/s²)
     * @param aE  East acceleration in world frame (m/s²)
     * @param dt  Elapsed seconds since the previous sample
     */
    fun predict(aN: Double, aE: Double, dt: Double) {
        if (!isInitialized || dt <= 0.0) return

        val dt2h = 0.5 * dt * dt

        // x_new = F·x + [aN·dt²/2, aE·dt²/2, aN·dt, aE·dt]
        x[0] = x[0] + x[2] * dt + dt2h * aN
        x[1] = x[1] + x[3] * dt + dt2h * aE
        x[2] = x[2] + aN * dt
        x[3] = x[3] + aE * dt

        // State transition matrix F
        val F = Mat4.of(
            1.0, 0.0, dt,  0.0,
            0.0, 1.0, 0.0, dt,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        )
        // P = F·P·Fᵀ + Q
        P = F * P * F.T + Q
    }

    /**
     * Update (correction) step — call on each GPS fix.
     *
     * On the first call this sets the ENU reference origin; subsequent calls
     * run the standard Kalman correction using GPS position as measurement.
     *
     * @param latDeg    GPS latitude (degrees)
     * @param lonDeg    GPS longitude (degrees)
     * @param accuracyM 1-sigma horizontal accuracy reported by the GPS (meters)
     * @param speedMs   GPS speed (m/s); pass null if unavailable or unreliable
     * @param bearingDeg GPS bearing (degrees, clockwise from North); null if unavailable
     */
    fun update(
        latDeg: Double,
        lonDeg: Double,
        accuracyM: Float,
        speedMs: Float? = null,
        bearingDeg: Float? = null
    ) {
        if (!isInitialized) {
            // First fix: set reference origin, reset state to zero
            lat0 = latDeg
            lon0 = lonDeg
            cosLat0 = cos(lat0 * PI / 180.0)
            x = DoubleArray(4)
            P = Mat4.diagonal(1000.0, 1000.0, 100.0, 100.0)
            isInitialized = true
            initVelocityFromGps(speedMs, bearingDeg)
            return
        }

        // ── Convert GPS fix to ENU meters ────────────────────────────────────
        val zN = (latDeg - lat0) * (PI / 180.0) * EARTH_RADIUS
        val zE = (lonDeg - lon0) * (PI / 180.0) * EARTH_RADIUS * cosLat0

        // ── Measurement noise R = σ²·I₂ ──────────────────────────────────────
        val sigma2 = accuracyM.toDouble().let { it * it }

        // ── Innovation covariance S = H·P·Hᵀ + R  (top-left 2×2 of P + R) ──
        // H = [[1,0,0,0],[0,1,0,0]]  so  H·P·Hᵀ = P[0:2, 0:2]
        val s00 = P[0, 0] + sigma2
        val s01 = P[0, 1]
        val s10 = P[1, 0]
        val s11 = P[1, 1] + sigma2
        val det = s00 * s11 - s01 * s10
        if (abs(det) < 1e-10) return    // near-singular — skip this update

        // ── Kalman gain K = P·Hᵀ · S⁻¹  (4×2) ──────────────────────────────
        // P·Hᵀ = first 2 columns of P
        // S⁻¹ = (1/det) · [[ s11, -s01], [-s10,  s00]]
        val k = Array(4) { i ->
            val p0 = P[i, 0]; val p1 = P[i, 1]
            doubleArrayOf(
                (p0 * s11 - p1 * s10) / det,
                (p1 * s00 - p0 * s01) / det
            )
        }

        // ── State update  x = x + K·y  (y = z − H·x) ────────────────────────
        val yN = zN - x[0]
        val yE = zE - x[1]
        for (i in 0..3) {
            x[i] += k[i][0] * yN + k[i][1] * yE
        }

        // ── Covariance update  P = (I − K·H)·P ───────────────────────────────
        // (I − K·H)[i,j] = δ(i,j) − (j==0 ? k[i][0] : 0) − (j==1 ? k[i][1] : 0)
        // (I − K·H)_im = δ(i,m) − (K·H)_im
        // (K·H)_im  = k[i][0] if m==0, k[i][1] if m==1, else 0   (H selects first 2 cols)
        val newP = Mat4()
        for (i in 0..3) for (j in 0..3) {
            var sum = 0.0
            for (m in 0..3) {
                val ikH_im = (if (i == m) 1.0 else 0.0) -
                             when (m) { 0 -> k[i][0]; 1 -> k[i][1]; else -> 0.0 }
                sum += ikH_im * P[m, j]
            }
            newP[i, j] = sum
        }
        P = newP

        // ── Optional soft velocity correction from GPS speed/bearing ─────────
        initVelocityFromGps(speedMs, bearingDeg, blend = 0.4)
    }

    /** Resets the filter.  Next GPS fix re-initializes the reference origin. */
    fun reset() {
        isInitialized = false
        x = DoubleArray(4)
        P = Mat4.diagonal(1000.0, 1000.0, 100.0, 100.0)
    }

    /** Current fused position as (latitude°, longitude°). */
    fun getPosition(): Pair<Double, Double> {
        val lat = lat0 + x[0] / EARTH_RADIUS * (180.0 / PI)
        val lon = lon0 + x[1] / (EARTH_RADIUS * cosLat0) * (180.0 / PI)
        return Pair(lat, lon)
    }

    /**
     * Estimated 1-sigma horizontal accuracy derived from the state covariance P.
     * Returns the combined standard deviation of N and E position errors.
     */
    fun getEstimatedAccuracy(): Float = sqrt(P[0, 0] + P[1, 1]).toFloat()

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun initVelocityFromGps(speedMs: Float?, bearingDeg: Float?, blend: Double = 1.0) {
        if (speedMs == null || bearingDeg == null || speedMs < 0.5f) return
        val br = bearingDeg * PI / 180.0
        val gpsVN = speedMs * cos(br)
        val gpsVE = speedMs * sin(br)
        x[2] = blend * gpsVN + (1.0 - blend) * x[2]
        x[3] = blend * gpsVE + (1.0 - blend) * x[3]
    }

    companion object {
        private const val EARTH_RADIUS = 6_371_000.0   // meters
    }
}

// ─── Internal 4×4 matrix ─────────────────────────────────────────────────────

/**
 * Minimal 4×4 row-major double matrix for EKF covariance arithmetic.
 * Not a general-purpose linear algebra class — only the operations needed
 * by [KalmanLocationFilter] are implemented.
 */
internal class Mat4 {
    val d = DoubleArray(16)   // row-major: d[r*4 + c]

    operator fun get(r: Int, c: Int): Double = d[r * 4 + c]
    operator fun set(r: Int, c: Int, v: Double) { d[r * 4 + c] = v }

    operator fun plus(o: Mat4) = Mat4().also { m -> for (i in 0..15) m.d[i] = d[i] + o.d[i] }

    operator fun times(o: Mat4): Mat4 {
        val result = Mat4()
        for (i in 0..3) for (j in 0..3) {
            var s = 0.0
            for (k in 0..3) s += this[i, k] * o[k, j]
            result[i, j] = s
        }
        return result
    }

    /** Transpose */
    val T: Mat4 get() = Mat4().also { t -> for (i in 0..3) for (j in 0..3) t[i, j] = this[j, i] }

    companion object {
        /** Diagonal matrix from up to 4 values; off-diagonals are 0. */
        fun diagonal(vararg v: Double) = Mat4().also { m -> v.forEachIndexed { i, x -> m[i, i] = x } }

        /** Build from 16 values in row-major order. */
        fun of(vararg v: Double) = Mat4().also { m -> v.forEachIndexed { i, x -> m.d[i] = x } }
    }
}
