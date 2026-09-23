package com.heartguard.mobile.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.mobile.ai.AssistantContext
import com.heartguard.mobile.ai.CareAssistant
import com.heartguard.mobile.ai.PhoneRhythmService
import com.heartguard.mobile.data.HealthDataHolder
import com.heartguard.mobile.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val fromUser: Boolean,
    val text: String
)

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = ""
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val healthDataHolder: HealthDataHolder,
    private val alertRepository: AlertRepository,
    private val rhythmService: PhoneRhythmService
) : ViewModel() {

    private val assistant = CareAssistant()

    private val _uiState = MutableStateFlow(
        AssistantUiState(messages = listOf(ChatMessage(false, assistant.greeting())))
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun send(text: String = _uiState.value.input) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val ctx = currentContext()
            val reply = assistant.reply(trimmed, ctx)
            _uiState.update {
                it.copy(
                    input = "",
                    messages = it.messages + ChatMessage(true, trimmed) + ChatMessage(false, reply)
                )
            }
        }
    }

    private suspend fun currentContext(): AssistantContext {
        val health = healthDataHolder.healthData.value
        val alerts = alertRepository.getAllAlerts().first()
        val contacts = alertRepository.getAllContacts().first()
        return AssistantContext(
            heartRate = health.heartRate,
            temperature = health.temperature,
            watchConnected = healthDataHolder.isWatchConnected.value,
            recentAlerts = alerts,
            contactCount = contacts.size,
            restBaseline = rhythmService.model.restBaselineBpm(),
            lastClassificationReason = rhythmService.lastClassification?.reasonAr
        )
    }
}
