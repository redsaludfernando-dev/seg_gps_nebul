package com.redsalud.seggpsnebul.location

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    @OptIn(ExperimentalTime::class)
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)
