package com.janreins.piso

import com.janreins.piso.ui.debts.DebtAmountInputState
import com.janreins.piso.util.CurrencyUtil
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtAmountInputStateTest {
    @Test
    fun typingOriginalKeepsRemainingInSyncThroughSave() {
        var amounts = DebtAmountInputState()
        for (text in listOf("2", "25", "250", "2500", "25000")) {
            amounts = amounts.editOriginal(text)
            assertEquals(text, amounts.remainingText)
        }

        val original = CurrencyUtil.parsePositiveAmount(amounts.originalText)!!
        assertEquals(25000.0, amounts.remainingForSave(original)!!, 0.0)
    }

    @Test
    fun manuallyEditedRemainingStopsSyncing() {
        var amounts = DebtAmountInputState().editOriginal("250")
        amounts = amounts.editRemaining("100")
        amounts = amounts.editOriginal("2500").editOriginal("25000")

        assertEquals("100", amounts.remainingText)
        assertEquals(100.0, amounts.remainingForSave(25000.0)!!, 0.0)
    }

    @Test
    fun clearedRemainingStaysBlankAndFallsBackToOriginalOnSave() {
        val amounts = DebtAmountInputState().editOriginal("250")
            .editRemaining("").editOriginal("25000")

        assertEquals("", amounts.remainingText)
        assertEquals(25000.0, amounts.remainingForSave(25000.0)!!, 0.0)
    }

    @Test
    fun editingExistingDebtPreservesRemaining() {
        val amounts = DebtAmountInputState(
            originalText = "100", remainingText = "150", remainingEdited = true
        ).editOriginal("200")

        assertEquals(150.0, amounts.remainingForSave(200.0)!!, 0.0)
    }
}
