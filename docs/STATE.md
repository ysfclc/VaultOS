## Session 16 — production download pause and emulator CI correction (2026-11-25)

- Reassigned all three public VaultOS project aliases to the verified pause-page deployment `dpl_BDH4QumZ5EpUzBiqX7incRygociV` (source commit `aca7054a7a4ed349332775f7c9706f8633b33c6e`). Each alias returned HTTP 200 and showed the signing-migration pause page with no APK/ZIP download links.
- The previous production deployment `dpl_DVZmVoFDqci15p7YbsUeU4xp1qu6` remains retained; only its aliases were reassigned. No release artifact was published or deleted.
- Updated PR #14's description to record the production alias changes and CI status. PR #14 remains draft and unmerged.
- Investigated API 35 instrumentation failure from run 45668107033. The emulator exited immediately with: `Software CPU emulation (-accel off) is not supported`. Added `ANDROID_I_WANT_MY_TCG: "yes"` to the instrumentation job in both workflow YAML copies on the security branch.
- New workflow run 45687257559: signer guard, unit tests and lint have passed; API 35 instrumentation is still running. Do not claim instrumentation passed until the job completes.
- Repository remains public and the historical signing identity remains exposed in Git history/current tree. No key rotation, lineage generation, history rewrite, repository-visibility change or APK publication has occurred.


## Session 15 — stop legacy signing in default branch (2026-11-25)

- Opened an isolated security branch from `main` so the default branch can be secured without merging unrelated USB-OTG feature work.
- Removed the historical sideload signer and hardcoded credentials from Gradle. Debug/instrumentation use Android's generated default debug signer; release remains unsigned.
- Replaced routine CI APK assembly/signing/publication with JVM tests, lint and API 35 instrumentation only. Added explicit checks that fail if Gradle references the exposed signer.
- Disabled the Vercel release APK pipeline; it now outputs a temporary download-paused page without provisioning Android tooling or reading signing material.
- Removed the full-repository ZIP import step from CI and set workflow token permissions to read-only; the root workflow mirror now matches the canonical workflow.
- The old keystore remains in the repository/history solely as migration input. Repository visibility is still public; key replacement, v3 lineage, GitHub secret provisioning and history cleanup remain blocked.
- This branch requires CI verification and review before merge. No release APK was generated.
# State Ledger
_Last updated: 2026-11-12 by Continuity Engineer — Vercel build fallback_

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
| Keyboard suggestions | 🟡 partial | KBD-1/KBD-2 source implemented; 43 JVM tests pass; 500-word host benchmark p50/p95=105/160 μs; six Android instrumentation tests compile but await emulator execution; Redmi acceptance open |
| Metadata extraction | 🟡 partial | Parser tests exist; end-to-end extraction/provider behavior lacks integration tests |
| Backup and restore | 🟡 partial | v1/v2 paths implemented; no automated round-trip, corruption, or migration test suite identified |
| DocumentsProvider | 🟡 partial | Implemented; no provider interoperability test evidence |
| Release distribution | 🔴 paused | GitHub Actions is validation-only; Vercel aliases serve a pause page; release signing and publication remain blocked pending verified replacement-key lineage |
| Physical-device acceptance | ⚪ not started | 1.0.0 gate remains open for Redmi Note 12 Pro 5G |
| Signing-material remediation | 🔴 blocked | GOV-001 needs user-provisioned replacement material and verified update-lineage plan |

## 3. Currently in flight
- 1.0.0 acceptance — Vercel build/test/lint/release verification is green; GitHub hosted-runner provisioning and Redmi physical-device matrix/data migration/restore evidence remain open.
- GOV-001 signing migration — blocked on replacement keystore/secrets and an update-continuity decision; preserve the existing key until migration is proven.
- Keyboard V2 — KBD-1/KBD-2 source is committed; Vercel JVM tests and the 500-word host benchmark pass; Android-backed tests compile but await emulator execution; candidate UX and Redmi acceptance remain; see docs/KEYBOARD-V2.md.
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
