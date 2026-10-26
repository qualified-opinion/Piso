# Batch 11: recurring expenses (U6), database version 5

Jan asked on 2026-10-26 for recurring entries, so fixed costs (rent, subscriptions, a daily fare, a yearly insurance premium) don't have to be typed in every time. Jan settled D5 on 2026-10-26 (option C, per schedule). Jan said go on 2026-10-26.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-co3kep`, from `main` at `518a591` (B10b).
- **Source:** Jan's request in the project thread, 2026-10-26.

## What Jan gets
- A **Recurring** screen under More, listing each schedule with its amount, account, how often it repeats and the next date.
- Add or edit a schedule: expense or income, amount, category and subcategory, account, note, how often (daily, weekly, monthly, yearly, with "every N", for example every 2 weeks), a start date and an optional end date.
- When a schedule's date comes round, Piso records the entry as a normal transaction on that date. It changes the account balance and counts in Budgets and Activity, exactly as if Jan had typed it.
- Pause and resume a schedule, and delete it. Deleting stops future entries only; entries already recorded stay.

## How it works
- **When entries are made.** Piso has no network and no background jobs, so it makes due entries when the main screen shows (the app is opened or unlocked), and after midnight while it is open (`DateUtil.currentEpochDayFlow`, the same once-a-minute check Budgets uses for month-end). A snackbar says "3 recurring entries added". No notifications, so no new permission.
- **Catch-up.** If Piso wasn't opened for a while, every missed date is caught up, each on its own date (rent for October is dated 1 October even if Jan opens the app on 5 October). A schedule is never caught up from before its start date, and never past its end date.
- **No doubles.** Each schedule stores its next due date. Making an entry and moving the next date forward happen in one database transaction, so an entry is never made twice, even if the app is closed halfway.
- **Month-end dates.** A monthly schedule on the 31st uses the last day of shorter months (28 or 29 February, 30 April) and goes back to the 31st after. Yearly on 29 February uses 28 February in other years.
- **Editing.** Changes apply to future entries only. Past entries are ordinary transactions and are edited or deleted from Activity as today. If nothing has been recorded yet, a new start date simply restarts the schedule; otherwise the next date is the first one of the new schedule that isn't already handled.
- **Pause and resume.** Dates that pass while a schedule is paused are not caught up; on resume the next date is the first one from today.
- **Accounts.** If the schedule's account is archived (B10b), the schedule pauses and shows "Account archived". An account with a schedule can't be deleted until the schedule is moved or deleted (the dialog says why).
- **Money.** The amount is rounded to centavos on save and on every entry (AGENTS.md).

## Decision D5: make entries automatically, or ask first?
- **A. Automatic.** Due entries are recorded straight away, with a "3 recurring entries added" message.
- **B. Ask first.** Due entries wait in a "Due" list on Home. Jan taps Record (and can change the amount first) or Skip. Nothing touches balances until he does.
- **C. Choose per schedule (Jan's choice, 2026-10-26).** Each schedule has an "Ask me before recording" switch, off by default. Fixed amounts like rent run on their own; ones that vary, like electricity, wait for Jan to confirm the real amount.

Under B and C, a skipped date is gone for good, and "Due" entries keep their original dates when recorded late.

## Schema (AGENTS.md)
- `MIGRATION_4_5` creates `recurring_rules`:
  `id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, category TEXT NOT NULL, subcategory TEXT NOT NULL, amount REAL NOT NULL, note TEXT NOT NULL, accountId INTEGER, frequency TEXT NOT NULL, intervalCount INTEGER NOT NULL, anchorDay INTEGER NOT NULL, startDay INTEGER NOT NULL, endDay INTEGER, nextDueDay INTEGER NOT NULL, isPaused INTEGER NOT NULL, askFirst INTEGER NOT NULL`.
  Dates are epoch days (`LocalDate.toEpochDay()`), so a schedule is about calendar days, not times; entries are dated at noon on their day. `anchorDay` keeps the 31st after a short month. A catch-up records at most 400 dates per database transaction and repeats until done.
- `transactions` gets `recurringId INTEGER` (nullable, default null), so Activity can show a small "Recurring" tag. Deleting a schedule sets it to null on its past entries, the same way goals and debts detach (`TransactionDao.detachGoal`).
- Commit `app/schemas/com.janreins.piso.data.local.AppDatabase/5.json`.
- `DatabaseMigrationTest`: 4 to 5 keeps all accounts, transactions and balances, existing transactions get a null `recurringId`, and the new table is empty.

## Backup
- Export `recurringRules` and each transaction's `recurringId`. A backup without them (older versions) imports with no schedules and null links. Round-trip tests both ways.
- Restore replaces schedules along with everything else, under the existing safety copy. Clear All Data clears them too.
- CSV export is unchanged, apart from recurring entries appearing as the ordinary transactions they are.

## Tests
- Next-date maths: daily, weekly, every 2 weeks, monthly on the 31st through February and back, yearly on 29 February, end date reached.
- Catch-up: 10 days closed on a daily schedule makes 10 entries with the right dates; running catch-up twice makes no extra entries; a paused schedule makes none; nothing before the start date.
- Balances: an automatic expense lowers the account by the rounded amount and counts in that month's budget spending.
- Under D5 B or C: a due entry changes nothing until recorded; Skip moves the next date without an entry.
- Archived account pauses the schedule; deleting an account with a schedule is refused.
- Deleting a schedule keeps its past entries.

## Phone checks
- Add a daily ₱50 fare starting two days ago, and see three entries (two days ago, yesterday and today) in Activity with the right dates and the wallet ₱150 lower.
- Add monthly rent on the 31st, and see the next date go to 30 November, then 31 December.
- Pause a schedule, wait a day, and see no new entry; resume it.
- Delete a schedule and see its past entries still in Activity.

## Out of scope
Reminders or notifications, recurring transfers, goal contributions and debt payments, and "make this recurring" from an existing transaction. Any of these can follow as a later batch.
