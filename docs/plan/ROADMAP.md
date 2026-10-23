# Piso roadmap

Updated 2026-10-23 by Kiln. Main is at `b78d388`. The latest release is v1.0.3 (2026-10-14), published by Jan.

## What Piso is
Piso is Jan's private, offline personal finance tracker for Android, covering accounts, transactions, budgets, goals, debts and investments in pesos. It's built with Kotlin, Compose and Room, and it has a PIN lock, `FLAG_SECURE` and no network access. Backups are JSON export and import, and CSV export.

## Status table

| Batch | Branch | Scope | Status | Pack |
|---|---|---|---|---|
| Pre-plan | various | MVP, ViewModel split, polish, early review fixes | Merged, PRs #1 to #4 | none |
| B3 | `fix/piso-b3-lint-schemas` | P06 BOM and lint in CI, P05 real schemas and migration tests | Merged, PR #5, `09814a2` | review 1 |
| B1 | `fix/piso-b1-money` | P01, P04, P10 money fixes | Merged, PR #6, `240f7ef` | review 1 |
| B2 | `fix/piso-b2-backup` | P02, P14, P18 backup and restore safety | Merged, PR #7, `c428b76` | review 1 |
| B4 | `fix/piso-b4-lock` | P03, P07, P11 lock fixes | Merged, PR #8, `b78d388` | review 1 |
| Docs | `docs/piso-roadmap` | This folder, plus `AGENTS.md` and `CLAUDE.md` | Not started | none |
| B5 | `fix/piso-b5-safety-copies` | P20, P21, P22, P24 safety-copy hygiene | Merged, PR #10 (622df4f) | [batch5](handoffs/batch5-safety-copies.md) |
| B6 | `fix/piso-b6-small-fixes` | P15, P16, P25, P23, P26, U4, B5 leftover copy cleanup | In progress | [batch6](handoffs/batch6-small-fixes.md) |
| B7 | `feat/piso-b7-backdate` | P08 backdated goal and debt moves | Outline only | none |
| B8 | `feat/piso-b8-goal-out-adjust` | P09 take money out of a goal, plus Adjust balance (U1) | Outline only | none |
| B9 | `fix/piso-b9-lock-policy` | P13, depending on Jan's decision (P12 optional) | Needs decision D1 | none |

B1 to B4 ran in the order B3, B1, B2, B4.

## Item index
Review 1 (October 2026, [review-1](reviews/review-1-2026-10.md)) and review 2 (2026-10-23, [kiln-review-2](reviews/kiln-review-2.md)).

| ID | Sev | Item | State |
|---|---|---|---|
| P01 | P0 | Debt Remaining auto-fill | Fixed in B1 |
| P02 | P1 | Strict restore, safety copy and undo | Fixed in B2, but undo has problems (P21) |
| P03 | P1 | Picker lock exemption | Fixed in B4 |
| P04 | P2 | Exact debt revert | Fixed in B1 |
| P05 | P2 | Real schemas and migration tests | Fixed in B3 |
| P06 | P2 | BOM fix and lint in CI | Fixed in B3 |
| P07 | P2 | Monotonic PIN lockout | Fixed in B4 (the `apply()` gap is hardening only) |
| P08 | P2 | Backdate goal and debt moves | B7 |
| P09 | P2 | Take money out of a goal | B8 |
| P10 | P3 | Goal completion updated by id | Fixed in B1 |
| P11 | P3 | Rate-limit every PIN check | Fixed in B4 |
| P12 | P3 | PIN key derivation (KDF) | Cut for now, optional in B9 |
| P13 | P3 | RESET from the lock screen | B9, decision D1 |
| P14 | P3 | Honest export result | Fixed in B2 |
| P15 | P3 | Budget edit keeps its month | B6 |
| P16 | P3 | Allow ₱0 investment value | B6 |
| P17 | P3 | Accounting time zone | Cut |
| P18 | P3 | Consistent export snapshot | Fixed in B2 |
| P19 | P3 | Money stored as `Double` | Cut |
| P20 | **P1** | Reset leaves the safety backups, so the lock can be bypassed through Undo restore | Fixed in B5 |
| P21 | P2 | Undo restore never goes away, has no confirmation and switches back and forth | Fixed in B5 |
| P22 | P2 | Clear All Data has no safety copy | Fixed in B5 |
| P23 | P3 | Import is parsed on the main thread with no size cap | B6 |
| P24 | P3 | Clipboard backup isn't marked sensitive | Fixed in B5 |
| P25 | P3 | A debt payment with no account counts as spending | B6 |
| P26 | P3 | Unused Secrets and Roborazzi plugins | B6 |
| U1 | UX | Adjust balance without a fake transaction | B8 |
| U2 | UX | Archive or hide old accounts | Later (needs a schema change) |
| U3 | UX | View past months' budgets | Later (may need a schema change) |
| U4 | UX | Tell Jan that Share Backup will need the PIN on return | B6 (copy only) |

## Decisions Jan owes
Each pack uses the default below unless Jan says otherwise.

- **D1 (P13), lock-screen RESET.** The options are to keep it as is, only offer it after 10 failed PINs, or add a 24-hour delay. Default: keep it as is until B9. B5 makes RESET delete every safety copy, so it can't be used to reach old data.
- **D2 (P24), clipboard backup export.** The options are to keep it and mark it sensitive, or remove it. Default: keep it and mark it sensitive.
- **D3, paste-JSON import.** Keep it or remove it? Default: keep it.
- **D4 (U2 and U3), schema batch.** Is a schema change for archiving accounts and viewing past budgets worth doing? Default: not scheduled.

## Later ideas (not scheduled)
U2 account archive, U3 past-month budgets, and P12 PBKDF2 for the PIN hash.

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

## First message for an agent starting alone
"Read `AGENTS.md` and `docs/plan/ROADMAP.md`. Take the first batch whose status is 'Waiting on Jan's go' or later, but only once Jan has said go. Follow its pack in `docs/plan/handoffs/`. Work on its branch, run `./gradlew test lintDebug assembleDebug`, open one PR that updates the status table, and don't merge."

## Facts a new owner needs
- The repo was renamed from `JanReins/Piso` to `qualified-opinion/Piso`. The package stays `com.janreins.piso`.
- Safety copies are stored as `filesDir/backups/pre-import-<19-digit ms>.json`. The last three are kept (`BackupSafety.kt`).
- The release key is Jan's ("Qualified Opinion, Cebu City"). Its secrets are in repo secrets and are never committed.
- The Room database is at version 3, with schemas in `app/schemas/`.
