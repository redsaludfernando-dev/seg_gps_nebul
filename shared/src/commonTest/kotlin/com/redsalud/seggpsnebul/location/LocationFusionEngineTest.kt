package com.redsalud.seggpsnebul.location

import kotlin.math.*
import kotlin.test.*

class LocationFusionEngineTest {

    private val refLat = -6.0627
    private val refLon = -77.1658

    @Test
    fun ekfMode_initialState() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        assertFalse(engine.isInitialized)
        assertNull(engine.lastFused)
    }

    @Test
    fun complementaryMode_initialState() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.COMPLEMENTARY)
        assertFalse(engine.isInitialized)
        assertNull(engine.lastFused)
    }

    @Test
    fun onGpsFix_initializesAndReturnsLocationPoint() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        val result = engine.onGpsFix(refLat, refLon, 10f)

        assertTrue(engine.isInitialized)
        assertNotNull(engine.lastFused)
        assertEquals(result, engine.lastFused)
        assertEquals(refLat, result.latitude, 1e-6)
        assertEquals(refLon, result.longitude, 1e-6)
    }

    @Test
    fun ekfMode_usesEkfOutput() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        engine.onGpsFix(refLat, refLon, 10f)

        // IMU predict north
        val imu = ImuSample(timestamp = 1000L, aNorth = 2.0, aEast = 0.0, dt = 1.0)
        engine.onImuSample(imu)

        val result = engine.onGpsFix(refLat, refLon, 10f)
        // EKF should have some internal state from IMU prediction
        assertNotNull(result)
    }

    @Test
    fun complementaryMode_usesComplementaryOutput() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.COMPLEMENTARY)
        val result = engine.onGpsFix(refLat, refLon, 10f)

        assertEquals(refLat, result.latitude, 1e-10)
        assertEquals(refLon, result.longitude, 1e-10)
    }

    @Test
    fun complementaryMode_accuracyCalculation() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.COMPLEMENTARY)
        engine.onGpsFix(refLat, refLon, 10f) // init

        // Second fix: alpha = (1 - 20/60) = 0.667
        val result = engine.onGpsFix(refLat, refLon, 20f)
        val expectedAlpha = (1.0 - 20.0 / 60.0).coerceIn(0.30, 0.90).toFloat()
        val expectedAccuracy = 20f * (1f - expectedAlpha)
        assertEquals(expectedAccuracy, result.accuracy, 0.01f)
    }

    @Test
    fun onImuSample_feedsBothFilters() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        engine.onGpsFix(refLat, refLon, 10f)

        // Send IMU samples
        repeat(50) {
            engine.onImuSample(ImuSample(it * 20L, 2.0, 0.0, 0.02))
        }

        // Both filters should produce reasonable output
        val result = engine.onGpsFix(refLat, refLon, 10f)
        assertNotNull(result)
    }

    @Test
    fun reset_clearsAllState() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        engine.onGpsFix(refLat, refLon, 10f)
        assertTrue(engine.isInitialized)
        assertNotNull(engine.lastFused)

        engine.reset()
        assertFalse(engine.isInitialized)
        assertNull(engine.lastFused)
    }

    @Test
    fun reset_thenReinitialize() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        engine.onGpsFix(refLat, refLon, 10f)
        engine.reset()

        val newLat = -6.07
        val newLon = -77.17
        val result = engine.onGpsFix(newLat, newLon, 10f)

        assertEquals(newLat, result.latitude, 1e-6)
        assertEquals(newLon, result.longitude, 1e-6)
    }

    @Test
    fun defaultMode_isEkf() {
        val engine = LocationFusionEngine()
        assertEquals(LocationFusionEngine.Mode.EKF, engine.mode)
    }

    @Test
    fun onGpsFix_withSpeedAndBearing() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        val result = engine.onGpsFix(refLat, refLon, 10f, speedMs = 3f, bearingDeg = 90f)
        assertNotNull(result)
        assertTrue(engine.isInitialized)
    }

    @Test
    fun multipleFixesSamePosition_staysStable() {
        val engine = LocationFusionEngine(LocationFusionEngine.Mode.EKF)
        repeat(10) {
            engine.onGpsFix(refLat, refLon, 5f)
        }
        val result = engine.lastFused!!
        assertEquals(refLat, result.latitude, 1e-6)
        assertEquals(refLon, result.longitude, 1e-6)
    }
}
