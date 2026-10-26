package com.janreins.piso

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.models.RecurringRule
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.home.summarizeMonth
import com.janreins.piso.ui.recurring.dueForConfirmation
import com.janreins.piso.util.DateUtil
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringRepositoryTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository
    private var wallet = 0L

    private val oct1 = LocalDate.of(2026, 10, 1).toEpochDay()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database, BackupSafety(temporaryFolder.newFolder("backups")))
        wallet = runBlocking { repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0)) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun rule(
        amount: Double = 50.0,
        frequency: String = RecurringRule.DAILY,
        start: Long = oct1,
        askFirst: Boolean = false,
        type: String = "EXPENSE"
    ) = RecurringRule(
        type = type,
        category = "Transport",
        amount = amount,
        note = "Fare",
        accountId = wallet,
        frequency = frequency,
        anchorDay = LocalDate.ofEpochDay(start).dayOfMonth,
        startDay = start,
        nextDueDay = start,
        askFirst = askFirst
    )

    private suspend fun balance() = database.accountDao().getAccountById(wallet)!!.balance
    private suspend fun transactions() = repository.allTransactions.first()
    private suspend fun storedRule(id: Long) = database.recurringDao().getRuleById(id)!!

    @Test
    fun catchUpRecordsEachMissedDateOnItsOwnDateAndOnlyOnce() = runBlocking {
        val id = repository.insertRecurringRule(rule())

        // Ten days not opened: Oct 1 to Oct 10.
        assertEquals(10, repository.postDueRecurring(today = oct1 + 9))
        assertEquals(500.0, balance(), 0.0)
        val days = transactions().map { DateUtil.toEpochDay(it.dateMillis) }.sorted()
        assertEquals((oct1..oct1 + 9).toList(), days)
        assertTrue(transactions().all { it.recurringId == id && it.note == "Fare" && it.category == "Transport" })
        assertEquals(oct1 + 10, storedRule(id).nextDueDay)

        // Running again the same day, or a second time, makes nothing new.
        assertEquals(0, repository.postDueRecurring(today = oct1 + 9))
        assertEquals(10, transactions().size)
        assertEquals(1, repository.postDueRecurring(today = oct1 + 10))
        assertEquals(11, transactions().size)
    }

    @Test
    fun nothingIsRecordedBeforeTheStartDate() = runBlocking {
        repository.insertRecurringRule(rule(start = oct1 + 5))
        assertEquals(0, repository.postDueRecurring(today = oct1))
        assertTrue(transactions().isEmpty())
        assertEquals(1000.0, balance(), 0.0)
    }

    @Test
    fun amountsAreRoundedAndCountInThatMonthsSpending() = runBlocking {
        repository.insertRecurringRule(rule(amount = 12.345, frequency = RecurringRule.MONTHLY))
        assertEquals(1, repository.postDueRecurring(today = oct1 + 3))
        assertEquals(12.35, transactions().single().amount, 0.0)
        assertEquals(987.65, balance(), 0.0)
        assertEquals(12.35, summarizeMonth(transactions(), "2026-10").spent, 0.0)
    }

    @Test
    fun recurringIncomeRaisesTheBalance() = runBlocking {
        repository.insertRecurringRule(rule(amount = 25000.0, frequency = RecurringRule.MONTHLY, type = "INCOME"))
        repository.postDueRecurring(today = oct1)
        assertEquals(26000.0, balance(), 0.0)
        assertEquals(25000.0, summarizeMonth(transactions(), "2026-10").income, 0.0)
    }

    @Test
    fun pausedScheduleRecordsNothingAndResumeSkipsThePausedDates() = runBlocking {
        val id = repository.insertRecurringRule(rule())
        assertTrue(repository.setRecurringPaused(id, true))
        assertEquals(0, repository.postDueRecurring(today = oct1 + 4))
        assertTrue(transactions().isEmpty())

        assertTrue(repository.setRecurringPaused(id, false, today = oct1 + 4))
        assertEquals(oct1 + 4, storedRule(id).nextDueDay)
        assertEquals(1, repository.postDueRecurring(today = oct1 + 4))
        assertEquals(950.0, balance(), 0.0)
    }

    @Test
    fun askFirstWaitsUntilRecordedOrSkipped() = runBlocking {
        val id = repository.insertRecurringRule(rule(amount = 1500.0, frequency = RecurringRule.MONTHLY, askFirst = true))
        val nov1 = LocalDate.of(2026, 11, 1).toEpochDay()

        assertEquals(0, repository.postDueRecurring(today = nov1))
        assertTrue(transactions().isEmpty())
        assertEquals(1000.0, balance(), 0.0)
        val due = dueForConfirmation(database.recurringDao().getAllRules().first(), nov1).single()
        assertEquals(oct1, due.dueDay)
        assertEquals(1, due.moreCount)

        // Record October with the real amount; a repeated tap on the same date does nothing.
        assertTrue(repository.recordDueRecurring(id, oct1, 1734.5, today = nov1))
        assertFalse(repository.recordDueRecurring(id, oct1, 1734.5, today = nov1))
        assertEquals(1734.5, transactions().single().amount, 0.0)
        assertEquals(DateUtil.epochDayToMillis(oct1), transactions().single().dateMillis)
        assertEquals(-734.5, balance(), 0.0)

        // Skip November: no entry, and the next date moves on.
        assertTrue(repository.skipDueRecurring(id, nov1, today = nov1))
        assertEquals(1, transactions().size)
        assertEquals(LocalDate.of(2026, 12, 1).toEpochDay(), storedRule(id).nextDueDay)
        assertTrue(dueForConfirmation(database.recurringDao().getAllRules().first(), nov1).isEmpty())

        // A date that hasn't come yet can't be recorded early.
        assertFalse(repository.recordDueRecurring(id, LocalDate.of(2026, 12, 1).toEpochDay(), 1500.0, today = nov1))
    }

    @Test
    fun archivingTheAccountPausesItsSchedulesAndResumeWaitsForUnarchive() = runBlocking {
        val id = repository.insertRecurringRule(rule())
        repository.setAccountArchived(wallet, true)
        assertTrue(storedRule(id).isPaused)
        assertEquals(0, repository.postDueRecurring(today = oct1 + 2))
        assertFalse(repository.setRecurringPaused(id, false, today = oct1 + 2))

        repository.setAccountArchived(wallet, false)
        assertTrue(repository.setRecurringPaused(id, false, today = oct1 + 2))
        assertEquals(1, repository.postDueRecurring(today = oct1 + 2))
    }

    @Test
    fun anAccountWithAScheduleCantBeDeleted() = runBlocking {
        val id = repository.insertRecurringRule(rule(start = oct1 + 30))
        assertTrue(repository.hasRecurringForAccount(wallet))
        assertFalse(repository.deleteAccount(database.accountDao().getAccountById(wallet)!!))
        repository.deleteRecurringRule(storedRule(id))
        assertTrue(repository.deleteAccount(database.accountDao().getAccountById(wallet)!!))
    }

    @Test
    fun deletingAScheduleKeepsItsPastEntries() = runBlocking {
        val id = repository.insertRecurringRule(rule())
        repository.postDueRecurring(today = oct1 + 2)
        repository.deleteRecurringRule(storedRule(id))
        assertEquals(3, transactions().size)
        assertTrue(transactions().all { it.recurringId == null })
        assertEquals(850.0, balance(), 0.0)
        assertNull(database.recurringDao().getRuleById(id))
    }

    @Test
    fun editingAppliesToFutureDatesOnly() = runBlocking {
        val id = repository.insertRecurringRule(rule())
        repository.postDueRecurring(today = oct1 + 2) // Oct 1 to 3 recorded

        // A new amount and an earlier start date don't repeat the dates already recorded.
        repository.updateRecurringRule(storedRule(id).copy(amount = 60.0, startDay = oct1 - 10))
        assertEquals(oct1 + 3, storedRule(id).nextDueDay)
        assertEquals(1, repository.postDueRecurring(today = oct1 + 3))
        assertEquals(listOf(50.0, 50.0, 50.0, 60.0), transactions().sortedBy { it.dateMillis }.map { it.amount })
        // Past entries keep their amount.
        assertEquals(790.0, balance(), 0.0)
    }

    @Test
    fun editingBeforeAnythingIsRecordedStartsFromTheNewStartDate() = runBlocking {
        val id = repository.insertRecurringRule(rule(start = oct1 + 10))
        repository.updateRecurringRule(storedRule(id).copy(startDay = oct1))
        assertEquals(oct1, storedRule(id).nextDueDay)
    }

    @Test
    fun scheduleStopsAtItsEndDate() = runBlocking {
        val id = repository.insertRecurringRule(rule().copy(endDay = oct1 + 2))
        assertEquals(3, repository.postDueRecurring(today = oct1 + 30))
        assertTrue(storedRule(id).hasEnded)
        assertEquals(0, repository.postDueRecurring(today = oct1 + 60))
    }

    @Test
    fun scheduleStartedLongAgoCatchesUpInBatches() = runBlocking {
        repository.insertRecurringRule(rule(amount = 1.0, start = oct1 - 499))
        assertEquals(500, repository.postDueRecurring(today = oct1))
        assertEquals(500, transactions().size)
        assertEquals(500.0, balance(), 0.0)
    }

    @Test
    fun backupRoundTripKeepsSchedulesAndLinks() = runBlocking {
        val id = repository.insertRecurringRule(rule(askFirst = true).copy(endDay = oct1 + 100))
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Transport", amount = 50.0, accountId = wallet, recurringId = id))
        val json = repository.exportBackupJson()
        val parsed = BackupData.fromJsonString(json)!!
        assertEquals(listOf(storedRule(id)), parsed.recurringRules)
        assertEquals(id, parsed.transactions.single().recurringId)

        repository.clearAllData()
        assertTrue(database.recurringDao().getAllRules().first().isEmpty())
        assertTrue(repository.importBackupJson(json))
        assertEquals(parsed.recurringRules, database.recurringDao().getAllRules().first())
        assertEquals(id, transactions().single().recurringId)
    }

    @Test
    fun olderBackupWithoutSchedulesImportsWithNone() = runBlocking {
        repository.insertRecurringRule(rule())
        val json = JSONObject(repository.exportBackupJson()).apply { remove("recurringRules") }.toString()
        val parsed = BackupData.fromJsonString(json)!!
        assertTrue(parsed.recurringRules.isEmpty())

        assertTrue(repository.importBackupJson(json))
        // Schedules from before the restore would point at the restored accounts by mistake.
        assertTrue(database.recurringDao().getAllRules().first().isEmpty())
    }

    @Test
    fun backupWithAnUnknownFrequencyIsRejected() = runBlocking {
        repository.insertRecurringRule(rule())
        val root = JSONObject(repository.exportBackupJson())
        root.getJSONArray("recurringRules").getJSONObject(0).put("frequency", "HOURLY")
        assertNull(BackupData.fromJsonString(root.toString()))
    }
}
