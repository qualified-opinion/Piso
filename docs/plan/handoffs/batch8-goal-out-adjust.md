# Batch 8: take money out of a goal (P09) and Adjust balance (U1)

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `feat/piso-b8-goal-out-adjust`, from `main` after B7 merges.
- **Engine:** Codex on high effort builds it, because both items change account balances. Claude Code reviews.
- **Source:** review 1 (P09) and review 2 (§3 item 1 and batch 4 of §6).
- **Size:** about 400 changed lines. No schema change: `Transaction.type` is a free text column, and `goalFlow = "OUT"` already exists.
- **Depends on:** B7, for the shared date field.

## Why
- **P09.** Jan can save toward a goal but can't take money back out. When the savings are spent, the only option is to edit the goal's saved amount by hand, which leaves no record and doesn't move any account.
- **U1.** When an account's balance in Piso doesn't match the real wallet or bank, the only fix is to invent an income or an expense. That fake entry then shows up in the month's Income or Spent and in budgets. Accountants would call what's needed a reconciling adjustment: it fixes the balance without being treated as income or spending.

## Scope

### Part A: "Use money" on a goal (P09)
1. **Repository.** Add `FinanceRepository.withdrawFromGoal(goalId, amount, toAccountId: Long?, dateMillis)`, the reverse of `addMoneyToGoal` (`:312`):
   - Cap the amount at the goal's current saved amount, the same way `recordDebtPayment` caps at what's owed. Without the cap, the `max(0.0, …)` in `applyEffect` would make a later delete add back more than was taken out.
   - If the goal is kept in a linked account and `toAccountId` is a different account, record a `TRANSFER` from the goal's account to `toAccountId`. Otherwise (no linked account, the same account or no destination) the money stays where it is and is only released from the goal: `accountId == transferToId`, which is the same "set aside" pattern contributions use.
   - Set `goalId`, `goalFlow = "OUT"`, category "Savings", and a note "Taken from <goal name>". `applyEffect` already lowers the goal for `OUT`, and `isCompleted` is recalculated there.
2. **UI.** Add a "Use money" button next to "Add money" on the goal card (`ui/goals/GoalsScreen.kt:296`), shown only when the saved amount is above zero. It opens a dialog like `AddMoneyGoalDialog` with an amount, a destination account (default: the goal's own account, so nothing moves) and the B7 date field.
3. **Home goal-moves total.** `HomeViewModel.kt:107-118` adds every goal move to `goalMoves` as a positive number. Make it net: contributions minus withdrawals. Check the Home label still reads correctly.

### Part B: Adjust balance (U1)
4. **New transaction type `ADJUSTMENT`.** The amount stays positive (as the model requires). The direction is carried by which side is filled in, using the transfer pattern that `applyEffect` already handles safely when one side is null:
   - Increase: `accountId = null`, `transferToId = account`.
   - Decrease: `accountId = account`, `transferToId = null`.
   In `applyEffect` (`FinanceRepository.kt:249`), treat `"ADJUSTMENT"` exactly like `"TRANSFER"`. Category "Balance adjustment", and the note says the old and new balances.
5. **Repository.** Add `adjustAccountBalance(accountId, newBalance, dateMillis)`. It reads the stored balance, works out the difference, rounds it to centavos, and does nothing if the difference is zero. It runs in one `withTransaction`.
6. **UI.** Add "Adjust balance" to the account's actions (`ui/accounts/AccountsScreen.kt` / `AccountDialog.kt`). Jan types the real balance; the dialog shows the difference ("Piso will add ₱120.50") before saving.
7. **Keep adjustments out of income, spending and budgets.** They already are, because those sums only count `INCOME` and `EXPENSE` (`HomeViewModel.kt:113-150`, `BudgetsViewModel.kt:58-69`, `ActivityScreen.kt:97-100`). Add tests so it stays that way.
8. **Activity.** Show adjustments with their own label and a neutral colour. Tapping one must not open the transaction edit dialog (it only knows three types); show "To change this, delete it and adjust again". Delete reverts it like any transfer. Add an "Adjustments" filter chip only if it fits without crowding; otherwise "All" is enough.
9. **CSV export.** `util/CsvExporter.kt:27-31` puts 0 in Signed Amount for anything not income or expense. For `ADJUSTMENT`, use +amount for an increase and -amount for a decrease, because unlike a transfer it does change the total.

## Out of scope
Schema changes, account archive (U2), past budgets (U3), lock policy (B9), and editing an adjustment in place.

## Tests (write each one failing first)
- Withdraw with a different destination: goal goes down, goal account goes down, destination goes up, net worth unchanged.
- Withdraw with no destination: goal goes down, no account changes.
- Withdraw more than is saved: capped at the saved amount; deleting the transaction restores the goal exactly.
- A completed goal that drops below target is no longer completed.
- Adjust up and adjust down: the account ends at exactly the typed balance; deleting the adjustment restores the old balance.
- Adjust to the same balance: no transaction is created.
- Adjustments don't change Home Income or Spent, any budget, or Activity's month totals.
- A backup with an `ADJUSTMENT` and a goal `OUT` round-trips through export and restore with the same balances (`BackupRestoreTest`).
- CSV: an increase has a positive Signed Amount and a decrease a negative one.

## Acceptance criteria
- Parts A and B are done with the tests above.
- `./gradlew test lintDebug assembleDebug assembleRelease` passes, with no new lint errors.
- There's no `INTERNET` permission, no new dependency and no schema change.
- This PR updates the ROADMAP B8 row and marks P09 and U1 fixed.
- The PR body lists Jan's phone checks: take money out of a goal into another account and see both balances move; adjust a wallet up and down and see Spent unchanged; export CSV and see the adjustment's sign.

## Risks
- This is the riskiest money change so far, because it adds a new kind of entry to the balance logic. The round-trip and delete-reverts tests are the safety net, so none of them can be skipped.
- An older Piso version restoring a backup made after B8 would show `ADJUSTMENT` rows it doesn't know. Balances are stored values, so totals stay right. Jan has one install, so this is noted, not handled.
