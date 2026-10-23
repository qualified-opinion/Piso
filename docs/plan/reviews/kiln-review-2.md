# Kiln review 2 (2026-10-23)

Planner preface. Fresh read-only review of `main` at `b78d388`, after batches B1 to B4 merged (PRs #5 to #8, 2026-10-14). Engine: Claude Code in plan mode. Kiln spot-checked P20 against the code: `FinanceRepository.clearAllData()` clears Room only, `AppViewModel.clearAllDataAndReset()` adds only `profileManager.clearProfile()`, and `hasUndoRestore()` only checks that a `pre-import-*.json` file exists. The finding stands.

Kiln's calls on the engine's output: P20 stays P1. P17, P19 and P12 are cut for now. P13 is Jan's decision. The account archive and past-budget view need a schema change and are later ideas, not scheduled.

---

# Piso review 2 — `main` @ `b78d388` (2026-10-23)

This is a read-only review. I read the source and the four batch diffs (`git show 09814a2 240f7ef c428b76 b78d388`). **I did not run Gradle**, because the session was read-only and a build writes `build/`. The test results quoted below come from the commit messages, not from a run. Paths are under `app/src/main/java/com/janreins/piso/` unless they start with `app/` or `.github/`.

## 1. Status of P01–P19

| ID | Status | Evidence |
|---|---|---|
| P01 debt Remaining auto-fill | **Fixed, tested** | `ui/debts/DebtAmountInputState.kt:11-14` keeps Remaining in step with Original until the user edits Remaining. `DebtDialog.kt:44-51` sets `remainingEdited = initialDebt != null`, so editing an existing debt doesn't overwrite it. Tests: `DebtAmountInputStateTest.kt`. The Compose wiring has no UI test. |
| P02 strict restore + undo | **Partly fixed** | Validation is now strict (`data/models/BackupData.kt:154-161`: integer version in {1,2,3}, all six core arrays). The confirmation shows counts and the export date (`BackupSummary.kt`), and the paste path goes through the same review dialog (`SettingsScreen.kt:1351`). A safety copy is written inside the restore transaction (`FinanceRepository.kt:433-439`). Tests: `BackupDataTest`, `BackupRestoreTest` (10 cases). **But Undo itself is unsafe (P21), and the safety copies outlive a reset (P20).** |
| P03 picker lock | **Fixed, tested** | `data/local/PickerLockExemption.kt`: the request code is tied to the launch, the window is 60 s on `elapsedRealtime`, and Home or Recents uses up the exemption (`:283-286`). Hooks are in `MainActivity.kt` (`startActivityForResult`/`onActivityResult`/`onStart`/`onResume`). A failed launch clears the exemption (`:250`). Tests: `PickerLockExemptionTest` (14), `MainActivityPickerLockTest` (5, Robolectric). Side effect: "Share Backup" (`SettingsScreen.kt:1291`) no longer skips the lock, so Piso locks after sharing. That is the right trade-off. |
| P04 exact debt revert | **Fixed, tested** | `FinanceRepository.kt:257-261` reverts by `remaining + amount` with no cap. An edited payment is capped against the restored debt (`:211-212`). Tests: `FinanceRepositoryTest:200,223,273` include remaining > original. |
| P05 schemas + migration tests | **Fixed, tested** | `exportSchema = true` (`AppDatabase.kt:92`). `app/schemas/.../1.json,2.json,3.json` were generated from `d7ef3ad`/`e66ea2d` (per the commit message). `DatabaseMigrationTest` uses `MigrationTestHelper` for 1→2→3, 1→3 and 2→3 and checks the data. I didn't independently re-derive 1.json/2.json. |
| P06 BOM + lint in CI | **Fixed** | `util/CsvExporter.kt` uses an escaped BOM, and `.github/workflows/android-ci.yml:39-40` runs `lintDebug`. |
| P07 monotonic lockout | **Fixed, tested** | `UserProfileManager.kt:111-146` uses `elapsedRealtime` plus `BOOT_COUNT`. A reboot restarts the cooldown, and a legacy wall-clock key is migrated once. Tests: `UserProfileManagerTest:151,168,222`. Small gap: failure counts are written with `apply()` (`:145`), so a kill within milliseconds could drop one. That's hardening only. |
| P08 backdate goal/debt moves | **Still real** | `FinanceRepository.kt:313,356` still use `System.currentTimeMillis()`. Activity still refuses to edit these (`ui/activity/ActivityScreen.kt:293-297`). |
| P09 withdraw from goal | **Still real** | `GoalsScreen.kt:296` only has "Add money", and nothing creates `goalFlow = "OUT"`. |
| P10 goal completion by id | **Fixed, tested** | `GoalDao.kt:36-37` adds `UPDATE … SET isCompleted`, called from `GoalsScreen.kt:301`. Tests: `FinanceRepositoryTest:306,311`. |
| P11 rate-limit every PIN check | **Fixed, tested** | `verifyPin` checks the lockout first and counts every failure (`UserProfileManager.kt:157-163`). `changePin`/`removePin`/Settings all go through it. Tests: `UserProfileManagerTest:180-222`. |
| P12 PIN KDF | **Still real** | `UserProfileManager.kt:82-87` still does one salted SHA-256 pass. |
| P13 RESET from lock screen | **Still real, and worse because of P20** | `ui/auth/LockScreen.kt:298-300` → `AppViewModel.clearAllDataAndReset()` (`:411-419`). |
| P14 honest export | **Fixed, tested** | `util/ExportWriter.kt` treats a null stream as an error and writes on IO. `SettingsViewModel.kt:204-215` reports the result. Test: `ExportWriterTest`. |
| P15 budget edit month | **Still real** | `ui/budgets/BudgetsScreen.kt:351` still passes `currentMonthKey`, and `BudgetDialog.kt:167` saves it. |
| P16 ₱0 investment | **Still real** | `ui/invest/InvestDialog.kt:179` still uses `parsePositiveAmount`. |
| P17 time zone | **Still real** | `util/DateUtil.kt:31` still uses `ZoneId.systemDefault()`. |
| P18 consistent export snapshot | **Fixed, tested** | `FinanceRepository.kt:400-402` runs the export inside `withTransaction`. Test: `BackupRestoreTest:75`. |
| P19 Double money | **Still real (by design)** | The models are unchanged, and rounding is still applied on every write. |

## 2. New findings

| ID | Sev | Where | What goes wrong for the user | Fix | Size |
|---|---|---|---|---|---|
| **P20** | **P1** | `data/repository/BackupSafety.kt` (files in `filesDir/backups`), `FinanceRepository.kt:475-488`, `ui/app/AppViewModel.kt:411-419`, `ui/settings/SettingsViewModel.kt:54`, `SettingsScreen.kt:586-592` | **A reset doesn't delete the safety backups, so anyone can get the old data back.** `clearAllData()` and the lock-screen RESET clear Room and prefs but leave the `pre-import-*.json` files in place. **Lock bypass:** someone holding the phone types RESET (no PIN needed, P13), creates a new profile without a PIN, and opens Settings. "Undo restore" appears (`hasUndoRestore()` only checks that a file exists), and one tap brings back the owner's full ledger as of their last restore. "Forgot PIN → reset" also leaves plaintext financial JSON on the device. | Delete `filesDir/backups` in `clearAllDataAndReset()`. For Settings → Clear All Data, either delete the folder too or *replace* it with one fresh safety copy (see P22). Add a test: reset → `hasUndoRestore()` is false. | S |
| **P21** | P2 | `FinanceRepository.kt:424-426` + `:437`, `SettingsScreen.kt:586-592`, `SettingsViewModel.kt:197`; behaviour is locked in by `BackupRestoreTest.kt:145-153` | **"Undo restore" never goes away, has no confirmation, and switches back and forth.** Undo runs a full restore, which first saves the *current* data as the newest safety copy. So the button stays visible, and a second tap re-applies the backup Jan just undid. The button also survives restarts: weeks after an import, one accidental tap replaces every entry since then with the old snapshot, with no dialog. Each undo also prunes the oldest copy (keeps 3), so after three taps the original pre-import data is gone. | Make undo one-shot: restore the snapshot, then delete that file and *don't* save a new one (or save it under a separate "redo" name). Show the button only for the current session or for N days, labelled "Undo restore of <date>". Ask for confirmation with the snapshot's counts (reuse `BackupSummary.confirmationText`). | S |
| **P22** | P2 | `SettingsScreen.kt:1218-1229` → `SettingsViewModel.kt:217-222` → `FinanceRepository.kt:475` | **Settings → Clear All Data has no safety copy.** Restore now protects against mistakes, but the bigger destructive button doesn't. One confirm tap wipes everything permanently. | Have `clearAllData` save a snapshot with `backupSafety.save(readBackupData())` in the same transaction, like `restoreBackup` does, so "Undo" works. Combine with the P20 decision: Settings clear keeps one undo copy; lock-screen RESET deletes them all. | S |
| **P23** | P3 | `SettingsScreen.kt:136-155`, `:1189` | **Choosing a large or wrong file can freeze or crash Piso.** The picker accepts `*/*`. The whole file is read with `readText()` and parsed on the **main thread**, with no size limit, so picking a video means an ANR or OOM. The confirmation dialog then calls `BackupData.fromJsonString(fileJson)!!` during composition, re-parsing the file on every recomposition. | Read and parse on `Dispatchers.IO` in the ViewModel, refusing files over ~20 MB. Keep the parsed `BackupData` and its `summary` in state instead of the raw string. | S |
| **P24** | P3 | `SettingsScreen.kt:1268-1273` | **Copying the backup to the clipboard exposes the whole ledger.** On Android 13+ the clipboard preview shows it, and keyboard clipboard history (e.g. Gboard) keeps it after Piso locks. | Set `ClipDescription.EXTRA_IS_SENSITIVE` on the clip's description. Better, cut clipboard export entirely (§5). | S |
| **P25** | P3 | `ui/debts/DebtPaymentDialog.kt:88,111`, `FinanceRepository.kt:355-364`, `ui/home/HomeViewModel.kt:107-118` | **A debt payment with no account still counts as spending.** "None (Just adjust debt remaining)" saves an EXPENSE in category "Debt", so the month's Spent and any "Debt" budget go up even though no money left an account. Net worth rises correctly. | When `fromAccountId == null`, either skip creating a transaction (adjust the debt directly with an audit note) or tag it so the monthly and budget sums exclude it. Rename the option to say what it does. | S |
| **P26** | P3 | `app/build.gradle.kts:5-6,78-81`, `.env.example` | **Unused build plugins.** The Secrets Gradle plugin reads `.env` for a fully offline app, and nothing uses `BuildConfig`. Roborazzi is applied, but no test calls it. Both are extra supply-chain and build surface for nothing. | Remove both plugins and their `libs.versions.toml` entries, and `buildConfig = true`. | S |

Checked with no new finding: `allowBackup="false"` plus both rule files exclude everything (including the new `file/backups`); FLAG_SECURE (`MainActivity.kt:88`); only the launcher activity is exported; no `INTERNET` in the source manifest (I didn't inspect the merged manifest); restore validation and rollback; and the edit-cap logic in `updateTransaction` (`FinanceRepository.kt:204-217`).

## 3. Product/UX friction (daily peso tracking)

1. **There's no way to correct a balance.** The starting balance can only be set when the account is created (`ui/accounts/AccountDialog.kt:123-157`). When the wallet doesn't match, Jan has to invent an income or expense, which skews Spent and budgets. Add an "Adjust balance" action that records a tagged ADJUSTMENT excluded from income/spending.
2. **Goal and debt moves can't be backdated (P08)**, and **money can't be taken out of a goal (P09)**. Both come up every week.
3. **Accounts with history can't be closed or hidden**, so old e-wallets clutter every picker (`FinanceRepository.kt:178-187`).
4. **Budgets only show the current month**, so last month's results can't be viewed (`BudgetsScreen.kt:72-73`).
5. **Piso relocks after Share Backup** and after picker sessions longer than 60 s. That's correct, but say so in the dialog ("You'll need your PIN when you come back") so it doesn't feel like a bug.

## 4. Test gaps

- **Reset/clear vs. safety copies (P20/P22):** no test that `clearAllData` / `clearAllDataAndReset` remove or replace `filesDir/backups`, or that `hasUndoRestore()` is false afterwards.
- **Undo semantics (P21):** the existing test (`BackupRestoreTest:145`) *asserts* the switch-back behaviour. Rewrite it to assert one-shot undo.
- **ViewModel layer:** no tests for `SettingsViewModel` (import/undo messages, the `restoreInProgress` guard) or `HomeViewModel`/`BudgetsViewModel` sums. The rule that goal moves are excluded and transfers are excluded from Spent is untested.
- **UI wiring:** `DebtDialog` itself isn't tested (only the state class). There's no Compose test for the restore confirmation dialog.
- **P15/P16/P25:** no tests. Write a failing test first for each when fixing.
- **Restore content sanity:** no test for backups with dangling ids (a transaction pointing at a missing account or debt), negative amounts, or duplicate ids.
- **CI:** CI runs `test lintDebug assembleDebug assembleRelease` (`android-ci.yml`), which is good. Nothing runs `androidTest`, which is fine since there are no instrumented tests.

## 5. What to cut

- **P17 (accounting time zone):** cut. Jan lives in Manila, and changing it means changing how dates are stored.
- **P19 (Long centavos):** cut for now. Rounding on every write works, and the tests show no drift. An L-sized migration isn't worth the risk unless a drift bug turns up.
- **P12 (KDF):** cut, or make it a later S-sized change. The database isn't encrypted, so the PIN hash isn't what protects the data. P20 is the real lock bypass.
- **Clipboard/paste backup paths:** cut clipboard *export* (P24). Keep paste-import only if Jan actually uses it.
- **Secrets + Roborazzi plugins (P26):** remove.

## 6. Ranked batch order (≤ ~450 changed lines each, riskiest last)

1. **B5 — Safety-copy hygiene: P20, P21, P22, P24.** Reset deletes the backups, undo is one-shot with a confirmation, Clear All Data keeps one undo copy, and the clipboard clip is marked sensitive or removed. Tests: reset → no undo; undo twice → second tap does nothing; clear → undo restores. ~250 lines. *Low code risk, closes the P1.*
2. **B6 — Small correctness and cleanup: P15, P16, P25, P23, P26.** Budget edit keeps its month, ₱0 investments allowed, no-account debt payment excluded from Spent, import parsing off the main thread with a size cap, plugin removal. ~300 lines.
3. **B7 — Backdating (P08).** Date picker in `AddMoneyGoalDialog`/`DebtPaymentDialog`, `dateMillis` passed through `addMoneyToGoal`/`recordDebtPayment`, tests for month attribution. ~250 lines.
4. **B8 — Goal withdrawal (P09) and balance adjustment (UX #1).** "Use money" creates an `OUT` move (the repository already handles `OUT`); "Adjust balance" creates an excluded ADJUSTMENT type. Update the Home/Budgets/CSV filters. ~400 lines. *Touches the money paths, so it needs repository round-trip tests.*
5. **B9 — Lock policy (P13 decision, optional P12).** Only offer RESET after ≥10 failed attempts or a delay. Optionally PBKDF2 with a hash version and re-hash on unlock. ~250 lines. *Risky because a mistake locks Jan out; needs a migration test for the old SHA-256 hashes.*
6. **B10 — Schema change (only if wanted): account archive (UX #3), past-month budget view (UX #4).** Needs `MIGRATION_3_4`, `4.json` and a `DatabaseMigrationTest` case (AGENTS.md). ~400 lines. *Riskiest: the only batch that touches the schema.*

## Not verified

- I didn't run any test or build (read-only session). The pass counts come from the commit messages.
- I didn't test device behaviour (the lock sequences, the P20 RESET→Undo path, the Android 13 clipboard preview). These come from reading the code.
- I didn't inspect the merged manifest for library-added permissions.
