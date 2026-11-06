# Batch 18: fingerprint unlock (U15)

Jan picked this on 2026-11-06 as the last "Later ideas" item. Typing the 6-digit PIN every time Piso opens is slow. It needs no database change.

This pack is the go-ahead AGENTS.md asks for before `UserProfileManager` changes. Only the parts listed below change. PIN hashing, the wrong-PIN waits, the reset after 10 wrong PINs, and `PickerLockExemption` stay as they are.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-vkg33n`, restarted from `main` after B17 (PR #24) merged.

## Scope
- **Settings, Security & PIN Lock:** a new **Unlock with fingerprint** switch. It shows only while a PIN is set, and only on Android 10 or later.
  - Turning it on asks for a fingerprint first. It is on only once the phone has confirmed one.
  - If the phone has no fingerprint saved, Piso says to add one in the phone's settings.
  - Turning it off takes effect at once.
- **Lock screen:** when the switch is on, the phone's fingerprint prompt opens by itself as the lock screen appears. A **Use fingerprint** button brings it back. The prompt's **Use PIN** button closes it, and the PIN pad works as before.
- **PIN stays the way in:** if the fingerprint fails, is cancelled, or the phone blocks it after too many tries, Jan types the PIN.
- **Wrong PINs:** a fingerprint proves it is the owner, so it clears the wrong-PIN count and any wait, the same as the right PIN does.
- **Turned off for Jan's safety:**
  - **When a fingerprint is added to the phone:** the next unlock says so and asks for the PIN, and Jan can turn the switch on again in Settings. So someone who knows the phone's own screen lock can't add their finger and then open Piso.
  - **When the PIN is removed, or Piso is reset:** fingerprint unlock goes off. Setting a new PIN starts with it off.
  - **Changing the PIN:** keeps it on.

## How it works
- **Prompt:** Android's own fingerprint prompt (`android.hardware.biometrics.BiometricPrompt`). No new library and no network. The only new permission is `USE_BIOMETRIC`.
- **Strong sensors only:** only fingerprints and other biometrics that Android rates as strong are accepted.
- **Key:** turning the switch on makes an AES key in the Android Keystore that works only right after a fingerprint scan, with `setInvalidatedByBiometricEnrollment(true)`.
- **Unlock check:** the lock screen opens only after that key has been used successfully. When Android destroys the key (for example, a new fingerprint was added), Piso turns the switch off.
- **Saved state:** only an on/off flag (`fingerprint_unlock`) is saved, in the existing private profile preferences. Piso never sees or stores the fingerprint itself.
- **Code:**
  - `util/FingerprintAuth.kt`: the prompt and the key.
  - `UserProfileManager`: `setFingerprintUnlock` and `unlockWithFingerprint`. `removePin` and `createProfile` clear the flag.
  - The lock screen and Settings gain the button and the switch.

## Tests
`FingerprintUnlockTest` checks the following. The prompt and key need a real phone, so they are covered by the phone checks below.
- It can't be turned on without a PIN.
- When on, it shows in the profile and survives a restart.
- It can't open the lock screen when off.
- It opens the lock screen, clears wrong PINs and the wait, and the PIN still works afterwards.
- Turning it off means the PIN is needed again.
- Removing the PIN turns it off, and a new PIN starts with it off.
- A wrong PIN when removing the PIN keeps it on.
- Changing the PIN keeps it on.
- Reset and a new profile start with it off.
- Closing the prompt isn't reported as an error.
- Removing the key is safe where there is no key store.

## Phone checks
1. In Settings, turn on **Unlock with fingerprint**, touch the sensor, and see the switch on.
2. Leave Piso, come back, and see the prompt. Unlock with a finger.
3. Leave and come back again. Tap **Use PIN** and unlock with the PIN.
4. Try a finger that isn't saved, and see the prompt refuse it.
5. Add a new fingerprint in the phone's settings. Open Piso and see the message, then unlock with the PIN and see the switch off.
6. Remove the PIN and see the switch disappear.

## Out of scope
Face unlock on phones whose face sensor Android rates as weak, unlocking with the phone's own screen lock instead of the Piso PIN, and P12 (PBKDF2 for the PIN hash).
