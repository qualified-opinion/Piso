# Batch 9: lock-screen RESET only after 10 wrong PINs (P13)

Jan chose this option for D1 on 2026-10-24.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `fix/piso-b9-lock-policy`, from `main` at `af59866` (B8) or later.
- **Engine:** Codex on high effort builds it, because it touches the lock. Claude Code reviews.
- **Source:** review 1 (P13) and review 2 (P13 row and batch 5 of §6). D1 in `ROADMAP.md`.
- **Size:** about 150 changed lines. No schema change. P12 (PIN key derivation) stays out of this batch.
- **AGENTS.md:** this pack authorises a change to `UserProfileManager`, limited to reading the failed-attempt count. Hashing, the lockout timings and `PickerLockExemption` don't change.

## Why
Today anyone holding the unlocked phone can open Piso's lock screen, tap "Clear all data and reset", type RESET and wipe everything, with no PIN. Since B5 this can't be used to read old data, because RESET also deletes every safety copy. What's left is the risk of losing everything by accident or mischief. Requiring 10 wrong PINs first makes that much harder, and Jan can still escape if he really forgets the PIN.

How long 10 wrong PINs take with today's lockout (`UserProfileManager.kt:130-134`): the first 4 are instant, the 5th to 9th each start a 30-second wait, and the 10th starts a 5-minute wait. So it's about 2½ minutes of deliberate effort. The reset itself doesn't have to wait for that last lockout to end.

## Scope
1. **Expose the count.** Add `fun failedAttempts(): Int` to `UserProfileManager`, reading `KEY_FAILED_ATTEMPTS` (0 when missing). The count already survives restarts and goes back to 0 after a correct PIN (`resetFailedAttempts`). Add a constant `RESET_AFTER_FAILED_ATTEMPTS = 10`.
2. **Pass it through.** `AppViewModel` gets `failedPinAttempts()`, and `MainActivity.kt:120` passes it into `LockScreen` as `getFailedAttempts: () -> Int = { 0 }`, the same pattern as `getLockoutRemainingSeconds`.
3. **Lock screen.** In `ui/auth/LockScreen.kt:209-240`:
   - Below 10 failed attempts, hide the "Clear all data and reset" button. Show one line instead: "Forgot your PIN? After 10 wrong tries you can clear all data and start over." Re-read the count after each failed attempt, so the button appears as soon as the 10th one fails.
   - From 10 failed attempts, show the button and the RESET dialog exactly as today. The dialog text keeps saying that everything, including safety copies, will be deleted.
   - Keep the existing "Piso cannot recover a forgotten PIN" message.
4. **Guard the action too.** `AppViewModel.clearAllDataAndReset()` (`:115`) does nothing and returns early when `failedPinAttempts() < 10` and a PIN is set. That way a later UI change can't bring the free wipe back. Settings → Clear All Data is a different path (unlocked, keeps an undo copy) and doesn't change.

## Out of scope
P12 (PBKDF2), any change to the lockout timings, a 24-hour wait, and Settings → Clear All Data.

## Tests (write each one failing first)
- `UserProfileManagerTest`: `failedAttempts()` is 0 at first, counts each wrong PIN, survives a new `UserProfileManager` on the same prefs, and is 0 again after a correct PIN.
- `AppViewModel` (or a small pure function it calls, such as `canResetFromLockScreen(hasPin, failedAttempts)`): reset is refused at 9 failed attempts and allowed at 10. With no PIN set, it's allowed.
- `clearAllDataAndReset` below the threshold leaves the data, the profile and the safety copies in place.
- The existing B5 test (RESET deletes every safety copy) still passes once the threshold is reached.

## Acceptance criteria
- Items 1 to 4 are done with the tests above.
- `./gradlew test lintDebug assembleDebug assembleRelease` passes, with no new lint errors.
- There's no `INTERNET` permission, no new dependency and no schema change. `FLAG_SECURE` and the backup rules are untouched.
- This PR updates the ROADMAP B9 row and marks P13 fixed and D1 settled.
- The PR body lists Jan's phone checks: lock Piso and see no reset button; enter 10 wrong PINs and see it appear; enter the right PIN and see the count reset (no button next time it locks).

## Risks
- A mistake here could leave Jan unable to reset when he really needs to. The 10-attempt test and the phone check cover that.
- No PIN is ever logged, and the count is the only new value read.
