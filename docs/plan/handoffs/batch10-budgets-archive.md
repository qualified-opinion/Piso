# Batch 10: past-month budgets (U3) and account archive (U2)

Jan chose both on 2026-10-24 (D4). They ship as two PRs, because only the second changes the database.

- **Repo:** `qualified-opinion/Piso`
- **Source:** review 2 §3 items 3 and 4, and D4 in `ROADMAP.md`.

## B10a: view past months' budgets (U3), no schema change
- **Branch:** `feat/piso-b10-past-budgets`, from `main` at `14f81c4` (B9).
- **Why no schema change:** each `Budget` already stores its `monthKey`, and `BudgetsScreen` only filters to the current month (`BudgetsScreen.kt:72-73`).
- **Scope:**
  - Add a month selector to Budgets with the same arrows as Activity. It can go back any number of months but never past the current month, and has a "Back to this month" link.
  - Show the budgets, spending and breakdown for the month on screen. Add, copy-from-previous, edit and delete work on that month, the same as today.
  - The ViewModel keeps a month offset (0 = this month) and derives the viewed month from `DateUtil.currentMonthKeyFlow()`, so the screen still rolls over at month-end.
- **Tests:** the offset never goes above 0, and budget spending for a past month counts only that month.
- **Phone checks:** go back to last month and see last month's budgets and spending; the forward arrow is disabled on this month.

## B10b: archive old accounts (U2), database version 4
- **Branch:** `feat/piso-b10-account-archive`, from `main` after B10a.
- **Schema (AGENTS.md):** add `isArchived INTEGER NOT NULL DEFAULT 0` to `accounts` in `MIGRATION_3_4`, commit `app/schemas/.../4.json`, and add a `DatabaseMigrationTest` case that keeps existing accounts, sets them unarchived and keeps their balances.
- **Backup:** export `isArchived`. On import, a backup without the field (older versions) is read as not archived. Add round-trip tests both ways.
- **Scope:**
  - In the account edit dialog, "Archive account" hides the account from the account pickers (transactions, goals, debts, transfers, adjust) and from the main Accounts list. A collapsed "Archived" section at the bottom of Accounts shows them, with "Unarchive".
  - An archived account's balance still counts in net worth, so totals don't change. The dialog says so, and suggests moving or adjusting a non-zero balance first.
  - History in Activity keeps showing the account's name.
  - Archiving needs no confirmation (it's undoable), and nothing is deleted.
- **Tests:** the migration test; archive and unarchive round-trip in the repository; pickers exclude archived accounts; net worth is unchanged by archiving; the backup round-trip with and without the field.
- **Phone checks:** archive an old e-wallet and see it gone from the add-transaction picker, net worth unchanged, and its history still in Activity; unarchive it again.

## Out of scope
Deleting accounts with history, money stored as integer centavos (P19), and time zones (P17).
