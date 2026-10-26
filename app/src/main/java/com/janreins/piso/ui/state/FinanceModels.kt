package com.janreins.piso.ui.state

enum class MainTab {
    HOME,
    ACTIVITY,
    ACCOUNTS,
    GOALS,
    MORE
}

enum class MoreSubScreen {
    BUDGETS,
    DEBTS,
    INVEST,
    SETTINGS,
    CATEGORIES,
    RECURRING
}

data class MonthlySummary(
    val income: Double = 0.0,
    val spent: Double = 0.0,
    val net: Double = 0.0,
    val goalMoves: Double = 0.0
)

data class NetWorthSummary(
    val netWorth: Double = 0.0,
    val accountsTotal: Double = 0.0,
    val investmentsTotal: Double = 0.0,
    val debtsTotal: Double = 0.0
)

data class SubcategorySplit(
    val name: String,
    val amount: Double
)

data class CategorySpendingBreakdown(
    val category: String,
    val totalAmount: Double,
    val subcategories: List<SubcategorySplit>
)
