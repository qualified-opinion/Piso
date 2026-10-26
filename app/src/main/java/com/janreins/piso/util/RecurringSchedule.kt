package com.janreins.piso.util

import com.janreins.piso.data.models.RecurringRule
import java.time.LocalDate
import java.time.YearMonth

/**
 * Date maths for recurring schedules. Works on calendar days only, so it doesn't depend on the
 * time zone or the time of day.
 */
object RecurringSchedule {

    /**
     * The date after [day] for a schedule that repeats every [intervalCount] [frequency] units.
     * Monthly and yearly dates use [anchorDay], or the last day of a month that is too short,
     * so a schedule on the 31st falls on 30 April and goes back to 31 May.
     */
    fun nextDate(day: LocalDate, frequency: String, intervalCount: Int, anchorDay: Int): LocalDate {
        val step = intervalCount.coerceAtLeast(1).toLong()
        return when (frequency) {
            RecurringRule.DAILY -> day.plusDays(step)
            RecurringRule.WEEKLY -> day.plusWeeks(step)
            RecurringRule.YEARLY -> onAnchorDay(YearMonth.from(day).plusYears(step), anchorDay)
            else -> onAnchorDay(YearMonth.from(day).plusMonths(step), anchorDay)
        }
    }

    fun nextDate(rule: RecurringRule, day: LocalDate): LocalDate =
        nextDate(day, rule.frequency, rule.intervalCount, rule.anchorDay)

    private fun onAnchorDay(month: YearMonth, anchorDay: Int): LocalDate =
        month.atDay(anchorDay.coerceIn(1, month.lengthOfMonth()))

    /**
     * The dates of [rule] from its next due date up to and including [today], oldest first,
     * stopping at the end date. At most [limit] dates, so a schedule started long ago can't run
     * away.
     */
    fun dueDates(rule: RecurringRule, today: LocalDate, limit: Int = MAX_CATCH_UP): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var day = LocalDate.ofEpochDay(rule.nextDueDay)
        val end = rule.endDay?.let(LocalDate::ofEpochDay)
        while (!day.isAfter(today) && (end == null || !day.isAfter(end)) && dates.size < limit) {
            dates.add(day)
            day = nextDate(rule, day)
        }
        return dates
    }

    /**
     * The first date of a schedule starting on [start] that is on or after [notBefore]. Used when
     * a schedule is edited, so dates that were already recorded or skipped aren't repeated.
     */
    fun firstDateOnOrAfter(
        start: LocalDate,
        notBefore: LocalDate,
        frequency: String,
        intervalCount: Int,
        anchorDay: Int
    ): LocalDate {
        var day = start
        while (day.isBefore(notBefore)) {
            day = nextDate(day, frequency, intervalCount, anchorDay)
        }
        return day
    }

    /** Plain words for how often a schedule repeats, e.g. "Monthly" or "Every 2 weeks". */
    fun describe(frequency: String, intervalCount: Int): String {
        val unit = when (frequency) {
            RecurringRule.DAILY -> "day"
            RecurringRule.WEEKLY -> "week"
            RecurringRule.YEARLY -> "year"
            else -> "month"
        }
        return if (intervalCount <= 1) {
            when (frequency) {
                RecurringRule.DAILY -> "Daily"
                RecurringRule.WEEKLY -> "Weekly"
                RecurringRule.YEARLY -> "Yearly"
                else -> "Monthly"
            }
        } else {
            "Every $intervalCount ${unit}s"
        }
    }

    /** A catch-up never records more than this many dates for one schedule at a time. */
    const val MAX_CATCH_UP = 400
}
