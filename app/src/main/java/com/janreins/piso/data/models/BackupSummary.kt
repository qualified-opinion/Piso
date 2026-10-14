package com.janreins.piso.data.models

import java.text.DateFormat
import java.util.Date

data class BackupSummary(
    val version: Int,
    val exportedAt: Long?,
    val accounts: Int,
    val transactions: Int,
    val budgets: Int,
    val goals: Int,
    val debts: Int,
    val investments: Int
) {
    fun confirmationText(): String {
        val date = exportedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Unknown"
        return "Backup version: $version\nExport date: $date\n\n" +
            "$accounts accounts, $transactions transactions, $budgets budgets, " +
            "$goals goals, $debts debts, $investments investments.\n\n" +
            "This replaces all current Piso data. A private safety backup will be saved so you can undo the restore. Continue?"
    }
}
