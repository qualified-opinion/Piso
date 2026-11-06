# Batch 13: category rename for schedules, backup reminder, search all months (U8, U9, U10)

Jan asked on 2026-11-06 for another review of Piso (review 3, in this thread's shortlist). He picked the three small items and said "do what best and fastest". None of them needs a database change.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-6gsfiw`, from `main` at `19febb7` (B12).
- **Why no schema change:** schedules already store their category and subcategory as text, the backup date lives in its own preferences file, and search works on the transactions already loaded.

## Scope

### U8 (bug): categories used by recurring schedules
- Renaming a category also renames it in the schedules of the same kind (expense or income), so their next entries land under the new name and count in its budget. Renaming a subcategory does the same.
- Deleting a category or subcategory that only a schedule uses (no transactions yet) archives it instead of deleting it, the same as one that transactions use.

### U9: backup reminder
- Saving a backup with **Export File** records the date. A CSV export doesn't count, since it can't be restored. Copy Text and Share don't count either, because Piso can't tell whether the copy was kept.
- Settings, under Backup & Restore, shows "Last backup file: Nov 6, 2026 (3 days ago)", or a red "No backup file saved yet" line.
- Home shows "Time to save a backup" when there are entries and the last backup file (or, with none, the oldest entry) is 30 days old or more. "Back up now" opens Settings. "Later (7 days)" hides it for a week. Saving a backup file clears the reminder.
- The date is kept in `piso_backup_status` preferences (excluded from Android backup like all the others). The lock-screen RESET clears it. Clear All Data keeps it, because the backup file still exists.

### U10: search every month in Activity
- When Jan types in Activity's search, it looks through every month, newest first, and says how many matches it found. An "Only <month>" chip narrows the search to the month on screen. With no search text, Activity shows the month on screen as before.
- A new "Adjustments" filter chip lists balance adjustments.

## Tests
- `RecurringRepositoryTest`: renaming a category moves its schedules (and the next recorded entry) to the new name, but not a same-named category of the other kind; renaming a subcategory moves only that parent's schedules; deleting a category or subcategory used only by a schedule archives it, and an unused one is still deleted.
- `BackupReminderTest`: no reminder with no entries; 29 against 30 days, with and without a past backup; a clock set back doesn't remind; "Later" hides it until the snooze ends; the Settings line ("today", "yesterday", "3 days ago"); the store keeps the date after reopening, drops the snooze on a backup, and clears.
- `ActivitySearchTest`: no search stays on the month; a search covers every month and year, any case, and matches account names on either side; it can be narrowed to the month; a blank search stays on the month; type filters including Adjustments.

## Phone checks
- Rename "Bills" to "Utilities" in Manage Categories when an electricity schedule uses Bills. Open Recurring and see Utilities. The next entry shows under Utilities in Activity and in the Utilities budget.
- Settings shows "No backup file saved yet" in red. Tap Export File, save, and see "Last backup file: (today)".
- With entries older than 30 days and no backup, Home shows "Time to save a backup". "Later" hides it. "Back up now" opens Settings.
- In Activity, search for a note from an earlier month and see it listed with its date and "N matches across all months". Tap "Only <month>" and see just this month's matches. Tap Adjustments and see only balance adjustments.

## Out of scope
Automatic backups to a folder, password-protected backup files, and searching a date range.
