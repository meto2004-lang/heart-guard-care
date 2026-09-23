package com.heartguard.shared.ai

import com.heartguard.shared.constants.SensorConstants
import kotlin.math.abs

/**
 * On-device online learner for resting vs walking heart-rate.
 * Not a clinical diagnostic model. Critical lows always raise an emergency
 * (safety net); only well-supported rest/sleep patterns can be downgraded.
 */
class PersonalRhythmModel(
    initial: RhythmSnapshot = RhythmSnapshot(),
    private val persist: (RhythmSnapshot) -> Unit = {}
) {
    companion object {
        const val MIN_SAMPLES_BEFORE_FILTER = 20
        const val MIN_REST_SAMPLES = 12
        const val REST_BAND_BPM = 10
        const val BIG_DROP_FROM_REST_BPM = 15
        private const val EMA_ALPHA = 0.08
        private const val REST_MOTION_MAX = 11.5f
        private const val WALK_MOTION_MIN = 12.0f
        private const val SLEEP_HOUR_START = 22
        private const val SLEEP_HOUR_END = 6
    }

    private var restHrEma = initial.restHrEma
    private var walkHrEma = initial.walkHrEma
    private var restSamples = initial.restSamples
    private var walkSamples = initial.walkSamples
    private var totalSamples = initial.totalSamples

    @Synchronized
    fun snapshot(): RhythmSnapshot = RhythmSnapshot(
        restHrEma = restHrEma,
        walkHrEma = walkHrEma,
        restSamples = restSamples,
        walkSamples = walkSamples,
        totalSamples = totalSamples
    )

    fun restBaselineBpm(): Int? = synchronized(this) {
        if (restSamples >= MIN_REST_SAMPLES) restHrEma.toInt() else null
    }

    fun guessActivity(motionMagnitude: Float, hourOfDay: Int): ActivityGuess {
        if (motionMagnitude <= 0f) return ActivityGuess.UNKNOWN
        val restLike = motionMagnitude <= REST_MOTION_MAX
        val walkLike = motionMagnitude >= WALK_MOTION_MIN
        return when {
            restLike && isNight(hourOfDay) -> ActivityGuess.SLEEP
            restLike -> ActivityGuess.REST
            walkLike -> ActivityGuess.WALK
            else -> ActivityGuess.UNKNOWN
        }
    }

    @Synchronized
    fun observe(heartRate: Int, motionMagnitude: Float, hourOfDay: Int) {
        if (heartRate <= 0) return
        totalSamples += 1
        val activity = guessActivity(motionMagnitude, hourOfDay)
        when (activity) {
            ActivityGuess.REST, ActivityGuess.SLEEP -> {
                if (heartRate in 45..110) {
                    restHrEma = ema(restHrEma, heartRate.toDouble(), restSamples == 0)
                    restSamples += 1
                }
            }
            ActivityGuess.WALK -> {
                if (heartRate in 60..170) {
                    walkHrEma = ema(walkHrEma, heartRate.toDouble(), walkSamples == 0)
                    walkSamples += 1
                }
            }
            ActivityGuess.UNKNOWN -> {
                if (heartRate in 60..90) {
                    restHrEma = ema(restHrEma, heartRate.toDouble(), restSamples == 0)
                    restSamples += 1
                }
            }
        }
        persist(snapshot())
    }

    @Synchronized
    fun classifyLowHeartRate(
        heartRate: Int,
        motionMagnitude: Float,
        hourOfDay: Int
    ): AuthenticityResult {
        val activity = guessActivity(motionMagnitude, hourOfDay)

        if (heartRate <= SensorConstants.HR_CRITICAL_LOW) {
            return AuthenticityResult(
                verdict = AlertVerdict.EMERGENCY,
                activity = activity,
                confidence = 0.99f,
                reasonAr = "نبض حرج ($heartRate). شبكة الأمان لا تسمح بتجاهله."
            )
        }

        if (totalSamples < MIN_SAMPLES_BEFORE_FILTER) {
            return AuthenticityResult(
                verdict = AlertVerdict.EMERGENCY,
                activity = activity,
                confidence = 0.55f,
                reasonAr = "ما زال النموذج يتعلم نمطك ($totalSamples قراءة). الإنذار يعمل احتياطاً."
            )
        }

        if (activity == ActivityGuess.WALK) {
            return AuthenticityResult(
                verdict = AlertVerdict.EMERGENCY,
                activity = activity,
                confidence = 0.9f,
                reasonAr = "نبض منخفض أثناء حركة (مشي/نشاط). هذا لا يشبه الراحة."
            )
        }

        val restReady = restSamples >= MIN_REST_SAMPLES && restHrEma > 0
        if (restReady && heartRate <= restHrEma - BIG_DROP_FROM_REST_BPM) {
            return AuthenticityResult(
                verdict = AlertVerdict.EMERGENCY,
                activity = activity,
                confidence = 0.88f,
                reasonAr = "النبض $heartRate أقل بكثير من معدل راحتك (${restHrEma.toInt()})."
            )
        }

        val closeToRest = restReady && abs(heartRate - restHrEma) <= REST_BAND_BPM
        val restOrSleep = activity == ActivityGuess.REST ||
            activity == ActivityGuess.SLEEP ||
            (activity == ActivityGuess.UNKNOWN && isNight(hourOfDay))

        if (closeToRest && restOrSleep) {
            val label = when (activity) {
                ActivityGuess.SLEEP -> "النوم"
                ActivityGuess.REST -> "الراحة"
                else -> "الراحة/النوم"
            }
            return AuthenticityResult(
                verdict = AlertVerdict.LIKELY_FALSE_ALARM,
                activity = activity,
                confidence = 0.78f,
                reasonAr = "النبض $heartRate قريب من معدل $label لديك (${restHrEma.toInt()}). إشعار فقط، بدون صفارة أو اتصال."
            )
        }

        return AuthenticityResult(
            verdict = AlertVerdict.EMERGENCY,
            activity = activity,
            confidence = 0.7f,
            reasonAr = "انخفاض النبض إلى $heartRate لا يطابق نمط الراحة المعروف بعد."
        )
    }

    private fun ema(current: Double, sample: Double, first: Boolean): Double {
        if (first || current == 0.0) return sample
        return current * (1.0 - EMA_ALPHA) + sample * EMA_ALPHA
    }

    private fun isNight(hourOfDay: Int): Boolean =
        hourOfDay >= SLEEP_HOUR_START || hourOfDay < SLEEP_HOUR_END
}
