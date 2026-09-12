package com.heartguard.mobile.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.heartguard.mobile.data.local.EmergencyContactEntity
import com.heartguard.mobile.data.repository.AlertRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<EmergencyContactEntity> = emptyList()
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    application: Application,
    private val alertRepository: AlertRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        observeContacts()
    }

    private fun observeContacts() {
        viewModelScope.launch {
            alertRepository.getAllContacts().collect { contacts ->
                _uiState.update { it.copy(contacts = contacts) }
            }
        }
    }

    fun addContact(name: String, phone: String, relationship: String) {
        viewModelScope.launch {
            val contact = EmergencyContactEntity(
                name = name,
                phoneNumber = phone,
                relationship = relationship
            )
            alertRepository.insertContact(contact)
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            alertRepository.deleteContact(contact)
        }
    }
}
