package com.heartguard.watch.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.*
import com.heartguard.watch.R

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.title2,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                HealthMetricCard(
                    label = stringResource(R.string.heart_rate),
                    value = if (uiState.heartRate > 0) "${uiState.heartRate} نبضة/دقيقة" else "غير متاح",
                    status = uiState.heartRateStatus,
                    statusColor = when {
                        uiState.heartRateStatus.contains("حرج") -> Color(0xFFF44336)
                        uiState.heartRateStatus.contains("مرتفع") || uiState.heartRateStatus.contains("منخفض") -> Color(0xFFFF9800)
                        uiState.heartRateStatus == "غير متاح" -> Color.Gray
                        else -> Color(0xFF4CAF50)
                    }
                )
            }

            item {
                HealthMetricCard(
                    label = stringResource(R.string.temperature),
                    value = if (uiState.temperature > 0) "${uiState.temperature}°م" else "غير متاح",
                    status = uiState.temperatureStatus,
                    statusColor = when {
                        uiState.temperatureStatus.contains("حمى عالية") -> Color(0xFFF44336)
                        uiState.temperatureStatus.contains("حمى") || uiState.temperatureStatus.contains("إجهاد") -> Color(0xFFFF9800)
                        uiState.temperatureStatus == "غير متاح" -> Color.Gray
                        else -> Color(0xFF4CAF50)
                    }
                )
            }

            item {
                FeatureStatusCard(
                    label = stringResource(R.string.fall_detection),
                    isEnabled = uiState.fallDetectionEnabled
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { viewModel.triggerSOS() },
                        modifier = Modifier.size(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFF44336)
                        )
                    ) {
                        Text(
                            text = "SOS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (uiState.isMonitoring) {
                                viewModel.stopMonitoring()
                            } else {
                                viewModel.startMonitoring()
                            }
                        },
                        modifier = Modifier.size(60.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (uiState.isMonitoring) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = if (uiState.isMonitoring) "إيقاف" else "بدء",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HealthMetricCard(
    label: String,
    value: String,
    status: String,
    statusColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = { }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                color = Color.Gray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = status,
                style = MaterialTheme.typography.caption2,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FeatureStatusCard(
    label: String,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        onClick = { }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.body1
            )
            Text(
                text = if (isEnabled) "مفعّل" else "معطّل",
                style = MaterialTheme.typography.caption1,
                color = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
