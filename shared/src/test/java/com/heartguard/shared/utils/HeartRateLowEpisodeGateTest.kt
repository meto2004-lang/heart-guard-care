package com.heartguard.shared.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateLowEpisodeGateTest {
    @Test
    fun crossingTo70TriggersOnceUntilRecovery() {
        val gate = HeartRateLowEpisodeGate()
        assertFalse(gate.onHeartRate(80))
        assertTrue(gate.onHeartRate(70))
        assertFalse(gate.onHeartRate(70))
        assertFalse(gate.onHeartRate(65))
        assertFalse(gate.onHeartRate(40))
        assertFalse(gate.onHeartRate(80))
        assertTrue(gate.onHeartRate(69))
    }

    @Test
    fun seventyOneDoesNotTrigger() {
        val gate = HeartRateLowEpisodeGate()
        assertFalse(gate.onHeartRate(71))
    }

    @Test
    fun firstSampleAlreadyAtOrBelowThresholdTriggers() {
        val gate = HeartRateLowEpisodeGate()
        assertTrue(gate.onHeartRate(60))
    }

    @Test
    fun zeroAndNegativeDoNotTriggerOrResetAnActiveEpisode() {
        val gate = HeartRateLowEpisodeGate()
        assertTrue(gate.onHeartRate(68))
        assertFalse(gate.onHeartRate(0))
        assertFalse(gate.onHeartRate(-1))
        assertTrue(gate.isEpisodeActive())
        assertFalse(gate.onHeartRate(68))
        assertFalse(gate.onHeartRate(80))
        assertTrue(gate.onHeartRate(68))
    }

    @Test
    fun tryBeginEpisodeIsIdempotentUntilReset() {
        val gate = HeartRateLowEpisodeGate()
        assertTrue(gate.tryBeginEpisode())
        assertFalse(gate.tryBeginEpisode())
        assertFalse(gate.onHeartRate(55))
        gate.reset()
        assertTrue(gate.tryBeginEpisode())
    }
}
