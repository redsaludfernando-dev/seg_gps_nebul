package com.redsalud.seggpsnebul.location

import kotlin.math.*
import kotlin.test.*

class KalmanLocationFilterTest {

    private lateinit var filter: KalmanLocationFilter

    // Rioja, Peru approx coordinates
    private val refLat = -6.0627
    private val refLon = -77.1658

    @BeforeTest
    fun setUp() {
        filter = KalmanLocationFilter()
    }

    @Test
    fun initialState_isNotInitialized() {
        assertFalse(filter.isInitialized)
    }

    @Test
    fun firstUpdate_initializesFilter() {
        filter.update(refLat, refLon, 10f)
        assertTrue(filter.isInitialized)
    }

    @Test
    fun firstUpdate_positionMatchesGpsFix() {
        filter.update(refLat, refLon, 10f)
        val (lat, lon) = filter.getPosition()
        assertEquals(refLat, lat, 1e-6)
        assertEquals(refLon, lon, 1e-6)
    }

    @Test
    fun predict_beforeInit_isNoop() {
        filter.predict(1.0, 1.0, 0.02)
        assertFalse(filter.isInitialized)
    }

    @Test
    fun predict_withZeroDt_isNoop() {
        filter.update(refLat, refLon, 10f)
        val posBefore = filter.getPosition()
        filter.predict(5.0, 5.0, 0.0)
        val posAfter = filter.getPosition()
        assertEquals(posBefore.first, posAfter.first, 1e-10)
        assertEquals(posBefore.second, posAfter.second, 1e-10)
    }

    @Test
    fun predict_withNegativeDt_isNoop() {
        filter.update(refLat, refLon, 10f)
        val posBefore = filter.getPosition()
        filter.predict(5.0, 5.0, -1.0)
        val posAfter = filter.getPosition()
        assertEquals(posBefore.first, posAfter.first, 1e-10)
        assertEquals(posBefore.second, posAfter.second, 1e-10)
    }

    @Test
    fun predict_movesPositionInAccelerationDirection() {
        filter.update(refLat, refLon, 10f)

        // Accelerate north for 1 second
        filter.predict(10.0, 0.0, 1.0)
        val (lat, _) = filter.getPosition()

        // Should have moved north (lat increases)
        assertTrue(lat > refLat, "Latitude should increase when accelerating north")
    }

    @Test
    fun predict_movesEast() {
        filter.update(refLat, refLon, 10f)

        // Accelerate east for 1 second
        filter.predict(0.0, 10.0, 1.0)
        val (_, lon) = filter.getPosition()

        // Should have moved east (lon increases in Rioja hemisphere)
        assertTrue(lon > refLon, "Longitude should increase when accelerating east")
    }

    @Test
    fun update_secondFix_correctsPosition() {
        filter.update(refLat, refLon, 10f)

        // Second GPS fix 100m north
        val offsetLat = refLat + 100.0 / 6_371_000.0 * (180.0 / PI)
        filter.update(offsetLat, refLon, 5f)

        val (lat, _) = filter.getPosition()
        // Should be close to the second fix (GPS is trusted more with better accuracy)
        assertTrue(abs(lat - offsetLat) < abs(lat - refLat),
            "Position should be closer to second GPS fix than to origin")
    }

    @Test
    fun update_withSpeedAndBearing_setsVelocity() {
        filter.update(refLat, refLon, 10f, speedMs = 5f, bearingDeg = 0f)
        // After init with speed=5 bearing=0 (north), predict should move north
        filter.predict(0.0, 0.0, 1.0)
        val (lat, _) = filter.getPosition()
        assertTrue(lat > refLat, "Should move north with northward velocity")
    }

    @Test
    fun reset_clearsInitialization() {
        filter.update(refLat, refLon, 10f)
        assertTrue(filter.isInitialized)

        filter.reset()
        assertFalse(filter.isInitialized)
    }

    @Test
    fun reset_thenUpdate_reinitializesAtNewOrigin() {
        filter.update(refLat, refLon, 10f)
        filter.reset()

        val newLat = -6.07
        val newLon = -77.17
        filter.update(newLat, newLon, 10f)

        val (lat, lon) = filter.getPosition()
        assertEquals(newLat, lat, 1e-6)
        assertEquals(newLon, lon, 1e-6)
    }

    @Test
    fun getEstimatedAccuracy_decreasesAfterGpsFix() {
        filter.update(refLat, refLon, 10f)
        val accAfterFirst = filter.getEstimatedAccuracy()

        // Second fix with good accuracy
        filter.update(refLat, refLon, 3f)
        val accAfterSecond = filter.getEstimatedAccuracy()

        assertTrue(accAfterSecond < accAfterFirst,
            "Accuracy should improve after second GPS fix")
    }

    @Test
    fun multipleFixesSamePosition_converges() {
        filter.update(refLat, refLon, 10f)

        // Many fixes at same position should converge
        repeat(10) {
            filter.update(refLat, refLon, 5f)
        }

        val (lat, lon) = filter.getPosition()
        assertEquals(refLat, lat, 1e-7)
        assertEquals(refLon, lon, 1e-7)
    }

    @Test
    fun predictAndUpdate_fusedPositionBetweenPredictedAndGps() {
        filter.update(refLat, refLon, 10f)

        // Predict north
        repeat(50) {
            filter.predict(2.0, 0.0, 0.02) // 50Hz for 1 second
        }
        val predictedLat = filter.getPosition().first

        // GPS fix at origin (disagreement with prediction)
        filter.update(refLat, refLon, 10f)
        val fusedLat = filter.getPosition().first

        // Fused should be between origin and predicted
        assertTrue(fusedLat >= refLat, "Fused lat should be >= origin")
        assertTrue(fusedLat <= predictedLat, "Fused lat should be <= predicted")
    }
}
