package com.heartguard.shared.utils

import com.heartguard.shared.constants.SensorConstants

/**
 * Raises at most one low-heart-rate episode while the reading stays at or
 * below [lowThresholdBpm]. A new alert is allowed only after the rate
 * returns above the threshold.
 */
class HeartRateLowEpisodeGate(
    private val lowThresholdBpm: Int = SensorConstants.HR_LOW_THRESHOLD
) {
    @Volatile
    private var episodeActive = false

    /**
     * @return true if this sample starts a new low-HR episode that should alert
     */
    @Synchronized
    fun onHeartRate(hr: Int): Boolean {
        if (hr <= 0) return false
        return if (hr <= lowThresholdBpm) {
            tryBeginEpisodeLocked()
        } else {
            episodeActive = false
            false
        }
    }

    /** Marks an episode from a watch-originated alert without inspecting HR. */
    @Synchronized
    fun tryBeginEpisode(): Boolean = tryBeginEpisodeLocked()

    @Synchronized
    fun isEpisodeActive(): Boolean = episodeActive

    @Synchronized
    fun reset() {
        episodeActive = false
    }

    private fun tryBeginEpisodeLocked(): Boolean {
        if (episodeActive) return false
        episodeActive = true
        return true
    }
}
