# State Ledger
_Last updated: 2026-11-15 by Continuity Engineer — release hygiene checkpoint_

## 1. Product snapshot
VaultOS is a local-first personal capture and retrieval layer for Android 9+: capture information and files from supported Android entry points, preserve complete payloads privately, connect them to one catalogue, retrieve them through global search and reuse surfaces, and recover them through tested backup/restore. The keyboard is optional infrastructure, not the product definition. `docs/PRODUCT-NORTH-STAR.md` is the durable source of truth for vision and roadmap trade-offs. “Complete” means data-safe operation, tested restore/migration compatibility, reliable everyday UX on the target Redmi Note 12 Pro 5G, and a reproducible signed release. Build success alone is not device acceptance.

## 2. Status board
| Area | State | Notes |
|---|---|---|
| Share/import and private archive | 🟡 partial | Four Android-backed capture/retrieval tests now exist for complete UTF-8 round-trip, beyond-preview search, dedupe and tamper rejection; emulator execution and physical share acceptance remain pending |
| Storage index and catch-up scan | 🟡 partial | Resumable scan and MediaStore tracking implemented; Xiaomi timing/device behavior unverified |
| Unified library, search, organization | 🟡 partial | Implemented; database and UI integration coverage is thin |
| File operations and ZIP safety | ✅ done | Core operations have 10 JVM regression tests; device UX acceptance remains separate |
| Clipboard history and capture IME | 🟡 partial | Gallery and capture flow implemented; event-level UTF-8 byte/hash readback guard plus five pure JVM regression cases added; Room-backed repository tests and physical app matrix remain open |
| Keyboard suggestions | 🟡 partial | KBD-1/KBD-2 source implemented; 43 JVM tests and 500-word host benchmark p50/p95=105/160 μs previously passed; six Android typing-memory tests plus four core repository tests are now in the instrumentation suite; API 35 execution pending; Redmi acceptance open |
| Metadata extraction | 🟡 partial | Parser tests exist; end-to-end extraction/provider behavior lacks integration tests |
| Backup and restore | 🟡 partial | v1/v2 paths implemented; no automated round-trip, corruption, or migration test suite identified |
| DocumentsProvider | 🟡 partial | Implemented; no provider interoperability test evidence |
| Signed CI/release | ✅ done | Vercel fallback assembled and verified the signed 0.13.0 release APK; GitHub hosted-runner provisioning remains unresolved |
| Physical-device acceptance | ⚪ not started | 1.0.0 gate remains open for Redmi Note 12 Pro 5G |
| Signing-material remediation | 🔴 blocked | GOV-001 needs user-provisioned replacement material and verified update-lineage plan |

## 3. Currently in flight
- 1.0.0 acceptance — Vercel build/test/lint/release verification is green; GitHub hosted-runner provisioning and Redmi physical-device matrix/data migration/restore evidence remain open.
- GOV-001 signing migration — blocked on replacement keystore/secrets and an update-continuity decision; preserve the existing key until migration is proven.
- Keyboard V2 — KBD-1/KBD-2 source is committed; JVM suite and 500-word host benchmark pass; candidate UX and Redmi acceptance remain; see docs/KEYBOARD-V2.md. The API 35 emulator now boots, but the test script was failing before tests because it invoked Bash-only `set -o pipefail` under `/usr/bin/sh`; both workflow mirrors now use POSIX `set -eu`. The next final-head run must prove tests actually execute.
- Core capture/retrieval — `VaultRepositoryInstrumentedTest` adds four Android-backed tests against real Room + private files: multilingual long-text fidelity, search beyond preview, duplicate archive reuse and same-length tamper rejection. Source is committed; no emulator pass is claimed yet. Backup/restore, Room migrations, scan worker, clipboard repository and DocumentsProvider still need integration coverage; see Phase 19 in docs/TASKS.md.

## 4. Known gaps & stubs
- VaultBackupManager.kt:22,136 — backup/restore exists, but no automated v1/v2 round-trip, corrupt-manifest, checksum-failure, or path-safety tests were found.
- VaultDatabase.kt:403 — Room database/migrations have no identified migration regression suite.
- StorageScanWorker.kt:18 and VaultRepository.kt:135,243 — indexing/scanning behavior has no identified worker/repository test suite.
- ClipboardHistoryRepository.kt — Room-backed capture/history repository tests are still missing. ClipboardPayloadIntegrity.kt now has five pure JVM regression tests for event-level readback validation.
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
1. Complete the latest GitHub Actions run after the API 35 emulator cold-boot/readiness-check fix; confirm all six Android-backed tests actually execute and pass.
2. Contain GOV-001: restore private repository visibility, then design and test signing-certificate rotation/lineage without breaking updates to existing installations.
3. Merge the consolidated keyboard PR only after CI and security gates; then install over the existing app and complete KBD-3 candidate UX plus KBD-4 EN/PL/TR Redmi Note 12 Pro 5G acceptance.

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
- GitHub Actions run [162](https://github.com/ysfclc/VaultOS/actions/runs/43226760701) and three failed-job attempts all ended without step summaries or downloadable logs (`BlobNotFound`). The latest retry also failed before exposing a `Set up job` step. The same workflow's known-good run [42261718395](https://github.com/ysfclc/VaultOS/actions/runs/42261718395) exposes a normal hosted-runner startup and completed all 13 build steps, confirming the earlier workflow baseline could provision a runner. The current failure remains outside actionable workflow-step output; no root cause is inferred. No Android build, instrumentation, lint, APK or benchmark pass is claimed.
- No physical Redmi Note 12 Pro 5G acceptance is claimed.

**Next**
1. Diagnose the GitHub Actions runner/job startup failure; once a job exposes steps/logs, fix any compilation, emulator or instrumentation failures.
2. Run and record the 500-word correction benchmark p50/p95.
3. Add any missing editor-safe-field integration tests, then move to KBD-3 candidate UX only after the verified keyboard baseline.

**Risk note**
- Existing `words_<lang>` and `pairs_<lang>` preference keys and JSON values remain unchanged. Trigram storage is additive. Learning remains opt-in; clearing personal typing memory preserves the opt-in toggle but removes all stored personal models.


## Session 6 — Android Actions startup investigation (2026-11-12)

**Done**
- Re-fetched the current API 35 instrumentation test, AndroidX dependencies, workflow and state ledger from GitHub.
- Compared the failed run 162 with known-good run 42261718395. The known-good run exposes a normal Ubuntu 24.04 hosted runner, action downloads, all 13 build steps and successful signed APK publication.
- Re-ran the failed job once more. The third failed-job attempt again completed without step summaries; its log download returned GitHub's `BlobNotFound` response. No `Set up job` step was exposed.
- Reviewed the six instrumentation test cases and dependency declarations against the app's applicationId and configured AndroidX runner. Source review found no clear compile/API mismatch; this is not a substitute for executing the tests.

**Verification**
- The workflow still runs the existing clean/unit-test/lint/debug+release build before the API 35 emulator step. The new emulator action is configured with API 35, Google APIs, x86_64 and Pixel 2 profile.
- No local Android SDK, Gradle executable or mounted `/testbed` checkout is available in this runtime. The Android tests cannot be executed locally here.
- The runner failure occurs before step output is available. It cannot currently be attributed to Kotlin, Gradle dependencies, emulator boot or test assertions.
- No Android build, lint, unit-test, instrumentation, APK or benchmark success is claimed for the latest source.

**Next**
1. Resolve GitHub Actions runner provisioning/visibility or obtain an actionable job log; do not keep blindly retrying run 162.
2. Once a runner starts, execute the full unit/lint/build sequence and API 35 `connectedDebugAndroidTest`.
3. Execute the 500-word correction benchmark and record p50/p95; keep Redmi physical acceptance open.

**Risk note**
- The known-good hosted run predates the new emulator step. It validates the previous build pipeline only, not the current instrumentation workflow.

## Session 7 — Split build and emulator CI; runner allocation evidence (2026-11-12)

**Done**
- Split the former single Android workflow into independent jobs: the existing signed build/test/lint/APK publication job and a read-only API 35 instrumentation job. The emulator suite can no longer block APK publication once the build runner is available.
- Kept the API 35 Google APIs emulator configuration and connectedDebugAndroidTest command unchanged.
- Set the instrumentation job's token permission to contents: read; the APK publishing job retains the workflow's required contents: write.
- The workflow update triggered run [163](https://github.com/ysfclc/VaultOS/actions/runs/43261340442).

**Verification and diagnosis**
- Run 163 created both expected jobs: Test, lint and build APK and Android API 35 instrumentation.
- Both jobs failed approximately two seconds after creation, before any step executed.
- GitHub's raw job records for both jobs show runner_id: 0, empty runner_name, and steps: []. This is direct evidence that no hosted runner was assigned to either job in this run.
- This is not a Gradle, Kotlin, dependency, test assertion or emulator-boot failure based on the available evidence. The underlying GitHub account/hosted-runner cause is not exposed by the available API.
- The earlier known-good run 42261718395 had a real hosted runner and completed its 13 build steps, but predates the instrumentation job split.
- No latest-source build, lint, unit test, instrumentation, benchmark or APK success is claimed.

**Next**
1. Restore GitHub-hosted runner allocation for this repository/account (inspect Actions usage/limits and GitHub service status in the repository owner UI).
2. Re-run run 163 after runner allocation is available. Confirm build job publishes signed APKs independently and instrumentation job executes all six Android-backed tests.
3. Record the correction benchmark p50/p95, then continue KBD-3 and Redmi device acceptance.

**Risk note**
- The CI architecture now isolates build delivery from emulator verification, but both still require GitHub-hosted runner capacity. No runner was allocated in run 163.

## Session 8 — Vercel Android build fallback (2026-11-12)

**Source and build environment**
- Built the current GitHub main tree at commit 056c93d044e6c2b398a96adfd923cd02c0390891, not the uploaded source archive.
- Provisioned a Vercel Linux Sandbox in fra1 with JDK 17, Gradle 8.9, Android SDK Platform 35 and Build Tools 35.0.0.
- The tracked signing keystore was recovered from the archived source copy only after its Git blob SHA was verified to exactly match the current repository blob (cc869a794f96e163048167ded34fb516033c0bae). The release APK therefore uses the repository's existing signing identity; no new signing lineage was created.

**Verification results**
- clean testDebugUnitTest lintDebug assembleDebug: BUILD SUCCESSFUL.
- Forced a fresh JVM test execution with --rerun-tasks :app:testDebugUnitTest: 43 tests, 0 failures, 0 errors, 0 skipped.
- 500-personal-word correction benchmark: p50 105 μs, p95 160 μs on the Vercel build host. This is a host microbenchmark, not a Redmi-device latency measurement.
- :app:assembleDebugAndroidTest: BUILD SUCCESSFUL; the six Android-backed SharedPreferences tests compile.
- :app:assembleRelease: BUILD SUCCESSFUL; release APK is 43,524,110 bytes, package com.vaultos.app, version 0.13.0 / code 14, target SDK 35. apksigner verify passed and the APK SHA-256 is 611ce20563b8836700a2e2c7a12b5d303910fccd9113c11e3e1967c5e649d435.
- lintDebug and lintVitalRelease passed. The debug lint report contains 25 warnings and no errors.

**Remaining verification**
- The Vercel sandbox has no /dev/kvm, so connectedDebugAndroidTest could not be executed there. The Android instrumentation APK compiled, but its six tests remain unexecuted.
- GitHub run 163 still documents the separate hosted-runner allocation problem. Vercel provides a working build fallback; it does not repair GitHub Actions runner provisioning.
- Redmi Note 12 Pro 5G physical acceptance remains open. Do not infer real-device keyboard latency or everyday UX from the host benchmark.

**Source archive warning**
- incoming/VaultOS-source.zip contains an older 0.6.0 / versionCode 6 snapshot while the current repository is 0.13.0 / versionCode 14. It was deliberately excluded from this build. Refresh or retire that archive before any future ZIP-import push; do not treat it as current source.

## Session 9 — Vercel non-persistent APK rebuild (2026-11-13)

**Source and environment**
- Rebuilt from GitHub `main` commit `36cffc5c6cc5c86f03e69d8262de7da4ece28e71`; the stale `incoming/VaultOS-source.zip` was excluded.
- Used a non-persistent Vercel Sandbox in `fra1` (2 vCPU / 4 GB) to avoid the team's exhausted Hobby snapshot-storage quota. No snapshot was created.
- Toolchain: JDK 17, Gradle 8.9, Android SDK Platform 35, Build Tools 35.0.0.

**Release verification**
- `:app:assembleRelease`: BUILD SUCCESSFUL.
- `lintDebug` and `lintVitalRelease`: BUILD SUCCESSFUL.
- JVM unit tests: 43 passed, 0 failures, 0 errors, 0 skipped.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: BUILD SUCCESSFUL. Instrumentation APK compiles; emulator/device execution remains outstanding.
- Release APK: 43,524,110 bytes; package `com.vaultos.app`; version `0.13.0` / code 14; target SDK 35. `apksigner verify` passed with the existing repository signing identity (certificate SHA-256 `c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23`).
- APK SHA-256: `02a9eaf911f12639d9ba8a6d46a451a013368ba638ef65bc0f34ac75a39d001a`.

**Delivery and caveats**
- Temporary download page: https://sb-4y1nz0v7zv7e.vercel.run/ ; direct APK: https://sb-4y1nz0v7zv7e.vercel.run/VaultOS-0.13.0.apk . The route was externally fetched and verified. It is tied to the non-persistent sandbox and expires when that session stops (45-minute maximum); download and keep a local copy.
- This avoids snapshot storage, but is not a permanent Vercel deployment or release asset. Do not describe it as durable hosting.
- No `/dev/kvm` emulator or physical Redmi test was available. Keep Android instrumentation execution and Redmi Note 12 Pro 5G acceptance open.


## Session 10 — Connected Vercel production APK pipeline (2026-11-13)

- Connected Vercel project: `vaultos` (`prj_1crRZ1Wy4SeL4iWvIRUWUnTYe8tS`), linked to `ysfclc/VaultOS` on `main`.
- Configured the project build command as `bash scripts/vercel-build.sh`, framework `null`, and output directory `dist`.
- Added a repository-owned Vercel build script and `vercel.json`. The script provisions/caches JDK 17, Gradle 8.9 and Android SDK 35 under `.vercel/cache/`; runs unit tests, lint and signed release assembly; verifies the stable signing certificate; and packages versioned APK/ZIP/checksum/build metadata plus a mobile download page.
- The first production deployment is the acceptance gate. Do not call the pipeline complete until the Vercel build reaches READY, the APK signature/checksum checks pass, and the deployed APK and ZIP are downloaded and verified from the project domain.
- Physical Redmi Note 12 Pro 5G acceptance and API 35 emulator instrumentation execution remain separate open tests.


### Connected deployment blocker — 2026-11-13

- Vercel project settings were updated: framework `null`, build command `bash scripts/vercel-build.sh`, output directory `dist`.
- Pipeline source was committed to `main` (initial implementation `9a4155efdf9080396e97bf8756f98ea76e70526e`; target-SDK metadata fix `23e1dbe02e3bba37f9f459dbe592675097c1089f`; post-link trigger commit `f41861fa4bed1ce0b2f8b2fcfc5441dc5f62bc7f`).
- Shell syntax and the target SDK parser were checked in a Vercel Sandbox.
- The first deployment was not created: Vercel returned HTTP 402 `api-deployments-free-per-day` because the team exceeded 100 API deployments for the day; the response specified a 24-hour retry window. A second Git push did not produce an automatic deployment. No Vercel CLI authentication was available in the sandbox for the suggested CLI fallback.
- Therefore the pipeline is configured in the connected project but is **not yet production-verified**. Re-trigger the Vercel production deployment after the API limit resets, then verify the deployment logs and both static download files.


## Session 10 — keyboard typing and app usability repair (2026-11-14)

**Branch:** `fix/keyboard-typing-and-ux-0.13.1`  
**Target:** VaultOS 0.13.1 / versionCode 15

**Implemented in source**
- Replaced single-tap-only Backspace handling with immediate delete plus touch-and-hold repeat (420 ms initial delay, then accelerated repeat); touch-up/cancel and service destruction stop the repeat task.
- Backspace now deletes a complete Android ICU character boundary rather than one UTF-16 code unit, respects selected text, and resynchronizes the active composing token from text immediately before the cursor.
- Debounced Android SpellCheckerSession requests by 95 ms and invalidate/remove stale pending requests as the user types or changes language.
- Automatic sentence Shift now updates the existing letter and Shift key views in place instead of rebuilding the complete keyboard after the first character or sentence punctuation.
- Added a candidate-source ranker that keeps the word being typed distinct from alternatives, merges platform corrections, personal completions, local typo matches and built-in prefix completions, and removes duplicates.
- Contextual prediction now fills unused slots from adjacent-word evidence rather than dropping all fallback candidates when only one trigram candidate exists. Added additional EN/PL/TR two-word phrase priors.
- Refreshed the Home dashboard, quick actions, local-first status, storage metrics, device access guidance, navigation selected states, Files heading and shared empty states.

**Verification state at handoff**
- GitHub Actions run [43642774636](https://github.com/ysfclc/VaultOS/actions/runs/43642774636) is the validation run for this branch. Source preflight passed and both Android jobs were assigned runners; final test/build and API 35 emulator outcomes must be recorded after completion.
- No physical Redmi Note 12 Pro 5G test has been performed in this session. Do not claim device latency, hold-repeat ergonomics or prediction relevance as accepted until the phone is tested.
- Package ID, Room schema, signing configuration and existing storage model are intentionally unchanged.

**Remaining acceptance**
1. Confirm unit tests, lint, signed debug/release APK assembly and certificate verification in the GitHub run.
2. Confirm Android API 35 instrumentation completes.
3. Install the release APK over the existing installation on the Redmi Note 12 Pro 5G (do not uninstall); test sustained typing, short/long backspace, selected-text deletion, emoji/diacritics, EN/PL/TR suggestions, language switching, clips, Vault and navigation.
4. Record on-device typing responsiveness and candidate usefulness. Build-host microbenchmarks are not substitutes for IME latency measurements.


## Session 11 — Keyboard feedback follow-up (2026-11-14)

User tested VaultOS 0.13.1 and reports rapid typing is somewhat better, but the keyboard still feels clunky. User also reported no emoji button. Source inspection confirmed emojiMode and an emoji panel existed, but the normal keyboard had no control to enter emoji mode. Added a visible 😊 toolbar toggle on the existing 0.13.1 repair branch, with an accessibility description. Remaining clunky feel and real-device emoji flow are still open for physical acceptance; do not mark the keyboard complete.


## Session 12 — clipboard history integrity increment (2026-11-15)

**Product audit**
- The clipboard-history table, event capture, searchable gallery, pin/delete/clear actions, full-text copy and keyboard Clips retrieval already exist from Phases 7–8. Do not create a parallel gallery or duplicate storage model.
- Automatic clipboard capture remains bounded by Android's selected-IME policy. A regular background app cannot silently observe clipboard changes while another keyboard is active.
- Existing VaultRepository.readFullText() verifies the archived VaultItem's own byte count and SHA-256. The history repository previously trusted that result without checking the separate metadata recorded on the clipboard event.

**Implemented on fix/keyboard-typing-and-ux-0.13.1**
- Added ClipboardPayloadIntegrity as a pure Kotlin verifier.
- ClipboardHistoryRepository.readText() now rejects non-text events and validates the returned text against that event's own UTF-8 byte count and SHA-256 after the VaultItem integrity check.
- Capture-time SHA-256 now uses the same helper as readback verification.
- Added five JVM regression tests: multilingual/emoji UTF-8, same-byte-count content corruption, truncated/extended byte count, legacy entries without a hash, and empty payload.
- Updated docs/SPEC.md and docs/TASKS.md; Phase 17 tracks the next clipboard-history test and device-acceptance work.

**Verification**
- Source changes and documentation were written directly to the existing GitHub repair branch. CI for the completed increment has not yet been confirmed.
- No Room-backed repository test, emulator run, APK verification, or Redmi physical-device test is claimed.
- Existing package ID, Room schema, signing configuration, clipboard event table and archive format are unchanged.

**Next**
1. Verify the branch's JVM tests, lint, signed APK build and signing-certificate check in GitHub Actions.
2. Add Room-backed repository tests for separate events sharing one archived payload, event readback, pin/delete/clear and missing/corrupt content.
3. Continue with the actual gallery/retrieval UX only after these data-integrity checks are established; preserve the existing gallery rather than replacing it.


## Session 11 — release hygiene checkpoint (2026-11-15)

**Repository and PR state**
- Default branch remains `main` at `0ac8f2f6d42c8d5ca7e5c9c7943224794df10a63`.
- `fix/keyboard-typing-and-ux-0.13.1` contains the 0.13.1 app/keyboard work plus clipboard payload-integrity hardening; it is 28 commits ahead of main at the start of this checkpoint.
- PR #5 remains open and draft. The unique cursor-safe autocorrect implementation/tests from PR #2 and multilingual smoke-evaluation assets from PR #3 have been copied into this release branch; their original PRs remain open on `main` until the consolidated candidate is verified. PR #4 is a closed temporary CI retry; PR #1 is closed.
- PR #2 and #3 implementation/test assets are consolidated in this branch (the temporary runner OS tweak from PR #2 was intentionally not copied). Verify tests, lint, signed APK/signature and instrumentation before closing the superseded PRs or merging PR #5.

**Corrections committed on the 0.13.1 branch**
- Fixed the clipboard integrity test fixture: `café` and `cafè` are distinct strings with equal UTF-8 byte counts. The prior `café` / `caff` fixture incorrectly asserted equal byte lengths and failed before testing the hash mismatch.
- Corrected both Android workflow summaries to report version 0.13.1 / versionCode 15, matching `app/build.gradle.kts`.
- Workflow runs triggered by individual commits were cancelled by subsequent pushes. Latest consolidated candidate head before this documentation refresh is `2c8e756a`; its validation run is expected to supersede earlier cancelled runs. No pass or APK artifact is claimed yet.

**Security / release hold**
- Repository visibility is still public. `signing/vaultos-stable.keystore` and static signing values remain tracked; treat the signing identity as exposed.
- GitHub connector in this session supports repository content/PR operations but not repository visibility settings. Owner action required: Settings → Danger Zone → Change visibility → Private. Public forks, stars/watchers and private-repository feature availability have consequences; review GitHub's visibility warning before confirming.
- Do not delete or regenerate the existing signing key until a tested Android signing lineage / update-continuity plan exists. No key rotation has been performed.

**Next actions**
1. Wait for run 43683981709 and inspect both job logs/artifacts.
2. Verify the consolidated cursor-safety and multilingual evaluation code/tests now present in the feature branch; then close superseded PR #2 and #3 without merging them independently.
3. Reconcile PR #5 description and project status with actual verification evidence.
4. Restore repository privacy (owner action), then design signing-key migration with an in-place update test.
5. Merge the verified consolidated PR into main; re-fetch main and compare branch ancestry after merge.
6. Add Room-backed clipboard repository tests, backup/restore fixtures and Room migration tests as subsequent increments.


## Session Handoff — 2026-11-15 (PR #5 consolidation)

- PR #5 consolidates the cursor-safe autocorrect guard and multilingual keyboard evaluation harness; PR #2 and PR #3 were closed as superseded after their relevant changes were integrated.
- Latest PR head includes the Android API 35 emulator workflow hardening: cold boot, software GPU, wipe-data/no-snapshot options, extended boot timeout, and explicit Android package-manager readiness check before connected tests.
- Prior run 43684374671: JVM tests, lint, debug/release APK assembly, APK signature verification, artifact upload and private draft release publication passed. API 35 instrumentation failed before tests started because the emulator's package service was unavailable (cmd: Can't find service: package). A retry reproduced the emulator failure; no keyboard assertion failed because the test APK could not be installed.
- New workflow run 43687738003 is in progress at the time of this checkpoint. Do not mark instrumentation green until its test results show actual executed tests.
- PR #5 remains open and unmerged; main is unchanged. Repository visibility remains public and signing material remains tracked. This is a critical security hold, not a documentation-only issue. Do not rotate/delete the old signing key or issue a new update build until an update-lineage migration is tested.


## Session 13 — product north star and core capture/retrieval increment (2026-11-15)

**Product intent recovered and made durable**
- The original user problem was long-text clipboard truncation on the Redmi Note 12 Pro 5G: preserve the complete content and paste/retrieve it later.
- The product grew into a local-first personal capture and retrieval layer, not a keyboard-first utility. Its core loop is capture → preserve → connect → retrieve → recover.
- Added `docs/PRODUCT-NORTH-STAR.md` and linked it from the README. It records the product definition, local-first/privacy contract, preservation of originals, event/content separation, Android boundaries, acceptance signals and roadmap priority.
- Future work must improve the weakest link in capture, preservation, retrieval or recovery. Keyboard polish supports those jobs but cannot displace them.

**Core product work added**
- Added `VaultRepositoryInstrumentedTest` with four Android-backed cases using the real Room database and app-private file archive: long multilingual UTF-8 exact round-trip; global search for text beyond the preview; verified duplicate-payload reuse; and rejection of same-byte-length tampering.
- These are test sources only until the API 35 emulator actually executes them. No pass is claimed.

**CI diagnosis and correction**
- The previous API 35 job successfully booted the emulator, then exited before ADB readiness or Gradle instrumentation because `reactivecircus/android-emulator-runner` invoked the script with `/usr/bin/sh` while the script began with Bash-only `set -euo pipefail`.
- Both mirrored workflows now use POSIX `set -eu`; the step is renamed to “Run Android-backed persistence tests”.
- The next run for the final branch head must demonstrate test APK installation and actual executed tests. A green emulator boot alone is not a test pass.

**Still blocked**
- Repository is still public; the tracked historical keystore and static signing values remain compromised. Owner must change visibility to private; signing migration requires a tested update-continuity design.
- PR #5 remains unmerged; `main` remains untouched.
- No physical Redmi acceptance or backup/restore test is claimed.

**Next**
1. Verify the final-head workflow, including all ten Android-backed tests (six typing-memory plus four repository tests).
2. Expand core data assurance with backup/restore v1/v2 round-trip/corruption tests and Room migration coverage.
3. Resolve GOV-001 before merge/release; then install over the existing Redmi app and verify capture, full-text retrieval, keyboard use, and recovery.


## Session 14 — backup manifest path containment (2026-11-15)

**Threat fixed**
- Backup manifests are external input. Restore previously resolved `trashOriginalRelativePath` directly with `File(vault, path)`, allowing a crafted manifest to persist an absolute or traversal path that could later be used as a destination by Recycle Bin restore.
- The archived `relativePath` fallback also relied on an unchecked manifest path when no restored-tree mapping existed.

**Implementation**
- Added `resolveVaultRelativePath`: reject blank, NUL-containing and absolute values; canonicalize the target; reject the vault root itself and any path not strictly beneath the canonical vault root. Canonicalization also detects symlink escapes.
- Use the same resolver for archived payload paths and Recycle Bin original destinations. An invalid trash destination becomes null so the existing safe category/name fallback is used.
- No backup format or Room schema change.

**Regression coverage**
- Added five JVM tests: valid nested path, parent traversal, absolute path, blank/root aliases and symlink escape.
- Tests are committed but not yet verified by a completed CI run. Symlink test must report as skipped (not passed) if the host cannot create symlinks.

**Next**
1. Execute the full JVM/lint/build job on this final head and inspect exact results.
2. Execute API 35 instrumentation for the ten Android-backed tests.
3. Add real backup/restore round-trip coverage for v1/v2, collisions, integrity failure and restored organization/recycle-bin state.
4. Keep security hold and physical Redmi acceptance open.


## Keyboard UX audit — 2026-11-16

- Reviewed the active 0.13.1 keyboard source and documented concrete UI gaps and a staged redesign in `docs/KEYBOARD-UX-REDESIGN.md`.
- This does not mark keyboard UX as fixed. KBD-3 candidate UX, adaptive layout, EN/PL/TR discoverability, accessibility and Redmi Note 12 Pro 5G acceptance remain open.
- Keep keyboard redesign focused on comfort, clarity and reliable typing; do not add visual effects or features that compete with the typing path.


## Keyboard UX implementation — 2026-11-16

- First source implementation committed: default typing no longer spends a toolbar slot on Type; Type is shown when leaving Clips, Vault or emoji mode. Tools is a compact overflow glyph with an accessibility description.
- Candidate cells now describe current word, spelling correction, completion and next-word prediction to accessibility services, with a restrained outlined affordance for secondary candidates.
- Commit is part of PR #5. Automated Android build, unit tests, lint and API 35 instrumentation are pending for this head. Physical Redmi Note 12 Pro 5G acceptance remains required.
- Do not call the redesign complete or claim speed improvement until device acceptance and repeatable measurements.
