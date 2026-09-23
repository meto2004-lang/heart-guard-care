package com.heartguard.mobile.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.heartguard.mobile.R
import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.local.EmergencyContactEntity
import com.heartguard.mobile.ui.assistant.AssistantEntryCard
import com.heartguard.shared.constants.SensorConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onNavigateToContacts: () -> Unit = {},
    onNavigateToAlerts: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("حارس القلب") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SosAlarmCard(
                    isAlarmActive = uiState.isAlarmActive,
                    alarmMessage = uiState.alarmMessage,
                    onSosClick = { viewModel.triggerSos() },
                    onTestClick = { viewModel.testAlarmSound() },
                    onStopClick = { viewModel.stopAlarm() }
                )
            }

            item {
                ConnectionStatusCard(
                    isConnected = uiState.isWatchConnected,
                    lastSyncTime = uiState.lastSyncTime
                )
            }

            item {
                AssistantEntryCard(onClick = onNavigateToAssistant)
            }

            item {
                Text(
                    text = "ملخص الصحة",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                val isHeartRateLow = uiState.heartRate > 0 &&
                    uiState.heartRate <= SensorConstants.HR_LOW_THRESHOLD
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HealthCard(
                            title = "النبض",
                            value = if (uiState.heartRate > 0) "${uiState.heartRate}" else "--",
                            unit = "نبضة/دقيقة",
                            isAlert = isHeartRateLow,
                            modifier = Modifier.weight(1f)
                        )
                        HealthCard(
                            title = "الحرارة",
                            value = if (uiState.temperature > 0) "${uiState.temperature}" else "--",
                            unit = "°م",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.hr_low_threshold_hint,
                            SensorConstants.HR_LOW_THRESHOLD
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHeartRateLow) Color(0xFFB71C1C) else Color.Gray
                    )
                }
            }

            item {
                Text(
                    text = "التنبيهات الأخيرة",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.recentAlerts.take(5)) { alert ->
                AlertCard(
                    alert = alert,
                    onMarkAsRead = { viewModel.markAlertAsRead(alert.id) }
                )
            }

            item {
                Text(
                    text = "جهات الاتصال",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.contacts) { contact ->
                ContactCard(
                    contact = contact,
                    onDelete = { viewModel.deleteContact(contact) }
                )
            }

            item {
                Button(
                    onClick = onNavigateToContacts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إضافة جهة اتصال")
                }
            }
        }
    }
}

/**
 * بطاقة نداء الطوارئ: ضغط الزر يشغّل صفارة إنذار عالية على الجوال
 * (نفس الصفارة التي تعمل عند الضغط على SOS في الساعة) ويرسل نداء الاستغاثة.
 */
@Composable
fun SosAlarmCard(
    isAlarmActive: Boolean,
    alarmMessage: String?,
    onSosClick: () -> Unit,
    onTestClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlarmActive) Color(0xFFB71C1C) else Color(0xFFFFEBEE)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { if (isAlarmActive) onStopClick() else onSosClick() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAlarmActive) Color.White else Color(0xFFD32F2F),
                    contentColor = if (isAlarmActive) Color(0xFFB71C1C) else Color.White
                )
            ) {
                Text(
                    text = if (isAlarmActive) {
                        stringResource(R.string.sos_alarm_stop)
                    } else {
                        stringResource(R.string.sos_button_label)
                    },
                    fontSize = if (isAlarmActive) 20.sp else 34.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isAlarmActive) {
                    alarmMessage ?: stringResource(R.string.sos_alarm_running_hint)
                } else {
                    stringResource(R.string.sos_button_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isAlarmActive) Color.White else Color(0xFFB71C1C),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (!isAlarmActive) {
                TextButton(onClick = onTestClick) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.sos_test_alarm))
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(isConnected: Boolean, lastSyncTime: String = "غير متاح") {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isConnected) Color.Green else Color.Red,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (isConnected) "الساعة متصلة" else "الساعة غير متصلة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "آخر مزامنة: $lastSyncTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun HealthCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) Color(0xFFFFEBEE) else Color(0xFFE3F2FD)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAlert) Color(0xFFB71C1C) else Color.Unspecified
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun AlertCard(
    alert: AlertEntity,
    onMarkAsRead: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (alert.severity) {
                "CRITICAL" -> Color(0xFFFFEBEE)
                "HIGH" -> Color(0xFFFFF3E0)
                else -> Color(0xFFE3F2FD)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (alert.type) {
                    "FALL_DETECTED" -> Icons.Default.Warning
                    "HEART_RATE_HIGH", "HEART_RATE_LOW",
                    "HEART_RATE_CRITICAL_HIGH", "HEART_RATE_CRITICAL_LOW" -> Icons.Default.Favorite
                    "TEMPERATURE_FEVER" -> Icons.Default.Thermostat
                    "SOS_MANUAL" -> Icons.Default.Warning
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = when (alert.severity) {
                    "CRITICAL" -> Color.Red
                    "HIGH" -> Color(0xFFF57F17)
                    else -> Color.Blue
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTimestamp(alert.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            if (!alert.isRead) {
                IconButton(onClick = onMarkAsRead) {
                    Icon(Icons.Default.Check, "تم القراءة")
                }
            }
        }
    }
}

@Composable
fun ContactCard(
    contact: EmergencyContactEntity,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = contact.relationship,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "حذف", tint = Color.Red)
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
