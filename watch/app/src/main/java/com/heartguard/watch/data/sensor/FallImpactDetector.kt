package com.heartguard.watch.data.sensor

import com.heartguard.shared.ai.SmartFallDetector

/**
 * Watch adapter around the shared three-phase fall classifier.
 */
internal class FallImpactDetector {
    companion object {
        const val COOLDOWN_MS = SmartFallDetector.COOLDOWN_MS
    }

    private val detector = SmartFallDetector()

    fun process(x: Float, y: Float, z: Float, elapsedRealtimeMs: Long): Boolean =
        detector.process(x, y, z, elapsedRealtimeMs)

    fun reset() {
        detector.reset()
    }
}
