package com.heartguard.mobile.ai

import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.shared.constants.SensorConstants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AssistantContext(
    val heartRate: Int,
    val temperature: Float,
    val watchConnected: Boolean,
    val recentAlerts: List<AlertEntity>,
    val contactCount: Int,
    val restBaseline: Int?,
    val lastClassificationReason: String?
)

/**
 * On-device Arabic assistant. Answers from app knowledge and local records.
 * Works offline; no cloud LLM required.
 */
class CareAssistant {
    fun reply(question: String, ctx: AssistantContext): String {
        val q = normalize(question)
        if (q.isBlank()) return help()
        return when {
            matches(q, "ملخص", "اليوم", "حالتي", "وضع", "تقرير") -> summary(ctx)
            matches(q, "لماذا", "ليش", "انذار", "صفار", "تنبيه", "اطلق") -> whyAlarm(ctx)
            matches(q, "جهه", "اتصال", "رقم", "اضف", "اضيف") -> addContactHelp()
            matches(q, "سقوط", "وقع", "وقع") -> fallHelp()
            matches(q, "نبض", "70", "قلب", "راحه", "كاذب") -> heartHelp(ctx)
            matches(q, "sos", "طوارئ", "استغاثه") -> sosHelp()
            matches(q, "مساعد", "تقدر", "ماذا", "كيف استخدم", "مساعده") -> help()
            else -> buildString {
                appendLine("لم أتعرف على السؤال بدقة، وهذا ما أعرفه الآن:")
                appendLine()
                append(summary(ctx))
                appendLine()
                appendLine("يمكنك السؤال عن: سبب الإنذار، إضافة جهة اتصال، السقوط، النبض، أو ملخص اليوم.")
            }
        }
    }

    fun greeting(): String = buildString {
        appendLine("مرحباً، أنا مساعد حارس القلب على جهازك.")
        appendLine("أقرأ سجلاتك المحلية فقط، بدون إنترنت.")
        append("اسأل مثلاً: لماذا انطلق الإنذار؟ كيف أضيف جهة اتصال؟ ملخص اليوم.")
    }

    private fun summary(ctx: AssistantContext): String = buildString {
        appendLine("ملخص الحالة:")
        appendLine("• الساعة: ${if (ctx.watchConnected) "متصلة" else "غير متصلة"}")
        appendLine(
            "• النبض الحالي: " +
                if (ctx.heartRate > 0) "${ctx.heartRate} نبضة/دقيقة" else "غير متاح"
        )
        appendLine(
            "• الحرارة: " +
                if (ctx.temperature > 0f) "${ctx.temperature}°م" else "غير متاحة"
        )
        ctx.restBaseline?.let {
            appendLine("• معدل راحتك الذي تعلمه التطبيق: $it نبضة/دقيقة")
        }
        appendLine("• جهات الطوارئ: ${ctx.contactCount}")
        val todayStart = startOfDay()
        val todayAlerts = ctx.recentAlerts.filter { it.timestamp >= todayStart }
        appendLine("• تنبيهات اليوم: ${todayAlerts.size}")
        if (todayAlerts.isNotEmpty()) {
            appendLine("آخرها: ${todayAlerts.first().message}")
        }
        ctx.lastClassificationReason?.let {
            appendLine("• آخر تقييم للنموذج: $it")
        }
    }

    private fun whyAlarm(ctx: AssistantContext): String {
        val last = ctx.recentAlerts.firstOrNull() ?: return "لا يوجد إنذار مسجّل بعد."
        val time = formatTime(last.timestamp)
        val falseAlarm = last.severity.equals("MEDIUM", true) || last.severity.equals("LOW", true)
        return buildString {
            appendLine("آخر تنبيه ($time):")
            appendLine(last.message)
            appendLine()
            when {
                last.type == "SOS_MANUAL" ->
                    append("هذا نداء طوارئ يدوي (زر SOS). الصفارة والرسائل مقصودة.")
                last.type == "FALL_DETECTED" ->
                    append("الساعة رصدت نمط سقوط: سقوط حر ثم اصطدام ثم سكون. وضع الساعة على الطاولة لا يكفي عادة.")
                falseAlarm ->
                    append("النموذج اعتبره راحة/نوماً محتملاً، لذلك إشعار فقط بدون صفارة أو اتصال.")
                last.type.contains("HEART") ->
                    append("نبض عند حد ${SensorConstants.HR_LOW_THRESHOLD} أو أقل، ولم يطابق نمط راحتك بما يكفي لتجاهله.")
                else ->
                    append("تنبيه محفوظ في السجل. إن كان خاطئاً أخبرني لأشرح كيف يفلتر النموذج الإنذارات.")
            }
        }
    }

    private fun addContactHelp(): String = """
        لإضافة جهة اتصال طوارئ:
        1) من لوحة التحكم اضغط «إضافة جهة اتصال».
        2) أدخل الاسم ورقم الهاتف وصلة القرابة.
        3) احفظ. عند إنذار حقيقي يُرسل SMS، وإن كان حرجاً تُجرى مكالمة.

        بدون جهات اتصال لن تخرج رسائل أو مكالمات، لكن الصفارة على جوالك تعمل.
    """.trimIndent()

    private fun fallHelp(): String = """
        كشف السقوط الذكي يتطلب ثلاث مراحل معاً:
        1) سقوط حر (التسارع يقترب من الصفر)
        2) اصطدام قوي
        3) سكون بعدها

        وضع الساعة فجأة أو حركة واحدة قوية لا تُعد سقوطاً. السقوط الحقيقي يشغّل صفارة + SMS ومكالمة.
    """.trimIndent()

    private fun heartHelp(ctx: AssistantContext): String = buildString {
        appendLine("حد النبض المنخفض: ${SensorConstants.HR_LOW_THRESHOLD} أو أقل.")
        appendLine("إن كان النبض ≤ ${SensorConstants.HR_CRITICAL_LOW} فالإنذار دائماً حقيقي (شبكة أمان).")
        appendLine("بعد أن يتعلم التطبيق معدل راحتك، انخفاض قريب من هذا المعدل أثناء راحة/نوم يصبح إشعاراً فقط.")
        ctx.restBaseline?.let { appendLine("معدل راحتك الحالي: $it.") }
            ?: appendLine("ما زال النموذج يجمع قراءات لبناء معدل الراحة.")
        ctx.lastClassificationReason?.let {
            appendLine()
            appendLine("آخر قرار: $it")
        }
    }

    private fun sosHelp(): String = """
        زر SOS على الجوال أو الساعة يشغّل صفارة عالية فوراً ويرسل نداءً لجهات الطوارئ.
        هذا المسار يدوي ولا يمرّ على فلتر الإنذار الكاذب.
    """.trimIndent()

    private fun help(): String = """
        يمكنني المساعدة في:
        • لماذا انطلق الإنذار؟
        • ملخص اليوم
        • كيف أضيف جهة اتصال؟
        • كيف يعمل كشف السقوط؟
        • ماذا يعني حد النبض 70؟

        أجيب من سجلات هذا الجهاز فقط.
    """.trimIndent()

    private fun matches(q: String, vararg keys: String): Boolean =
        keys.any { q.contains(it) }

    private fun normalize(raw: String): String =
        raw.lowercase(Locale.getDefault())
            .replace(Regex("[ًٌٍَُِّْ]"), "")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .trim()

    private fun startOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
