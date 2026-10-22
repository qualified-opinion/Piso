# AGENTS.md

Start with `docs/plan/ROADMAP.md`. It holds the status table, the next batch and the decisions Jan still owes.

## Project
Piso (`qualified-opinion/Piso`, formerly `JanReins/Piso`) is Jan's private, 100% offline personal finance tracker for Android. Kotlin, Jetpack Compose, Material 3, Room (`piso_database`). Package and application id: `com.janreins.piso`. minSdk and targetSdk are in `app/build.gradle.kts`.

## Hard rules
- Offline only. No network calls, no `INTERNET` permission, no cloud sync, no analytics.
- Privacy: keep `FLAG_SECURE`, `allowBackup="false"` and the backup and data-extraction rules that exclude everything. Never log money amounts or PINs.
- Data safety: anything that deletes or replaces user data needs a safety copy or an explicit confirmation, plus a test.
- Schema: any Room change needs a new migration, a committed schema JSON under `app/schemas/`, and a `DatabaseMigrationTest` case.
- Money: round to centavos on every write (amounts are `Double` by design; see the ROADMAP cut list).
- PIN and lock: `UserProfileManager` hashing and lockout and `PickerLockExemption` change only when a handoff pack says so, with tests.

## Build and test (what CI runs)
`./gradlew test lintDebug assembleDebug assembleRelease` (`.github/workflows/android-ci.yml`).

## Process
- Work only from a handoff pack in `docs/plan/handoffs/`, on a feature branch. Open one PR per batch.
- Never push to main, force-push, tag or create GitHub Releases. Jan releases himself. A signed APK is built only on request with the manual `android-release.yml` workflow.
- Every batch PR updates the status table in `docs/plan/ROADMAP.md`.
- Review uses a different engine than the builder. Merge is a squash pinned to the approved head, after green CI and Jan's OK.
