package com.janreins.piso

import com.janreins.piso.data.models.Account
import com.janreins.piso.data.models.Transaction
import com.janreins.piso.util.CsvExporter
import com.janreins.piso.util.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun transactionsToCsv_writesOneRowPerTransactionOldestFirst() {
        val accounts = listOf(
            Account(id = 1, name = "Wallet", kind = "Cash", balance = 0.0),
            Account(id = 2, name = "BPI", kind = "Bank", balance = 0.0)
        )
        val newer = Transaction(id = 2, dateMillis = 2_000_000_000_000, type = "EXPENSE", category = "Food", amount = 150.5, accountId = 1)
        val older = Transaction(id = 1, dateMillis = 1_900_000_000_000, type = "INCOME", category = "Salary", amount = 20000.0, accountId = 2)
        val transfer = Transaction(id = 3, dateMillis = 2_100_000_000_000, type = "TRANSFER", category = "Transfer", amount = 500.0, accountId = 2, transferToId = 1)

        val lines = CsvExporter.transactionsToCsv(listOf(newer, older, transfer), accounts)
            .removePrefix("﻿")
            .trimEnd()
            .split("\r\n")

        assertEquals(4, lines.size)
        assertEquals("Date,Type,Category,Subcategory,Amount,Signed Amount,Account,To Account,Note,Linked To", lines[0])
        assertEquals("${DateUtil.formatInputDate(older.dateMillis)},INCOME,Salary,,20000.00,20000.00,BPI,,,", lines[1])
        assertEquals("${DateUtil.formatInputDate(newer.dateMillis)},EXPENSE,Food,,150.50,-150.50,Wallet,,,", lines[2])
        assertTrue(lines[3].contains(",TRANSFER,Transfer,,500.00,0.00,BPI,Wallet,"))
    }

    @Test
    fun adjustmentSignedAmountFollowsDirection() {
        val accounts = listOf(Account(id = 1, name = "Wallet", kind = "Cash", balance = 0.0))
        val up = Transaction(id = 1, dateMillis = 1_900_000_000_000, type = "ADJUSTMENT", category = "Balance adjustment", amount = 120.5, transferToId = 1)
        val down = Transaction(id = 2, dateMillis = 2_000_000_000_000, type = "ADJUSTMENT", category = "Balance adjustment", amount = 40.0, accountId = 1)

        val lines = CsvExporter.transactionsToCsv(listOf(up, down), accounts)
            .removePrefix("\uFEFF")
            .trimEnd()
            .split("\r\n")

        assertTrue(lines[1].contains(",ADJUSTMENT,Balance adjustment,,120.50,120.50,,Wallet,"))
        assertTrue(lines[2].contains(",ADJUSTMENT,Balance adjustment,,40.00,-40.00,Wallet,,"))
    }

    @Test
    fun escape_quotesSeparatorsAndNeutralisesFormulas() {
        assertEquals("\"Lunch, coffee\"", CsvExporter.escape("Lunch, coffee"))
        assertEquals("\"Say \"\"hi\"\"\"", CsvExporter.escape("Say \"hi\""))
        assertEquals("'=1+1", CsvExporter.escape("=1+1"))
        assertEquals("-150.50", CsvExporter.escape("-150.50"))
        assertEquals("Plain", CsvExporter.escape("Plain"))
    }
}
