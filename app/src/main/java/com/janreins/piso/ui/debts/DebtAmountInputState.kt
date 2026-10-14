package com.janreins.piso.ui.debts

import com.janreins.piso.util.CurrencyUtil

/** Keeps a new debt's remaining amount in sync until the user edits it. */
internal data class DebtAmountInputState(
    val originalText: String = "",
    val remainingText: String = "",
    val remainingEdited: Boolean = false
) {
    fun editOriginal(text: String) = copy(
        originalText = text,
        remainingText = if (remainingEdited) remainingText else text
    )

    fun editRemaining(text: String) = copy(remainingText = text, remainingEdited = true)

    fun remainingForSave(parsedOriginal: Double): Double? =
        if (remainingText.isBlank()) parsedOriginal else CurrencyUtil.parseAmount(remainingText)
}
