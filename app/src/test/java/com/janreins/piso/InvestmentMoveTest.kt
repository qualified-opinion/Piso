package com.janreins.piso

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.models.INVESTMENT_CATEGORY
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.isInvestmentBuy
import com.janreins.piso.data.models.isInvestmentSale
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.budgets.budgetSpending
import com.janreins.piso.ui.home.summarizeMonth
import com.janreins.piso.ui.home.summarizeNetWorth
import com.janreins.piso.ui.invest.saleAmountError
import com.janreins.piso.util.CsvExporter
import com.janreins.piso.util.DateUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvestmentMoveTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository

    private val october = LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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

    private suspend fun wallet(balance: Double = 10000.0) =
        repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = balance))

    private suspend fun fund(value: Double = 3000.0) =
        repository.insertInvestment(Investment(name = "Index Fund", kind = "Stocks", currentValue = value))

    private suspend fun value(id: Long) = database.investmentDao().getInvestmentById(id)!!.currentValue
    private suspend fun balance(id: Long) = database.accountDao().getAccountById(id)!!.balance
    private suspend fun netWorth() = summarizeNetWorth(
        repository.allAccounts.first(), repository.allInvestments.first(), repository.allDebts.first()
    ).netWorth

    @Test
    fun buyingMovesMoneyIntoTheInvestmentWithoutSpending() = runBlocking {
        val walletId = wallet()
        val fundId = fund()
        val before = netWorth()

        assertTrue(repository.buyInvestment(fundId, 2500.0, walletId, october))

        assertEquals(7500.0, balance(walletId), 0.0)
        assertEquals(5500.0, value(fundId), 0.0)
        assertEquals(before, netWorth(), 0.0)
        val tx = repository.allTransactions.first().single()
        assertTrue(tx.isInvestmentBuy)
        assertEquals("TRANSFER", tx.type)
        assertEquals(INVESTMENT_CATEGORY, tx.category)
        assertEquals("Bought Index Fund", tx.note)
        val month = summarizeMonth(listOf(tx), "2026-10")
        assertEquals(0.0, month.spent, 0.0)
        assertEquals(0.0, month.income, 0.0)
        assertTrue(budgetSpending(listOf(tx), "2026-10").isEmpty())
    }

    @Test
    fun sellingMovesMoneyBackWithoutIncomeAndCantExceedTheValue() = runBlocking {
        val walletId = wallet()
        val fundId = fund(3000.0)

        assertFalse(repository.sellInvestment(fundId, 3000.01, walletId, october))
        assertTrue(repository.allTransactions.first().isEmpty())

        assertTrue(repository.sellInvestment(fundId, 1000.0, walletId, october, note = "Partial"))

        assertEquals(11000.0, balance(walletId), 0.0)
        assertEquals(2000.0, value(fundId), 0.0)
        val tx = repository.allTransactions.first().single()
        assertTrue(tx.isInvestmentSale)
        assertEquals("Partial", tx.note)
        assertEquals(0.0, summarizeMonth(listOf(tx), "2026-10").income, 0.0)
        assertNotNull(saleAmountError(3000.01, 3000.0))
        assertNull(saleAmountError(3000.0, 3000.0))
    }

    @Test
    fun deletingABuyOrSalePutsBothSidesBack() = runBlocking {
        val walletId = wallet()
        val fundId = fund(3000.0)
        repository.buyInvestment(fundId, 500.0, walletId, october)
        repository.sellInvestment(fundId, 1200.0, walletId, october)

        repository.allTransactions.first().forEach { repository.deleteTransaction(it) }

        assertEquals(10000.0, balance(walletId), 0.0)
        assertEquals(3000.0, value(fundId), 0.0)
    }

    @Test
    fun newInvestmentPaidFromAnAccountStartsAtItsCost() = runBlocking {
        val walletId = wallet()
        val before = netWorth()

        val id = repository.insertInvestmentFromAccount(
            Investment(name = "Gold 10g", kind = "Gold", currentValue = 4200.0), walletId, october
        )

        assertEquals(4200.0, value(id), 0.0)
        assertEquals(5800.0, balance(walletId), 0.0)
        assertEquals(before, netWorth(), 0.0)
        val tx = repository.allTransactions.first().single()
        assertEquals(id, tx.investmentId)
        assertEquals(DateUtil.getMonthKey(october), DateUtil.getMonthKey(tx.dateMillis))
    }

    @Test
    fun refusedWhenTheInvestmentOrAccountIsMissingOrTheAmountIsZero() = runBlocking {
        val walletId = wallet()
        val fundId = fund()

        assertFalse(repository.buyInvestment(999L, 100.0, walletId))
        assertFalse(repository.buyInvestment(fundId, 100.0, 999L))
        assertFalse(repository.buyInvestment(fundId, 0.0, walletId))
        assertFalse(repository.sellInvestment(fundId, 0.004, walletId))
        assertTrue(repository.allTransactions.first().isEmpty())
        assertEquals(10000.0, balance(walletId), 0.0)
    }

    @Test
    fun deletingTheInvestmentKeepsItsMovesAsTransfersAndDetachesThem() = runBlocking {
        val walletId = wallet()
        val fundId = fund()
        repository.buyInvestment(fundId, 500.0, walletId, october)

        repository.deleteInvestment(database.investmentDao().getInvestmentById(fundId)!!)

        val tx = repository.allTransactions.first().single()
        assertNull(tx.investmentId)
        assertEquals("TRANSFER", tx.type)
        assertEquals(9500.0, balance(walletId), 0.0)
    }

    @Test
    fun backupAndCsvKeepTheInvestmentLink() = runBlocking {
        val walletId = wallet()
        val fundId = fund()
        repository.buyInvestment(fundId, 500.0, walletId, october)

        val restored = BackupData.fromJsonString(repository.exportBackupJson())!!
        assertEquals(fundId, restored.transactions.single().investmentId)

        val old = """
            {"version": 3, "appName": "Piso", "accounts": [], "budgets": [], "goals": [], "debts": [], "investments": [],
             "transactions": [{"id": 1, "dateMillis": 1700000000000, "type": "TRANSFER", "category": "Transfer", "amount": 50.0, "accountId": 1}]}
        """.trimIndent()
        assertNull(BackupData.fromJsonString(old)!!.transactions.single().investmentId)

        val csv = CsvExporter.transactionsToCsv(repository.allTransactions.first(), emptyList())
        assertTrue(csv.lines()[1].endsWith("Investment"))
    }
}
