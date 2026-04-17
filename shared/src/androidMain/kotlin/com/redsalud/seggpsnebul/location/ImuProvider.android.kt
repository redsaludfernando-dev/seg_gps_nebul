package com.redsalud.seggpsnebul.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * Android IMU provider — wraps [SensorManager] to deliver world-frame
 * accelerations as [ImuSample] objects at ~50 Hz.
 *
 * ## Sensors used
 * - [Sensor.TYPE_ROTATION_VECTOR] — fused orientation (gyro + acc + mag),
 *   used to build the device→world rotation matrix.
 * - [Sensor.TYPE_LINEAR_ACCELERATION] — accelerometer with gravity removed,
 *   in device frame.  Rotated to world frame (X=East, Y=North, Z=Up) before
 *   being emitted as [ImuSample].
 *
 * ## Threading
 * Both sensors are registered with [Looper.getMainLooper] so callbacks arrive
 * on the main thread — the same thread that receives GPS fixes in
 * [GpsTrackingService].  This avoids synchronisation on [LocationFusionEngine].
 *
 * @param context Application context.
 */
class ImuProvider(private val context: Context) : SensorEventListener {

    /** Invoked on the main thread for each new world-frame acceleration sample. */
    var onSample: ((ImuSample) -> Unit)? = null

    private var sensorManager: SensorManager? = null

    // Latest rotation matrix from TYPE_ROTATION_VECTOR (3×3, row-major)
    private val rotMatrix    = FloatArray(9)
    private var hasRotation  = false

    // Nanosecond timestamp of the previous LINEAR_ACCELERATION event
    private var lastNs: Long = 0L

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    fun start() {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        val handler = Handler(Looper.getMainLooper())

        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.also { s ->
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME, handler)
        }
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.also { s ->
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME, handler)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager  = null
        hasRotation    = false
        lastNs         = 0L
    }

    // ─── SensorEventListener ─────────────────────────────────────────────────

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {

            Sensor.TYPE_ROTATION_VECTOR -> {
                // Produces a 3×3 rotation matrix R such that:
                //   world_vec = R · device_vec
                // where world axes are  X=East, Y=North, Z=Up
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                hasRotation = true
            }

            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (!hasRotation) return

                val nowNs = event.timestamp
                val dt = if (lastNs == 0L) DEFAULT_DT_SEC
                         else (nowNs - lastNs) / 1_000_000_000.0
                lastNs = nowNs

                // Guard against implausible intervals (first sample, sensor gaps)
                if (dt <= 0.0 || dt > 0.5) return

                // Rotate device-frame [ax, ay, az] → world-frame [East, North, Up]
                // R is row-major:  world[row] = Σ_col R[row*3+col] * device[col]
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()
                val az = event.values[2].toDouble()

                val east  = rotMatrix[0] * ax + rotMatrix[1] * ay + rotMatrix[2] * az
                val north = rotMatrix[3] * ax + rotMatrix[4] * ay + rotMatrix[5] * az
                // val up = rotMatrix[6]*ax + rotMatrix[7]*ay + rotMatrix[8]*az  // unused

                onSample?.invoke(
                    ImuSample(
                        timestamp = System.currentTimeMillis(),
                        aNorth    = north,
                        aEast     = east,
                        dt        = dt
                    )
                )
            }
        }
    }

    companion object {
        /** Assumed dt for the very first sample when no previous timestamp exists. */
        private const val DEFAULT_DT_SEC = 0.02   // 50 Hz default
    }
}
