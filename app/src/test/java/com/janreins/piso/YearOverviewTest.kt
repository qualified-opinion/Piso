package com.janreins.piso

import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.overview.buildYearOverview
import com.janreins.piso.ui.overview.stepYearOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class YearOverviewTest {

    private fun millisOn(y: Int, m: Int, d: Int): Long =
        LocalDate.of(y, m, d).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun income(y: Int, m: Int, amount: Double) =
        Transaction(dateMillis = millisOn(y, m, 15), type = "INCOME", category = "Salary", amount = amount, accountId = 1)

    private fun expense(y: Int, m: Int, amount: Double, category: String = "Food") =
        Transaction(dateMillis = millisOn(y, m, 10), type = "EXPENSE", category = category, amount = amount, accountId = 1)

    @Test
    fun thisYearStopsAtThisMonthAndPastYearHasTwelveMonths() {
        val txs = listOf(income(2025, 3, 1000.0))
        val thisYear = buildYearOverview(txs, 2026, "2026-11")
        assertEquals(11, thisYear.months.size)
        assertEquals("2026-01", thisYear.months.first().monthKey)
        assertTrue(thisYear.months.last().isCurrentMonth)
        assertTrue(thisYear.isCurrentYear)

        val lastYear = buildYearOverview(txs, 2025, "2026-11")
        assertEquals(12, lastYear.months.size)
        assertEquals("2025-12", lastYear.months.last().monthKey)
        assertFalse(lastYear.isCurrentYear)
        assertFalse(lastYear.months.any { it.isCurrentMonth })
    }

    @Test
    fun monthTotalsAndYearTotalsAddUp() {
        val txs = listOf(
            income(2026, 1, 30000.0),
            expense(2026, 1, 12000.0),
            income(2026, 2, 30000.0),
            expense(2026, 2, 35000.5, "Rent"),
            expense(2025, 12, 999.0) // last year, not counted
        )
        val o = buildYearOverview(txs, 2026, "2026-03")

        val jan = o.months[0]
        assertEquals(30000.0, jan.income, 0.0)
        assertEquals(12000.0, jan.spent, 0.0)
        assertEquals(18000.0, jan.saved, 0.0)
        assertEquals(-5000.5, o.months[1].saved, 0.0)
        assertFalse(o.months[2].hasEntries)

        assertEquals(60000.0, o.totalIncome, 0.0)
        assertEquals(47000.5, o.totalSpent, 0.0)
        assertEquals(12999.5, o.totalSaved, 0.0)
        assertEquals(12999.5 / 60000.0, o.savingsRate!!, 1e-9)
        assertEquals(1, o.monthsSpentMoreThanEarned)
        // 47,000.50 over the 3 months shown
        assertEquals(15666.83, o.averageMonthlySpent, 0.0)
    }

    @Test
    fun goalMovesTransfersAndAdjustmentsAreNeitherIncomeNorSpending() {
        val day = millisOn(2026, 5, 5)
        val txs = listOf(
            income(2026, 5, 1000.0),
            Transaction(dateMillis = day, type = "TRANSFER", category = "Savings", amount = 400.0, accountId = 1, transferToId = 1, goalId = 3, goalFlow = "IN"),
            Transaction(dateMillis = day, type = "TRANSFER", category = "Savings", amount = 100.0, goalId = 3, goalFlow = "OUT"),
            Transaction(dateMillis = day, type = "TRANSFER", category = "Transfer", amount = 700.0, accountId = 1, transferToId = 2),
            Transaction(dateMillis = day, type = "ADJUSTMENT", category = "Balance adjustment", amount = 50.0, accountId = 1)
        )
        val may = buildYearOverview(txs, 2026, "2026-05").months.last()
        assertEquals(1000.0, may.income, 0.0)
        assertEquals(0.0, may.spent, 0.0)
        assertEquals(1000.0, may.saved, 0.0)
        assertEquals(300.0, may.goalMoves, 0.0)
    }

    @Test
    fun centavosAreRoundedSoTinyResidueIsNotALoss() {
        val txs = listOf(income(2026, 1, 0.3), expense(2026, 1, 0.1), expense(2026, 1, 0.2))
        val o = buildYearOverview(txs, 2026, "2026-01")
        assertEquals(0.0, o.months[0].saved, 0.0)
        assertEquals(0, o.monthsSpentMoreThanEarned)
        assertEquals(0.0, o.totalSaved, 0.0)
    }

    @Test
    fun noIncomeMeansNoSavingsRate() {
        val o = buildYearOverview(listOf(expense(2026, 2, 500.0)), 2026, "2026-02")
        assertNull(o.savingsRate)
        assertEquals(-500.0, o.totalSaved, 0.0)
    }

    @Test
    fun topCategoriesAreLargestFirstWithShareAndBlankAsOther() {
        val txs = listOf(
            expense(2026, 1, 600.0, "Rent"),
            expense(2026, 2, 300.0, "Food"),
            expense(2026, 3, 100.0, "Food"),
            expense(2026, 3, 0.0, "Zero"),
            expense(2026, 3, 1000.0, "")
        )
        val cats = buildYearOverview(txs, 2026, "2026-03").topCategories
        assertEquals(listOf("Other", "Rent", "Food"), cats.map { it.category })
        assertEquals(400.0, cats[2].spent, 0.0)
        assertEquals(0.5, cats[0].shareOfSpending, 1e-9)
    }

    @Test
    fun onlyTopFiveCategoriesAreListed() {
        val txs = (1..7).map { expense(2026, 1, it * 10.0, "C$it") }
        val cats = buildYearOverview(txs, 2026, "2026-01").topCategories
        assertEquals(listOf("C7", "C6", "C5", "C4", "C3"), cats.map { it.category })
    }

    @Test
    fun previousYearStopsAtTheEarliestEntryAndNextYearNeverPassesThisYear() {
        val txs = listOf(income(2024, 6, 100.0))
        assertTrue(buildYearOverview(txs, 2026, "2026-11").hasPreviousYear)
        assertTrue(buildYearOverview(txs, 2025, "2026-11").hasPreviousYear)
        assertFalse(buildYearOverview(txs, 2024, "2026-11").hasPreviousYear)
        assertFalse(buildYearOverview(emptyList(), 2026, "2026-11").hasPreviousYear)

        assertEquals(0, stepYearOffset(0, 1))
        assertEquals(-1, stepYearOffset(0, -1))
        assertEquals(0, stepYearOffset(-1, 1))
    }

    @Test
    fun anEmptyYearShowsZeroesNotErrors() {
        val o = buildYearOverview(emptyList(), 2026, "2026-04")
        assertEquals(4, o.months.size)
        assertEquals(0.0, o.totalSaved, 0.0)
        assertEquals(0.0, o.averageMonthlySpent, 0.0)
        assertTrue(o.topCategories.isEmpty())
    }
}
