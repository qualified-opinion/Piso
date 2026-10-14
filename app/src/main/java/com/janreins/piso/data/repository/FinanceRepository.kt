package com.janreins.piso.data.repository

import androidx.room.withTransaction
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.BackupData
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Categories
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Goal
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.models.UserCategory
import com.janreins.piso.data.models.UserSubcategory
import com.janreins.piso.util.CurrencyUtil.roundCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.math.max

/**
 * Single source of truth repository managing all financial operations and live account balances.
 */
class FinanceRepository(
    private val database: AppDatabase,
    private val backupSafety: BackupSafety? = null
) {

    private val accountDao = database.accountDao()
    private val transactionDao = database.transactionDao()
    private val budgetDao = database.budgetDao()
    private val goalDao = database.goalDao()
    private val debtDao = database.debtDao()
    private val investmentDao = database.investmentDao()
    private val categoryDao = database.categoryDao()

    // --- Flows ---
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()
    val allGoals: Flow<List<Goal>> = goalDao.getAllGoals()
    val activeGoals: Flow<List<Goal>> = goalDao.getActiveGoals()
    val allDebts: Flow<List<Debt>> = debtDao.getAllDebts()
    val openDebts: Flow<List<Debt>> = debtDao.getOpenDebts()
    val allInvestments: Flow<List<Investment>> = investmentDao.getAllInvestments()
    val allCategories: Flow<List<UserCategory>> = categoryDao.getAllCategories()
    val allSubcategories: Flow<List<UserSubcategory>> = categoryDao.getAllSubcategories()

    fun getRecentTransactions(limit: Int = 6): Flow<List<Transaction>> =
        transactionDao.getRecentTransactions(limit)

    fun getBudgetsForMonth(monthKey: String): Flow<List<Budget>> =
        budgetDao.getBudgetsForMonth(monthKey)

    fun getCategoriesByKind(kind: String): Flow<List<UserCategory>> =
        categoryDao.getCategoriesByKind(kind)

    fun getSubcategoriesForParent(parentName: String, parentKind: String): Flow<List<UserSubcategory>> =
        categoryDao.getSubcategoriesForParent(parentName, parentKind)

    // --- Seeding Default Categories ---
    suspend fun seedDefaultCategoriesIfEmpty() {
        val existing = categoryDao.getAllCategories().first()
        if (existing.isEmpty()) {
            val expenseCats = Categories.EXPENSE.map {
                UserCategory(name = it, kind = "EXPENSE", isArchived = false)
            }
            val incomeCats = Categories.INCOME.map {
                UserCategory(name = it, kind = "INCOME", isArchived = false)
            }
            categoryDao.insertCategories(expenseCats + incomeCats)

            val defaultFoodSubcategories = listOf(
                UserSubcategory(parentCategoryName = "Food", name = "Groceries", parentKind = "EXPENSE"),
                UserSubcategory(parentCategoryName = "Food", name = "Dining out", parentKind = "EXPENSE"),
                UserSubcategory(parentCategoryName = "Food", name = "Coffee", parentKind = "EXPENSE")
            )
            categoryDao.insertSubcategories(defaultFoodSubcategories)
        }
    }

    // --- User Category CRUD ---
    suspend fun insertCategory(category: UserCategory): Long =
        categoryDao.insertCategory(category)

    suspend fun updateCategoryName(category: UserCategory, newName: String) {
        val trimmedNewName = newName.trim()
        if (trimmedNewName.isEmpty() || trimmedNewName == category.name) return
        val kind = category.kind.uppercase()
        database.withTransaction {
            // A category name can exist for both kinds (e.g. "Other"), so only touch
            // subcategories, transactions and budgets of this category's own kind.
            categoryDao.updateCategory(category.copy(name = trimmedNewName))
            categoryDao.updateSubcategoriesParentName(category.name, trimmedNewName, kind)
            categoryDao.updateTransactionsCategoryNameForType(category.name, trimmedNewName, kind)
            if (kind == "EXPENSE") {
                categoryDao.updateBudgetsCategoryName(category.name, trimmedNewName)
            }
        }
    }

    suspend fun setCategoryArchived(category: UserCategory, isArchived: Boolean) {
        categoryDao.updateCategory(category.copy(isArchived = isArchived))
    }

    suspend fun deleteCategory(category: UserCategory) {
        database.withTransaction {
            val count = categoryDao.countTransactionsForCategoryAndType(category.name, category.kind.uppercase())
            if (count > 0) {
                // If transactions use this category, archive it to keep historical data intact
                categoryDao.updateCategory(category.copy(isArchived = true))
            } else {
                categoryDao.deleteCategory(category)
                // Otherwise they'd reappear under a new category that reuses the name
                categoryDao.deleteSubcategoriesForParent(category.name, category.kind.uppercase())
            }
        }
    }

    // --- User Subcategory CRUD ---
    suspend fun insertSubcategory(subcategory: UserSubcategory): Long =
        categoryDao.insertSubcategory(subcategory)

    suspend fun updateSubcategoryName(subcategory: UserSubcategory, newName: String) {
        val trimmedNewName = newName.trim()
        if (trimmedNewName.isEmpty() || trimmedNewName == subcategory.name) return
        database.withTransaction {
            categoryDao.updateSubcategory(subcategory.copy(name = trimmedNewName))
            categoryDao.updateTransactionsSubcategoryName(
                subcategory.parentCategoryName,
                subcategory.name,
                trimmedNewName,
                subcategory.parentKind.uppercase()
            )
        }
    }

    suspend fun setSubcategoryArchived(subcategory: UserSubcategory, isArchived: Boolean) {
        categoryDao.updateSubcategory(subcategory.copy(isArchived = isArchived))
    }

    suspend fun deleteSubcategory(subcategory: UserSubcategory) {
        database.withTransaction {
            val count = categoryDao.countTransactionsForSubcategory(
                subcategory.parentCategoryName,
                subcategory.name,
                subcategory.parentKind.uppercase()
            )
            if (count > 0) {
                // Archive if referenced
                categoryDao.updateSubcategory(subcategory.copy(isArchived = true))
            } else {
                categoryDao.deleteSubcategory(subcategory)
            }
        }
    }

    // --- Accounts ---
    suspend fun insertAccount(account: Account): Long =
        accountDao.insertAccount(account.copy(balance = roundCents(account.balance)))

    /**
     * Updates an account's name, kind and notes. The balance is owned by the transaction logic,
     * so it is taken from the database rather than from the (possibly stale) caller copy.
     */
    suspend fun updateAccount(account: Account) {
        database.withTransaction {
            val stored = accountDao.getAccountById(account.id) ?: return@withTransaction
            accountDao.updateAccount(account.copy(balance = stored.balance))
        }
    }

    suspend fun hasTransactionsForAccount(accountId: Long): Boolean =
        transactionDao.getTransactionCountForAccount(accountId) > 0

    suspend fun deleteAccount(account: Account): Boolean {
        return database.withTransaction {
            if (hasTransactionsForAccount(account.id)) {
                return@withTransaction false
            }
            accountDao.deleteAccount(account)
            goalDao.detachAccount(account.id)
            true
        }
    }

    // --- Transactions & Balance Logic ---
    //
    // Account balances, goal progress and debt balances are stored values kept in step with
    // the transactions table: every insert applies a transaction's effect, every delete reverts
    // it, and an edit reverts the stored version before applying the new one. All of it runs
    // inside a database transaction so a failure can't leave half an update behind.

    suspend fun addTransaction(tx: Transaction): Long {
        val normalized = tx.copy(amount = roundCents(tx.amount))
        return database.withTransaction {
            applyTransactionBalance(normalized)
            transactionDao.insertTransaction(normalized)
        }
    }

    suspend fun updateTransaction(oldTx: Transaction, newTx: Transaction) {
        database.withTransaction {
            // Revert what is actually stored, not the caller's copy, which may be stale.
            // If the row is gone (already deleted) there is nothing to update.
            val stored = transactionDao.getTransactionById(oldTx.id) ?: return@withTransaction
            revertTransactionBalance(stored)
            // Cap against the restored debt balance so the saved payment can be fully reverted.
            val debt = newTx.debtId?.let { debtDao.getDebtById(it) }
            val amount = debt?.let { minOf(newTx.amount, it.remainingAmount) } ?: newTx.amount
            val normalized = newTx.copy(id = stored.id, amount = roundCents(amount))
            applyTransactionBalance(normalized)
            transactionDao.updateTransaction(normalized)
        }
    }

    suspend fun deleteTransaction(tx: Transaction) {
        database.withTransaction {
            // Re-read so a repeated delete (e.g. a double tap) can't revert the effect twice
            val stored = transactionDao.getTransactionById(tx.id) ?: return@withTransaction
            revertTransactionBalance(stored)
            transactionDao.deleteTransaction(stored)
        }
    }

    private suspend fun adjustAccount(accountId: Long?, delta: Double) {
        if (accountId == null) return
        val acc = accountDao.getAccountById(accountId) ?: return
        accountDao.updateAccount(acc.copy(balance = roundCents(acc.balance + delta)))
    }

    private suspend fun applyTransactionBalance(tx: Transaction) = applyEffect(tx, sign = 1.0)

    private suspend fun revertTransactionBalance(tx: Transaction) = applyEffect(tx, sign = -1.0)

    /**
     * Applies (sign = 1) or reverts (sign = -1) a transaction's effect on accounts, goals and
     * debts.
     */
    private suspend fun applyEffect(tx: Transaction, sign: Double) {
        val amount = tx.amount
        when (tx.type) {
            "INCOME" -> adjustAccount(tx.accountId, sign * amount)
            "EXPENSE" -> adjustAccount(tx.accountId, -sign * amount)
            "TRANSFER" -> {
                adjustAccount(tx.accountId, -sign * amount)
                adjustAccount(tx.transferToId, sign * amount)
            }
        }

        // Debt payment: reduces what's owed, never below zero. Reverting adds the payment back
        // without capping at the original principal (the balance may include interest or fees).
        tx.debtId?.let { debtId ->
            val debt = debtDao.getDebtById(debtId) ?: return@let
            val remaining = if (sign > 0) {
                max(0.0, debt.remainingAmount - amount)
            } else {
                debt.remainingAmount + amount
            }
            debtDao.updateDebt(debt.copy(remainingAmount = roundCents(remaining)))
        }

        // Goal contribution ("IN") or withdrawal ("OUT")
        if (tx.goalId != null && (tx.goalFlow == "IN" || tx.goalFlow == "OUT")) {
            val goal = goalDao.getGoalById(tx.goalId)
            if (goal != null) {
                val direction = if (tx.goalFlow == "IN") 1.0 else -1.0
                val newAmount = roundCents(max(0.0, goal.currentAmount + sign * direction * amount))
                goalDao.updateGoal(goal.copy(currentAmount = newAmount, isCompleted = newAmount >= goal.targetAmount))
            }
        }
    }

    // --- Goals ---
    suspend fun insertGoal(goal: Goal): Long = goalDao.insertGoal(normalizeGoal(goal))
    suspend fun updateGoal(goal: Goal) = goalDao.updateGoal(normalizeGoal(goal))
    suspend fun setGoalCompleted(goalId: Long, done: Boolean) = goalDao.setGoalCompleted(goalId, done)

    suspend fun deleteGoal(goal: Goal) {
        database.withTransaction {
            // Past contributions stay in Activity (still marked as savings moves) but no longer
            // point at the deleted goal.
            transactionDao.detachGoal(goal.id)
            goalDao.deleteGoal(goal)
        }
    }

    private fun normalizeGoal(goal: Goal) = goal.copy(
        targetAmount = roundCents(goal.targetAmount),
        currentAmount = roundCents(goal.currentAmount)
    )

    /**
     * Records a contribution to a goal. A goal is an allocation of money, not a separate asset,
     * so a contribution never makes money disappear from net worth:
     *  - If the goal's savings are kept in a different account than [fromAccountId], the
     *    money moves between the two accounts (a transfer).
     *  - Otherwise (no linked account, the same account, or no source) the money stays where it
     *    is and is only set aside for the goal.
     *
     * Returns true when the goal is reached.
     */
    suspend fun addMoneyToGoal(goalId: Long, amount: Double, fromAccountId: Long?): Boolean {
        return database.withTransaction {
            val goal = goalDao.getGoalById(goalId) ?: return@withTransaction false
            val source = fromAccountId?.takeIf { accountDao.getAccountById(it) != null }
            val goalAccount = goal.accountId?.takeIf { accountDao.getAccountById(it) != null }
            val destination = if (source != null && goalAccount != null && goalAccount != source) goalAccount else source

            val tx = Transaction(
                dateMillis = System.currentTimeMillis(),
                type = "TRANSFER",
                category = "Savings",
                subcategory = "",
                amount = roundCents(amount),
                note = "Added to ${goal.name}",
                accountId = source,
                transferToId = destination, // same as source when the money is only set aside
                goalId = goalId,
                goalFlow = "IN"
            )
            applyTransactionBalance(tx)
            transactionDao.insertTransaction(tx)
            goalDao.getGoalById(goalId)?.isCompleted == true
        }
    }

    // --- Debts ---
    suspend fun insertDebt(debt: Debt): Long = debtDao.insertDebt(normalizeDebt(debt))
    suspend fun updateDebt(debt: Debt) = debtDao.updateDebt(normalizeDebt(debt))

    suspend fun deleteDebt(debt: Debt) {
        database.withTransaction {
            // Payments stay as ordinary "Debt" expenses; detaching them stops a later debt that
            // reuses this id (possible after a backup restore) from being changed by them.
            transactionDao.detachDebt(debt.id)
            debtDao.deleteDebt(debt)
        }
    }

    private fun normalizeDebt(debt: Debt) = debt.copy(
        originalAmount = roundCents(debt.originalAmount),
        remainingAmount = roundCents(debt.remainingAmount)
    )

    suspend fun recordDebtPayment(debtId: Long, amount: Double, fromAccountId: Long?) {
        database.withTransaction {
            val debt = debtDao.getDebtById(debtId) ?: return@withTransaction
            // Never record more than is owed, so the transaction amount is exactly what the
            // payment took off the debt and deleting it adds back exactly that.
            val applied = roundCents(minOf(amount, debt.remainingAmount))
            if (applied <= 0.0) return@withTransaction
            val tx = Transaction(
                dateMillis = System.currentTimeMillis(),
                type = "EXPENSE",
                category = "Debt",
                subcategory = "",
                amount = applied,
                note = "Payment for ${debt.name}",
                accountId = fromAccountId,
                debtId = debtId
            )
            applyTransactionBalance(tx)
            transactionDao.insertTransaction(tx)
        }
    }

    // --- Budgets ---
    suspend fun insertBudget(budget: Budget): Long =
        budgetDao.insertBudget(budget.copy(limitAmount = roundCents(budget.limitAmount)))
    suspend fun updateBudget(budget: Budget) =
        budgetDao.updateBudget(budget.copy(limitAmount = roundCents(budget.limitAmount)))

    /**
     * Copies [fromMonthKey]'s budgets into [toMonthKey] for categories that have no budget there
     * yet. Runs in one database transaction, so a repeated tap can't create duplicates.
     * Returns the number of budgets copied.
     */
    suspend fun copyBudgets(fromMonthKey: String, toMonthKey: String): Int {
        return database.withTransaction {
            val all = budgetDao.getAllBudgets().first()
            val alreadySet = all.filter { it.monthKey == toMonthKey }.map { it.category }.toSet()
            val toCopy = all.filter { it.monthKey == fromMonthKey && it.category !in alreadySet }
            toCopy.forEach { budgetDao.insertBudget(it.copy(id = 0, monthKey = toMonthKey)) }
            toCopy.size
        }
    }
    suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    // --- Investments ---
    suspend fun insertInvestment(investment: Investment): Long =
        investmentDao.insertInvestment(investment.copy(currentValue = roundCents(investment.currentValue)))
    suspend fun updateInvestment(investment: Investment) =
        investmentDao.updateInvestment(investment.copy(currentValue = roundCents(investment.currentValue)))
    suspend fun deleteInvestment(investment: Investment) = investmentDao.deleteInvestment(investment)

    // --- Backup & Restore & Clear ---
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        database.withTransaction { readBackupData().toJsonString() }
    }

    private suspend fun readBackupData() = BackupData(
        accounts = accountDao.getAllAccounts().first(),
        transactions = transactionDao.getAllTransactions().first(),
        budgets = budgetDao.getAllBudgets().first(),
        goals = goalDao.getAllGoals().first(),
        debts = debtDao.getAllDebts().first(),
        investments = investmentDao.getAllInvestments().first(),
        categories = categoryDao.getAllCategories().first(),
        subcategories = categoryDao.getAllSubcategories().first()
    )

    suspend fun importBackupJson(jsonString: String): Boolean {
        val data = BackupData.fromJsonString(jsonString) ?: return false
        return restoreBackup { data }
    }

    suspend fun hasUndoRestore(): Boolean = withContext(Dispatchers.IO) {
        backupSafety?.latestBackup() != null
    }

    suspend fun undoRestore(): Boolean = restoreBackup {
        backupSafety?.latestBackup()?.readText(Charsets.UTF_8)?.let(BackupData::fromJsonString)
    }

    private suspend fun restoreBackup(load: () -> BackupData?): Boolean = withContext(Dispatchers.IO) {
        // Fail closed if this repository has not been configured with app-private storage.
        val safety = checkNotNull(backupSafety) { "Safety backup storage is required for restore" }
        var saved: File? = null
        val success = try {
            database.withTransaction {
                val data = load() ?: return@withTransaction false
                // Keep the snapshot and replacement in the same transaction so writes cannot
                // intervene. A filesystem failure happens before any DELETE or INSERT.
                saved = safety.save(readBackupData().toJsonString())
                replaceBackupData(data)
                true
            }
        } catch (failure: Throwable) {
            // The DB rolled back; do not make a failed attempt the latest undo target.
            saved?.delete()
            throw failure
        }
        if (success) safety.prune()
        success
    }

    private suspend fun replaceBackupData(data: BackupData) {
        accountDao.clearAccounts()
        transactionDao.clearTransactions()
        budgetDao.clearBudgets()
        goalDao.clearGoals()
        debtDao.clearDebts()
        investmentDao.clearInvestments()

        accountDao.insertAccounts(data.accounts)
        transactionDao.insertTransactions(data.transactions)
        budgetDao.insertBudgets(data.budgets)
        goalDao.insertGoals(data.goals)
        debtDao.insertDebts(data.debts)
        investmentDao.insertInvestments(data.investments)

        if (data.hasCategoryData) {
            categoryDao.clearCategories()
            categoryDao.clearSubcategories()
            categoryDao.insertCategories(data.categories)
            categoryDao.insertSubcategories(data.subcategories)
        } else {
            seedDefaultCategoriesIfEmpty()
        }
    }

    suspend fun clearAllData() {
        database.withTransaction {
            accountDao.clearAccounts()
            transactionDao.clearTransactions()
            budgetDao.clearBudgets()
            goalDao.clearGoals()
            debtDao.clearDebts()
            investmentDao.clearInvestments()
            // Reset categories to defaults
            categoryDao.clearCategories()
            categoryDao.clearSubcategories()
            seedDefaultCategoriesIfEmpty()
        }
    }
}
