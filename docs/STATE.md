# State Ledger
_Last updated: 2026-11-12 by Continuity Engineer — session 5_

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
| Keyboard suggestions | 🟡 partial | KBD-1/KBD-2 source implemented: cached typing memory, bounded async correction, stale-result gate, candidate-view reuse and opt-in EN/PL/TR trigram/two-word context; 24 JVM tests + six Android instrumentation tests and 500-word benchmark harness added; emulator execution/benchmark and Redmi acceptance open |
| Metadata extraction | 🟡 partial | Parser tests exist; end-to-end extraction/provider behavior lacks integration tests |
| Backup and restore | 🟡 partial | v1/v2 paths implemented; no automated round-trip, corruption, or migration test suite identified |
| DocumentsProvider | 🟡 partial | Implemented; no provider interoperability test evidence |
| Signed CI/release | ✅ done | 0.13.0 hosted run 42261718395 passed and published private draft-release assets |
| Physical-device acceptance | ⚪ not started | 1.0.0 gate remains open for Redmi Note 12 Pro 5G |
| Signing-material remediation | 🔴 blocked | GOV-001 needs user-provisioned replacement material and verified update-lineage plan |

## 3. Currently in flight
- 1.0.0 acceptance — automated CI is green; Redmi physical-device matrix and data migration/restore evidence remain open.
- GOV-001 signing migration — blocked on replacement keystore/secrets and an update-continuity decision; preserve the existing key until migration is proven.
- Keyboard V2 — KBD-1/KBD-2 source implementation is committed, including asynchronous bounded correction and stale-result rejection; hosted Android/instrumentation verification, benchmark execution and candidate UX remain; see docs/KEYBOARD-V2.md.
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
1. Verify the full Android build, lint, JVM suite and API 35 instrumentation suite; execute the 500-word correction benchmark and record p50/p95.
2. Obtain hosted Android build/test/lint/instrumentation evidence and execute the 500-word benchmark; fix any emulator or device-specific failures.
3. KBD-3 candidate UX and KBD-4 EN/PL/TR Redmi Note 12 Pro 5G acceptance. Resume backup/restore integrity tests after the keyboard predictor has a verified baseline.

## 7. Open questions for the human
- [ ] When will replacement signing material and GitHub Actions secrets be available for GOV-001? Default: do not rotate or delete the existing identity; keep the remediation blocked.
- [ ] Can the Redmi Note 12 Pro 5G be used for the physical acceptance matrix, and what Android/MIUI build is installed? Default: device verification remains pending; do not infer results.

## Session Handoff

**Done this session**
- KBD-1: cached one LocalTypingMemory per IME lifetime; cached parsed word/pair snapshots and dictionary words; reused candidate strip cells; bounded correction ranking to 120 candidates with bounded-memory Damerau–Levenshtein.
- KBD-1: added correction regression cases and a repeatable 500-word JVM benchmark (150 warmups, 1,000 samples, p50/p95 output).
- KBD-2: added opt-in per-language trigram counts capped at 1,200 contexts, preserving existing v1 word/bigram keys and fallback behavior.
- KBD-2: added transient two-word safe-editor context, cursor-navigation refresh, sentence-boundary resets, comma/semicolon continuation, and EN/PL/TR built-in phrase priors.
- Extracted ContextualPredictionRanker and added tests for trigram precedence, bigram fallback, candidate limits, and two-word priors.
- Added ADR-010 and updated keyboard product/task/status documentation.

**State changed**
- app/src/main/java/com/vaultos/app/keyboard/LocalTypingMemory.kt — bounded trigram storage, lookup and preference invalidation.
- app/src/main/java/com/vaultos/app/keyboard/ContextualPredictionRanker.kt — pure deterministic trigram/bigram precedence helper.
- app/src/main/java/com/vaultos/app/keyboard/KeyboardSuggestionEngine.kt — EN/PL/TR two-word phrase priors.
- app/src/main/java/com/vaultos/app/keyboard/VaultKeyboardService.kt — two-word context tracking, safe editor cursor reads, sentence/cursor reset, comma/semicolon continuation and reduced reads during composition.
- app/src/test/java/com/vaultos/app/keyboard/KeyboardSuggestionEngineTest.kt — 21 JVM test cases including context and ranker cases.
- app/src/test/java/com/vaultos/app/keyboard/KeyboardSuggestionEngineBenchmarkTest.kt — repeatable correction benchmark with 500 personal words.
- docs/KEYBOARD-V2.md, docs/TASKS.md, docs/PROJECT-STATUS.md, docs/DECISIONS.md — implementation and remaining gates recorded.
- docs/STATE.md — this ledger and handoff.

**Verified**
- Changed source/docs re-fetched from GitHub; the IME service uses the shared typing-memory instance and does not instantiate LocalTypingMemory during render.
- Four standalone Kotlin checks for ContextualPredictionRanker and four checks for the cursor-context regex (including comma/semicolon preservation and sentence-stop reset) compiled and passed locally with kotlinc 1.9.0. These are narrow helper checks, not the Android/JUnit suite.
- The test source currently contains 21 @Test methods; none of the Android/JVM project tests have been executed in this session.
- The benchmark harness is committed but has not been executed; no p50/p95 performance result is claimed.
- The combined status visible for the latest app-source commit exposes only a failing Vercel check pointing to Vercel's build-rate limit. The available workflow-run query returns no PR-triggered runs; it does not expose the push-triggered Android run. No Android build, lint or APK success is claimed.
- No Redmi Note 12 Pro 5G device measurement was possible.

**Not done / next**
1. Obtain the push-triggered Android Actions run evidence and fix any build/test/lint errors; run the 500-word benchmark and record output.
2. Add Android-backed LocalTypingMemory tests for opt-in, trigram persistence, clear/reset and v1 bigram compatibility. (Implemented in session 5; execution pending.)
3. Move candidate ranking off the IME main thread with generation-token stale-result rejection; then proceed to KBD-3 visual/interaction work and KBD-4 Redmi acceptance.

**Open questions**
- None blocking for source implementation. Android CI run visibility and physical Redmi acceptance remain outstanding.

**Risks / landmines**
- Preserve existing SharedPreferences word/bigram keys and JSON values; the new trigram key is additive.
- Learning remains opt-in. Only safe text fields are read; at most 120 characters before the cursor are examined transiently. No sentence/editor text is persisted and no network processing was added.
- Local fuzzy correction ranking now runs asynchronously; contextual prediction and other small candidate assembly work still execute on the IME thread. This does not yet prove typing latency or remove all main-thread computation.
- Do not mark the keyboard complete until Android tests, benchmark, async stale-result protection and Redmi device acceptance are recorded.


## Session 4 — asynchronous keyboard correction ranking (2026-11-12)

**Done**
- Moved bounded local fuzzy correction ranking out of `renderSuggestions()` and onto one reusable single-thread executor.
- Kept immediate typed-word and prefix-completion candidates visible while the worker calculates corrections.
- Added `SuggestionRequestGate`; each render invalidates older work and each request receives a fresh token.
- Before applying a worker result, the main-thread callback checks token, input-session generation, exact typed word, selected language, candidate-row identity, safe-editor status and whether newer SpellChecker results have arrived.
- Clear pending queued tasks on new requests; invalidate pending tokens and shut down the executor on service destruction. Clear the correction cache when learning changes, the user clears personal typing data, or the keyboard language changes.
- Added three pure JVM tests for newest-request precedence and invalidation. Independently compiled and executed the gate helper with kotlinc; all checks passed.

**Files changed**
- `app/src/main/java/com/vaultos/app/keyboard/VaultKeyboardService.kt` — worker lifecycle, immutable vocabulary snapshot, async correction request and guarded main-thread apply.
- `app/src/main/java/com/vaultos/app/keyboard/SuggestionRequestGate.kt` — new generation-token helper.
- `app/src/test/java/com/vaultos/app/keyboard/SuggestionRequestGateTest.kt` — three regression tests.
- `docs/TASKS.md`, `docs/KEYBOARD-V2.md`, `docs/PROJECT-STATUS.md` — updated delivery and verification state.

**Verification**
- GitHub source re-fetched and checked for worker, generation guard, lifecycle shutdown and removal of the synchronous correction call from `renderSuggestions()`.
- Standalone Kotlin compile/run passed for the generation-gate helper: latest-request precedence, invalidation and current-token behavior.
- No Android build, lint, full JUnit suite or benchmark result is claimed. The available CI status shows a successful Vercel check only; no Android Actions run is exposed by the available workflow query.
- No Redmi Note 12 Pro 5G measurement is available.

**Remaining**
1. Verify Android build, lint and full JVM tests in hosted Actions; execute and record the 500-word benchmark.
2. Add Android-backed typing-memory tests for opt-in, persistence, clear/reset and v1 compatibility.
3. Proceed to KBD-3 candidate UX and KBD-4 physical EN/PL/TR acceptance only after the verified baseline.

**Risk note**
- The worker only performs pure correction ranking against an immutable word-list snapshot. All `InputConnection` reads/writes and all candidate view updates remain on the IME main thread. Do not mark the keyboard complete without hosted verification and Redmi acceptance.


## Session 5 — private typing-memory instrumentation (2026-11-12)

**Done**
- Added six Android-backed tests using AndroidX instrumentation and the real app sandbox SharedPreferences implementation.
- Covered opt-in default, word/bigram/trigram persistence across new LocalTypingMemory instances, opt-out without data deletion, clear/reset while preserving the opt-in choice, EN/PL/TR isolation and legacy v1 word/bigram JSON fallback.
- Source review found that the shared normalizer rejected one-letter words, preventing common context tokens such as English "I" from contributing to learned bigrams/trigrams. Changed the minimum accepted word length to one; punctuation-only and over-32-character tokens remain rejected.
- Added AndroidX instrumentation dependencies and configured the Android Actions workflow to run `connectedDebugAndroidTest` on an API 35 Google APIs emulator.

**Files changed**
- `app/src/androidTest/java/com/vaultos/app/keyboard/LocalTypingMemoryInstrumentedTest.kt` — six persistence/privacy/compatibility tests.
- `app/src/main/java/com/vaultos/app/keyboard/LocalTypingMemory.kt` — one-letter context token support.
- `app/build.gradle.kts` — AndroidX test dependencies.
- `.github/workflows/android-build.yml` — API 35 emulator instrumentation step.
- `docs/TASKS.md`, `docs/KEYBOARD-V2.md`, `docs/PROJECT-STATUS.md` — delivery and verification state.

**Verification**
- Re-fetched source, tests, Gradle configuration and workflow from GitHub to verify committed content.
- Confirmed the instrumentation workflow step follows the existing unit-test/lint/APK build step and runs `connectedDebugAndroidTest`.
- No Android emulator run has completed in the evidence available to this session. No Android build, instrumentation, lint, APK or benchmark pass is claimed.
- No physical Redmi Note 12 Pro 5G acceptance is claimed.

**Next**
1. Inspect the API 35 Android Actions run; fix compilation, emulator or instrumentation failures.
2. Run and record the 500-word correction benchmark p50/p95.
3. Add any missing editor-safe-field integration tests, then move to KBD-3 candidate UX only after the verified keyboard baseline.

**Risk note**
- Existing `words_<lang>` and `pairs_<lang>` preference keys and JSON values remain unchanged. Trigram storage is additive. Learning remains opt-in; clearing personal typing memory preserves the opt-in toggle but removes all stored personal models.
