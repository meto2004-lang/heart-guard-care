package com.heartguard.mobile.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CaregiverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity)

    @Query("SELECT * FROM emergency_contacts ORDER BY isPrimary DESC, name ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryContact(): EmergencyContactEntity?

    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :contactId")
    suspend fun deleteContactById(contactId: Long)

    // The primary key is the event ID shared by both Wear transports. Inserting
    // atomically avoids a check-then-insert race between concurrent callbacks.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadAlerts(): Flow<List<AlertEntity>>

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :alertId")
    suspend fun markAlertAsRead(alertId: String)

    @Query("UPDATE alerts SET isHandled = 1 WHERE id = :alertId")
    suspend fun markAlertAsHandled(alertId: String)

    @Query("DELETE FROM alerts WHERE timestamp < :timestamp")
    suspend fun deleteOldAlerts(timestamp: Long)
}
