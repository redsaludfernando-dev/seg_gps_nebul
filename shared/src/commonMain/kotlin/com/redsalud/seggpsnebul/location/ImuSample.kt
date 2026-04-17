package com.redsalud.seggpsnebul.location

/**
 * A single world-frame IMU measurement used by the location fusion filters.
 *
 * @param timestamp  Epoch milliseconds (Clock.System) at the moment of capture
 * @param aNorth     Acceleration in the geographic North direction (m/s²)
 * @param aEast      Acceleration in the geographic East direction (m/s²)
 * @param dt         Elapsed seconds since the previous sample
 */
data class ImuSample(
    val timestamp: Long,
    val aNorth: Double,
    val aEast: Double,
    val dt: Double
)
