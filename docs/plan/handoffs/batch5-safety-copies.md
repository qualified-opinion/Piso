# Batch 5: safety-copy hygiene (P20, P21, P22, P24)

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `fix/piso-b5-safety-copies`, from `main` at `b78d388` or the docs merge if that lands first.
- **Engine:** Codex builds and Claude Code reviews.
- **Source:** `docs/plan/reviews/kiln-review-2.md`, items P20, P21, P22 and P24.

## Goal
After this batch, a reset leaves nothing behind that can rebuild the old ledger. Undo is one-shot and confirmed, and Clear All Data can be undone once.

## Scope
1. **P20, lock-screen RESET.** `AppViewModel.clearAllDataAndReset()` deletes every safety copy, using a new `BackupSafety.deleteAll()` that also removes leftover `pending-*.tmp` files. Wire it in whatever way `AppViewModel` builds its repository. `canUndoRestore` must be false afterwards.
2. **P22, Settings → Clear All Data.**
   - `FinanceRepository.clearAllData(keepUndo = true)` saves one safety copy of the current data in the same transaction, then clears the tables, the same way `restoreBackup` does. That copy replaces any older ones, so only one is left.
   - The confirmation text says the clear can be undone once from Settings.
   - Lock-screen RESET calls it with `keepUndo = false` and then `deleteAll()`.
3. **P21, one-shot Undo.**
   - `undoRestore()` restores the latest safety copy without saving a new one, then deletes that file.
   - A second tap must do nothing, because the button is hidden once no copy is left. If older copies remain, the button undoes the next one only after a fresh confirmation.
   - Add a confirmation dialog that shows the copy's date and counts, reusing `BackupSummary`.
   - The label reads "Undo restore of <date>", or "Undo clear of <date>" for a copy saved by Clear All Data. Tell them apart with a name prefix: `pre-import-` or `pre-clear-`. Both use the same 19-digit timestamp and the same keep-three pruning.
4. **P24, clipboard.** Mark the backup clip as sensitive. On API 33 and later, set `ClipDescription.EXTRA_IS_SENSITIVE`. On older versions, use the string key `android.content.extra.IS_SENSITIVE`. Keep the existing warning text.
5. **ROADMAP.** In `docs/plan/ROADMAP.md`, set B5 to "In review, PR #N", and set P20, P21, P22 and P24 to "Fixed in B5" when the PR merges.

## Out of scope
P13 RESET policy (B9), PIN hashing, schema changes, the import size cap (P23, B6), and any UI beyond the dialog, the label and the button visibility.

## Acceptance criteria
- `./gradlew test lintDebug assembleDebug` passes, with no new lint errors.
- New or changed tests, each failing on current `main`:
  - Reset deletes every safety copy, and `hasUndoRestore()` becomes false.
  - Restore, undo, then undo again: the second undo restores nothing new, and the data still equals the pre-import state.
  - Clear All Data, then undo: all six tables come back.
  - After a clear, exactly one `pre-clear-` copy exists.
  - A failed undo (corrupt file) leaves the data unchanged and keeps the file.
- `BackupRestoreTest` at line 145 or so currently asserts the toggle behaviour. Rewrite it to assert one-shot undo, and say so in the PR body.
- No change to schema JSON, the manifest, the workflows, or the PIN code.
- Jan's phone checks, listed in the PR:
  1. Import a backup, undo it, and confirm the button disappears.
  2. Clear All Data, undo it, and confirm the data is back.
  3. Use lock-screen RESET, set up with no PIN, and confirm Settings shows no Undo.
  4. Copy the backup and confirm the Android 13+ clipboard preview hides the content.

## Risks
- Undo restores data inside the transaction, so delete the file only after the commit. Deleting it before the commit could lose the only copy if the restore fails.
- `AppViewModel` and `SettingsViewModel` build separate repositories. Make sure both point at the same `filesDir/backups`.
- The batch should be about 250 changed lines. If it goes over 450, stop and say so.

## Process
Open one PR against `main`, update the status table, and don't merge. Kiln reviews, then Jan gives the OK.
