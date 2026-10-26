package com.janreins.piso.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A schedule that records the same income or expense again and again (rent, a subscription,
 * a daily fare). Dates are stored as epoch days (`LocalDate.toEpochDay()`), so a schedule
 * is about calendar days, not times.
 *
 * [nextDueDay] is the next date that hasn't been recorded or skipped yet. Everything before it
 * has been handled, which is what stops an entry from being made twice.
 */
@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // INCOME or EXPENSE
    val category: String,
    val subcategory: String = "",
    val amount: Double, // Always positive
    val note: String = "",
    val accountId: Long? = null,
    val frequency: String, // DAILY, WEEKLY, MONTHLY, YEARLY
    val intervalCount: Int = 1, // "every N" days, weeks, months or years
    // Day of the month the schedule was set up on, so a schedule on the 31st goes back to the
    // 31st after a shorter month.
    val anchorDay: Int,
    val startDay: Long,
    val endDay: Long? = null,
    val nextDueDay: Long,
    val isPaused: Boolean = false,
    // Wait for Jan to record (and maybe change the amount) instead of recording automatically
    val askFirst: Boolean = false
) {
    /** True when the schedule has no more dates: the next one is past the end date. */
    val hasEnded: Boolean get() = endDay != null && nextDueDay > endDay

    companion object {
        const val DAILY = "DAILY"
        const val WEEKLY = "WEEKLY"
        const val MONTHLY = "MONTHLY"
        const val YEARLY = "YEARLY"
        val FREQUENCIES = listOf(DAILY, WEEKLY, MONTHLY, YEARLY)
    }
}
