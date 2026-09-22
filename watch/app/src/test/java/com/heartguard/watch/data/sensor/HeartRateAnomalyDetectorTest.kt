package com.heartguard.watch.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateAnomalyDetectorTest {
    @Test
    fun dropTo70FiresLowOnceUntilRecovery() {
        val detector = HeartRateAnomalyDetector()
        assertNull(detector.process(80))
        assertEquals("LOW", detector.process(70))
        assertNull(detector.process(70))
        assertNull(detector.process(65))
        assertNull(detector.process(80))
        assertEquals("LOW", detector.process(69))
    }

    @Test
    fun firstCrossingAtOrBelow40IsCriticalLow() {
        val detector = HeartRateAnomalyDetector()
        assertEquals("CRITICAL_LOW", detector.process(40))
        assertNull(detector.process(35))
    }

    @Test
    fun seventyOneIsNormalAndHighThresholdStillFires() {
        val detector = HeartRateAnomalyDetector()
        assertNull(detector.process(71))
        assertEquals("HIGH", detector.process(120))
        assertEquals("CRITICAL_HIGH", detector.process(150))
    }

    @Test
    fun zeroDoesNotTriggerOrReArmLowEpisode() {
        val detector = HeartRateAnomalyDetector()
        assertEquals("LOW", detector.process(60))
        assertNull(detector.process(0))
        assertNull(detector.process(60))
        assertNull(detector.process(80))
        assertEquals("LOW", detector.process(60))
    }

    @Test
    fun resetAllowsAnotherAlertWithoutRecovery() {
        val detector = HeartRateAnomalyDetector()
        assertEquals("LOW", detector.process(55))
        detector.reset()
        assertEquals("LOW", detector.process(55))
    }
}
