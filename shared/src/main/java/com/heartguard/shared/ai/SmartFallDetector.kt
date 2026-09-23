package com.heartguard.shared.ai

import kotlin.math.sqrt

/**
 * Three-phase on-device fall classifier: free-fall, impact, then stillness.
 * A single shock (putting the watch down) does not count as a fall.
 * Not a clinically validated medical fall detector.
 */
class SmartFallDetector {
    companion object {
        const val COOLDOWN_MS = 10_000L
        const val FREE_FALL_MAX_MS2 = 2.5f
        const val FREE_FALL_MIN_MS = 80L
        const val FREE_FALL_MAX_MS = 450L
        const val IMPACT_MIN_MS2 = 22f
        const val IMPACT_WINDOW_MS = 400L
        const val IMPACT_TAIL_SKIP_MS = 80L
        const val STILL_MS = 700L
        const val STILL_MEAN_MIN = 6.0
        const val STILL_MEAN_MAX = 12.5
        const val STILL_MAX_MS2 = 16f
    }

    private enum class Phase { IDLE, FREE_FALL, AWAIT_IMPACT, AWAIT_STILL }

    private var phase = Phase.IDLE
    private var freeFallStart = 0L
    private var awaitStart = 0L
    private var impactAt = 0L
    private var lastFallAt: Long? = null
    private val stillSamples = ArrayList<Float>(32)

    fun process(x: Float, y: Float, z: Float, elapsedRealtimeMs: Long): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return false
        val t = elapsedRealtimeMs
        val previous = lastFallAt
        if (previous != null && t - previous < COOLDOWN_MS) return false

        val mag = sqrt(x * x + y * y + z * z)

        when (phase) {
            Phase.IDLE -> {
                if (mag < FREE_FALL_MAX_MS2) {
                    phase = Phase.FREE_FALL
                    freeFallStart = t
                }
            }
            Phase.FREE_FALL -> {
                if (mag < FREE_FALL_MAX_MS2) {
                    if (t - freeFallStart > FREE_FALL_MAX_MS) resetPhase()
                } else {
                    val duration = t - freeFallStart
                    if (duration >= FREE_FALL_MIN_MS) {
                        phase = Phase.AWAIT_IMPACT
                        awaitStart = t
                        if (mag >= IMPACT_MIN_MS2) enterStill(t)
                    } else {
                        resetPhase()
                        if (mag < FREE_FALL_MAX_MS2) {
                            phase = Phase.FREE_FALL
                            freeFallStart = t
                        }
                    }
                }
            }
            Phase.AWAIT_IMPACT -> {
                if (mag >= IMPACT_MIN_MS2) {
                    enterStill(t)
                } else if (t - awaitStart > IMPACT_WINDOW_MS) {
                    resetPhase()
                }
            }
            Phase.AWAIT_STILL -> {
                if (t - impactAt >= IMPACT_TAIL_SKIP_MS) {
                    stillSamples.add(mag)
                }
                if (t - impactAt >= IMPACT_TAIL_SKIP_MS + STILL_MS) {
                    val fell = looksStill()
                    resetPhase()
                    if (fell) {
                        lastFallAt = t
                        return true
                    }
                }
            }
        }
        return false
    }

    fun reset() {
        resetPhase()
        lastFallAt = null
    }

    private fun enterStill(t: Long) {
        phase = Phase.AWAIT_STILL
        impactAt = t
        stillSamples.clear()
    }

    private fun looksStill(): Boolean {
        if (stillSamples.size < 5) return false
        val mean = stillSamples.average()
        val max = stillSamples.max()
        return mean in STILL_MEAN_MIN..STILL_MEAN_MAX && max < STILL_MAX_MS2
    }

    private fun resetPhase() {
        phase = Phase.IDLE
        stillSamples.clear()
    }
}
