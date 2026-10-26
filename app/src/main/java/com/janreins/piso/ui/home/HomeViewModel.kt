package com.janreins.piso.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.janreins.piso.data.local.AppDatabase
import com.janreins.piso.data.local.UserProfile
import com.janreins.piso.data.local.UserProfileManager
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Goal
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.data.repository.FinanceRepository
import com.janreins.piso.ui.state.CategorySpendingBreakdown
import com.janreins.piso.ui.state.MonthlySummary
import com.janreins.piso.ui.state.NetWorthSummary
import com.janreins.piso.ui.recurring.DueRecurring
import com.janreins.piso.ui.recurring.dueForConfirmation
import com.janreins.piso.ui.state.SubcategorySplit
import com.janreins.piso.util.DateUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val netWorthSummary: NetWorthSummary = NetWorthSummary(),
    val currentMonthSummary: MonthlySummary = MonthlySummary(),
    val recentTransactions: List<Transaction> = emptyList(),
    val currentMonthSpendingBreakdown: List<CategorySpendingBreakdown> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val activeGoals: List<Goal> = emptyList(),
    val openDebts: List<Debt> = emptyList(),
    val currentMonthCategorySpending: Map<String, Double> = emptyMap(),
    val userProfile: UserProfile = UserProfile(),
    val currentMonthKey: String = DateUtil.getCurrentMonthKey(),
    val dueRecurring: List<DueRecurring> = emptyList(),
    val isEmpty: Boolean = false
)

private data class HomePart1(
    val netWorthSummary: NetWorthSummary,
    val currentMonthSummary: MonthlySummary,
    val recentTransactions: List<Transaction>,
    val currentMonthSpendingBreakdown: List<CategorySpendingBreakdown>
)

private data class HomePart2(
    val accounts: List<Account>,
    val budgets: List<Budget>,
    val activeGoals: List<Goal>,
    val openDebts: List<Debt>,
    val currentMonthCategorySpending: Map<String, Double>
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val profileManager = UserProfileManager.getInstance(application)

    private val _messageEvent = MutableSharedFlow<String>()
    val messageEvent: SharedFlow<String> = _messageEvent.asSharedFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = FinanceRepository(db)
    }

    private val userProfile = profileManager.userProfile

    private val accounts = repository.allAccounts
    private val transactions = repository.allTransactions
    private val recentTransactions = repository.getRecentTransactions(6)
    private val budgets = repository.allBudgets
    private val activeGoals = repository.activeGoals
    private val openDebts = repository.openDebts
    private val investments = repository.allInvestments
    private val debts = repository.allDebts
    private val currentMonthKey = DateUtil.currentMonthKeyFlow()
    private val dueRecurring = combine(repository.allRecurringRules, DateUtil.currentEpochDayFlow()) { rules, today ->
        dueForConfirmation(rules, today)
    }

    private val netWorthSummary = combine(
        accounts,
        investments,
        debts
    ) { accList, invList, debtList ->
        summarizeNetWorth(accList, invList, debtList)
    }

    private val currentMonthSummary = combine(transactions, currentMonthKey) { txList, currentKey ->
        summarizeMonth(txList, currentKey)
    }

    private val currentMonthCategorySpending = combine(transactions, currentMonthKey) { txList, currentKey ->
        val spending = mutableMapOf<String, Double>()
        for (tx in txList) {
            val isGoalMove = tx.goalId != null || tx.goalFlow != null
            if (DateUtil.getMonthKey(tx.dateMillis) == currentKey && tx.type == "EXPENSE" && !isGoalMove) {
                val cat = tx.category.ifBlank { "Other" }
                spending[cat] = (spending[cat] ?: 0.0) + tx.amount
            }
        }
        spending
    }

    private val currentMonthSpendingBreakdown = combine(transactions, currentMonthKey) { txList, currentKey ->
        val expenseTxs = txList.filter {
            DateUtil.getMonthKey(it.dateMillis) == currentKey &&
                it.type == "EXPENSE" &&
                it.goalId == null &&
                it.goalFlow == null
        }

        val grouped = expenseTxs.groupBy { it.category.ifBlank { "Other" } }

        grouped.map { (catName, txs) ->
            val total = txs.sumOf { it.amount }

            val hasSubcategories = txs.any { it.subcategory.isNotBlank() }
            val splits = if (hasSubcategories) {
                val subMap = mutableMapOf<String, Double>()
                var unassigned = 0.0
                for (tx in txs) {
                    val sub = tx.subcategory.trim()
                    if (sub.isNotBlank()) {
                        subMap[sub] = (subMap[sub] ?: 0.0) + tx.amount
                    } else {
                        unassigned += tx.amount
                    }
                }
                val list = subMap.entries
                    .sortedByDescending { it.value }
                    .map { SubcategorySplit(it.key, it.value) }
                    .toMutableList()

                if (unassigned > 0.0) {
                    list.add(SubcategorySplit("Other", unassigned))
                }
                list
            } else {
                emptyList()
            }

            CategorySpendingBreakdown(
                category = catName,
                totalAmount = total,
                subcategories = splits
            )
        }.sortedByDescending { it.totalAmount }
    }

    private val part1Flow = combine(
        netWorthSummary,
        currentMonthSummary,
        recentTransactions,
        currentMonthSpendingBreakdown
    ) { nw, cms, recentTx, breakdown ->
        HomePart1(nw, cms, recentTx, breakdown)
    }

    private val part2Flow = combine(
        accounts,
        budgets,
        activeGoals,
        openDebts,
        currentMonthCategorySpending
    ) { accs, bdgts, goals, dts, catSpending ->
        HomePart2(accs, bdgts, goals, dts, catSpending)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        part1Flow,
        part2Flow,
        userProfile,
        currentMonthKey,
        dueRecurring
    ) { p1, p2, profile, monthKey, due ->
        HomeUiState(
            isLoading = false,
            netWorthSummary = p1.netWorthSummary,
            currentMonthSummary = p1.currentMonthSummary,
            recentTransactions = p1.recentTransactions,
            currentMonthSpendingBreakdown = p1.currentMonthSpendingBreakdown,
            accounts = p2.accounts,
            budgets = p2.budgets,
            activeGoals = p2.activeGoals,
            openDebts = p2.openDebts,
            currentMonthCategorySpending = p2.currentMonthCategorySpending,
            userProfile = profile,
            currentMonthKey = monthKey,
            dueRecurring = due,
            isEmpty = p1.recentTransactions.isEmpty() && p2.accounts.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun recordDueRecurring(due: DueRecurring, amount: Double) {
        viewModelScope.launch {
            val recorded = repository.recordDueRecurring(due.rule.id, due.dueDay, amount)
            if (recorded) showMessage("Recorded for ${DateUtil.formatEpochDay(due.dueDay)}")
        }
    }

    fun skipDueRecurring(due: DueRecurring) {
        viewModelScope.launch {
            val skipped = repository.skipDueRecurring(due.rule.id, due.dueDay)
            if (skipped) showMessage("Skipped ${DateUtil.formatEpochDay(due.dueDay)}")
        }
    }

    fun showMessage(message: String) {
        viewModelScope.launch {
            _messageEvent.emit(message)
        }
    }
}

/**
 * Income, spending and goal moves for the month [monthKey], counted by each transaction's own
 * date. Goal moves are kept out of income and spending, and are net: money taken out of a goal
 * is subtracted from money put in. Balance adjustments count as neither income nor spending.
 */
internal fun summarizeMonth(txList: List<Transaction>, monthKey: String): MonthlySummary {
    var incomeSum = 0.0
    var spentSum = 0.0
    var goalMovesSum = 0.0

    for (tx in txList) {
        if (DateUtil.getMonthKey(tx.dateMillis) == monthKey) {
            val isGoalMove = tx.goalId != null || tx.goalFlow != null
            if (isGoalMove) {
                goalMovesSum += if (tx.goalFlow == "OUT") -tx.amount else tx.amount
            } else if (tx.type == "INCOME") {
                incomeSum += tx.amount
            } else if (tx.type == "EXPENSE") {
                spentSum += tx.amount
            }
        }
    }

    return MonthlySummary(
        income = incomeSum,
        spent = spentSum,
        net = incomeSum - spentSum,
        goalMoves = goalMovesSum
    )
}

/** Net worth from every account, archived or not, so archiving an account never changes it. */
internal fun summarizeNetWorth(
    accounts: List<Account>,
    investments: List<Investment>,
    debts: List<Debt>
): NetWorthSummary {
    val accTotal = accounts.sumOf { it.balance }
    val invTotal = investments.sumOf { it.currentValue }
    val debtTotal = debts.sumOf { it.remainingAmount }
    return NetWorthSummary(
        netWorth = accTotal + invTotal - debtTotal,
        accountsTotal = accTotal,
        investmentsTotal = invTotal,
        debtsTotal = debtTotal
    )
}
