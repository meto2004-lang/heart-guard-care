package com.heartguard.mobile.ai

import com.heartguard.mobile.data.local.AlertEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class CareAssistantTest {
    private val assistant = CareAssistant()

    @Test
    fun explainsLastHeartRateFalseAlarm() {
        val ctx = context(
            alerts = listOf(
                AlertEntity(
                    id = "1",
                    type = "HEART_RATE_LOW",
                    severity = "MEDIUM",
                    message = "نبض منخفض: 66 — قريب من معدل الراحة"
                )
            )
        )
        val reply = assistant.reply("لماذا انطلق الإنذار؟", ctx)
        assertTrue(reply.contains("راحة") || reply.contains("إشعار"))
    }

    @Test
    fun contactQuestionMentionsAddButton() {
        val reply = assistant.reply("كيف أضيف جهة اتصال؟", emptyContext())
        assertTrue(reply.contains("جهة اتصال"))
        assertTrue(reply.contains("رقم"))
    }

    @Test
    fun summaryIncludesHeartRate() {
        val reply = assistant.reply("ملخص اليوم", emptyContext().copy(heartRate = 72, contactCount = 2))
        assertTrue(reply.contains("72"))
        assertTrue(reply.contains("2"))
    }

    @Test
    fun fallQuestionDescribesThreePhases() {
        val reply = assistant.reply("كيف يعمل كشف السقوط؟", emptyContext())
        assertTrue(reply.contains("سقوط حر"))
        assertTrue(reply.contains("اصطدام"))
    }

    private fun emptyContext() = AssistantContext(
        heartRate = 0,
        temperature = 0f,
        watchConnected = false,
        recentAlerts = emptyList(),
        contactCount = 0,
        restBaseline = null,
        lastClassificationReason = null
    )

    private fun context(alerts: List<AlertEntity>) = emptyContext().copy(recentAlerts = alerts)
}
