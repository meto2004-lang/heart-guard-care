package com.heartguard.shared.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFallDetectorTest {
    @Test
    fun gravityAndSingleImpactAreNotFalls() {
        val detector = SmartFallDetector()
        assertFalse(detector.process(0f, 0f, 9.81f, 0L))
        assertFalse(detector.process(0f, 0f, 30f, 20L))
        assertFalse(detector.process(15f, -15f, 15f, 40L))
    }

    @Test
    fun freeFallThenImpactThenStillnessTriggersOnce() {
        val detector = SmartFallDetector()
        assertTrue(replay(detector, fallSequence()))
        assertFalse(replay(detector, fallSequence(startAt = 2_100L)))
    }

    @Test
    fun freeFallThenImpactThenContinuedMotionIsRejected() {
        val detector = SmartFallDetector()
        var t = 0L
        t = feed(detector, t, 8, 0f, 0f, 0.4f, 20L)
        t = feed(detector, t, 1, 0f, 0f, 32f, 20L)
        var triggered = false
        repeat(45) {
            triggered = detector.process(0f, 0f, 22f, t) || triggered
            t += 20L
        }
        assertFalse(triggered)
    }

    @Test
    fun briefDipIsNotFreeFall() {
        val detector = SmartFallDetector()
        detector.process(0f, 0f, 0.4f, 0L)
        detector.process(0f, 0f, 0.4f, 40L)
        detector.process(0f, 0f, 30f, 60L)
        var triggered = false
        var t = 140L
        repeat(40) {
            triggered = detector.process(0f, 0f, 9.7f, t) || triggered
            t += 20L
        }
        assertFalse(triggered)
    }

    @Test
    fun invalidValuesDoNotTrigger() {
        val detector = SmartFallDetector()
        assertFalse(detector.process(Float.NaN, 0f, 0f, 0L))
        assertTrue(replay(detector, fallSequence()))
    }

    @Test
    fun cooldownBlocksASecondFallUntilItExpires() {
        val detector = SmartFallDetector()
        assertTrue(replay(detector, fallSequence()))
        assertFalse(replay(detector, fallSequence(startAt = 2_100L)))
        assertTrue(replay(detector, fallSequence(startAt = SmartFallDetector.COOLDOWN_MS + 2_000L)))
    }

    @Test
    fun resetAllowsANewSession() {
        val detector = SmartFallDetector()
        assertTrue(replay(detector, fallSequence()))
        detector.reset()
        assertTrue(replay(detector, fallSequence(startAt = 100L)))
    }

    private fun fallSequence(startAt: Long = 0L): List<Sample> {
        val samples = ArrayList<Sample>()
        var t = startAt
        repeat(3) {
            samples += Sample(0f, 0f, 9.81f, t)
            t += 20L
        }
        repeat(8) {
            samples += Sample(0f, 0f, 0.4f, t)
            t += 20L
        }
        samples += Sample(0f, 0f, 32f, t)
        t += 20L
        repeat(45) {
            samples += Sample(0f, 0f, 9.7f, t)
            t += 20L
        }
        return samples
    }

    private fun replay(detector: SmartFallDetector, samples: List<Sample>): Boolean {
        var hit = false
        for (s in samples) {
            hit = detector.process(s.x, s.y, s.z, s.t) || hit
        }
        return hit
    }

    private fun feed(
        detector: SmartFallDetector,
        start: Long,
        count: Int,
        x: Float,
        y: Float,
        z: Float,
        step: Long
    ): Long {
        var t = start
        repeat(count) {
            detector.process(x, y, z, t)
            t += step
        }
        return t
    }

    private data class Sample(val x: Float, val y: Float, val z: Float, val t: Long)
}
