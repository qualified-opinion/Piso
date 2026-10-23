package com.janreins.piso

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.janreins.piso.data.models.Budget
import com.janreins.piso.data.models.Investment
import com.janreins.piso.ui.budgets.BudgetDialog
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
}
