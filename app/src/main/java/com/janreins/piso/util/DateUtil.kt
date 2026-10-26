package com.janreins.piso.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utility functions for date formatting, month keys, and time calculations.
 *
 * Uses java.time formatters, which (unlike SimpleDateFormat) are immutable and safe to share
 * across the coroutine threads that build UI state.
 */
object DateUtil {

    private val monthYearFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
    private val monthKeyFormat = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
    private val fullDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val shortDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val inputDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    private fun zone(): ZoneId = ZoneId.systemDefault()

    private fun toLocalDateTime(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone())

    /**
     * Formats timestamp into readable date (e.g. "Sep 1, 2026")
     */
    fun formatDate(millis: Long): String {
        return fullDateFormat.format(toLocalDateTime(millis))
    }

    /**
     * Formats timestamp into short date (e.g. "Sep 1")
     */
    fun formatDateShort(millis: Long): String {
        return shortDateFormat.format(toLocalDateTime(millis))
    }

    /**
     * Formats timestamp into "yyyy-MM-dd" for date pickers/inputs
     */
    fun formatInputDate(millis: Long): String {
        return inputDateFormat.format(toLocalDateTime(millis))
    }

    /**
     * Returns the current month key, e.g. "2026-09"
     */
    fun getCurrentMonthKey(): String {
        return monthKeyFormat.format(YearMonth.now(zone()))
    }

    /**
     * Emits the current month key now and again whenever the month changes, so screens left
     * open across midnight at month-end move on to the new month. Checks once a minute while
     * collected; collection restarts (and re-reads the clock) when the app comes back to the
     * foreground.
     */
    fun currentMonthKeyFlow(checkIntervalMillis: Long = 60_000L): Flow<String> = flow {
        while (true) {
            emit(getCurrentMonthKey())
            delay(checkIntervalMillis)
        }
    }.distinctUntilChanged()

    /** Today as an epoch day (`LocalDate.toEpochDay()`), the unit recurring schedules use. */
    fun todayEpochDay(): Long = LocalDate.now(zone()).toEpochDay()

    /** Emits today's epoch day now and again after midnight, checking once a minute. */
    fun currentEpochDayFlow(checkIntervalMillis: Long = 60_000L): Flow<Long> = flow {
        while (true) {
            emit(todayEpochDay())
            delay(checkIntervalMillis)
        }
    }.distinctUntilChanged()

    /** The calendar day of a local timestamp, as an epoch day. */
    fun toEpochDay(millis: Long): Long = toLocalDateTime(millis).toLocalDate().toEpochDay()

    /**
     * A timestamp on the epoch day [epochDay], at noon local time. Entries made by a recurring
     * schedule use this, so they sit in the middle of their day.
     */
    fun epochDayToMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atTime(12, 0).atZone(zone()).toInstant().toEpochMilli()

    /** Formats an epoch day like [formatDate], e.g. "Sep 1, 2026". */
    fun formatEpochDay(epochDay: Long): String = fullDateFormat.format(LocalDate.ofEpochDay(epochDay))

    /**
     * Returns the month key for a given timestamp, e.g. "2026-09"
     */
    fun getMonthKey(millis: Long): String {
        return monthKeyFormat.format(toLocalDateTime(millis))
    }

    /**
     * Converts a month key like "2026-09" into "September 2026"
     */
    fun getMonthDisplayName(monthKey: String): String {
        return try {
            monthYearFormat.format(YearMonth.parse(monthKey, monthKeyFormat))
        } catch (_: Exception) {
            monthKey
        }
    }

    /**
     * Shifts monthKey by delta (-1 for previous month, +1 for next month)
     */
    fun shiftMonthKey(monthKey: String, delta: Int): String {
        return try {
            monthKeyFormat.format(YearMonth.parse(monthKey, monthKeyFormat).plusMonths(delta.toLong()))
        } catch (_: Exception) {
            getCurrentMonthKey()
        }
    }

    /**
     * Material date pickers work in UTC midnight millis. Converts a local timestamp to the
     * picker value for the same calendar day.
     */
    fun toPickerUtcMillis(localMillis: Long): Long {
        val date = toLocalDateTime(localMillis).toLocalDate()
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /**
     * Converts a picker value (UTC midnight millis) back to a local timestamp on that calendar
     * day, keeping the time of day from [keepTimeOf] so same-day ordering stays natural.
     */
    fun fromPickerUtcMillis(pickerMillis: Long, keepTimeOf: Long): Long {
        val date: LocalDate = Instant.ofEpochMilli(pickerMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val time: LocalTime = toLocalDateTime(keepTimeOf).toLocalTime()
        return date.atTime(time).atZone(zone()).toInstant().toEpochMilli()
    }

    /**
     * Returns greeting text based on current hour of day
     */
    fun getGreeting(): String {
        val hour = LocalTime.now(zone()).hour
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
