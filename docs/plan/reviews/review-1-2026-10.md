# Piso review (October 2026)

This is a read-only review of `qualified-opinion/Piso` at `main` `f3cbfb5`. This branch adds this file and nothing else; no app code changed.

**Method.** Codex CLI (gpt-6.1-sol, high effort, read-only sandbox) did a first pass in the order the brief asked for. I then checked every finding against the source myself. I dropped or downgraded anything I couldn't confirm in code (see "Dropped or downgraded" at the end).

Severity scale:
- **P0:** money wrong or data lost on a normal path
- **P1:** serious risk to data or privacy
- **P2:** should fix
- **P3:** nice to have, or hardening

## Baseline build

I ran `JAVA_HOME=/home/box/tools/jdk17 ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug --continue`. The `--continue` flag lets assemble run even though lint fails.

| Step | Result |
|---|---|
| `testDebugUnitTest` | **42 tests, 0 failures, 0 skipped**. By class: FinanceRepositoryTest 22, UserProfileManagerTest 6, BackupDataTest 5, CurrencyUtilTest 4, CsvExporterTest 2, DateUtilTest 2, DatabaseMigrationTest 1 |
| `lintDebug` | **FAILED: 1 error, 57 warnings.** The error is `ByteOrderMark` at `util/CsvExporter.kt:21` (see P06). |
| `assembleDebug` | Succeeded and produced `app-debug.apk` |

Lint warnings by id:
- GradleDependency 24
- NewerVersionAvailable 9
- UnusedResources 9
- UseKtx 8
- AndroidGradlePluginVersion 2
- IconDipSize 2
- ObsoleteSdkInt 1
- OldTargetApi 1
- RedundantLabel 1

Without `--continue`, the requested command fails at `lintDebug`. CI (`.github/workflows/android-ci.yml:39`) only runs `test` and `assemble*`, so it never catches this.

## What's sound (checked, no finding)

- **Net worth.** `HomeViewModel.kt:93` computes `accTotal + invTotal - debtTotal`, which matches the correct rule (balances + investments − debts). **There's no P0 here.**
- **Balances.**
  - Balances are stored and kept in step with transactions inside Room transactions.
  - An edit reverts the *stored* row before applying the new one, and a delete re-reads the row before reverting it (`FinanceRepository.kt:190-216`).
  - Amounts are rounded half-up to centavos on every write (`CurrencyUtil.roundCents`).
- **Transfers.** A transfer debits one account and credits the other. It's left out of income and spending totals, and the CSV shows it as a signed 0.00.
- **Goals and debts.**
  - A goal contribution either moves money between two different accounts or only sets it aside, so net worth doesn't change.
  - Debt payments are capped at what's still owed, both in the dialog (`DebtPaymentDialog.kt:149`) and in the repository.
- **CSV.** Uses `Locale.US`, `%.2f`, CRLF line endings, and RFC-4180 quoting. Formula injection is neutralised (`CsvExporter.kt:63`).
- **Backup coverage and restore.**
  - All 8 Room entities are in the backup.
  - Restore parses the whole file before clearing anything, and runs in one DB transaction.
  - Both migrations are registered, and there's no destructive fallback.
- **Dates.**
  - Month keys come from local `LocalDateTime`, with no off-by-one at month end.
  - Date pickers convert UTC midnight to the local calendar day correctly (`DateUtil.kt:110-123`).
  - The current month re-checks once a minute, so a screen left open moves on when the month changes.
- **Privacy.**
  - `FLAG_SECURE` is always on (`MainActivity.kt:88`), so Recents shows a blank card.
  - `allowBackup="false"`, and both the backup and data-extraction rules exclude everything.
  - The only exported component is the launcher activity, with no deep links.
  - The app starts locked after process death.
  - `.env.example` and `metadata.json` contain no secrets.
- **Deleting things.**
  - An account with transactions (either side of a transfer) can't be deleted.
  - A category or subcategory that's in use is archived instead of deleted.
  - Deleting a goal or debt detaches its transactions rather than leaving them pointing at a missing record.

## Findings

| ID | Sev | Where | What goes wrong for Jan | Fix (about 2 lines) | Size |
|---|---|---|---|---|---|
| P01 | **P0** | `ui/debts/DebtDialog.kt:123-127` | **A new debt is saved as owing only the first digit.** The Original Amount field copies its text into Remaining only while Remaining is blank. Typing `25000` puts `2` into Remaining on the first keystroke, and it stays `2`. Saving records ₱2 owed, so net worth is ₱24,998 too high unless Jan notices and retypes it. | Keep Remaining in sync with Original until the user edits Remaining (track a `remainingEdited` flag), or leave it blank and default it on save (that fallback already exists at line 191). | S |
| P02 | **P1** | `data/models/BackupData.kt:146-151`, `data/repository/FinanceRepository.kt:409-417`, `ui/settings/SettingsScreen.kt:1193`, `:1348-1356` | **One restore can wipe everything, and there's no undo.** Validation accepts any JSON with `appName: "Piso"` or *any* known array key: `{"accounts":[]}` is accepted as an empty backup. Missing arrays silently become empty and the `version` is never checked. A wrong or old file replaces every account, transaction, budget, goal, debt and investment, with no safety copy. The confirmation doesn't say what's in the file, and the paste-JSON path restores with no "this replaces all data" warning at all. | Require `version` plus all six core arrays, and show counts and export date in the confirmation; add the warning to the paste path. Before replacing, write the current data to an app-private safety backup and offer "Undo restore". | M |
| P03 | **P1** | `ui/settings/SettingsScreen.kt:541,563,583,1295`, `ui/app/AppViewModel.kt:77-80`, `MainActivity.kt:155` | **The PIN lock can stay off after leaving the app from a file picker.** Before opening a picker, Settings sets "skip lock once", and Piso's `onStop` uses up the skip. If Jan presses Home or switches apps while the picker is open, Piso never gets another `onStop`, so it stays unlocked until the next one. Anyone who opens Piso from the launcher or Recents gets straight in. If the picker fails to launch, the skip flag is left set and the next real background is skipped. | Record the picker's start time and expected return instead of a boolean. On `onStart`, lock unless the app is coming back from that picker within about 60 s. Clear the flag in `onStart` and when the launch fails. | M |
| P04 | P2 | `data/repository/FinanceRepository.kt:246-253`, `ui/debts/DebtDialog.kt:191-196` | **Deleting a debt payment can restore less than it took off.** Reverting caps the debt at `max(original, remaining)`. The debt editor lets Remaining be more than Original (interest, fees) or Original be lowered later. Example: original ₱100, remaining ₱150, pay ₱60 (now ₱90), delete the payment. The account gets ₱60 back but the debt only goes up to ₱100, so net worth rises by ₱50. | Revert by exactly the amount the payment actually took off. Payments are already capped at what's owed, so `remaining + amount` with no cap is correct. Alternatively validate `remaining <= original` in the dialog. | S |
| P05 | P2 | `data/local/AppDatabase.kt:92`, `app/src/test/.../DatabaseMigrationTest.kt:43` | **Migrations are only tested against a hand-written v2 schema.** `exportSchema = false`. The one test builds v2 from SQL written by hand, and v1→2 and v1→3 are never tested. A mismatch with real v1/v2 installs would crash on launch for existing users. The repo's AGENTS.md requires a test case for every schema change. | Turn on schema export and commit `3.json`. Generate real `1.json`/`2.json` from the commits where those versions shipped, and add Robolectric `MigrationTestHelper` tests for 1→3 and 2→3 that check the data survives. | M |
| P06 | P2 | `util/CsvExporter.kt:21`, `.github/workflows/android-ci.yml:39` | **`lintDebug` fails, and CI never runs lint.** A literal BOM character inside `'﻿'` is a lint *error* (`ByteOrderMark`), so the standard verify command fails. This would make future PRs look broken. | Write `'\uFEFF'` instead of the literal character. Add `lintDebug` to CI. | S |
| P07 | P2 | `data/local/UserProfileManager.kt:117,135` | **The PIN lockout runs on the adjustable wall clock.** Moving the phone's clock forward ends a lockout early. Moving it back can lock Jan out for a long time, even with the right PIN. | Base the cooldown on `SystemClock.elapsedRealtime()`, and store the boot count so a reboot restarts the cooldown (the same approach as Habitude H09). | M |
| P08 | P2 | `data/repository/FinanceRepository.kt:303,342`, `ui/activity/ActivityScreen.kt:293-297` | **(UX) Goal contributions and debt payments can't be backdated.** Both are always stamped `System.currentTimeMillis()`. Activity refuses to edit them ("Change this from the Goals tab") and neither section has a date field. Recording last month's loan payment puts it in this month's spending and budget. | Add a date picker to `AddMoneyGoalDialog` and `DebtPaymentDialog` (default today), and pass `dateMillis` through to the repository. | M |
| P09 | P2 | `ui/goals/GoalsScreen.kt:296` (only "Add money"), `data/repository/FinanceRepository.kt:256-264` | **(UX) Jan can't take money out of a goal.** The repository handles `goalFlow = "OUT"`, but nothing creates it. When savings are spent, the only option is to edit the goal's saved amount by hand, which leaves no transaction and no account move. | Add a "Use money" action that records an `OUT` move (the reverse of `addMoneyToGoal`), with an optional destination account. | M |
| P10 | P3 | `ui/goals/GoalsScreen.kt:301`, `data/repository/FinanceRepository.kt:269` | **Mark completed / Reopen writes back the whole goal from the card's copy.** If the card hasn't caught up with a contribution that was just added (a short window), that contribution's amount is overwritten, while its transaction and account move stay. | Add `UPDATE goals SET isCompleted = :done WHERE id = :id` and use it for this toggle. | S |
| P11 | P3 | `ui/settings/SettingsScreen.kt:1015`, `data/local/UserProfileManager.kt:185-198` | **Change PIN and Remove PIN allow unlimited guesses.** They call `verifyPin` directly and skip the attempt counter and lockout. This needs an already-unlocked session. | Send every PIN check through one rate-limited verifier that counts failures. | S |
| P12 | P3 | `data/local/UserProfileManager.kt:88-93` | **The PIN is hashed with one salted SHA-256 pass.** If the prefs file were extracted, all 10⁶ PINs could be tried in moments. The database itself isn't encrypted, so this is defence in depth. | Use PBKDF2 (or Argon2) with a calibrated iteration count and a stored hash version, and re-hash after the next successful unlock. | M |
| P13 | P3 | `ui/auth/LockScreen.kt:231,298` | **Anyone holding the phone can wipe all of Piso from the lock screen** by typing `RESET`, with no PIN and no backup. That's a deliberate "forgot PIN" escape hatch, but there's no delay and nothing to stop it. | Jan's call. Either only offer it after N failed PIN attempts or with a 24-hour delay, or keep it as is and say so in the README. | S |
| P14 | P3 | `ui/settings/SettingsScreen.kt:121,138` | **Export says "saved" even if nothing was written.** When `openOutputStream` returns null, writing is skipped and the success message still shows. The write also runs on the main thread. | Treat a null stream as a failure. Do the write on `Dispatchers.IO`, then show the message. | S |
| P15 | P3 | `ui/budgets/BudgetsScreen.kt:351`, `ui/budgets/BudgetDialog.kt:163-167` | **A budget edit dialog left open past month-end moves the budget.** Edit uses the live `currentMonthKey`, so saving after midnight on the last day re-keys the budget into the new month. | Use `initialBudget.monthKey` when editing. | S |
| P16 | P3 | `ui/invest/InvestDialog.kt:179` | **(UX) An investment can't be valued at ₱0.** `parsePositiveAmount("0")` returns null, so a written-off holding has to be deleted. | Use `parseAmount` and accept values ≥ 0. | S |
| P17 | P3 | `util/DateUtil.kt:31` | **Dates follow the phone's current time zone.** If Jan travels, old transactions near midnight move day or month in history, budgets and CSV. In Asia/Manila nothing changes. | Pin the accounting zone to Asia/Manila, or store each transaction's zone/local date with it. | M |
| P18 | P3 | `data/repository/FinanceRepository.kt:386-406` | **Backup export reads the tables one by one, outside a transaction.** An edit landing mid-export could give a slightly inconsistent snapshot. This is unlikely, since Jan is on the Settings screen. | Wrap the eight reads in `database.withTransaction { … }`. | S |
| P19 | P3 | models (`Account.balance`, `Transaction.amount`, …) | **Money is stored as `Double`.** It's mitigated by rounding to centavos on every write, and I found no drift bug. Long term, whole centavos are safer. | When a migration is next needed, store `Long` centavos, converting in the migration and the backup. | L |

**Counts:** P0 1 · P1 2 · P2 6 · P3 10. UX gaps are kept to the brief's limit of 5 (P08, P09, P16, plus the two listed below).

Other everyday UX gaps (no separate ID):
- **Accounts with history can't be closed.** Delete is refused and there's no archive (`FinanceRepository.kt:172-181`). Fold an archive flag into the schema change in batch 5.
- **Budgets only show the current month.** You can copy last month's budgets but not view them.

## Proposed fix batches (one PR each, money and data safety first)

1. **Money correctness:**
   - P01: debt Remaining auto-fill
   - P04: exact debt revert
   - P10: goal completion update by id

   Add tests for each. S–M.
2. **Backup and restore safety:**
   - P02: strict validation, counts in the confirmation, warning on the paste path, safety copy and Undo
   - P14: honest export result, write on IO
   - P18: snapshot inside one transaction
3. **Build hygiene and migration safety:**
   - P06: BOM fix and lint in CI
   - P05: schema export, real v1/v2 schemas, 1→3 and 2→3 tests

   Do this before any schema change.
4. **Lock and privacy:**
   - P03: picker lock exemption
   - P07: monotonic lockout
   - P11: rate-limit every PIN check
   - P12: KDF
   - P13: only if Jan decides to change it
5. **Everyday UX:**
   - P08: backdating
   - P09: take money out of a goal
   - P16: zero-value investments
   - P15: budget month
   - account archive and past-month budgets (the archive needs a schema change, so after batch 3)
6. **Later:**
   - P17: accounting zone
   - P19: integer centavos (needs a migration, after batch 3)

## Dropped or downgraded from the Codex pass

- **"Backup export isn't a consistent snapshot" (Codex P0) → P18, P3.** The race is real in the code, but it needs a write during the few milliseconds of an export started from Settings.
- **"Toggling goal completion overwrites money" (Codex P0) → P10, P3.** Same reason: it's a short window before Room's flow catches up.
- **"Budget editor can move a historical budget" (Codex P1) → P15, P3.** It only happens if the dialog stays open across month-end midnight.
- **"Export reports success with a null stream" (Codex P1) → P14, P3.** The SAF create-document flow practically never returns a null stream.
- **"Time zone changes historical dates" (Codex P2) → P17, P3.** Jan lives in Asia/Manila, so it only matters when travelling.

## Not verified

- Nothing was run on a device. The lock sequences (P03, P13), FLAG_SECURE in Recents, and the picker behaviour are from reading the code only.
- The migration from *real* v1/v2 installs: there are no exported schemas to test against (P05).
- How likely the races in P10 and P18 are in practice.
