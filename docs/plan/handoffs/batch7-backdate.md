# Batch 7: backdated goal and debt moves (P08)

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `feat/piso-b7-backdate`, from `main` at `3d4ce09` (or the docs PR that adds this pack, if it lands first).
- **Engine:** Codex builds it. Claude Code reviews.
- **Source:** review 1 (P08) and review 2 (`docs/plan/reviews/kiln-review-2.md`, P08 row and batch 3 of §6).
- **Size:** about 250 changed lines. No schema change.

## Why
Goal contributions and debt payments are always stamped with the current time. If Jan records last month's loan payment today, it lands in this month's Spent and in this month's "Debt" budget, and the month that actually had the payment looks too low. The Transactions dialog already has a date picker; these two dialogs don't.

## Scope
1. **Share the date field.** The date row and `DatePickerDialog` in `ui/activity/TransactionDialog.kt:437` and `:569-596` become one small composable (for example `ui/components/DateField.kt`) that keeps the same `DateUtil.toPickerUtcMillis` / `fromPickerUtcMillis` handling. `TransactionDialog` uses it with no change in behaviour; its existing test tags stay.
2. **Goal contribution date.** `AddMoneyGoalDialog` (`ui/goals/AddMoneyGoalDialog.kt:37`) gets the date field, defaulting to today. `onConfirmAdd` gains `dateMillis`. `FinanceRepository.addMoneyToGoal` (`:312`) takes `dateMillis: Long` and uses it instead of `System.currentTimeMillis()`. `GoalsViewModel` passes it through.
3. **Debt payment date.** The same for `DebtPaymentDialog` (`ui/debts/DebtPaymentDialog.kt:37`) and `FinanceRepository.recordDebtPayment` (`:355`). When no account is picked (the B6 "only lower what I owe" option) there's no transaction, so the date is not used; hide the date field in that case.
4. **Future dates.** Use the same rule the Transactions dialog uses today. Don't add a new rule in this batch.
5. **Honest Activity message.** Tapping a goal move or debt payment in Activity says "Change this from the Goals tab" or "...Debts section" (`ui/activity/ActivityScreen.kt:293-297`), but neither tab can edit a past move. Change both to say what is true: "To change this, delete it here and record it again from Goals" (and the Debts version). Delete already reverts the effect correctly.

## Out of scope
Editing a past goal or debt move in place, taking money out of a goal (B8), balance adjustment (B8), lock policy (B9) and any schema change.

## Tests (write each one failing first)
- `addMoneyToGoal` with a date last month: the transaction has that `dateMillis`, and the goal's saved amount still goes up.
- `recordDebtPayment` with a date last month: the transaction has that `dateMillis`, and the debt goes down by the payment.
- Month attribution: a backdated debt payment counts in last month's Spent and "Debt" category, not this month's. Test this at the level that sums by `DateUtil.getMonthKey` (the Home or Budgets summary logic). If that logic is only in a ViewModel, a small pure function pulled out of it is fine.
- Default: with no date chosen, both repository calls still stamp "now" (keep a default parameter or pass today from the dialog, and test whichever is chosen).

## Acceptance criteria
- Items 1 to 5 are done with the tests above.
- `./gradlew test lintDebug assembleDebug assembleRelease` passes, with no new lint errors.
- There's no `INTERNET` permission, no new dependency and no schema change.
- This PR updates the ROADMAP B7 row and marks P08 fixed.
- The PR body lists Jan's phone checks: add money to a goal dated last month and see it under last month in Activity; record a debt payment dated last month and see this month's Spent unchanged; tap a goal move in Activity and read the new message.

## Risks
- Money amounts don't change; only the date does. Low risk.
- Date pickers work in UTC. Reuse the existing `DateUtil` helpers rather than writing new conversions, so a payment picked for the 1st doesn't land on the last day of the previous month.
