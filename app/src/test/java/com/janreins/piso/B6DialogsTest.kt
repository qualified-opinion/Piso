package com.janreins.piso

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Debt
import com.janreins.piso.data.models.Goal
import com.janreins.piso.data.models.Investment
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.activity.TransactionDialog
import com.janreins.piso.ui.budgets.BudgetDialog
import com.janreins.piso.ui.debts.DebtDialog
import com.janreins.piso.ui.goals.GoalDialog
import com.janreins.piso.ui.invest.InvestDialog
import com.janreins.piso.util.DateUtil
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B6DialogsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun editingBudgetKeepsItsOwnMonthEvenWhenCallerPassesAnotherMonth() {
        val original = Budget(id = 12, category = "Food", limitAmount = 100.0, monthKey = "2026-09")
        var saved: Budget? = null
        compose.setContent {
            MaterialTheme {
                BudgetDialog(initialBudget = original, monthKey = "2026-10",
                    onDismiss = {}, onSave = { saved = it })
            }
        }
        compose.onNodeWithText("For ${DateUtil.getMonthDisplayName(original.monthKey)}").assertIsDisplayed()
        compose.onNodeWithTag("budget_limit_input").performTextReplacement("200")
        compose.onNodeWithTag("budget_save_button").performClick()
        compose.runOnIdle { assertEquals(original.copy(limitAmount = 200.0), saved) }
    }

    @Test
    fun newBudgetUsesRequestedMonth() {
        var saved: Budget? = null
        compose.setContent {
            MaterialTheme {
                BudgetDialog(monthKey = "2026-10", onDismiss = {}, onSave = { saved = it })
            }
        }
        compose.onNodeWithTag("budget_limit_input").performTextReplacement("100")
        compose.onNodeWithTag("budget_save_button").performClick()
        compose.runOnIdle { assertEquals("2026-10", saved?.monthKey) }
    }

    @Test
    fun investmentRejectsBlankAndNegativeValuesButSavesZero() {
        var saved: Investment? = null
        compose.setContent {
            MaterialTheme {
                InvestDialog(initialInvestment = Investment(name = "Fund", kind = "Stocks", currentValue = 50.0),
                    onDismiss = {}, onSave = { saved = it })
            }
        }
        for (invalid in listOf("", "-1", "-0.001", "NaN", "Infinity")) {
            compose.onNodeWithTag("invest_value_input").performTextReplacement(invalid)
            compose.onNodeWithTag("invest_save_button").performClick()
            compose.runOnIdle { assertNull(saved) }
        }
        compose.onNodeWithTag("invest_value_input").performTextReplacement("₱0")
        compose.onNodeWithTag("invest_save_button").performClick()
        compose.runOnIdle { assertEquals(0.0, requireNotNull(saved).currentValue, 0.0) }
    }

    @Test
    fun editingAPurchaseWithNoAccountDoesNotAssignOne() {
        val wallet = Account(id = 7, name = "Wallet", kind = "Cash", balance = 5000.0)
        val purchase = Transaction(id = 4, type = "EXPENSE", category = "Food", amount = 400.0, accountId = null)
        var saved: Transaction? = null
        compose.setContent {
            MaterialTheme {
                TransactionDialog(
                    initialTransaction = purchase,
                    accounts = listOf(wallet),
                    onDismiss = {},
                    onSave = { saved = it }
                )
            }
        }
        compose.onNodeWithTag("transaction_save_button").performClick()
        compose.runOnIdle {
            val updated = requireNotNull(saved)
            assertNull(updated.accountId)
            assertEquals("EXPENSE", updated.type)
            assertEquals(400.0, updated.amount, 0.0)
        }
    }

    @Test
    fun turningOffPurchaseTrackingIsRefusedWhenPurchasesExist() {
        var saved = false
        val card = Debt(
            id = 3, name = "BPI Card", kind = "Credit Card",
            originalAmount = 50000.0, remainingAmount = 800.0, tracksPurchases = true
        )
        compose.setContent {
            MaterialTheme {
                DebtDialog(
                    initialDebt = card,
                    hasRecordedPurchases = true,
                    onDismiss = {},
                    onSave = { _, _ -> saved = true }
                )
            }
        }
        // The switch is below the fold of the scrolling dialog; scroll to it so the click lands.
        compose.onNodeWithTag("debt_tracks_purchases_switch").performScrollTo().performClick()
        compose.onNodeWithTag("debt_tracks_purchases_switch").assertIsOff()
        compose.onNodeWithTag("debt_save_button").performClick()
        compose.onNodeWithText("This card has purchases recorded", substring = true).assertExists()
        compose.runOnIdle { assertFalse(saved) }
    }

    @Test
    fun debtSaveReportsTheAmountTheDialogOpenedWith() {
        var seenRemaining: Double? = null
        var savedRemaining: Double? = null
        compose.setContent {
            MaterialTheme {
                DebtDialog(
                    initialDebt = Debt(
                        id = 1, name = "Loan", kind = "Personal",
                        originalAmount = 1000.0, remainingAmount = -600.0
                    ),
                    onDismiss = {},
                    onSave = { debt, seen ->
                        savedRemaining = debt.remainingAmount
                        seenRemaining = seen
                    }
                )
            }
        }
        compose.onNodeWithTag("debt_save_button").performClick()
        compose.runOnIdle {
            assertEquals(0.0, requireNotNull(seenRemaining), 0.0)
            assertEquals(0.0, requireNotNull(savedRemaining), 0.0)
        }
    }

    @Test
    fun goalSaveReportsTheAmountTheDialogOpenedWith() {
        var seenCurrent: Double? = null
        compose.setContent {
            MaterialTheme {
                GoalDialog(
                    initialGoal = Goal(id = 2, name = "Phone", targetAmount = 1000.0, currentAmount = 250.0),
                    accounts = emptyList(),
                    onDismiss = {},
                    onSave = { _, seen -> seenCurrent = seen }
                )
            }
        }
        compose.onNodeWithTag("goal_save_button").performClick()
        compose.runOnIdle { assertEquals(250.0, requireNotNull(seenCurrent), 0.0) }
    }
}
