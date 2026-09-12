package com.heartguard.mobile.data.repository

import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.local.CaregiverDao
import com.heartguard.mobile.data.local.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val caregiverDao: CaregiverDao
) {
    fun getAllContacts(): Flow<List<EmergencyContactEntity>> {
        return caregiverDao.getAllContacts()
    }

    suspend fun getPrimaryContact(): EmergencyContactEntity? {
        return caregiverDao.getPrimaryContact()
    }

    suspend fun insertContact(contact: EmergencyContactEntity) {
        caregiverDao.insertContact(contact)
    }

    suspend fun deleteContact(contact: EmergencyContactEntity) {
        caregiverDao.deleteContact(contact)
    }

    fun getAllAlerts(): Flow<List<AlertEntity>> {
        return caregiverDao.getAllAlerts()
    }

    fun getUnreadAlerts(): Flow<List<AlertEntity>> {
        return caregiverDao.getUnreadAlerts()
    }

    suspend fun insertAlert(alert: AlertEntity) {
        caregiverDao.insertAlert(alert)
    }

    suspend fun markAlertAsRead(alertId: String) {
        caregiverDao.markAlertAsRead(alertId)
    }

    suspend fun markAlertAsHandled(alertId: String) {
        caregiverDao.markAlertAsHandled(alertId)
    }

    suspend fun cleanupOldData() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        caregiverDao.deleteOldAlerts(thirtyDaysAgo)
    }
}
