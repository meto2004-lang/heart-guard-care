package com.heartguard.mobile.ui.alarm

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartguard.mobile.R
import com.heartguard.mobile.service.EmergencyAlarmService
import com.heartguard.mobile.service.SosAlarmState
import kotlinx.coroutines.delay

private val AlarmRed = Color(0xFFB71C1C)
private val AlarmRedDark = Color(0xFF7F0000)

/** مهلة قصيرة ننتظر فيها تشغيل الخدمة قبل اعتبار أن الإنذار متوقف */
private const val ALARM_START_GRACE_MS = 4_000L

/**
 * واجهة الإنذار: خلفية حمراء، عداد للوقت المنقضي، وزر كبير لإيقاف الصوت.
 * تُغلق الشاشة تلقائياً بمجرد توقف الإنذار من أي مصدر آخر.
 */
@Composable
fun EmergencyAlarmScreen(
    message: String,
    onDismissAlarm: () -> Unit,
    onFinishScreen: () -> Unit
) {
    val alarmState by EmergencyAlarmService.alarmState.collectAsState()
    val startedAt = (alarmState as? SosAlarmState.Active)?.startedAt ?: System.currentTimeMillis()

    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt().coerceAtLeast(0)
            delay(500L)
        }
    }

    // إغلاق الشاشة عند إيقاف الإنذار من الإشعار أو من لوحة التحكم.
    // ننتظر أول حالة تشغيل حتى لا تُغلق الشاشة قبل أن تبدأ الخدمة (فرق أجزاء من الثانية).
    var sawActiveAlarm by remember { mutableStateOf(alarmState is SosAlarmState.Active) }
    LaunchedEffect(alarmState) {
        when {
            alarmState is SosAlarmState.Active -> sawActiveAlarm = true
            sawActiveAlarm -> onFinishScreen()
            else -> {
                delay(ALARM_START_GRACE_MS)
                if (EmergencyAlarmService.alarmState.value !is SosAlarmState.Active) {
                    onFinishScreen()
                }
            }
        }
    }

    val pulse = rememberInfiniteTransition(label = "sos-pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos-scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlarmRed)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .size(132.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = AlarmRed,
                modifier = Modifier.size(76.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SOS",
            fontSize = 44.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        Text(
            text = stringResource(R.string.sos_alarm_screen_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 18.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = formatElapsed(elapsedSeconds),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFCDD2)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDismissAlarm,
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = AlarmRedDark
            )
        ) {
            Text(
                text = stringResource(R.string.sos_alarm_stop),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.sos_alarm_auto_stop_hint),
            fontSize = 12.sp,
            color = Color(0xFFFFCDD2),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
