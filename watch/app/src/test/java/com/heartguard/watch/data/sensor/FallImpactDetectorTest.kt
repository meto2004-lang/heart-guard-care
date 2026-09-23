package com.heartguard.watch.data.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallImpactDetectorTest {
    @Test
    fun puttingTheWatchDownDoesNotCountAsAFall() {
        val detector = FallImpactDetector()
        assertFalse(detector.process(0f, 0f, 9.81f, 0L))
        assertFalse(detector.process(0f, 0f, 25.1f, 20L))
        assertFalse(detector.process(15f, -15f, 15f, 40L))
    }

    @Test
    fun freeFallImpactAndStillnessCountAsAFall() {
        val detector = FallImpactDetector()
        assertTrue(replayFall(detector))
    }

    @Test
    fun cooldownSuppressesASecondFall() {
        val detector = FallImpactDetector()
        assertTrue(replayFall(detector, startAt = 0L))
        assertFalse(replayFall(detector, startAt = 2_000L))
        assertTrue(replayFall(detector, startAt = FallImpactDetector.COOLDOWN_MS + 1_000L))
    }

    @Test
    fun invalidValuesDoNotTriggerOrPoisonNextSample() {
        val detector = FallImpactDetector()
        assertFalse(detector.process(Float.NaN, 0f, 0f, 0L))
        assertFalse(detector.process(0f, Float.POSITIVE_INFINITY, 0f, 0L))
        assertTrue(replayFall(detector, startAt = 100L))
    }

    @Test
    fun resetAllowsANewMonitoringSession() {
        val detector = FallImpactDetector()
        assertTrue(replayFall(detector))
        detector.reset()
        assertTrue(replayFall(detector, startAt = 50L))
    }

    private fun replayFall(detector: FallImpactDetector, startAt: Long = 0L): Boolean {
        var t = startAt
        var hit = false
        fun step(x: Float, y: Float, z: Float) {
            hit = detector.process(x, y, z, t) || hit
            t += 20L
        }
        repeat(3) { step(0f, 0f, 9.81f) }
        repeat(8) { step(0f, 0f, 0.4f) }
        step(0f, 0f, 32f)
        repeat(45) { step(0f, 0f, 9.7f) }
        return hit
    }
}
