package com.heartguard.shared.ai

import kotlin.math.sqrt

/** Rolling accelerometer magnitude window used to guess rest vs walk. */
class MotionActivityTracker(
    private val windowSize: Int = 25
) {
    private val window = ArrayDeque<Float>()

    fun update(x: Float, y: Float, z: Float): Float {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return magnitude()
        val mag = sqrt(x * x + y * y + z * z)
        window.addLast(mag)
        while (window.size > windowSize) window.removeFirst()
        return mag
    }

    fun magnitude(): Float = window.lastOrNull() ?: 0f

    fun mean(): Float {
        if (window.isEmpty()) return 0f
        return window.average().toFloat()
    }

    fun reset() {
        window.clear()
    }
}
