package com.heartguard.watch.data.sensor

import com.heartguard.shared.constants.SensorConstants
import com.heartguard.shared.utils.HeartRateLowEpisodeGate

/**
 * Classifies heart-rate samples. Low-HR alerts fire once per episode
 * (at or below [SensorConstants.HR_LOW_THRESHOLD]) and re-arm only after
 * recovery above the threshold.
 */
internal class HeartRateAnomalyDetector(
    private val lowGate: HeartRateLowEpisodeGate = HeartRateLowEpisodeGate()
) {
    fun process(hr: Int): String? {
        if (hr <= 0) return null
        if (hr <= SensorConstants.HR_LOW_THRESHOLD) {
            if (!lowGate.onHeartRate(hr)) return null
            return if (hr <= SensorConstants.HR_CRITICAL_LOW) "CRITICAL_LOW" else "LOW"
        }
        lowGate.onHeartRate(hr)
        return when {
            hr >= SensorConstants.HR_CRITICAL_HIGH -> "CRITICAL_HIGH"
            hr >= SensorConstants.HR_HIGH_THRESHOLD -> "HIGH"
            else -> null
        }
    }

    fun reset() {
        lowGate.reset()
    }
}
