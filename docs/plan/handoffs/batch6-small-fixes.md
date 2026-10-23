# Batch 6: small correctness and cleanup (P15, P16, P25, P23, P26, U4, B5 leftover)

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `fix/piso-b6-small-fixes`, from `main` at `622df4f` (or the ROADMAP docs merge if it lands first).
- **Engine:** Codex on high effort builds it, because P25 touches the money totals. Claude Code reviews.
- **Source:** `docs/plan/reviews/kiln-review-2.md` (P15, P16, P23, P25, P26, U4) and the PR #10 review (B5 leftover).
- **Size:** about 300 changed lines. Each fix gets a failing test first.

## Scope
1. **P15, budget edit keeps its month.** `ui/budgets/BudgetsScreen.kt:351` passes `currentMonthKey` into the edit dialog, and `BudgetDialog.kt:167` saves it. Editing a budget must keep the budget's own `monthKey`; only new budgets use the current month. Test: edit a budget from another month, and its `monthKey` stays the same.
2. **P16, allow a ₱0 investment value.** `ui/invest/InvestDialog.kt:179` uses `parsePositiveAmount`. Accept 0 for the current value, keep rejecting negative or blank input, and leave other dialogs unchanged. Test: the parser or validator accepts 0 here.
3. **P25, a no-account debt payment isn't spending.** When `fromAccountId == null` in `recordDebtPayment` (`FinanceRepository.kt:355-364`), reduce the debt without creating an EXPENSE transaction, so Home "Spent" (`HomeViewModel.kt:107-118`) and any "Debt" budget don't change. Rename the option in `DebtPaymentDialog.kt:88,111` to "No account (only lower what I owe)". Payments with an account must behave as before. Tests: no-account payment lowers the debt with no transaction; account payment is unchanged.
4. **P23, import off the main thread with a size cap.** `SettingsScreen.kt:136-155,1189` reads the whole file with `readText()` on the main thread and re-parses it during composition with `fromJsonString(...)!!`. Move the read and parse into the SettingsViewModel on `Dispatchers.IO`, refuse files over 20 MB with a clear message, and keep the parsed `BackupData` plus its `BackupSummary` in state. The confirmation dialog reads from state; there's no `!!` and no parsing during composition. A bad or oversized file shows an error instead of crashing. Tests: oversized input is refused, invalid JSON gives an error state, and valid input gives a summary.
5. **P26, remove unused plugins.** Remove the Secrets Gradle plugin and Roborazzi from `app/build.gradle.kts:5-6,78-81` and `libs.versions.toml`, remove `buildConfig = true`, and delete `.env.example` if nothing else uses it. Confirm nothing references `BuildConfig` first.
6. **U4, copy only.** Before Share Backup opens the share sheet, tell Jan in one line that Piso will ask for the PIN when he comes back.
7. **B5 leftover.** If an undo's delete fails, the used safety copy stays on disk until the next clear or RESET. Have the next successful import also clean up any copy that's marked used. Test: a used copy left by a failed delete is gone after the next import.

## Out of scope
Schema changes, lock policy (B9), backdating (B7), goal withdrawal and balance adjustment (B8), and money-type changes (P19).

## Acceptance criteria
- All seven items above are done, each with the test listed.
- `./gradlew test lintDebug assembleDebug assembleRelease` passes, with no new lint errors.
- There's no `INTERNET` permission and no new dependency.
- This PR updates the ROADMAP B6 row.
- The PR body lists Jan's phone checks: edit a past-month budget; save a ₱0 investment; make a no-account debt payment and see that Spent doesn't change; pick a large non-backup file to import and see an error, not a freeze; check the Share Backup note.

## Risks
- P25 changes how totals are counted. Any existing no-account payments saved as EXPENSE stay as they are, so don't migrate old data in this batch. Note it in the PR body.
- P26 can break the build if something hidden uses `BuildConfig`. CI catches that.
