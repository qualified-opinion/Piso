package com.janreins.piso.data.local

import android.content.Context
import android.content.SharedPreferences
import com.janreins.piso.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** When Jan last saved a backup file, and until when the Home reminder is put off. */
data class BackupStatus(
    val lastBackupMillis: Long? = null,
    val snoozedUntilMillis: Long? = null
)

/**
 * Remembers when a backup file was last saved, so Settings can show it and Home can remind
 * Jan when it's been a while. Kept in its own preferences file (excluded from Android backup
 * like everything else), apart from the PIN settings.
 */
class BackupReminderStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("piso_backup_status", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(load())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    private fun load() = BackupStatus(
        lastBackupMillis = prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0L },
        snoozedUntilMillis = prefs.getLong(KEY_SNOOZED_UNTIL, 0L).takeIf { it > 0L }
    )

    /** A backup file was saved: record the time and drop any snooze. */
    fun markBackupSaved(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_BACKUP, nowMillis).remove(KEY_SNOOZED_UNTIL).apply()
        _status.value = load()
    }

    /** "Later" on the Home reminder: don't show it again for [BACKUP_SNOOZE_DAYS] days. */
    fun snooze(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_SNOOZED_UNTIL, nowMillis + BACKUP_SNOOZE_DAYS * DAY_MILLIS).apply()
        _status.value = load()
    }

    /** Used by the full reset, so a new start doesn't show the old backup date. */
    fun clear() {
        prefs.edit().clear().apply()
        _status.value = BackupStatus()
    }

    companion object {
        private const val KEY_LAST_BACKUP = "last_backup_millis"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until_millis"

        @Volatile
        private var INSTANCE: BackupReminderStore? = null

        fun getInstance(context: Context): BackupReminderStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackupReminderStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}

const val BACKUP_REMINDER_DAYS = 30L
const val BACKUP_SNOOZE_DAYS = 7L
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * True when Home should remind Jan to save a backup: there is data, the reminder isn't put off,
 * and the last backup file (or, if there has never been one, the oldest entry) is at least
 * [BACKUP_REMINDER_DAYS] days old.
 */
fun backupReminderDue(status: BackupStatus, oldestEntryMillis: Long?, nowMillis: Long): Boolean {
    if (oldestEntryMillis == null) return false
    val snoozedUntil = status.snoozedUntilMillis
    if (snoozedUntil != null && nowMillis < snoozedUntil) return false
    val since = status.lastBackupMillis ?: oldestEntryMillis
    return nowMillis - since >= BACKUP_REMINDER_DAYS * DAY_MILLIS
}

/** Whole days since [millis], never negative (a clock set back counts as today). */
fun daysSince(millis: Long, nowMillis: Long): Long = maxOf(0L, (nowMillis - millis) / DAY_MILLIS)

/** The line under Backup & Restore, e.g. "Last backup file: Nov 6, 2026 (3 days ago)". */
fun backupStatusText(status: BackupStatus, nowMillis: Long): String {
    val last = status.lastBackupMillis
        ?: return "No backup file saved yet. Export one and keep it somewhere other than this phone."
    val ago = when (val days = daysSince(last, nowMillis)) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }
    return "Last backup file: ${DateUtil.formatDate(last)} ($ago)"
}
