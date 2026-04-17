package com.redsalud.seggpsnebul.location

import kotlin.math.*
import kotlin.test.*

class ComplementaryLocationFilterTest {

    private lateinit var filter: ComplementaryLocationFilter

    private val refLat = -6.0627
    private val refLon = -77.1658

    @BeforeTest
    fun setUp() {
        filter = ComplementaryLocationFilter()
    }

    @Test
    fun initialState_isNotInitialized() {
        assertFalse(filter.isInitialized)
    }

    @Test
    fun firstGpsFix_initializesFilter() {
        filter.onGpsFix(refLat, refLon, 10f)
        assertTrue(filter.isInitialized)
    }

    @Test
    fun firstGpsFix_positionMatchesGps() {
        filter.onGpsFix(refLat, refLon, 10f)
        val (lat, lon) = filter.getPosition()
        assertEquals(refLat, lat, 1e-10)
        assertEquals(refLon, lon, 1e-10)
    }

    @Test
    fun predict_beforeInit_isNoop() {
        filter.predict(5.0, 5.0, 0.02)
        assertFalse(filter.isInitialized)
    }

    @Test
    fun predict_withZeroDt_isNoop() {
        filter.onGpsFix(refLat, refLon, 10f)
        val posBefore = filter.getPosition()
        filter.predict(5.0, 5.0, 0.0)
        val posAfter = filter.getPosition()
        assertEquals(posBefore.first, posAfter.first, 1e-10)
        assertEquals(posBefore.second, posAfter.second, 1e-10)
    }

    @Test
    fun predict_movesNorth_withNorthVelocity() {
        filter.onGpsFix(refLat, refLon, 10f, speedMs = 5f, bearingDeg = 0f)

        // Dead reckon for 1 second (50 samples at 0 accel)
        repeat(50) { filter.predict(0.0, 0.0, 0.02) }

        val (lat, _) = filter.getPosition()
        assertTrue(lat > refLat, "Should move north with northward velocity")
    }

    @Test
    fun onGpsFix_blendsPositionBasedOnAccuracy() {
        filter.onGpsFix(refLat, refLon, 10f)

        // Dead reckon north
        filter.onGpsFix(refLat, refLon, 10f, speedMs = 5f, bearingDeg = 0f)
        repeat(100) { filter.predict(0.0, 0.0, 0.02) } // 2 seconds north

        val latBeforeFix = filter.getPosition().first

        // GPS fix back at origin with good accuracy -> blend heavily toward GPS
        filter.onGpsFix(refLat, refLon, 3f)
        val latAfterFix = filter.getPosition().first

        // Should be pulled back toward refLat
        assertTrue(abs(latAfterFix - refLat) < abs(latBeforeFix - refLat),
            "Good GPS fix should pull position back toward GPS")
    }

    @Test
    fun onGpsFix_highAccuracy_trustsGpsMore() {
        filter.onGpsFix(refLat, refLon, 10f)
        val offsetLat = refLat + 0.001 // ~111m north

        // Dead reckon slightly (so position differs from GPS)
        repeat(50) { filter.predict(1.0, 0.0, 0.02) }

        val latBefore = filter.getPosition().first

        // High accuracy fix at offset position
        filter.onGpsFix(offsetLat, refLon, 2f)
        val latAfterGoodGps = filter.getPosition().first

        // Reset and try with poor accuracy
        filter.reset()
        filter.onGpsFix(refLat, refLon, 10f)
        repeat(50) { filter.predict(1.0, 0.0, 0.02) }

        filter.onGpsFix(offsetLat, refLon, 55f)
        val latAfterBadGps = filter.getPosition().first

        // Good GPS should move position closer to GPS than bad GPS
        assertTrue(abs(latAfterGoodGps - offsetLat) < abs(latAfterBadGps - offsetLat),
            "Higher accuracy GPS should pull position closer to GPS fix")
    }

    @Test
    fun onGpsFix_lowSpeed_dampsVelocity() {
        filter.onGpsFix(refLat, refLon, 10f, speedMs = 5f, bearingDeg = 0f)

        // GPS fix with no speed data and good accuracy
        filter.onGpsFix(refLat, refLon, 3f)

        // Predict - should barely move due to damped velocity
        repeat(50) { filter.predict(0.0, 0.0, 0.02) }

        val (lat, _) = filter.getPosition()
        // Position should be very close to origin since velocity was damped
        assertTrue(abs(lat - refLat) < 0.0001,
            "Velocity should be damped after GPS fix with no speed data")
    }

    @Test
    fun predict_clampsExtremeAcceleration() {
        filter.onGpsFix(refLat, refLon, 10f)

        // Extreme acceleration that should be clamped to ACC_CLAMP=6
        filter.predict(100.0, 0.0, 1.0)

        val (lat, _) = filter.getPosition()
        // With clamp at 6 m/s², displacement ≈ 0.5*6*1² = 3m
        val displacementDeg = abs(lat - refLat)
        val displacementM = displacementDeg * PI / 180.0 * 6_371_000.0
        assertTrue(displacementM < 10.0, "Displacement should be clamped (got ${displacementM}m)")
    }

    @Test
    fun reset_clearsState() {
        filter.onGpsFix(refLat, refLon, 10f)
        assertTrue(filter.isInitialized)

        filter.reset()
        assertFalse(filter.isInitialized)
    }

    @Test
    fun alpha_rangeIsCorrect() {
        filter.onGpsFix(refLat, refLon, 10f)

        // Very good accuracy (2m) → alpha = (1 - 2/60) = 0.967 → clamped to 0.90
        val offsetLat = refLat + 0.001
        filter.onGpsFix(offsetLat, refLon, 2f)
        val (latGoodGps, _) = filter.getPosition()
        // alpha=0.90 → lat = 0.90*offsetLat + 0.10*refLat
        val expectedGood = 0.90 * offsetLat + 0.10 * refLat
        assertEquals(expectedGood, latGoodGps, 1e-8)

        // Very bad accuracy (60m+) → alpha clamped to 0.30
        filter.reset()
        filter.onGpsFix(refLat, refLon, 10f)
        filter.onGpsFix(offsetLat, refLon, 60f)
        val (latBadGps, _) = filter.getPosition()
        val expectedBad = 0.30 * offsetLat + 0.70 * refLat
        assertEquals(expectedBad, latBadGps, 1e-8)
    }
}
