package com.janreins.piso

import com.janreins.piso.data.models.RecurringRule
import com.janreins.piso.util.RecurringSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringScheduleTest {

    private fun rule(
        frequency: String,
        start: LocalDate,
        interval: Int = 1,
        end: LocalDate? = null,
        next: LocalDate = start
    ) = RecurringRule(
        type = "EXPENSE",
        category = "Bills",
        amount = 100.0,
        frequency = frequency,
        intervalCount = interval,
        anchorDay = start.dayOfMonth,
        startDay = start.toEpochDay(),
        endDay = end?.toEpochDay(),
        nextDueDay = next.toEpochDay()
    )

    private fun dates(rule: RecurringRule, today: LocalDate) = RecurringSchedule.dueDates(rule, today)

    @Test
    fun dailyAndWeeklyStepByTheirInterval() {
        val start = LocalDate.of(2026, 10, 1)
        assertEquals(LocalDate.of(2026, 10, 2), RecurringSchedule.nextDate(start, RecurringRule.DAILY, 1, 1))
        assertEquals(LocalDate.of(2026, 10, 8), RecurringSchedule.nextDate(start, RecurringRule.WEEKLY, 1, 1))
        assertEquals(LocalDate.of(2026, 10, 15), RecurringSchedule.nextDate(start, RecurringRule.WEEKLY, 2, 1))
        // An interval below 1 is treated as 1, never as "no step" (which would loop forever).
        assertEquals(LocalDate.of(2026, 10, 2), RecurringSchedule.nextDate(start, RecurringRule.DAILY, 0, 1))
    }

    @Test
    fun monthlyOnThe31stUsesTheLastDayOfShortMonthsAndGoesBack() {
        val rent = rule(RecurringRule.MONTHLY, LocalDate.of(2026, 10, 31))
        val due = dates(rent, LocalDate.of(2027, 3, 31))
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)
            ),
            due
        )
    }

    @Test
    fun monthlyEveryThreeMonths() {
        val quarterly = rule(RecurringRule.MONTHLY, LocalDate.of(2026, 1, 15), interval = 3)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 4, 15), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 10, 15)),
            dates(quarterly, LocalDate.of(2026, 12, 31))
        )
    }

    @Test
    fun yearlyOn29FebruaryUses28FebruaryInOtherYears() {
        val premium = rule(RecurringRule.YEARLY, LocalDate.of(2028, 2, 29))
        assertEquals(
            listOf(LocalDate.of(2028, 2, 29), LocalDate.of(2029, 2, 28), LocalDate.of(2030, 2, 28),
                LocalDate.of(2031, 2, 28), LocalDate.of(2032, 2, 29)),
            dates(premium, LocalDate.of(2032, 3, 1))
        )
    }

    @Test
    fun dueDatesStopAtTodayAndAtTheEndDate() {
        val fare = rule(RecurringRule.DAILY, LocalDate.of(2026, 10, 1), end = LocalDate.of(2026, 10, 5))
        assertEquals(3, dates(fare, LocalDate.of(2026, 10, 3)).size)
        assertEquals(LocalDate.of(2026, 10, 5), dates(fare, LocalDate.of(2026, 12, 1)).last())
        assertEquals(5, dates(fare, LocalDate.of(2026, 12, 1)).size)
        // Nothing before the start date, and nothing when the next date is still ahead.
        assertTrue(dates(fare, LocalDate.of(2026, 9, 30)).isEmpty())
        val later = fare.copy(nextDueDay = LocalDate.of(2026, 10, 4).toEpochDay())
        assertEquals(listOf(LocalDate.of(2026, 10, 4)), dates(later, LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun dueDatesAreCappedPerRun() {
        val longAgo = rule(RecurringRule.DAILY, LocalDate.of(2020, 1, 1))
        assertEquals(RecurringSchedule.MAX_CATCH_UP, dates(longAgo, LocalDate.of(2026, 1, 1)).size)
    }

    @Test
    fun firstDateOnOrAfterSkipsHandledDates() {
        val first = RecurringSchedule.firstDateOnOrAfter(
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1), RecurringRule.MONTHLY, 1, 31
        )
        assertEquals(LocalDate.of(2026, 3, 31), first)
        assertEquals(
            LocalDate.of(2026, 5, 1),
            RecurringSchedule.firstDateOnOrAfter(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1), RecurringRule.DAILY, 1, 1)
        )
    }

    @Test
    fun describeInPlainWords() {
        assertEquals("Monthly", RecurringSchedule.describe(RecurringRule.MONTHLY, 1))
        assertEquals("Every 2 weeks", RecurringSchedule.describe(RecurringRule.WEEKLY, 2))
        assertEquals("Daily", RecurringSchedule.describe(RecurringRule.DAILY, 1))
        assertEquals("Every 3 years", RecurringSchedule.describe(RecurringRule.YEARLY, 3))
    }
}
