package com.janreins.piso

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.models.forPicker
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.accounts.archiveExplanation
import com.janreins.piso.ui.home.summarizeNetWorth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AccountArchiveTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(database, BackupSafety(temporaryFolder.newFolder("backups")))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun stored(id: Long) = database.accountDao().getAccountById(id)!!

    @Test
    fun archiveAndUnarchiveKeepBalanceAndHistory() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Old GCash", kind = "E-Wallet", balance = 500.0))
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Food", amount = 120.0, accountId = wallet))

        repository.setAccountArchived(wallet, true)
        assertTrue(stored(wallet).isArchived)
        assertEquals(380.0, stored(wallet).balance, 0.0)
        assertEquals(1, repository.allTransactions.first().size)

        // Editing the name of an archived account keeps it archived.
        repository.updateAccount(stored(wallet).copy(name = "GCash (old)", isArchived = false))
        assertTrue(stored(wallet).isArchived)
        assertEquals("GCash (old)", stored(wallet).name)

        repository.setAccountArchived(wallet, false)
        assertFalse(stored(wallet).isArchived)
        assertEquals(380.0, stored(wallet).balance, 0.0)
        assertEquals(1, repository.allTransactions.first().size)
    }

    @Test
    fun pickersLeaveOutArchivedAccountsExceptOnesAlreadyInUse() {
        val accounts = listOf(
            Account(id = 1, name = "Wallet", kind = "Cash", balance = 100.0),
            Account(id = 2, name = "Old GCash", kind = "E-Wallet", balance = 0.0, isArchived = true),
            Account(id = 3, name = "BPI", kind = "Bank", balance = 900.0)
        )

        assertEquals(listOf(1L, 3L), accounts.forPicker().map { it.id })
        assertEquals(listOf(1L, 3L), accounts.forPicker(null, 3L).map { it.id })
        // Editing a transaction that already uses the archived account keeps it selectable.
        assertEquals(listOf(1L, 2L, 3L), accounts.forPicker(2L).map { it.id })
    }

    @Test
    fun netWorthIsUnchangedByArchiving() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        repository.insertAccount(Account(name = "Old GCash", kind = "E-Wallet", balance = 250.0))
        repository.insertInvestment(Investment(name = "Fund", kind = "Stocks", currentValue = 3000.0))
        repository.insertDebt(Debt(name = "Loan", kind = "Personal", originalAmount = 2000.0, remainingAmount = 1500.0))

        suspend fun netWorth() = summarizeNetWorth(
            repository.allAccounts.first(), repository.allInvestments.first(), repository.allDebts.first()
        )
        val before = netWorth()

        val gcash = repository.allAccounts.first().first { it.id != wallet }.id
        repository.setAccountArchived(gcash, true)

        assertEquals(before, netWorth())
        assertEquals(2750.0, netWorth().netWorth, 0.0)
        assertEquals(1250.0, netWorth().accountsTotal, 0.0)
    }

    @Test
    fun archivedFlagSurvivesBackupRoundTrip() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        val gcash = repository.insertAccount(Account(name = "Old GCash", kind = "E-Wallet", balance = 0.0))
        repository.setAccountArchived(gcash, true)

        val json = repository.exportBackupJson()
        repository.clearAllData()
        assertTrue(repository.importBackupJson(json))

        assertFalse(stored(wallet).isArchived)
        assertTrue(stored(gcash).isArchived)
    }

    @Test
    fun olderBackupWithoutArchivedFlagReadsAsNotArchived() {
        val json = BackupData(
            accounts = listOf(Account(id = 1, name = "Old GCash", kind = "E-Wallet", balance = 0.0, isArchived = true)),
            transactions = emptyList(),
            budgets = emptyList(),
            goals = emptyList(),
            debts = emptyList(),
            investments = emptyList()
        ).toJsonString()
        val root = JSONObject(json)
        root.getJSONArray("accounts").getJSONObject(0).remove("isArchived")

        val parsed = BackupData.fromJsonString(root.toString())!!

        assertFalse(parsed.accounts.single().isArchived)
        assertTrue(BackupData.fromJsonString(json)!!.accounts.single().isArchived)
    }

    @Test
    fun archiveExplanationSuggestsClearingANonZeroBalance() {
        assertTrue(archiveExplanation(0.0).contains("still counts in net worth"))
        assertFalse(archiveExplanation(0.0).contains("still holds"))
        assertTrue(archiveExplanation(250.0).contains("still holds ₱250.00"))
    }
}
