package com.janreins.piso

import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.budgets.budgetSpending
import com.janreins.piso.ui.home.summarizeMonth
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MonthSummaryTest {

    private fun millisOn(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun backdatedDebtPaymentCountsInItsOwnMonth() {
        val payment = Transaction(
            dateMillis = millisOn(LocalDate.of(2026, 9, 30)),
            type = "EXPENSE",
            category = "Debt",
            amount = 2500.0,
            accountId = 1,
            debtId = 7
        )
        val lunch = Transaction(
            dateMillis = millisOn(LocalDate.of(2026, 10, 2)),
            type = "EXPENSE",
            category = "Food",
            amount = 150.0,
            accountId = 1
        )

        assertEquals(2500.0, summarizeMonth(listOf(payment, lunch), "2026-09").spent, 0.0)
        assertEquals(150.0, summarizeMonth(listOf(payment, lunch), "2026-10").spent, 0.0)
    }

    @Test
    fun backdatedGoalMoveStaysOutOfIncomeAndSpending() {
        val contribution = Transaction(
            dateMillis = millisOn(LocalDate.of(2026, 9, 15)),
            type = "TRANSFER",
            category = "Savings",
            amount = 300.0,
            accountId = 1,
            transferToId = 1,
            goalId = 3,
            goalFlow = "IN"
        )

        val september = summarizeMonth(listOf(contribution), "2026-09")
        assertEquals(300.0, september.goalMoves, 0.0)
        assertEquals(0.0, september.spent, 0.0)
        assertEquals(0.0, september.income, 0.0)
        assertEquals(0.0, summarizeMonth(listOf(contribution), "2026-10").goalMoves, 0.0)
    }

    @Test
    fun goalWithdrawalIsSubtractedFromGoalMoves() {
        val day = millisOn(LocalDate.of(2026, 10, 5))
        val moves = listOf(
            Transaction(dateMillis = day, type = "TRANSFER", category = "Savings", amount = 800.0, goalId = 3, goalFlow = "IN"),
            Transaction(dateMillis = day, type = "TRANSFER", category = "Savings", amount = 300.0, goalId = 3, goalFlow = "OUT")
        )

        assertEquals(500.0, summarizeMonth(moves, "2026-10").goalMoves, 0.0)
    }

    @Test
    fun adjustmentsStayOutOfIncomeSpendingAndBudgets() {
        val day = millisOn(LocalDate.of(2026, 10, 5))
        val txs = listOf(
            Transaction(dateMillis = day, type = "ADJUSTMENT", category = "Balance adjustment", amount = 120.0, transferToId = 1),
            Transaction(dateMillis = day, type = "ADJUSTMENT", category = "Balance adjustment", amount = 40.0, accountId = 1),
            Transaction(dateMillis = day, type = "EXPENSE", category = "Food", amount = 150.0, accountId = 1)
        )

        val october = summarizeMonth(txs, "2026-10")
        assertEquals(0.0, october.income, 0.0)
        assertEquals(150.0, october.spent, 0.0)
        assertEquals(0.0, october.goalMoves, 0.0)
        assertEquals(mapOf("Food" to 150.0), budgetSpending(txs, "2026-10"))
    }
}
