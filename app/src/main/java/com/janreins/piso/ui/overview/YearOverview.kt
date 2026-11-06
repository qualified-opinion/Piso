package com.janreins.piso.ui.overview

import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.budgets.budgetSpending
import com.janreins.piso.ui.home.summarizeMonth
import com.janreins.piso.util.CurrencyUtil
import com.janreins.piso.util.DateUtil

/** How many spending categories the overview lists for the year. */
const val OVERVIEW_TOP_CATEGORIES = 5

/** One month of the year: income, spending and what was left over, counted the Home way. */
data class OverviewMonth(
    val monthKey: String,
    val income: Double,
    val spent: Double,
    /** Net goal moves (in minus out). Part of what was saved, never spending. */
    val goalMoves: Double,
    val isCurrentMonth: Boolean
) {
    /** Income minus spending. Negative means more went out than came in. */
    val saved: Double get() = CurrencyUtil.roundCents(income - spent)
    val hasEntries: Boolean get() = income != 0.0 || spent != 0.0 || goalMoves != 0.0
}

data class OverviewCategory(val category: String, val spent: Double, val shareOfSpending: Double)

data class YearOverview(
    val year: Int,
    /** January first. A past year has all 12 months; this year stops at this month. */
    val months: List<OverviewMonth>,
    val totalIncome: Double,
    val totalSpent: Double,
    val totalSaved: Double,
    val totalGoalMoves: Double,
    /** Saved as a share of income (0.25 = 25%). Null when there was no income. */
    val savingsRate: Double?,
    /** Spending spread over the months shown, this month included. */
    val averageMonthlySpent: Double,
    /** Largest first, at most [OVERVIEW_TOP_CATEGORIES]. */
    val topCategories: List<OverviewCategory>,
    val monthsSpentMoreThanEarned: Int,
    val isCurrentYear: Boolean,
    /** False for the earliest year with entries (or this year when there are none). */
    val hasPreviousYear: Boolean
)

/** The calendar year of a "yyyy-MM" month key. */
internal fun yearOf(monthKey: String): Int = monthKey.substringBefore('-').toIntOrNull() ?: 0

/**
 * Income, spending and savings per month for [year], like a monthly profit and loss.
 * Income and spending follow the Home "This Month" card ([summarizeMonth]): goal moves,
 * transfers and balance adjustments are neither. Categories follow the Budgets rule ([budgetSpending]).
 */
fun buildYearOverview(
    transactions: List<Transaction>,
    year: Int,
    thisMonthKey: String
): YearOverview {
    val thisYear = yearOf(thisMonthKey)
    val isCurrentYear = year == thisYear
    val lastMonth = when {
        year < thisYear -> 12
        isCurrentYear -> thisMonthKey.substringAfter('-').toIntOrNull()?.coerceIn(1, 12) ?: 12
        else -> 0 // a future year has nothing to show yet
    }
    val monthKeys = (1..lastMonth).map { "$year-${it.toString().padStart(2, '0')}" }

    // Bucket the transactions by month once, then count each month the usual way.
    val txByMonth = transactions.groupBy { DateUtil.getMonthKey(it.dateMillis) }

    val months = monthKeys.map { key ->
        val summary = summarizeMonth(txByMonth[key].orEmpty(), key)
        OverviewMonth(
            monthKey = key,
            income = CurrencyUtil.roundCents(summary.income),
            spent = CurrencyUtil.roundCents(summary.spent),
            goalMoves = CurrencyUtil.roundCents(summary.goalMoves),
            isCurrentMonth = key == thisMonthKey
        )
    }

    val totalIncome = CurrencyUtil.roundCents(months.sumOf { it.income })
    val totalSpent = CurrencyUtil.roundCents(months.sumOf { it.spent })
    val totalSaved = CurrencyUtil.roundCents(totalIncome - totalSpent)

    val categoryTotals = monthKeys
        .flatMap { key -> budgetSpending(txByMonth[key].orEmpty(), key).entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, amounts) -> CurrencyUtil.roundCents(amounts.sum()) }
        .filterValues { it > 0.0 }
    val categorySum = categoryTotals.values.sum()
    val topCategories = categoryTotals.entries
        .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key.lowercase() })
        .take(OVERVIEW_TOP_CATEGORIES)
        .map { OverviewCategory(it.key, it.value, if (categorySum > 0.0) it.value / categorySum else 0.0) }

    val earliestYear = transactions.minOfOrNull { yearOf(DateUtil.getMonthKey(it.dateMillis)) } ?: thisYear

    return YearOverview(
        year = year,
        months = months,
        totalIncome = totalIncome,
        totalSpent = totalSpent,
        totalSaved = totalSaved,
        totalGoalMoves = CurrencyUtil.roundCents(months.sumOf { it.goalMoves }),
        savingsRate = if (totalIncome > 0.0) totalSaved / totalIncome else null,
        averageMonthlySpent = if (months.isEmpty()) 0.0 else CurrencyUtil.roundCents(totalSpent / months.size),
        topCategories = topCategories,
        monthsSpentMoreThanEarned = months.count { it.saved < 0.0 },
        isCurrentYear = isCurrentYear,
        hasPreviousYear = year > minOf(earliestYear, thisYear)
    )
}

/** Steps the year offset (0 = this year, -1 = last year), never into the future. */
internal fun stepYearOffset(offset: Int, delta: Int): Int = (offset + delta).coerceAtMost(0)
