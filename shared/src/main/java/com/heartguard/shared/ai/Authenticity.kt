package com.heartguard.shared.ai

enum class ActivityGuess {
    REST,
    SLEEP,
    WALK,
    UNKNOWN
}

enum class AlertVerdict {
    EMERGENCY,
    LIKELY_FALSE_ALARM
}

data class AuthenticityResult(
    val verdict: AlertVerdict,
    val activity: ActivityGuess,
    val confidence: Float,
    val reasonAr: String
) {
    val isEmergency: Boolean get() = verdict == AlertVerdict.EMERGENCY
}

data class RhythmSnapshot(
    val restHrEma: Double = 0.0,
    val walkHrEma: Double = 0.0,
    val restSamples: Int = 0,
    val walkSamples: Int = 0,
    val totalSamples: Int = 0
)
