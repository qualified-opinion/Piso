package com.janreins.piso

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.models.CARD_PAYMENT_CATEGORY
import com.janreins.piso.data.models.DEBT_FLOW_CHARGE
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.models.amountOwed
import com.janreins.piso.data.models.isCardPurchase
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.budgets.budgetSpending
import com.janreins.piso.ui.home.summarizeMonth
import com.janreins.piso.ui.home.summarizeNetWorth
import com.janreins.piso.util.CsvExporter
import com.janreins.piso.util.DateUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardPurchaseTest {

    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var database: AppDatabase
    private lateinit var repository: FinanceRepository

    private val october = LocalDate.of(2026, 10, 10).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val november = LocalDate.of(2026, 11, 5).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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

    private suspend fun card(remaining: Double = 0.0, tracks: Boolean = true) = repository.insertDebt(
        Debt(name = "BPI Card", kind = "Credit Card", originalAmount = 50000.0, remainingAmount = remaining, tracksPurchases = tracks)
    )

    // What's shown as owed. The stored remaining can sit below zero between out-of-order deletes.
    private suspend fun owed(debtId: Long) = database.debtDao().getDebtById(debtId)!!.amountOwed()
    private suspend fun rawRemaining(debtId: Long) = database.debtDao().getDebtById(debtId)!!.remainingAmount
    private suspend fun balance(accountId: Long) = database.accountDao().getAccountById(accountId)!!.balance

    @Test
    fun purchaseIsSpendingInItsMonthAndAddsToWhatIsOwed() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card(remaining = 1000.0)

        assertTrue(repository.recordCardPurchase(cardId, 1250.5, "Food", "Groceries", "SM", october))

        assertEquals(2250.5, owed(cardId), 0.0)
        assertEquals(5000.0, balance(wallet), 0.0)
        val tx = repository.allTransactions.first().single()
        assertTrue(tx.isCardPurchase)
        assertEquals("EXPENSE", tx.type)
        assertNull(tx.accountId)
        assertEquals("Groceries", tx.subcategory)
        assertEquals(1250.5, summarizeMonth(listOf(tx), "2026-10").spent, 0.0)
        assertEquals(mapOf("Food" to 1250.5), budgetSpending(listOf(tx), "2026-10"))
        assertEquals(0.0, summarizeMonth(listOf(tx), "2026-11").spent, 0.0)
    }

    @Test
    fun payingATrackingCardMovesMoneyWithoutCountingAsSpending() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card()
        repository.recordCardPurchase(cardId, 3000.0, "Shopping", dateMillis = october)
        val netWorthBefore = netWorth()

        repository.recordDebtPayment(cardId, 3000.0, wallet, november)

        assertEquals(0.0, owed(cardId), 0.0)
        assertEquals(2000.0, balance(wallet), 0.0)
        assertEquals(netWorthBefore, netWorth(), 0.0)
        val payment = repository.allTransactions.first().single { !it.isCardPurchase }
        assertEquals("TRANSFER", payment.type)
        assertEquals(CARD_PAYMENT_CATEGORY, payment.category)
        assertEquals(cardId, payment.debtId)
        // The purchase was the spending; the payment month shows none.
        val all = repository.allTransactions.first()
        assertEquals(3000.0, summarizeMonth(all, "2026-10").spent, 0.0)
        assertEquals(0.0, summarizeMonth(all, "2026-11").spent, 0.0)
        assertTrue(budgetSpending(all, "2026-11").isEmpty())
    }

    @Test
    fun payingACardThatDoesNotTrackPurchasesIsStillSpending() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card(remaining = 2000.0, tracks = false)

        assertFalse(repository.recordCardPurchase(cardId, 100.0, "Food"))
        repository.recordDebtPayment(cardId, 500.0, wallet, november)

        val payment = repository.allTransactions.first().single()
        assertEquals("EXPENSE", payment.type)
        assertEquals("Debt", payment.category)
        assertEquals(500.0, summarizeMonth(listOf(payment), "2026-11").spent, 0.0)
        assertEquals(1500.0, owed(cardId), 0.0)
    }

    @Test
    fun deletingAPurchaseOrPaymentPutsTheCardBack() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card()
        repository.recordCardPurchase(cardId, 800.0, "Food", dateMillis = october)
        repository.recordDebtPayment(cardId, 300.0, wallet, november)
        assertEquals(500.0, owed(cardId), 0.0)

        val payment = repository.allTransactions.first().single { !it.isCardPurchase }
        repository.deleteTransaction(payment)
        assertEquals(800.0, owed(cardId), 0.0)
        assertEquals(5000.0, balance(wallet), 0.0)

        val purchase = repository.allTransactions.first().single()
        repository.deleteTransaction(purchase)
        repository.deleteTransaction(purchase) // a double tap reverts once only
        assertEquals(0.0, owed(cardId), 0.0)
        assertTrue(repository.allTransactions.first().isEmpty())
    }

    @Test
    fun deletingAPurchaseAfterTheCardWasPaidNeverGoesBelowZero() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card()
        repository.recordCardPurchase(cardId, 800.0, "Food", dateMillis = october)
        repository.recordDebtPayment(cardId, 800.0, wallet, november)

        repository.deleteTransaction(repository.allTransactions.first().single { it.isCardPurchase })

        assertEquals(0.0, owed(cardId), 0.0)
    }

    @Test
    fun editingAPurchaseAmountMovesWhatIsOwed() = runBlocking {
        val cardId = card(remaining = 100.0)
        repository.recordCardPurchase(cardId, 800.0, "Food", dateMillis = october)
        val purchase = repository.allTransactions.first().single()

        // Not capped at what's owed, unlike a payment.
        repository.updateTransaction(purchase, purchase.copy(amount = 1500.0))

        assertEquals(1600.0, owed(cardId), 0.0)
        assertEquals(1500.0, repository.allTransactions.first().single().amount, 0.0)
    }

    @Test
    fun deletingTheCardKeepsPurchasesAsSpendingAndDetachesThem() = runBlocking {
        val cardId = card()
        repository.recordCardPurchase(cardId, 400.0, "Food", dateMillis = october)

        repository.deleteDebt(database.debtDao().getDebtById(cardId)!!)

        val tx = repository.allTransactions.first().single()
        assertNull(tx.debtId)
        assertNull(tx.debtFlow)
        assertEquals(400.0, summarizeMonth(listOf(tx), "2026-10").spent, 0.0)
    }

    @Test
    fun backupKeepsCardSettingAndPurchasesAndOldBackupsDefaultToPayments() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card()
        repository.recordCardPurchase(cardId, 400.0, "Food", dateMillis = october)
        repository.recordDebtPayment(cardId, 100.0, wallet, november)

        val restored = BackupData.fromJsonString(repository.exportBackupJson())!!
        assertTrue(restored.debts.single().tracksPurchases)
        assertEquals(DEBT_FLOW_CHARGE, restored.transactions.single { it.type == "EXPENSE" }.debtFlow)
        assertNull(restored.transactions.single { it.type == "TRANSFER" }.debtFlow)

        val old = """
            {"version": 3, "appName": "Piso", "accounts": [], "budgets": [], "goals": [], "investments": [],
             "debts": [{"id": 1, "name": "Card", "kind": "Credit Card", "originalAmount": 100.0, "remainingAmount": 50.0, "notes": ""}],
             "transactions": [{"id": 1, "dateMillis": 1700000000000, "type": "EXPENSE", "category": "Debt", "amount": 50.0, "debtId": 1}]}
        """.trimIndent()
        val parsed = BackupData.fromJsonString(old)!!
        assertFalse(parsed.debts.single().tracksPurchases)
        assertNull(parsed.transactions.single().debtFlow)
    }

    @Test
    fun csvMarksCardPurchases() = runBlocking {
        val cardId = card()
        repository.recordCardPurchase(cardId, 400.0, "Food", dateMillis = october)

        val csv = CsvExporter.transactionsToCsv(repository.allTransactions.first(), emptyList())

        assertTrue(csv.lines()[1].endsWith("Card purchase"))
        assertTrue(csv.lines()[1].contains(DateUtil.formatInputDate(october)))
    }

    @Test
    fun editingAPurchaseAfterItsCardWasDeletedDoesNotChargeAnAccount() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card()
        repository.recordCardPurchase(cardId, 400.0, "Food", dateMillis = october)
        repository.deleteDebt(database.debtDao().getDebtById(cardId)!!)
        val detached = repository.allTransactions.first().single()

        repository.updateTransaction(
            detached,
            detached.copy(accountId = wallet, type = "EXPENSE", category = "Shopping", note = "edited", amount = 250.0)
        )

        val saved = repository.allTransactions.first().single()
        assertNull(saved.accountId)
        assertNull(saved.transferToId)
        assertEquals("EXPENSE", saved.type)
        assertEquals("Shopping", saved.category)
        assertEquals("edited", saved.note)
        assertEquals(250.0, saved.amount, 0.0)
        assertEquals(5000.0, balance(wallet), 0.0)
        assertEquals(250.0, summarizeMonth(listOf(saved), "2026-10").spent, 0.0)
    }

    @Test
    fun turningOffPurchaseTrackingKeepsPaymentsFromCountingTheSpendAgain() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card(remaining = 200.0)
        repository.recordCardPurchase(cardId, 800.0, "Food", dateMillis = october)
        val stored = database.debtDao().getDebtById(cardId)!!

        repository.updateDebt(
            stored.copy(kind = "Personal", tracksPurchases = false),
            seenRemaining = stored.amountOwed()
        )

        val kept = database.debtDao().getDebtById(cardId)!!
        assertTrue(kept.tracksPurchases)
        assertEquals("Personal", kept.kind)
        repository.recordDebtPayment(cardId, 200.0, wallet, november)
        val payment = repository.allTransactions.first().single { !it.isCardPurchase }
        assertEquals("TRANSFER", payment.type)
        assertEquals(CARD_PAYMENT_CATEGORY, payment.category)
        val all = repository.allTransactions.first()
        assertEquals(800.0, summarizeMonth(all, "2026-10").spent, 0.0)
        assertEquals(0.0, summarizeMonth(all, "2026-11").spent, 0.0)
    }

    @Test
    fun aPaymentStaysATransferWhenPurchasesWereRecordedEvenIfTrackingIsOff() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card(remaining = 200.0, tracks = false)
        repository.addTransaction(
            Transaction(
                dateMillis = october,
                type = "EXPENSE",
                category = "Food",
                amount = 400.0,
                debtId = cardId,
                debtFlow = DEBT_FLOW_CHARGE
            )
        )

        repository.recordDebtPayment(cardId, 100.0, wallet, november)

        val payment = repository.allTransactions.first().single { !it.isCardPurchase }
        assertEquals("TRANSFER", payment.type)
        assertEquals(CARD_PAYMENT_CATEGORY, payment.category)
        assertFalse(database.debtDao().getDebtById(cardId)!!.tracksPurchases)
        val all = repository.allTransactions.first()
        assertEquals(400.0, summarizeMonth(all, "2026-10").spent, 0.0)
        assertEquals(0.0, summarizeMonth(all, "2026-11").spent, 0.0)
        assertEquals(500.0, rawRemaining(cardId), 0.0)
        assertEquals(4900.0, balance(wallet), 0.0)
    }

    @Test
    fun deletingAPurchaseThenItsPaymentRestoresWhatWasOwedAndTheCash() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val cardId = card(remaining = 200.0)
        repository.recordCardPurchase(cardId, 800.0, "Food", dateMillis = october)
        repository.recordDebtPayment(cardId, 800.0, wallet, november)
        val purchase = repository.allTransactions.first().single { it.isCardPurchase }
        val payment = repository.allTransactions.first().single { !it.isCardPurchase }

        repository.deleteTransaction(purchase)
        val dipped = database.debtDao().getDebtById(cardId)!!
        assertEquals(-600.0, dipped.remainingAmount, 0.0)
        assertEquals(0.0, dipped.amountOwed(), 0.0)
        // Saving the dialog's clamped "0 owed" must not wipe the balance the payment delete still needs.
        repository.updateDebt(
            dipped.copy(name = "Renamed", remainingAmount = dipped.amountOwed()),
            seenRemaining = dipped.amountOwed()
        )
        assertEquals("Renamed", database.debtDao().getDebtById(cardId)!!.name)
        assertEquals(-600.0, rawRemaining(cardId), 0.0)

        repository.deleteTransaction(payment)
        assertEquals(200.0, rawRemaining(cardId), 0.0)
        assertEquals(5000.0, balance(wallet), 0.0)

        repository.recordCardPurchase(cardId, 800.0, "Food", dateMillis = october)
        repository.recordDebtPayment(cardId, 800.0, wallet, november)
        val again = repository.allTransactions.first()
        repository.deleteTransaction(again.single { !it.isCardPurchase })
        repository.deleteTransaction(again.single { it.isCardPurchase })
        assertEquals(200.0, rawRemaining(cardId), 0.0)
        assertEquals(5000.0, balance(wallet), 0.0)
    }

    private suspend fun netWorth() = summarizeNetWorth(
        repository.allAccounts.first(), repository.allInvestments.first(), repository.allDebts.first()
    ).netWorth
}
