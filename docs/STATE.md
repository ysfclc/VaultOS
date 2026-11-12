# State Ledger
_Last updated: 2026-11-12 by Continuity Engineer — session 2_

## 1. Product snapshot
VaultOS is a local-first Android file manager and personal content library for Android 9+. It combines a non-destructive index of existing shared storage with a private archive for incoming and newly detected content, a searchable/organizable Room catalogue, file operations, an optional capture keyboard, a DocumentsProvider, app/APK catalogue, and manual SAF backup/restore. “Complete” means data-safe operation, tested restore/migration compatibility, reliable everyday UX on the target Redmi Note 12 Pro 5G, and a reproducible signed release. Build success alone is not device acceptance.

## 2. Status board
| Area | State | Notes |
|---|---|---|
| Share/import and private archive | 🟡 partial | Implemented; no direct repository-level regression suite or physical share acceptance recorded |
| Storage index and catch-up scan | 🟡 partial | Resumable scan and MediaStore tracking implemented; Xiaomi timing/device behavior unverified |
| Unified library, search, organization | 🟡 partial | Implemented; database and UI integration coverage is thin |
| File operations and ZIP safety | ✅ done | Core operations have 10 JVM regression tests; device UX acceptance remains separate |
| Clipboard history and capture IME | 🟡 partial | Implemented; Android selected-IME boundary applies; physical app matrix open |
| Keyboard suggestions | 🟡 partial | KBD-1 source changes committed: cached IME-lifetime typing memory, bounded correction ranking and candidate-view reuse; regression tests and 500-word benchmark harness added; execution/hosted verification pending; device latency/prediction acceptance open |
| Metadata extraction | 🟡 partial | Parser tests exist; end-to-end extraction/provider behavior lacks integration tests |
| Backup and restore | 🟡 partial | v1/v2 paths implemented; no automated round-trip, corruption, or migration test suite identified |
| DocumentsProvider | 🟡 partial | Implemented; no provider interoperability test evidence |
| Signed CI/release | ✅ done | 0.13.0 hosted run 42261718395 passed and published private draft-release assets |
| Physical-device acceptance | ⚪ not started | 1.0.0 gate remains open for Redmi Note 12 Pro 5G |
| Signing-material remediation | 🔴 blocked | GOV-001 needs user-provisioned replacement material and verified update-lineage plan |

## 3. Currently in flight
- 1.0.0 acceptance — automated CI is green; Redmi physical-device matrix and data migration/restore evidence remain open.
- GOV-001 signing migration — blocked on replacement keystore/secrets and an update-continuity decision; preserve the existing key until migration is proven.
- Keyboard V2 — KBD-1 responsiveness implementation is in progress; KBD-1 hosted verification/microbenchmark remain, then KBD-2 contextual prediction; see docs/KEYBOARD-V2.md.
- Test-coverage uplift — backup/restore, Room migrations, scan worker, clipboard repository, and DocumentsProvider currently lack identified automated integration tests.

## 4. Known gaps & stubs
- VaultBackupManager.kt:22,136 — backup/restore exists, but no automated v1/v2 round-trip, corrupt-manifest, checksum-failure, or path-safety tests were found.
- VaultDatabase.kt:403 — Room database/migrations have no identified migration regression suite.
- StorageScanWorker.kt:18 and VaultRepository.kt:135,243 — indexing/scanning behavior has no identified worker/repository test suite.
- ClipboardHistoryRepository.kt:25,52,76 — clipboard event capture/readback has no identified repository-level tests.
- VaultDocumentsProvider.kt:26 — system provider behavior has no identified automated interoperability tests.
- Device acceptance steps in docs/DEVICE-TEST-PLAN.md remain unrecorded; CI is not a substitute.
- Backups are not encrypted by VaultOS; the destination is user-selected.
- The historical signing material remains tracked and must not be removed or regenerated without an approved migration plan.

## 5. Environment & how to run
- JDK 17; Android SDK Platform 35; Build Tools 35.0.0; Gradle 8.9.
- Android Gradle Plugin 8.7.3; Kotlin 2.0.21; Java/Kotlin target 17; minSdk 28; targetSdk 35.
- Build/test: gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleRelease.
- Debug APK: app/build/outputs/apk/debug/app-debug.apk; release APK: app/build/outputs/apk/release/app-release.apk.
- GitHub Actions workflow: .github/workflows/android-build.yml; root android-build.yml is its mirror. The workflow validates, tests, lints, builds, verifies signatures, and publishes APKs/checksums/build metadata on a private draft release.
- No checked-in Gradle wrapper was found. The local runtime used for this session has no mounted /testbed checkout; repository inspection/writes are through the connected GitHub repository.

## 6. Next three increments
1. Finish KBD-1: obtain hosted CI evidence, execute the 500-word correction benchmark, and record p50/p95; no device-latency claim until Redmi measurement.
2. KBD-2: add opt-in, bounded contextual trigram learning while preserving existing word/bigram data and privacy boundaries.
3. KBD-3/KBD-4: refine candidate UX, then measure EN/PL/TR latency and prediction usefulness on the Redmi Note 12 Pro 5G. Resume backup/restore integrity tests after the keyboard predictor has a verified baseline.

## 7. Open questions for the human
- [ ] When will replacement signing material and GitHub Actions secrets be available for GOV-001? Default: do not rotate or delete the existing identity; keep the remediation blocked.
- [ ] Can the Redmi Note 12 Pro 5G be used for the physical acceptance matrix, and what Android/MIUI build is installed? Default: device verification remains pending; do not infer results.

## Session Handoff

**Done this session**
- Implemented KBD-1 source changes: one cached LocalTypingMemory per IME service lifetime; parsed word/pair snapshots and dictionary word list cached in memory and invalidated on preference changes.
- Bounded local correction ranking to 120 length-compatible candidates, prioritized same-initial candidates with a fallback pool, and replaced full edit-distance matrices with bounded-memory Damerau–Levenshtein plus early exit.
- Reused the three candidate cells and separators across keystrokes instead of rebuilding the candidate strip each time.
- Added correction limit, adjacent-transposition and low-noise regression test cases, plus a repeatable 500-word JVM benchmark (150 warmups, 1,000 samples, p50/p95 output).

**State changed**
- app/src/main/java/com/vaultos/app/keyboard/LocalTypingMemory.kt — parsed SharedPreferences snapshots and dictionary list caching.
- app/src/main/java/com/vaultos/app/keyboard/KeyboardSuggestionEngine.kt — bounded correction candidate pool and bounded-memory distance.
- app/src/main/java/com/vaultos/app/keyboard/VaultKeyboardService.kt — reuse typing-memory instance for the IME lifetime and update candidate strip views in place.
- app/src/test/java/com/vaultos/app/keyboard/KeyboardSuggestionEngineTest.kt — new correction regression cases.
- app/src/test/java/com/vaultos/app/keyboard/KeyboardSuggestionEngineBenchmarkTest.kt — repeatable correction latency benchmark.
- docs/KEYBOARD-V2.md, docs/TASKS.md, docs/PROJECT-STATUS.md — implementation and remaining verification recorded.
- docs/STATE.md — current ledger and handoff.

**Verified**
- Repository writes completed and source files re-fetched successfully.
- Service uses the shared lazy typingMemory property; no per-render LocalTypingMemory(this) constructions remain.
- Test source contains the added cases and benchmark harness. Neither tests nor benchmark have been executed in this session.
- The code commit's combined status exposes a failing Vercel check pointing to Vercel's build-rate limit; this is not an Android build result. No GitHub Actions Android run result is exposed by the available inspection actions. No Android build, lint, test-pass or APK claim is made.
- No Redmi Note 12 Pro 5G device measurement was possible.

**Not done / next**
1. Confirm the automatically triggered GitHub Actions run for the code changes; fix any compile/test/lint failures.
2. Execute the 500-word correction benchmark in hosted CI and record p50/p95; a valid pre-change comparison is not available yet.
3. Then proceed to KBD-2 two-word contextual prediction and bounded trigram learning.

**Open questions**
- None blocking for code review/CI. Physical Redmi acceptance still requires the user's device.

**Risks / landmines**
- Preserve the existing SharedPreferences keys and JSON format; no migration or data deletion is intended.
- Learning remains opt-in; safe-editor exclusions remain unchanged; no network or cloud processing was added.
- Candidate ranking and the Android view row still execute on the IME main thread. This increment removes repeated parsing and bounds fuzzy work, but does not yet move all ranking off-main-thread or prove latency improvement.
- Do not mark KBD-1 or keyboard V2 complete until CI, benchmark and Redmi device acceptance are recorded.
