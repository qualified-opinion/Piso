package com.janreins.piso.util

import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Transaction
import java.util.Locale

/**
 * Builds a spreadsheet-friendly CSV of transactions (one row per transaction, oldest first).
 */
object CsvExporter {

    private val HEADER = listOf(
        "Date", "Type", "Category", "Subcategory", "Amount", "Signed Amount",
        "Account", "To Account", "Note", "Linked To"
    )

    fun transactionsToCsv(transactions: List<Transaction>, accounts: List<Account>): String {
        val accountNames = accounts.associate { it.id to it.name }
        val sb = StringBuilder()
        // UTF-8 BOM so Excel opens accented names and notes correctly
        sb.append('\uFEFF')
        sb.append(HEADER.joinToString(",") { escape(it) }).append("\r\n")

        transactions
            .sortedWith(compareBy<Transaction> { it.dateMillis }.thenBy { it.id })
            .forEach { tx ->
                val signed = when (tx.type) {
                    "INCOME" -> tx.amount
                    "EXPENSE" -> -tx.amount
                    else -> 0.0 // transfers move money between own accounts
                }
                val linkedTo = when {
                    tx.goalId != null || tx.goalFlow != null -> "Goal"
                    tx.debtId != null -> "Debt"
                    else -> ""
                }
                val row = listOf(
                    DateUtil.formatInputDate(tx.dateMillis),
                    tx.type,
                    tx.category,
                    tx.subcategory,
                    formatAmount(tx.amount),
                    formatAmount(signed),
                    tx.accountId?.let { accountNames[it] } ?: "",
                    tx.transferToId?.let { accountNames[it] } ?: "",
                    tx.note,
                    linkedTo
                )
                sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
            }
        return sb.toString()
    }

    private fun formatAmount(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return String.format(Locale.US, "%.2f", if (rounded == 0.0) 0.0 else rounded)
    }

    /**
     * Quotes fields containing separators and neutralises leading formula characters so a note
     * like "=HYPERLINK(...)" can't execute when the file is opened in a spreadsheet.
     */
    internal fun escape(raw: String): String {
        val looksNumeric = raw.toDoubleOrNull() != null
        val safe = if (!looksNumeric && raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
