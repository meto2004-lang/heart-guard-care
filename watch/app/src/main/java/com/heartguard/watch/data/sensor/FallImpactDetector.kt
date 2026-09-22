package com.heartguard.watch.data.sensor

import com.heartguard.shared.constants.SensorConstants
import kotlin.math.sqrt

/**
 * Simple impact heuristic, not a clinically validated fall classifier.
 * Requires a new threshold crossing and a cooldown so one impact's sensor
 * samples do not create many distinct emergency events.
 */
internal class FallImpactDetector {
    companion object {
        const val COOLDOWN_MS = 10_000L
    }

    private var aboveThreshold = false
    private var lastFallAt: Long? = null

    fun process(x: Float, y: Float, z: Float, elapsedRealtimeMs: Long): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return false
        val magnitude = sqrt(x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)
        val isAbove = magnitude > SensorConstants.ACCELEROMETER_FALL_THRESHOLD
        val crossing = isAbove && !aboveThreshold
        aboveThreshold = isAbove
        val previous = lastFallAt
        if (!crossing || (previous != null && elapsedRealtimeMs - previous < COOLDOWN_MS)) {
            return false
        }
        lastFallAt = elapsedRealtimeMs
        return true
    }

    fun reset() {
        aboveThreshold = false
        lastFallAt = null
    }
}
