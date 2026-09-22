package com.heartguard.mobile.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.heartguard.mobile.data.local.AlertEntity
import com.heartguard.mobile.data.local.MobileDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class AlertRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "alert-dedup-test.db"
    private lateinit var database: MobileDatabase
    private lateinit var repository: AlertRepository

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun concurrentCopiesOnlyAllowOneProcessingAttempt() = runBlocking {
        val event = alert("same-event")
        // Models simultaneous DataClient/MessageClient deliveries and retries.
        val accepted = (1..32).map {
            async(Dispatchers.IO) { repository.insertAlertIfNew(event) }
        }.awaitAll()
        assertEquals(1, accepted.count { it })
        assertEquals(1, repository.getAllAlerts().first().size)
    }

    @Test
    fun duplicateDoesNotOverwriteReadOrHandledFlags() = runBlocking {
        val event = alert("same-event")
        assertTrue(repository.insertAlertIfNew(event))
        repository.markAlertAsRead(event.id)
        repository.markAlertAsHandled(event.id)
        assertFalse(repository.insertAlertIfNew(event.copy(message = "delayed copy")))
        val saved = repository.getAllAlerts().first().single()
        assertTrue(saved.isRead)
        assertTrue(saved.isHandled)
        assertEquals(event.message, saved.message)
    }

    @Test
    fun duplicateIsStillRejectedAfterDatabaseIsReopened() = runBlocking {
        val event = alert("persisted-event")
        assertTrue(repository.insertAlertIfNew(event))
        database.close()
        openDatabase()
        assertFalse(repository.insertAlertIfNew(event))
        assertEquals(1, repository.getAllAlerts().first().size)
    }

    @Test
    fun differentEventsWithIdenticalContentAreBothAccepted() = runBlocking {
        assertTrue(repository.insertAlertIfNew(alert("first")))
        assertTrue(repository.insertAlertIfNew(alert("second")))
        assertEquals(2, repository.getAllAlerts().first().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingIdentityIsRejected() = runBlocking {
        repository.insertAlertIfNew(alert(" "))
        Unit
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, MobileDatabase::class.java, databaseName).build()
        repository = AlertRepository(database.caregiverDao())
    }

    private fun alert(id: String) = AlertEntity(
        id = id,
        type = "SOS_MANUAL",
        severity = "CRITICAL",
        message = "SOS",
        timestamp = 1_700_000_000_000L
    )
}
