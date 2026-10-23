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
    val date: String
        get() = exportedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Unknown"

    fun confirmationText(isUndo: Boolean = false): String {
        return "Backup version: $version\nExport date: $date\n\n" +
            "$accounts accounts, $transactions transactions, $budgets budgets, " +
            "$goals goals, $debts debts, $investments investments.\n\n" +
            (if (isUndo) "This replaces all current Piso data and uses up this safety copy. Continue?"
            else "This replaces all current Piso data. A private safety backup will be saved so you can undo the restore. Continue?")
    }
}
