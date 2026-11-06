package com.janreins.piso

import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.budgets.buildBudgetHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetHistoryTest {

    private fun millisOn(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun expense(y: Int, m: Int, d: Int, category: String, amount: Double) =
        Transaction(dateMillis = millisOn(LocalDate.of(y, m, d)), type = "EXPENSE", category = category, amount = amount, accountId = 1)

    @Test
    fun periodEndsWithTheGivenMonthAndCrossesTheYear() {
        val history = buildBudgetHistory(emptyList(), emptyList(), "2026-02", 3)
        assertEquals(listOf("2025-12", "2026-01", "2026-02"), history.monthKeys)
    }

    @Test
    fun comparesBudgetWithSpendingPerMonthAndFlagsOverspending() {
        val budgets = listOf(
            Budget(category = "Food", limitAmount = 5000.0, monthKey = "2026-08"),
            Budget(category = "Food", limitAmount = 5000.0, monthKey = "2026-09"),
            Budget(category = "Food", limitAmount = 5000.0, monthKey = "2026-10"),
            Budget(category = "Transport", limitAmount = 2000.0, monthKey = "2026-10")
        )
        val txs = listOf(
            expense(2026, 8, 5, "Food", 4200.0),
            expense(2026, 9, 3, "Food", 3000.0),
            expense(2026, 9, 20, "Food", 2500.50),
            expense(2026, 10, 1, "Food", 6000.0),
            expense(2026, 10, 2, "Transport", 1500.0),
            expense(2026, 7, 30, "Food", 99999.0) // before the period, not counted
        )

        val history = buildBudgetHistory(budgets, txs, "2026-10", 3)

        val food = history.categories.first { it.category == "Food" }
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), food.months.map { it.monthKey })
        assertEquals(1000.0, food.months[0].difference!!, 0.001)
        assertEquals(500.5, food.months[1].difference!!, 0.001)
        assertEquals(-800.0, food.months[2].difference!!, 0.001)
        assertEquals(2, food.monthsOver)
        assertEquals(3, food.monthsBudgeted)
        assertEquals(15000.0, food.totalLimit, 0.001)
        assertEquals(15700.5, food.totalSpentInBudgetedMonths, 0.001)
        // The under-budget month doesn't offset the overspend in the other two
        assertEquals(1500.5, food.totalOverspent, 0.001)

        val transport = history.categories.first { it.category == "Transport" }
        assertEquals(0, transport.monthsOver)
        assertEquals(1, transport.monthsBudgeted)
        assertNull(transport.months[1].limit)

        // Worst overspend first, and the totals add up
        assertEquals("Food", history.categories.first().category)
        assertEquals(1500.5, history.totalOverspent, 0.001)
        assertEquals(2, history.timesOver)
        assertEquals(4, history.budgetedCategoryMonths)
    }

    @Test
    fun monthWithoutBudgetIsNeverCountedAsOver() {
        val budgets = listOf(Budget(category = "Food", limitAmount = 1000.0, monthKey = "2026-10"))
        val txs = listOf(expense(2026, 9, 10, "Food", 5000.0), expense(2026, 10, 10, "Food", 200.0))

        val food = buildBudgetHistory(budgets, txs, "2026-10", 2).categories.single()
        val september = food.months.first { it.monthKey == "2026-09" }
        assertFalse(september.hasBudget)
        assertFalse(september.isOver)
        assertEquals(5000.0, september.spent, 0.001)
        assertEquals(0, food.monthsOver)
        // Totals only compare months that had a budget
        assertEquals(200.0, food.totalSpentInBudgetedMonths, 0.001)
    }

    @Test
    fun spendingExactlyOnBudgetIsNotOver() {
        val budgets = listOf(Budget(category = "Food", limitAmount = 0.3, monthKey = "2026-10"))
        val txs = listOf(expense(2026, 10, 1, "Food", 0.1), expense(2026, 10, 2, "Food", 0.2))

        val month = buildBudgetHistory(budgets, txs, "2026-10", 1).categories.single().months.single()
        assertFalse(month.isOver)
        assertEquals(0.0, month.difference!!, 0.0)
    }

    @Test
    fun goalMovesTransfersAndIncomeDoNotCountAsSpending() {
        val budgets = listOf(Budget(category = "Savings", limitAmount = 100.0, monthKey = "2026-10"))
        val day = millisOn(LocalDate.of(2026, 10, 5))
        val txs = listOf(
            Transaction(dateMillis = day, type = "EXPENSE", category = "Savings", amount = 500.0, accountId = 1, goalId = 7),
            Transaction(dateMillis = day, type = "TRANSFER", category = "Savings", amount = 500.0, accountId = 1),
            Transaction(dateMillis = day, type = "INCOME", category = "Savings", amount = 500.0, accountId = 1)
        )

        val history = buildBudgetHistory(budgets, txs, "2026-10", 1)
        assertEquals(0.0, history.categories.single().months.single().spent, 0.0)
        assertEquals(0, history.timesOver)
        assertTrue(history.unbudgeted.isEmpty())
    }

    @Test
    fun listsSpendingInCategoriesWithNoBudget() {
        val budgets = listOf(Budget(category = "Food", limitAmount = 1000.0, monthKey = "2026-10"))
        val txs = listOf(
            expense(2026, 9, 1, "Shopping", 300.0),
            expense(2026, 10, 1, "Shopping", 450.0),
            expense(2026, 10, 2, "", 80.0),
            expense(2026, 10, 3, "Food", 100.0)
        )

        val history = buildBudgetHistory(budgets, txs, "2026-10", 2)
        assertEquals(listOf("Shopping", "Other"), history.unbudgeted.map { it.category })
        assertEquals(750.0, history.unbudgeted.first().spent, 0.001)
    }
}
