package com.janreins.piso.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Utility functions for formatting and parsing Philippine Peso (₱) amounts.
 */
object CurrencyUtil {

    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    private val pesoFormatter = DecimalFormat("#,##0.00", symbols)

    /**
     * Formats a number as Philippine Peso, e.g. ₱12,500.00 or -₱500.00
     */
    fun formatPeso(amount: Double): String {
        // Round to centavos first so floating-point residue (e.g. -0.000001) doesn't render as "-₱0.00"
        val cents = Math.round(amount * 100)
        val formatted = pesoFormatter.format(kotlin.math.abs(cents) / 100.0)
        return if (cents < 0) "-₱$formatted" else "₱$formatted"
    }

    /**
     * Safely parses a string input into a positive Double amount.
     * Returns null if invalid or <= 0.
     */
    fun parsePositiveAmount(text: String): Double? {
        val value = parseAmount(text) ?: return null
        return if (value > 0) value else null
    }

    /**
     * Parses a string input into a finite Double amount (may be zero or negative).
     * Returns null for blank, malformed, NaN, or infinite input.
     */
    fun parseAmount(text: String): Double? {
        return parseFiniteAmount(text)?.let(::roundCents)
    }

    /** Investment values may reach zero, but negative input is rejected before rounding. */
    fun parseNonNegativeAmount(text: String): Double? {
        return parseFiniteAmount(text)?.takeIf { it >= 0.0 }?.let(::roundCents)
    }

    private fun parseFiniteAmount(text: String): Double? {
        val clean = text.trim().replace(",", "").replace("₱", "")
        val value = clean.toDoubleOrNull() ?: return null
        return value.takeIf { it.isFinite() }
    }

    /**
     * Rounds to whole centavos (half-up). Money is stored as Double, so every stored amount and
     * running balance is rounded to keep binary floating-point residue (e.g. 0.7 + 0.1 =
     * 0.7999999...) from accumulating or tipping comparisons such as "goal reached".
     */
    fun roundCents(amount: Double): Double {
        if (!amount.isFinite()) return amount
        val rounded = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toDouble()
        return if (rounded == 0.0) 0.0 else rounded // normalise -0.0
    }

    /**
     * Formats double with 2 decimal places for text field editing.
     */
    fun formatInputAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", amount)
        } else {
            String.format(Locale.US, "%.2f", amount)
        }
    }
}
