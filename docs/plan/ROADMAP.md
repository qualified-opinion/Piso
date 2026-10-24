# Piso roadmap

Updated 2026-10-24. Main is at `cdb20cd` (B7). The latest release is v1.0.3 (2026-10-14), published by Jan, so B5 to B7 are merged but not released yet. Jan plans to install one APK once the open batches are done.

## What Piso is
Piso is Jan's private, offline personal finance tracker for Android, covering accounts, transactions, budgets, goals, debts and investments in pesos. It's built with Kotlin, Compose and Room, and it has a PIN lock, `FLAG_SECURE` and no network access. Backups are JSON export and import, and CSV export.

## In plain words
- **Done:** the first six batches of fixes are merged. Money totals are right, backups and restores are safe, the PIN lock holds, safety copies are cleaned up properly, and a set of small fixes is in (B1 to B6).
- **Done since:** B7 lets Jan record goal and debt moves on a past date.
- **In review:** B8 lets Jan take money out of a goal and correct an account balance without a fake income or expense.
- **Waiting on Jan:** B9 (lock-screen RESET) needs decision D1 below. B10 (archive old accounts, view past budgets) needs decision D4.
- **Not released:** v1.0.3 doesn't have B5 or B6 yet. Jan can ask for a signed APK whenever he wants them on his phone.

## Status table

| Batch | Branch | Scope | Status | Pack |
|---|---|---|---|---|
| Pre-plan | various | MVP, ViewModel split, polish, early review fixes | Merged, PRs #1 to #4 | none |
| B3 | `fix/piso-b3-lint-schemas` | P06 BOM and lint in CI, P05 real schemas and migration tests | Merged, PR #5, `09814a2` | review 1 |
| B1 | `fix/piso-b1-money` | P01, P04, P10 money fixes | Merged, PR #6, `240f7ef` | review 1 |
| B2 | `fix/piso-b2-backup` | P02, P14, P18 backup and restore safety | Merged, PR #7, `c428b76` | review 1 |
| B4 | `fix/piso-b4-lock` | P03, P07, P11 lock fixes | Merged, PR #8, `b78d388` | review 1 |
| Docs | `docs/piso-roadmap` | This folder, plus `AGENTS.md` and `CLAUDE.md` | Merged, PR #9, `a0c60b7` | none |
| B5 | `fix/piso-b5-safety-copies` | P20, P21, P22, P24 safety-copy hygiene | Merged, PR #10, `622df4f` | [batch5](handoffs/batch5-safety-copies.md) |
| B6 | `fix/piso-b6-small-fixes` | P15, P16, P25, P23, P26, U4, B5 leftover copy cleanup | Merged, PR #11, `3d4ce09` | [batch6](handoffs/batch6-small-fixes.md) |
| B7 | `feat/piso-b7-backdate` | P08 backdated goal and debt moves, U5 Activity message | Merged, PR #13, `cdb20cd` | [batch7](handoffs/batch7-backdate.md) |
| B8 | `feat/piso-b8-goal-out-adjust` | P09 take money out of a goal, plus Adjust balance (U1) | In review | [batch8](handoffs/batch8-goal-out-adjust.md) |
| B9 | `fix/piso-b9-lock-policy` | P13, depending on Jan's decision (P12 optional) | Needs decision D1 | none |
| B10 | `feat/piso-b10-schema` | U2 account archive, U3 past-month budgets (database version 4) | Needs decision D4 | none |

B1 to B4 ran in the order B3, B1, B2, B4. B7 goes before B8, because B8 reuses B7's date field.

## Item index
Review 1 (October 2026, [review-1](reviews/review-1-2026-10.md)) and review 2 (2026-10-23, [kiln-review-2](reviews/kiln-review-2.md)).

| ID | Sev | Item | State |
|---|---|---|---|
| P01 | P0 | Debt Remaining auto-fill | Fixed in B1 |
| P02 | P1 | Strict restore, safety copy and undo | Fixed in B2, undo made one-shot in B5 (P21) |
| P03 | P1 | Picker lock exemption | Fixed in B4 |
| P04 | P2 | Exact debt revert | Fixed in B1 |
| P05 | P2 | Real schemas and migration tests | Fixed in B3 |
| P06 | P2 | BOM fix and lint in CI | Fixed in B3 |
| P07 | P2 | Monotonic PIN lockout | Fixed in B4 (the `apply()` gap is hardening only) |
| P08 | P2 | Backdate goal and debt moves | Fixed in B7 |
| P09 | P2 | Take money out of a goal | Fixed in B8 |
| P10 | P3 | Goal completion updated by id | Fixed in B1 |
| P11 | P3 | Rate-limit every PIN check | Fixed in B4 |
| P12 | P3 | PIN key derivation (KDF) | Cut for now, optional in B9 |
| P13 | P3 | RESET from the lock screen | B9, decision D1 |
| P14 | P3 | Honest export result | Fixed in B2 |
| P15 | P3 | Budget edit keeps its month | Fixed in B6 |
| P16 | P3 | Allow ₱0 investment value | Fixed in B6 |
| P17 | P3 | Accounting time zone | Cut |
| P18 | P3 | Consistent export snapshot | Fixed in B2 |
| P19 | P3 | Money stored as `Double` | Cut |
| P20 | **P1** | Reset leaves the safety backups, so the lock can be bypassed through Undo restore | Fixed in B5 |
| P21 | P2 | Undo restore never goes away, has no confirmation and switches back and forth | Fixed in B5 |
| P22 | P2 | Clear All Data has no safety copy | Fixed in B5 |
| P23 | P3 | Import is parsed on the main thread with no size cap | Fixed in B6 |
| P24 | P3 | Clipboard backup isn't marked sensitive | Fixed in B5 |
| P25 | P3 | A debt payment with no account counts as spending | Fixed in B6 |
| P26 | P3 | Unused Secrets and Roborazzi plugins | Fixed in B6 |
| U1 | UX | Adjust balance without a fake transaction | Fixed in B8 |
| U2 | UX | Archive or hide old accounts | B10, decision D4 |
| U3 | UX | View past months' budgets | B10, decision D4 |
| U4 | UX | Tell Jan that Share Backup will need the PIN on return | Fixed in B6 (copy only) |
| U5 | UX | Activity says "Change this from the Goals tab" (or Debts), but neither tab can change a past move | Fixed in B7 (copy only) |

## Decisions Jan owes
Each pack uses the default below unless Jan says otherwise.

- **D1 (P13), lock-screen RESET.** Today anyone holding the phone can type RESET on the lock screen and wipe Piso, with no PIN. Since B5 this no longer exposes old data (RESET also deletes every safety copy), so the remaining risk is losing data, not someone reading it. The choices:
  - **Keep it as is.** Nothing to build. Jan is never stuck if he forgets the PIN, but a child or a stranger can wipe everything in seconds.
  - **Only offer RESET after 10 wrong PINs.** Small change. A casual tap can't wipe the data, and the existing lockout makes 10 wrong tries slow.
  - **Add a 24-hour wait.** Typing RESET starts a timer and the wipe happens only if Jan types it again a day later. Safest, but the most work and the slowest when Jan really is locked out.
  Recommended: the 10-wrong-PINs option, which removes the accidental wipe for little work. Default until Jan chooses: keep it as is, and B9 isn't started.
- **D2 (P24), clipboard backup export.** Settled in B5: kept and marked sensitive.
- **D3, paste-JSON import.** Keep it or remove it? Default: keep it.
- **D4 (U2 and U3), schema batch.** Is a database change for archiving old accounts and viewing past months' budgets worth doing? It's the only batch that changes how data is stored, so it needs a migration and is the riskiest. Default: not scheduled.

## Later ideas (not scheduled)
P12 PBKDF2 for the PIN hash (optional in B9). U2 and U3 moved to B10.

## Cut list
- **P17 time zone:** Jan lives in Manila, and the change would alter how dates are stored.
- **P19 integer centavos:** rounding on every write works, and no drift bug has been found. It's an L-sized migration.
- **P12 KDF:** the database isn't encrypted, so the PIN hash isn't what protects the data. It may come back as an option in B9.

## How work is run
1. Kiln writes a handoff pack, and Jan says go, directly or as "from Jan via Atlas".
2. Nightshift builds it on the pack's branch (Codex by default) and opens one PR that also updates this status table.
3. Kiln reviews the PR with a different engine (Claude Code for Codex PRs) and posts approve or changes needed in Coding.
4. Nightshift squash-merges pinned to the approved head, after green CI and Jan's OK.
5. Signed APKs are built only on request through `android-release.yml`. Jan tags and releases himself.

## Open verification (Jan's phone checks)
- No phone checks are recorded for v1.0.3 lock behaviour (picker return, Home during a picker, Share Backup relock). Report them if done.
- After B5: RESET on the lock screen, then set up with no PIN. Settings must not show Undo restore.
- After B8 (once released): take money out of a goal into another account and see both balances move; adjust a wallet up and down and see Spent unchanged; export CSV and see the adjustment's sign.
- After B7 (once released): add money to a goal dated last month and see it under last month in Activity; record a debt payment dated last month and see this month's Spent unchanged; tap a goal move in Activity and read the new message.
- After B6 (once released): edit a past month's budget and see it stay in that month; save a ₱0 investment; make a no-account debt payment and see Spent unchanged; pick a large non-backup file to import and see an error, not a freeze; read the Share Backup PIN note.

## First message for an agent starting alone
"Read `AGENTS.md` and `docs/plan/ROADMAP.md`. Take the first batch whose status is 'Pack ready, waiting on Jan's go', but only once Jan has said go. Follow its pack in `docs/plan/handoffs/`. Work on its branch, run `./gradlew test lintDebug assembleDebug`, open one PR that updates the status table, and don't merge."

## Facts a new owner needs
- The repo was renamed from `JanReins/Piso` to `qualified-opinion/Piso`. The package stays `com.janreins.piso`.
- Safety copies are stored as `filesDir/backups/pre-import-<19-digit ms>.json`. The last three are kept (`BackupSafety.kt`).
- The release key is Jan's ("Qualified Opinion, Cebu City"). Its secrets are in repo secrets and are never committed.
- The Room database is at version 3, with schemas in `app/schemas/`.
