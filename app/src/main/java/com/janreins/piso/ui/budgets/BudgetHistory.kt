package com.janreins.piso.ui.budgets

import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

/** The history periods Jan can pick, in months. */
val BUDGET_HISTORY_PERIODS = listOf(3, 6, 12)
const val DEFAULT_BUDGET_HISTORY_MONTHS = 6

/** One month of one category: its budget (null if none was set) and what was spent. */
data class BudgetHistoryMonth(
    val monthKey: String,
    val limit: Double?,
    val spent: Double
) {
    val hasBudget: Boolean get() = limit != null
    /** Spent minus budget, so a positive number is an overspend. Null when no budget was set. */
    val difference: Double? get() = limit?.let { CurrencyUtil.roundCents(spent - it) }
    val isOver: Boolean get() = (difference ?: 0.0) > 0.0
}

/** A category that had a budget in at least one month of the period. */
data class BudgetHistoryCategory(
    val category: String,
    /** Newest month first. */
    val months: List<BudgetHistoryMonth>,
    /** Sum of the budgets that were set. */
    val totalLimit: Double,
    /** Spending in the months that had a budget, so it compares like with like. */
    val totalSpentInBudgetedMonths: Double,
    /** Sum of the overspend in each month that went over (months under budget don't offset it). */
    val totalOverspent: Double,
    val monthsOver: Int,
    val monthsBudgeted: Int
)

/** A category with spending in the period but no budget in any of its months. */
data class UnbudgetedSpending(val category: String, val spent: Double)

data class BudgetHistory(
    /** Oldest month first, ending with the newest month in the period. */
    val monthKeys: List<String>,
    /** Worst overspend first. */
    val categories: List<BudgetHistoryCategory>,
    /** Largest first. */
    val unbudgeted: List<UnbudgetedSpending>,
    val totalOverspent: Double,
    val timesOver: Int,
    val budgetedCategoryMonths: Int
)

/**
 * Budget against spending per category over the [months] months ending with [endMonthKey].
 * Spending is counted the same way as on the Budgets screen ([budgetSpending]).
 */
fun buildBudgetHistory(
    budgets: List<Budget>,
    transactions: List<Transaction>,
    endMonthKey: String,
    months: Int
): BudgetHistory {
    val count = months.coerceAtLeast(1)
    val monthKeys = (count - 1 downTo 0).map { DateUtil.shiftMonthKey(endMonthKey, -it) }
    val monthSet = monthKeys.toSet()

    // Bucket the transactions by month once, then count each month the Budgets way.
    val txByMonth = transactions.groupBy { DateUtil.getMonthKey(it.dateMillis) }
    val spendingByMonth = monthKeys.associateWith { key -> budgetSpending(txByMonth[key].orEmpty(), key) }

    // If a category somehow has two budgets in one month, add them up, as the total card does.
    val limitByMonth: Map<String, Map<String, Double>> = budgets
        .filter { it.monthKey in monthSet }
        .groupBy { it.monthKey }
        .mapValues { (_, list) ->
            list.groupBy { it.category }.mapValues { (_, b) -> CurrencyUtil.roundCents(b.sumOf { it.limitAmount }) }
        }

    val budgetedCategories = limitByMonth.values.flatMap { it.keys }.toSet()

    val categories = budgetedCategories.map { category ->
        val rows = monthKeys.reversed().map { key ->
            BudgetHistoryMonth(
                monthKey = key,
                limit = limitByMonth[key]?.get(category),
                spent = CurrencyUtil.roundCents(spendingByMonth[key]?.get(category) ?: 0.0)
            )
        }
        val budgeted = rows.filter { it.hasBudget }
        BudgetHistoryCategory(
            category = category,
            months = rows,
            totalLimit = CurrencyUtil.roundCents(budgeted.sumOf { it.limit ?: 0.0 }),
            totalSpentInBudgetedMonths = CurrencyUtil.roundCents(budgeted.sumOf { it.spent }),
            totalOverspent = CurrencyUtil.roundCents(budgeted.filter { it.isOver }.sumOf { it.difference ?: 0.0 }),
            monthsOver = budgeted.count { it.isOver },
            monthsBudgeted = budgeted.size
        )
    }.sortedWith(
        compareByDescending<BudgetHistoryCategory> { it.totalOverspent }
            .thenByDescending { it.monthsOver }
            .thenBy { it.category.lowercase() }
    )

    val unbudgeted = spendingByMonth.values
        .flatMap { it.entries }
        .filter { it.key !in budgetedCategories }
        .groupBy({ it.key }, { it.value })
        .map { (category, amounts) -> UnbudgetedSpending(category, CurrencyUtil.roundCents(amounts.sum())) }
        .filter { it.spent > 0.0 }
        .sortedByDescending { it.spent }

    return BudgetHistory(
        monthKeys = monthKeys,
        categories = categories,
        unbudgeted = unbudgeted,
        totalOverspent = CurrencyUtil.roundCents(categories.sumOf { it.totalOverspent }),
        timesOver = categories.sumOf { it.monthsOver },
        budgetedCategoryMonths = categories.sumOf { it.monthsBudgeted }
    )
}
