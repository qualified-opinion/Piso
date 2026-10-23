package com.janreins.piso

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Goal
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.data.repository.BackupSafety
import com.janreins.piso.data.repository.FinanceRepository
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.janreins.piso.util.CurrencyUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.janreins.piso.util.DateUtil
import java.time.LocalDate
import java.time.ZoneId
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinanceRepositoryTest {

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

    @Test
    fun testCurrencyFormatting() {
        assertEquals("₱1,250.00", CurrencyUtil.formatPeso(1250.0))
        assertEquals("₱0.00", CurrencyUtil.formatPeso(0.0))
        assertEquals("-₱500.50", CurrencyUtil.formatPeso(-500.5))
    }

    @Test
    fun testAddIncomeUpdatesAccountBalance() = runBlocking {
        val accountId = repository.insertAccount(
            Account(name = "BPI Savings", kind = "Bank", balance = 10000.0)
        )

        repository.addTransaction(
            Transaction(
                dateMillis = System.currentTimeMillis(),
                type = "INCOME",
                category = "Salary",
                amount = 25000.0,
                accountId = accountId
            )
        )

        val updatedAccount = database.accountDao().getAccountById(accountId)
        assertNotNull(updatedAccount)
        assertEquals(35000.0, updatedAccount!!.balance, 0.001)
    }

    @Test
    fun testAddExpenseDeductsAccountBalance() = runBlocking {
        val accountId = repository.insertAccount(
            Account(name = "GCash", kind = "E-Wallet", balance = 5000.0)
        )

        repository.addTransaction(
            Transaction(
                dateMillis = System.currentTimeMillis(),
                type = "EXPENSE",
                category = "Food",
                amount = 450.0,
                accountId = accountId
            )
        )

        val updatedAccount = database.accountDao().getAccountById(accountId)
        assertNotNull(updatedAccount)
        assertEquals(4550.0, updatedAccount!!.balance, 0.001)
    }

    @Test
    fun testTransferBetweenAccounts() = runBlocking {
        val fromId = repository.insertAccount(
            Account(name = "Bank Account", kind = "Bank", balance = 20000.0)
        )
        val toId = repository.insertAccount(
            Account(name = "GCash", kind = "E-Wallet", balance = 1000.0)
        )

        repository.addTransaction(
            Transaction(
                dateMillis = System.currentTimeMillis(),
                type = "TRANSFER",
                category = "Transfer",
                amount = 3000.0,
                accountId = fromId,
                transferToId = toId
            )
        )

        val fromAcc = database.accountDao().getAccountById(fromId)
        val toAcc = database.accountDao().getAccountById(toId)

        assertEquals(17000.0, fromAcc!!.balance, 0.001)
        assertEquals(4000.0, toAcc!!.balance, 0.001)
    }

    @Test
    fun testAddMoneyToGoal() = runBlocking {
        val accountId = repository.insertAccount(
            Account(name = "Wallet", kind = "Cash", balance = 10000.0)
        )
        val goalId = repository.insertGoal(
            Goal(name = "Emergency Fund", targetAmount = 50000.0, currentAmount = 5000.0)
        )

        val completed = repository.addMoneyToGoal(goalId, 2000.0, accountId)

        val updatedGoal = database.goalDao().getGoalById(goalId)
        val updatedAccount = database.accountDao().getAccountById(accountId)

        assertEquals(7000.0, updatedGoal!!.currentAmount, 0.001)
        // The goal has no account of its own, so the money stays in the wallet, set aside
        assertEquals(10000.0, updatedAccount!!.balance, 0.001)
        assertEquals(false, completed)
    }

    @Test
    fun testRecordDebtPayment() = runBlocking {
        val accountId = repository.insertAccount(
            Account(name = "Checking", kind = "Bank", balance = 15000.0)
        )
        val debtId = repository.insertDebt(
            Debt(name = "Credit Card", kind = "Credit Card", originalAmount = 10000.0, remainingAmount = 10000.0)
        )

        repository.recordDebtPayment(debtId, 2500.0, accountId)

        val updatedDebt = database.debtDao().getDebtById(debtId)
        val updatedAccount = database.accountDao().getAccountById(accountId)

        assertEquals(7500.0, updatedDebt!!.remainingAmount, 0.001)
        assertEquals(12500.0, updatedAccount!!.balance, 0.001)
        val payment = repository.allTransactions.first().single()
        assertEquals("EXPENSE", payment.type)
        assertEquals("Debt", payment.category)
        assertEquals(accountId, payment.accountId)
        assertEquals(debtId, payment.debtId)
        assertEquals(2500.0, payment.amount, 0.0)
    }

    @Test
    fun noAccountPaymentLowersDebtWithoutChangingTransactionsOrAccounts() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 500.0))
        val debtId = repository.insertDebt(
            Debt(name = "IOU", kind = "Family", originalAmount = 800.0, remainingAmount = 800.0)
        )
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Debt", amount = 25.0, accountId = wallet))
        val before = repository.allTransactions.first()
        repository.recordDebtPayment(debtId, 200.005, null)
        assertEquals(599.99, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.0)
        assertEquals(before, repository.allTransactions.first())
        assertEquals(475.0, balanceOf(wallet), 0.0)
        repository.recordDebtPayment(debtId, 1000.0, null)
        assertEquals(0.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.0)
        assertEquals(before, repository.allTransactions.first())
    }

    @Test
    fun testBackupExportAndImport() = runBlocking {
        repository.insertAccount(Account(name = "Test Account", kind = "Cash", balance = 1234.0))

        val json = repository.exportBackupJson()
        assertTrue(json.contains("Test Account"))

        repository.clearAllData()
        assertEquals(0, repository.allAccounts.first().size)

        val success = repository.importBackupJson(json)
        assertTrue(success)
        assertEquals(1, repository.allAccounts.first().size)
    }

    @Test
    fun testEditingDebtPaymentKeepsDebtBalance() = runBlocking {
        val accountId = repository.insertAccount(Account(name = "Checking", kind = "Bank", balance = 5000.0))
        val debtId = repository.insertDebt(
            Debt(name = "Loan", kind = "Personal", originalAmount = 3000.0, remainingAmount = 3000.0)
        )
        repository.recordDebtPayment(debtId, 1000.0, accountId)
        val paymentTx = repository.allTransactions.first().single()

        repository.updateTransaction(paymentTx, paymentTx.copy(amount = 1500.0, note = "Edited"))

        assertEquals(1500.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
        assertEquals(3500.0, database.accountDao().getAccountById(accountId)!!.balance, 0.001)
    }

    @Test
    fun testEditingDebtPaymentCapsAmountAndDeletingRestoresBalances() = runBlocking {
        val accountId = repository.insertAccount(Account(name = "Checking", kind = "Bank", balance = 5000.0))
        val debtId = repository.insertDebt(
            Debt(name = "Loan", kind = "Personal", originalAmount = 1000.0, remainingAmount = 1000.0)
        )
        repository.recordDebtPayment(debtId, 500.0, accountId)
        val paymentTx = repository.allTransactions.first().single()

        repository.updateTransaction(paymentTx, paymentTx.copy(amount = 2000.0))

        val saved = repository.allTransactions.first().single()
        assertEquals(1000.0, saved.amount, 0.001)
        assertEquals(0.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
        assertEquals(4000.0, balanceOf(accountId), 0.001)

        repository.deleteTransaction(saved)

        assertEquals(1000.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
        assertEquals(5000.0, balanceOf(accountId), 0.001)
        assertTrue(repository.allTransactions.first().isEmpty())
    }

    @Test
    fun testEditingDebtPaymentDownRestoresUnpaidAmount() = runBlocking {
        val accountId = repository.insertAccount(Account(name = "Checking", kind = "Bank", balance = 5000.0))
        val debtId = repository.insertDebt(
            Debt(name = "Loan", kind = "Personal", originalAmount = 1000.0, remainingAmount = 1000.0)
        )
        repository.recordDebtPayment(debtId, 500.0, accountId)
        val paymentTx = repository.allTransactions.first().single()

        repository.updateTransaction(paymentTx, paymentTx.copy(amount = 200.0))

        assertEquals(200.0, repository.allTransactions.first().single().amount, 0.001)
        assertEquals(800.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
        assertEquals(4800.0, balanceOf(accountId), 0.001)
    }

    @Test
    fun testEditingGoalContributionKeepsGoalBalance() = runBlocking {
        val accountId = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val goalId = repository.insertGoal(Goal(name = "Laptop", targetAmount = 1000.0))
        repository.addMoneyToGoal(goalId, 400.0, accountId)
        val goalTx = repository.allTransactions.first().single()

        repository.updateTransaction(goalTx, goalTx.copy(amount = 1000.0))

        val goal = database.goalDao().getGoalById(goalId)!!
        assertEquals(1000.0, goal.currentAmount, 0.001)
        assertTrue(goal.isCompleted)
        assertEquals(5000.0, database.accountDao().getAccountById(accountId)!!.balance, 0.001)
    }

    @Test
    fun testOverpaymentIsCappedAndDeletingRestoresExactlyWhatWasOwed() = runBlocking {
        val accountId = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        val debtId = repository.insertDebt(
            Debt(name = "IOU", kind = "Family", originalAmount = 800.0, remainingAmount = 800.0)
        )
        repository.recordDebtPayment(debtId, 1000.0, accountId)
        assertEquals(0.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
        assertEquals(800.0, repository.allTransactions.first().single().amount, 0.001)

        repository.deleteTransaction(repository.allTransactions.first().single())

        assertEquals(800.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
    }

    @Test
    fun deletingDebtPaymentRestoresBalancesAndNetWorth() = runBlocking {
        assertDebtPaymentRoundTrip(original = 100.0, remaining = 100.0, payment = 60.0)
    }

    @Test
    fun deletingDebtPaymentRestoresRemainingAboveOriginalAndNetWorth() = runBlocking {
        assertDebtPaymentRoundTrip(original = 100.0, remaining = 150.0, payment = 60.0)
    }

    private suspend fun assertDebtPaymentRoundTrip(original: Double, remaining: Double, payment: Double) {
        val accountId = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 500.0))
        repository.insertInvestment(Investment(name = "Fund", kind = "Mutual Funds", currentValue = 200.0))
        val debtId = repository.insertDebt(
            Debt(name = "Loan", kind = "Personal", originalAmount = original, remainingAmount = remaining)
        )
        val beforeNetWorth = netWorth()
        assertEquals(500.0 + 200.0 - remaining, beforeNetWorth, 0.001)

        repository.recordDebtPayment(debtId, payment, accountId)

        assertEquals(remaining - payment, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
        assertEquals(500.0 - payment, balanceOf(accountId), 0.001)
        assertEquals(beforeNetWorth, netWorth(), 0.001)

        repository.deleteTransaction(repository.allTransactions.first().single())

        assertEquals(remaining, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.001)
        assertEquals(500.0, balanceOf(accountId), 0.001)
        assertEquals(beforeNetWorth, netWorth(), 0.001)
        assertTrue(repository.allTransactions.first().isEmpty())
    }

    private suspend fun netWorth(): Double =
        repository.allAccounts.first().sumOf { it.balance } +
            repository.allInvestments.first().sumOf { it.currentValue } -
            repository.allDebts.first().sumOf { it.remainingAmount }

    @Test
    fun completingGoalFromStaleCardPreservesContribution() = runBlocking {
        assertGoalCompletionPreservesContribution(initiallyCompleted = false)
    }

    @Test
    fun reopeningGoalFromStaleCardPreservesContribution() = runBlocking {
        assertGoalCompletionPreservesContribution(initiallyCompleted = true)
    }

    private suspend fun assertGoalCompletionPreservesContribution(initiallyCompleted: Boolean) {
        val accountId = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 500.0))
        val goalId = repository.insertGoal(
            Goal(name = "Trip", targetAmount = if (initiallyCompleted) 300.0 else 1000.0, currentAmount = 100.0,
                isCompleted = initiallyCompleted, accountId = accountId)
        )
        val staleCard = database.goalDao().getGoalById(goalId)!!
        repository.addMoneyToGoal(goalId, 200.0, accountId)
        val afterContribution = database.goalDao().getGoalById(goalId)!!
        assertEquals(300.0, afterContribution.currentAmount, 0.001)

        repository.setGoalCompleted(staleCard.id, !staleCard.isCompleted)

        val saved = database.goalDao().getGoalById(goalId)!!
        assertEquals(afterContribution.copy(isCompleted = !staleCard.isCompleted), saved)
        assertEquals(300.0, saved.currentAmount, 0.001)
        assertEquals(500.0, balanceOf(accountId), 0.001)
    }

    @Test
    fun testRenamingCategoryOnlyAffectsItsOwnKind() = runBlocking {
        val expenseOther = UserCategory(name = "Other", kind = "EXPENSE")
        val incomeOther = UserCategory(name = "Other", kind = "INCOME")
        val expenseId = repository.insertCategory(expenseOther)
        repository.insertCategory(incomeOther)
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Other", amount = 10.0))
        repository.addTransaction(Transaction(type = "INCOME", category = "Other", amount = 20.0))

        repository.updateCategoryName(expenseOther.copy(id = expenseId), "Misc")

        val txs = repository.allTransactions.first()
        assertEquals("Misc", txs.single { it.type == "EXPENSE" }.category)
        assertEquals("Other", txs.single { it.type == "INCOME" }.category)
    }

    // --- Balance soundness ---

    private suspend fun balanceOf(accountId: Long) = database.accountDao().getAccountById(accountId)!!.balance

    @Test
    fun goalContributionMovesMoneyToGoalAccountWithoutChangingNetWorth() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 10000.0))
        val savings = repository.insertAccount(Account(name = "Savings", kind = "Savings", balance = 0.0))
        val goalId = repository.insertGoal(Goal(name = "Trip", targetAmount = 5000.0, accountId = savings))

        repository.addMoneyToGoal(goalId, 2000.0, wallet)

        assertEquals(8000.0, balanceOf(wallet), 0.001)
        assertEquals(2000.0, balanceOf(savings), 0.001)
        assertEquals(10000.0, repository.allAccounts.first().sumOf { it.balance }, 0.001)
        assertEquals(2000.0, database.goalDao().getGoalById(goalId)!!.currentAmount, 0.001)

        // Deleting the contribution puts everything back
        repository.deleteTransaction(repository.allTransactions.first().single())
        assertEquals(10000.0, balanceOf(wallet), 0.001)
        assertEquals(0.0, balanceOf(savings), 0.001)
        assertEquals(0.0, database.goalDao().getGoalById(goalId)!!.currentAmount, 0.001)
    }

    @Test
    fun deletingTwiceOnlyRevertsOnce() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Food", amount = 200.0, accountId = wallet))
        val tx = repository.allTransactions.first().single()

        repository.deleteTransaction(tx)
        repository.deleteTransaction(tx)

        assertEquals(1000.0, balanceOf(wallet), 0.001)
    }

    @Test
    fun updateRevertsStoredVersionNotCallersStaleCopy() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Food", amount = 100.0, accountId = wallet))
        val original = repository.allTransactions.first().single()
        repository.updateTransaction(original, original.copy(amount = 300.0))

        // A second edit made from the same (now stale) snapshot
        repository.updateTransaction(original, original.copy(amount = 50.0))

        assertEquals(950.0, balanceOf(wallet), 0.001)
    }

    @Test
    fun updatingDeletedTransactionChangesNothing() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Food", amount = 100.0, accountId = wallet))
        val tx = repository.allTransactions.first().single()
        repository.deleteTransaction(tx)

        repository.updateTransaction(tx, tx.copy(amount = 400.0))

        assertEquals(1000.0, balanceOf(wallet), 0.001)
        assertTrue(repository.allTransactions.first().isEmpty())
    }

    @Test
    fun amountsAreKeptToWholeCentavos() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 0.0))
        repeat(10) {
            repository.addTransaction(Transaction(type = "INCOME", category = "Gift", amount = 0.1, accountId = wallet))
        }
        assertEquals(1.0, balanceOf(wallet), 0.0) // exact, no 0.9999999999999999

        val goalId = repository.insertGoal(Goal(name = "Snack", targetAmount = 0.8))
        repository.addMoneyToGoal(goalId, 0.7, null)
        val reached = repository.addMoneyToGoal(goalId, 0.1, null)
        assertTrue(reached)
    }

    @Test
    fun revertingKeepsGoalCompleteWhenStillAboveTarget() = runBlocking {
        val goalId = repository.insertGoal(Goal(name = "Phone", targetAmount = 1000.0, currentAmount = 1500.0, isCompleted = true))
        repository.addMoneyToGoal(goalId, 100.0, null)

        repository.deleteTransaction(repository.allTransactions.first().single())

        val goal = database.goalDao().getGoalById(goalId)!!
        assertEquals(1500.0, goal.currentAmount, 0.001)
        assertTrue(goal.isCompleted)
    }

    @Test
    fun deletingGoalOrDebtDetachesItsTransactions() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 5000.0))
        val goalId = repository.insertGoal(Goal(name = "Bike", targetAmount = 3000.0))
        val debtId = repository.insertDebt(Debt(name = "Loan", kind = "Personal", originalAmount = 1000.0, remainingAmount = 1000.0))
        repository.addMoneyToGoal(goalId, 500.0, wallet)
        repository.recordDebtPayment(debtId, 200.0, wallet)

        repository.deleteGoal(database.goalDao().getGoalById(goalId)!!)
        repository.deleteDebt(database.debtDao().getDebtById(debtId)!!)

        val txs = repository.allTransactions.first()
        assertTrue(txs.all { it.goalId == null && it.debtId == null })
        // Still recognisable as a savings move, so it stays out of spending totals
        assertEquals("IN", txs.single { it.category == "Savings" }.goalFlow)
    }

    @Test
    fun deletingAccountUnlinksGoals() = runBlocking {
        val savings = repository.insertAccount(Account(name = "Savings", kind = "Savings", balance = 0.0))
        val goalId = repository.insertGoal(Goal(name = "Trip", targetAmount = 5000.0, accountId = savings))

        assertTrue(repository.deleteAccount(database.accountDao().getAccountById(savings)!!))

        assertEquals(null, database.goalDao().getGoalById(goalId)!!.accountId)
    }

    @Test
    fun editingAccountDetailsKeepsStoredBalance() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        val staleCopy = database.accountDao().getAccountById(wallet)!!
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Food", amount = 250.0, accountId = wallet))

        repository.updateAccount(staleCopy.copy(name = "Pocket"))

        val acc = database.accountDao().getAccountById(wallet)!!
        assertEquals("Pocket", acc.name)
        assertEquals(750.0, acc.balance, 0.001)
    }

    @Test
    fun copyingBudgetsTwiceDoesNotDuplicate() = runBlocking {
        repository.insertBudget(Budget(category = "Food", limitAmount = 5000.0, monthKey = "2026-09"))
        repository.insertBudget(Budget(category = "Transport", limitAmount = 2000.0, monthKey = "2026-09"))
        repository.insertBudget(Budget(category = "Food", limitAmount = 6000.0, monthKey = "2026-10"))

        assertEquals(1, repository.copyBudgets("2026-09", "2026-10"))
        assertEquals(0, repository.copyBudgets("2026-09", "2026-10"))

        val october = repository.allBudgets.first().filter { it.monthKey == "2026-10" }
        assertEquals(2, october.size)
        assertEquals(6000.0, october.single { it.category == "Food" }.limitAmount, 0.001) // not overwritten
    }

    // --- Subcategories belong to one category kind ---

    @Test
    fun subcategoriesOfSameNamedCategoriesStaySeparate() = runBlocking {
        val expenseOther = UserCategory(name = "Other", kind = "EXPENSE")
        val expenseId = repository.insertCategory(expenseOther)
        repository.insertCategory(UserCategory(name = "Other", kind = "INCOME"))
        val expenseSubId = repository.insertSubcategory(UserSubcategory(parentCategoryName = "Other", name = "Fees", parentKind = "EXPENSE"))
        repository.insertSubcategory(UserSubcategory(parentCategoryName = "Other", name = "Fees", parentKind = "INCOME"))
        repository.addTransaction(Transaction(type = "EXPENSE", category = "Other", subcategory = "Fees", amount = 10.0))
        repository.addTransaction(Transaction(type = "INCOME", category = "Other", subcategory = "Fees", amount = 20.0))

        // Renaming the expense subcategory leaves income transactions alone
        val expenseSub = repository.allSubcategories.first().single { it.id == expenseSubId }
        repository.updateSubcategoryName(expenseSub, "Bank fees")
        var txs = repository.allTransactions.first()
        assertEquals("Bank fees", txs.single { it.type == "EXPENSE" }.subcategory)
        assertEquals("Fees", txs.single { it.type == "INCOME" }.subcategory)

        // Renaming the expense category moves only its own subcategories
        repository.updateCategoryName(expenseOther.copy(id = expenseId), "Misc")
        val subs = repository.allSubcategories.first()
        assertEquals("Misc", subs.single { it.parentKind == "EXPENSE" }.parentCategoryName)
        assertEquals("Other", subs.single { it.parentKind == "INCOME" }.parentCategoryName)
        txs = repository.allTransactions.first()
        assertEquals("Other", txs.single { it.type == "INCOME" }.category)
    }

    // --- Backdated goal and debt moves (P08) ---

    private fun lastMonthNoon(): Long =
        LocalDate.now().minusMonths(1).withDayOfMonth(15).atTime(12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun backdatedGoalContributionKeepsItsDate() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        val goalId = repository.insertGoal(Goal(name = "Trip", targetAmount = 5000.0, currentAmount = 0.0))
        val lastMonth = lastMonthNoon()

        repository.addMoneyToGoal(goalId, 300.0, wallet, lastMonth)

        val tx = repository.allTransactions.first().single()
        assertEquals(lastMonth, tx.dateMillis)
        assertEquals(DateUtil.shiftMonthKey(DateUtil.getCurrentMonthKey(), -1), DateUtil.getMonthKey(tx.dateMillis))
        assertEquals(300.0, database.goalDao().getGoalById(goalId)!!.currentAmount, 0.0)
    }

    @Test
    fun backdatedDebtPaymentKeepsItsDate() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        val debtId = repository.insertDebt(
            Debt(name = "Loan", kind = "Personal", originalAmount = 800.0, remainingAmount = 800.0)
        )
        val lastMonth = lastMonthNoon()

        repository.recordDebtPayment(debtId, 250.0, wallet, lastMonth)

        val tx = repository.allTransactions.first().single()
        assertEquals(lastMonth, tx.dateMillis)
        assertEquals(550.0, database.debtDao().getDebtById(debtId)!!.remainingAmount, 0.0)
        assertEquals(750.0, balanceOf(wallet), 0.0)
    }

    @Test
    fun goalAndDebtMovesDefaultToNow() = runBlocking {
        val wallet = repository.insertAccount(Account(name = "Wallet", kind = "Cash", balance = 1000.0))
        val goalId = repository.insertGoal(Goal(name = "Trip", targetAmount = 5000.0, currentAmount = 0.0))
        val debtId = repository.insertDebt(
            Debt(name = "Loan", kind = "Personal", originalAmount = 800.0, remainingAmount = 800.0)
        )
        val before = System.currentTimeMillis()

        repository.addMoneyToGoal(goalId, 100.0, wallet)
        repository.recordDebtPayment(debtId, 100.0, wallet)

        val after = System.currentTimeMillis()
        repository.allTransactions.first().forEach { tx ->
            assertTrue(tx.dateMillis in before..after)
        }
    }
}
