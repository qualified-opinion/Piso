package com.janreins.piso

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.janreins.piso.data.local.BackupReminderStore
import com.janreins.piso.data.local.BackupStatus
import com.janreins.piso.data.local.backupReminderDue
import com.janreins.piso.data.local.backupStatusText
import com.janreins.piso.util.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupReminderTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test
    fun noReminderWithoutAnyEntries() {
        assertFalse(backupReminderDue(BackupStatus(), oldestEntryMillis = null, nowMillis = now))
    }

    @Test
    fun neverBackedUpRemindsOnceTheOldestEntryIs30DaysOld() {
        assertFalse(backupReminderDue(BackupStatus(), oldestEntryMillis = now - 29 * day, nowMillis = now))
        assertTrue(backupReminderDue(BackupStatus(), oldestEntryMillis = now - 30 * day, nowMillis = now))
    }

    @Test
    fun remindsWhenTheLastBackupIs30DaysOld() {
        val old = now - 400 * day
        assertFalse(backupReminderDue(BackupStatus(lastBackupMillis = now - 29 * day), old, now))
        assertTrue(backupReminderDue(BackupStatus(lastBackupMillis = now - 30 * day), old, now))
        // A clock set back before the last backup doesn't remind.
        assertFalse(backupReminderDue(BackupStatus(lastBackupMillis = now + day), old, now))
    }

    @Test
    fun laterHidesTheReminderUntilTheSnoozeEnds() {
        val status = BackupStatus(lastBackupMillis = now - 60 * day, snoozedUntilMillis = now + day)
        assertFalse(backupReminderDue(status, now - 400 * day, now))
        assertTrue(backupReminderDue(status, now - 400 * day, now + day))
    }

    @Test
    fun settingsLineSaysWhenTheLastBackupWas() {
        assertTrue(backupStatusText(BackupStatus(), now).startsWith("No backup file saved yet"))
        val last = now - 3 * day
        assertEquals(
            "Last backup file: ${DateUtil.formatDate(last)} (3 days ago)",
            backupStatusText(BackupStatus(lastBackupMillis = last), now)
        )
        assertTrue(backupStatusText(BackupStatus(lastBackupMillis = now - 1000), now).endsWith("(today)"))
        assertTrue(backupStatusText(BackupStatus(lastBackupMillis = now - day), now).endsWith("(yesterday)"))
    }

    @Test
    fun storeRemembersTheBackupDropsTheSnoozeAndClears() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = BackupReminderStore(context)
        store.clear()

        store.snooze(nowMillis = now)
        assertEquals(now + 7 * day, store.status.value.snoozedUntilMillis)

        store.markBackupSaved(nowMillis = now + day)
        assertEquals(now + day, store.status.value.lastBackupMillis)
        assertNull(store.status.value.snoozedUntilMillis)
        // A new instance (the app reopened) reads the same values.
        assertEquals(now + day, BackupReminderStore(context).status.value.lastBackupMillis)

        store.clear()
        assertEquals(BackupStatus(), store.status.value)
        assertEquals(BackupStatus(), BackupReminderStore(context).status.value)
    }
}
