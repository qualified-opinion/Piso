package com.janreins.piso

import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.budgets.budgetSpending
import com.janreins.piso.ui.budgets.stepMonthOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BudgetMonthTest {

    private fun millisOn(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun monthOffsetStepsBackButNeverPastTheCurrentMonth() {
        assertEquals(-1, stepMonthOffset(0, -1))
        assertEquals(-3, stepMonthOffset(-2, -1))
        assertEquals(-1, stepMonthOffset(-2, 1))
        assertEquals(0, stepMonthOffset(-1, 1))
        assertEquals(0, stepMonthOffset(0, 1))
    }

    @Test
    fun pastMonthBudgetSpendingCountsOnlyThatMonth() {
        val txs = listOf(
            Transaction(dateMillis = millisOn(LocalDate.of(2026, 9, 10)), type = "EXPENSE", category = "Food", amount = 400.0, accountId = 1),
            Transaction(dateMillis = millisOn(LocalDate.of(2026, 9, 28)), type = "EXPENSE", category = "Transport", amount = 120.0, accountId = 1),
            Transaction(dateMillis = millisOn(LocalDate.of(2026, 10, 2)), type = "EXPENSE", category = "Food", amount = 150.0, accountId = 1)
        )

        assertEquals(mapOf("Food" to 400.0, "Transport" to 120.0), budgetSpending(txs, "2026-09"))
        assertEquals(mapOf("Food" to 150.0), budgetSpending(txs, "2026-10"))
    }
}
