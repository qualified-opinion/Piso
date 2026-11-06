package com.janreins.piso

import com.janreins.piso.data.models.Transaction
import com.janreins.piso.ui.activity.filterActivity
import com.janreins.piso.util.DateUtil
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivitySearchTest {

    private fun day(y: Int, m: Int, d: Int) = DateUtil.epochDayToMillis(LocalDate.of(y, m, d).toEpochDay())

    private val wallet = 1L
    private val bank = 2L
    private val names = mapOf(wallet to "Wallet", bank to "BPI")

    // Newest first, as Activity receives them.
    private val txs = listOf(
        Transaction(id = 5, dateMillis = day(2026, 11, 3), type = "EXPENSE", category = "Food", note = "Jollibee", amount = 250.0, accountId = wallet),
        Transaction(id = 4, dateMillis = day(2026, 11, 1), type = "ADJUSTMENT", category = "Balance adjustment", amount = 20.0, transferToId = wallet),
        Transaction(id = 3, dateMillis = day(2026, 10, 15), type = "EXPENSE", category = "Food", note = "jollibee lunch", amount = 180.0, accountId = bank),
        Transaction(id = 2, dateMillis = day(2026, 9, 2), type = "INCOME", category = "Salary", amount = 30000.0, accountId = bank),
        Transaction(id = 1, dateMillis = day(2025, 12, 24), type = "TRANSFER", category = "Transfer", amount = 1000.0, accountId = bank, transferToId = wallet)
    )

    private fun ids(
        query: String = "",
        filter: String = "ALL",
        allMonths: Boolean = true,
        monthKey: String = "2026-11"
    ) = filterActivity(txs, monthKey, filter, query, allMonths, names).map { it.id }

    @Test
    fun withoutASearchOnlyTheMonthOnScreenIsListed() {
        assertEquals(listOf(5L, 4L), ids())
        assertEquals(listOf(3L), ids(monthKey = "2026-10"))
    }

    @Test
    fun aSearchLooksThroughEveryMonthNewestFirst() {
        // Matches the note in any case, across months and years.
        assertEquals(listOf(5L, 3L), ids(query = "JOLLIBEE"))
        // Account names count on either side of a transfer or adjustment.
        assertEquals(listOf(5L, 4L, 1L), ids(query = "wallet"))
        assertEquals(listOf(3L, 2L, 1L), ids(query = "BPI"))
        assertEquals(listOf(2L), ids(query = "30000"))
    }

    @Test
    fun aSearchCanBeNarrowedToTheMonthOnScreen() {
        assertEquals(listOf(5L), ids(query = "jollibee", allMonths = false))
        assertEquals(emptyList<Long>(), ids(query = "salary", allMonths = false))
    }

    @Test
    fun aBlankSearchStaysOnTheMonth() {
        assertEquals(listOf(5L, 4L), ids(query = "   "))
    }

    @Test
    fun typeFiltersIncludingAdjustments() {
        assertEquals(listOf(4L), ids(filter = "ADJUSTMENT"))
        assertEquals(listOf(5L), ids(filter = "EXPENSE"))
        assertEquals(listOf(5L, 3L), ids(query = "food", filter = "EXPENSE"))
        assertEquals(listOf(1L), ids(query = "transfer", filter = "TRANSFER"))
        assertEquals(emptyList<Long>(), ids(query = "food", filter = "INCOME"))
    }
}
