package com.heartguard.watch.data.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallImpactDetectorTest {
    private val detector = FallImpactDetector()

    @Test
    fun normalMotionAndThresholdBoundaryDoNotTrigger() {
        assertFalse(detector.process(0f, 0f, 9.81f, 0L))
        assertFalse(detector.process(0f, 0f, 25f, 10L))
        assertTrue(detector.process(0f, 0f, 25.1f, 20L))
    }

    @Test
    fun combinesAllThreeAxes() {
        assertTrue(detector.process(15f, -15f, 15f, 0L))
    }

    @Test
    fun repeatedSamplesFromOneImpactTriggerOnlyOnce() {
        assertTrue(detector.process(0f, 0f, 30f, 0L))
        assertFalse(detector.process(0f, 0f, 31f, 20L))
        assertFalse(detector.process(0f, 0f, 30f, 20_000L))
    }

    @Test
    fun cooldownSuppressesBouncesButAllowsANewImpactLater() {
        assertTrue(detector.process(0f, 0f, 30f, 0L))
        assertFalse(detector.process(0f, 0f, 9.81f, 100L))
        assertFalse(detector.process(0f, 0f, 30f, 200L))
        assertFalse(detector.process(0f, 0f, 9.81f, 300L))
        assertTrue(detector.process(0f, 0f, 30f, FallImpactDetector.COOLDOWN_MS))
    }

    @Test
    fun invalidValuesDoNotTriggerOrPoisonNextSample() {
        assertFalse(detector.process(Float.NaN, 0f, 0f, 0L))
        assertFalse(detector.process(0f, Float.POSITIVE_INFINITY, 0f, 0L))
        assertFalse(detector.process(0f, 0f, Float.NEGATIVE_INFINITY, 0L))
        assertTrue(detector.process(0f, 0f, 30f, 0L))
    }

    @Test
    fun resetAllowsANewMonitoringSession() {
        assertTrue(detector.process(0f, 0f, 30f, 100L))
        detector.reset()
        assertTrue(detector.process(0f, 0f, 30f, 200L))
    }
}
