package com.heartguard.shared.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRhythmModelTest {
    @Test
    fun criticalLowIsAlwaysEmergency() {
        val model = trainedResting()
        val result = model.classifyLowHeartRate(40, motionMagnitude = 9.8f, hourOfDay = 2)
        assertEquals(AlertVerdict.EMERGENCY, result.verdict)
        assertTrue(result.reasonAr.contains("حرج"))
    }

    @Test
    fun unknownUserDoesNotFilterYet() {
        val model = PersonalRhythmModel()
        val result = model.classifyLowHeartRate(65, motionMagnitude = 9.8f, hourOfDay = 23)
        assertEquals(AlertVerdict.EMERGENCY, result.verdict)
        assertTrue(result.reasonAr.contains("يتعلم"))
    }

    @Test
    fun restPatternNearBaselineIsLikelyFalseAlarm() {
        val model = trainedResting()
        val result = model.classifyLowHeartRate(66, motionMagnitude = 9.8f, hourOfDay = 23)
        assertEquals(AlertVerdict.LIKELY_FALSE_ALARM, result.verdict)
        assertFalse(result.isEmergency)
    }

    @Test
    fun lowHeartRateWhileWalkingIsEmergency() {
        val model = trainedResting()
        val result = model.classifyLowHeartRate(65, motionMagnitude = 15f, hourOfDay = 12)
        assertEquals(AlertVerdict.EMERGENCY, result.verdict)
        assertEquals(ActivityGuess.WALK, result.activity)
    }

    @Test
    fun bigDropBelowRestBaselineIsEmergency() {
        val model = trainedResting()
        val result = model.classifyLowHeartRate(50, motion = 9.8f, hourOfDay = 14)
        assertEquals(AlertVerdict.EMERGENCY, result.verdict)
    }

    private fun trainedResting(): PersonalRhythmModel {
        val model = PersonalRhythmModel()
        repeat(25) { model.observe(68, motionMagnitude = 9.7f, hourOfDay = 23) }
        return model
    }
}
