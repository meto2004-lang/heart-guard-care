package com.heartguard.mobile.ai

import android.content.Context
import com.heartguard.shared.ai.AuthenticityResult
import com.heartguard.shared.ai.PersonalRhythmModel
import com.heartguard.shared.ai.RhythmSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneRhythmService @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS = "heartguard_rhythm"
        private const val KEY_REST_EMA = "rest_ema"
        private const val KEY_WALK_EMA = "walk_ema"
        private const val KEY_REST_N = "rest_n"
        private const val KEY_WALK_N = "walk_n"
        private const val KEY_TOTAL = "total"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val model = PersonalRhythmModel(
        initial = RhythmSnapshot(
            restHrEma = prefs.getFloat(KEY_REST_EMA, 0f).toDouble(),
            walkHrEma = prefs.getFloat(KEY_WALK_EMA, 0f).toDouble(),
            restSamples = prefs.getInt(KEY_REST_N, 0),
            walkSamples = prefs.getInt(KEY_WALK_N, 0),
            totalSamples = prefs.getInt(KEY_TOTAL, 0)
        ),
        persist = { snap ->
            prefs.edit()
                .putFloat(KEY_REST_EMA, snap.restHrEma.toFloat())
                .putFloat(KEY_WALK_EMA, snap.walkHrEma.toFloat())
                .putInt(KEY_REST_N, snap.restSamples)
                .putInt(KEY_WALK_N, snap.walkSamples)
                .putInt(KEY_TOTAL, snap.totalSamples)
                .apply()
        }
    )

    @Volatile
    var lastClassification: AuthenticityResult? = null
        private set

    fun observe(heartRate: Int, motion: Float, hourOfDay: Int) {
        model.observe(heartRate, motion, hourOfDay)
    }

    fun classifyLowHeartRate(heartRate: Int, motion: Float, hourOfDay: Int): AuthenticityResult {
        val result = model.classifyLowHeartRate(heartRate, motion, hourOfDay)
        lastClassification = result
        return result
    }
}
