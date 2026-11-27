# VaultOS task ledger

Tasks are grouped by delivery phase. A checked item means implemented in source; it does not imply physical-device verification.

## Phase 1 — reliable capture and storage foundation

- [x] Create native Android/Compose application and permission model.
- [x] Import single and multiple files through Android sharing.
- [x] Import through the system document picker.
- [x] Stream incoming files to app-private storage and calculate SHA-256.
- [x] Detect duplicate content without duplicating archive bytes.
- [x] Save full text as UTF-8 files and index content in bounded search chunks.
- [x] Build optional IME and clipboard capture flow.
- [x] Add full-text paste through the active IME in bounded input-connection chunks.
- [x] Index existing shared storage without copying on initial scan.
- [x] Archive later detected shared-storage additions while preserving originals.
- [x] Add resumable initial scan and incremental MediaStore tracking.
- [x] Add unit tests for deterministic file classification.
- [x] Add incoming ZIP import workflow with SHA-256 replay guard and automatic commit notes.
- [ ] Run Gradle tests/lint/build in GitHub Actions.
- [ ] Install and test on Redmi Note 12 Pro 5G.

## Phase 2 — file manager hardening

- [x] Device and private-vault browsing.
- [x] Copy, cut/move, rename, delete and folder creation.
- [x] Open and share archived/device files.
- [x] Native DocumentsProvider integration.
- [x] Add recycle bin for managed archive deletions.
- [x] Add robust progress/cancel UI for large file operations.
- [x] Add thumbnail loading and file-type previews.
- [x] Add multi-select and batch operations.
- [x] Add archive extraction and creation.

Phase 2 implementation is in release line 0.2.0. The Room 1→2 migration adds recycle-bin metadata; managed archive files are moved under the private .trash directory and are restorable. ZIP extraction rejects path traversal and caps expanded data at 2 GiB / 20,000 entries. Operation cancellation cleans temporary outputs. Unit tests cover ZIP round-trip, Zip Slip rejection, copy integrity/progress, and cancellation safety. Physical Redmi validation remains in DEVICE-TEST-PLAN.md.

## Phase 3 — application and backup management

- [x] Installed application catalogue.
- [x] Archive base and split APK components exposed by PackageManager.
- [x] Manual external backup and restore through SAF.
- [x] Add signed release build and key-management instructions.
- [x] Add integrity verification for every restored archived payload.
- [x] Add installed-versus-archived package version comparison and split-package export guidance.

## Phase 4 — intelligent organization

- [x] Deterministic categories and unified search.
- [x] Pinned items and category collections.
- [x] Preserve index-only item references in backup manifest.
- [x] User-defined tags and collections, with item assignment/removal and cascade-safe storage.
- [x] Related-item and SHA-256 duplicate-group views.
- [x] Explicit local metadata extraction for PDFs, DOCX/ODT, images, audio/video, APKs and text items.
- [x] Saved searches, including metadata and tag matches in unified search.
- [ ] Optional on-device semantic indexing, disabled until an embedding runtime/model is explicitly selected.

## Phase 5 — device integration and polish

- [x] Share-sheet target and IME registration.
- [x] MediaStore observer and periodic catch-up scan.
- [x] Xiaomi autostart/battery optimization onboarding with Android settings shortcuts and manual vendor-specific steps.
- [ ] Test clipboard behavior across Chrome, ChatGPT, WhatsApp, Notes and password fields.
- [ ] Test Android 13/14/15 behavior and permission flows.
- [x] Make Settings independently scrollable for short displays and enlarged system text; keep the app orientation-flexible.
- [x] Add a subtle page-content size transition and an explicit screen-reader label to the automatic clipboard-capture switch.
- [ ] Complete motion, screen-reader, large-font and landscape acceptance review on physical devices.

## Build gate — source compile correction

- [x] Fix IME locale lookup to use `InputMethodManager`.
- [x] Fix DocumentsProvider `MatrixCursor.RowBuilder` column writes.
- [x] Make backup restore suspend-safe for Room and full-text reindexing.
- [ ] Confirm the next GitHub Actions run completes unit tests, lint, and APK assembly.
- [ ] Install and exercise the resulting APK on the Redmi Note 12 Pro 5G.


## Build gate — Android resource linking correction (2026-11-02)

- [x] Give values-v27/Theme.VaultOS the same explicit parent as the base theme.
- [x] Preserve every base theme item in the API-27+ style and keep the light-navigation-bar override.
- [x] Reject source ZIPs that omit or regress the qualified theme parent/items before importing them.
- [x] Keep both workflow YAML copies protected and synchronized during ZIP imports.
- [x] Run unit tests, lint, and APK assembly with JDK 17, Gradle 8.9, Android SDK 35 (local verification).
- [ ] Run GitHub Actions with the corrected ZIP and confirm the hosted CI result.
- [ ] Install and exercise the resulting APK on the Redmi Note 12 Pro 5G.

## Phase 2 verification gate (2026-11-03)

- [x] Run a clean local Gradle test, lint and debug APK build with JDK 17 / Gradle 8.9 / Android SDK 35.
- [x] Confirm all 9 unit tests pass (0 failures).
- [x] Confirm lint completes with 0 errors (15 warnings remain; see `docs/PROJECT-STATUS.md`).
- [ ] Run the GitHub Actions workflow against the delivered source ZIP.
- [ ] Complete the Redmi Note 12 Pro 5G device test plan.


## Phase 3 verification gate (2026-11-03)

- [x] Stable signing certificate carried forward from the 0.2.0 APK for update installs.
- [x] Debug and release APK variants signed with the same local sideload key.
- [x] Installed-app catalogue queries moved off the main thread.
- [x] Installed-versus-archived APK version comparison and split-component export guidance.
- [x] Reduce redundant full-vault reconciliation during device-storage scans; debounce MediaStore scan requests and bound initial scan work.
- [ ] GitHub Actions and physical Redmi validation.


## Phase 4 verification gate (2026-11-03)

- [x] Add Room 2→3 migration for tags, collections, saved searches, metadata and relationship tables without clearing existing items.
- [x] Add user-defined tags and collections, related-item navigation, and SHA-256 duplicate groups.
- [x] Add user-triggered local metadata extraction and include metadata in unified search.
- [x] Extend external backup manifest to v2 while retaining v1 restore compatibility.
- [x] Add focused PDF/XML metadata parser unit tests.
- [x] Clean local Gradle test, lint, debug APK and signed release APK build.
- [x] Confirm 13 unit tests pass and Android lint has 0 errors (16 warnings).
- [x] Confirm debug/release APK certificate matches the retained 0.3.0 release certificate.
- [x] Validate migration DDL with SQLite, including uniqueness, foreign-key integrity and cascades.
- [ ] Configure and validate an on-device embedding engine before enabling semantic indexing.
- [ ] Run GitHub Actions and complete physical Redmi Note 12 Pro 5G acceptance testing.


## Phase 5 implementation gate (2026-11-03)

- [x] Add MIUI/HyperOS background-work guidance without relying on undocumented Xiaomi activities or silently changing system settings.
- [x] Provide safe Android shortcuts to VaultOS app info and the battery-optimization app list.
- [x] Make Settings content vertically scrollable and add bottom breathing room for gesture navigation.
- [x] Bump version to 0.5.0 / versionCode 5 while retaining application ID and stable sideload signing identity.
- [x] Run unit tests (13 passed), lint (0 errors / 16 warnings), debug/release assembly and verify both APK signatures against the preserved certificate.
- [ ] Verify the GitHub-hosted workflow after updating both protected YAML copies.
- [ ] Run the Redmi Note 12 Pro 5G device test plan, including Chrome, ChatGPT, WhatsApp, Notes, password fields, Android permissions and Xiaomi background restrictions.

## Phase 6 — filesystem safety hardening (0.6.0)

- [x] Refuse symbolic links in selected copy, move, archive and delete trees.
- [x] Use no-follow filesystem checks during recursive copy, ZIP creation and deletion.
- [x] Avoid repeated source-tree statistics when creating ZIP archives.
- [x] Add regression tests for deleting a directory symlink, copying a nested file symlink, moving a linked directory, refusing partial deletion when a nested symlink is present, and creating a ZIP from a symlink root.
- [x] Run unit tests, lint and signed debug/release assembly; verify both APK certificates.
- [ ] Run the GitHub-hosted workflow for 0.6.0.
- [ ] Complete all physical-device acceptance items before 1.0.0.

## Phase 7 — clipboard history foundation (0.7.0)

- [x] Add a dedicated Room clipboard-history event table with a non-destructive 3→4 migration.
- [x] Record each clipboard copy as a separate event, even when identical content already exists in the library.
- [x] Reuse deduplicated archived payloads while retaining distinct copy timestamps and history entries.
- [x] Record foreground editor package context, clipboard label, MIME types, SHA-256, character count, byte count and capture time.
- [x] Capture every supported text/URI item in a multi-item clipboard payload.
- [x] Exclude password input fields and Android 13+ clips explicitly marked sensitive from automatic capture.
- [x] Add a searchable clipboard gallery with full-text copy, pin, individual delete and clear-unpinned actions.
- [x] Expose clipboard history from both the main app and the optional VaultOS keyboard.
- [x] Verify full-text payload size and SHA-256 on write, duplicate reuse and readback before paste.
- [x] Confirm GitHub Actions test/lint/APK build for this phase (run `42026208055`; BUILD SUCCESSFUL; signed debug and release APK artifacts uploaded).
- [ ] Verify clipboard access and multi-item behavior on the Redmi Note 12 Pro 5G across Chrome, ChatGPT, WhatsApp, Notes and password fields.

**Platform boundary:** automatic clipboard capture is available only while VaultOS is the selected input method (or when the user manually captures from inside VaultOS). Android does not permit a normal background app to silently read another keyboard's clipboard. The foreground editor package is recorded as context, not asserted as definitive clipboard-origin metadata.

### Phase 7 CI verification — 2026-11-05

- [x] Run `clean testDebugUnitTest lintDebug assembleDebug assembleRelease` in GitHub Actions with Java 17 / Gradle 8.9 / Android SDK 35.
- [x] Confirm Kotlin compilation, unit-test task, lint and both APK assembly tasks complete successfully.
- [x] Verify the persistent signing certificate step succeeds.
- [x] Upload `VaultOS-debug-APK`, `VaultOS-release-APK` and `VaultOS-build-metadata` artifacts (run 60).
- [ ] Install the APK on the Redmi Note 12 Pro 5G and complete the clipboard device acceptance matrix.
## Phase 8 — keyboard integration (0.8.0)

- [x] Add a persistent EN / PL / TR selector, synchronized with Settings.
- [x] Add inline clipboard Clips mode with full-text search, pinning and complete-text paste.
- [x] Keep full clipboard History available without leaving the keyboard.
- [x] Confirm hosted CI for 0.8.0 (run `42027643930`).
- [ ] Complete the 0.8.0 device acceptance plan.


## Phase 10 — keyboard redesign (0.10.0)

- [x] Replace the two-row micro-toolbar with a single-row Type / Clips / Cmd / Language / More mode bar.
- [x] Make key presses visible: high-contrast keycaps, stateful pressed colors, ripple feedback, press scale and popup key preview.
- [x] Increase default key height to 50 dp and support 46 / 50 / 54 / 58 dp settings.
- [x] Add optional number row, dark/light themes and off/left/right one-handed layout.
- [x] Add optional system key sounds alongside configurable haptics and key preview.
- [x] Add an emoji panel and cursor movement by sliding on the spacebar.
- [x] Add a Vault Cmd panel that searches the local library and inserts complete saved Text/Code content.
- [x] Add live spell-check requests with stale-result sequencing and only autocorrect when the spell-checker flags a likely typo.
- [x] Preserve EN / PL / TR, long-press alternatives, Clips, clipboard capture, pinning, History and complete-text insertion.
- [x] Run hosted 0.10.0 CI: run `42041620803` passed `clean testDebugUnitTest lintDebug assembleDebug assembleRelease`, signature verification and all three artifact uploads.
- [ ] Install on Redmi Note 12 Pro 5G and verify that each pressed key is unmistakably visible, the popup preview follows touch, and missed/duplicate characters do not occur.
- [ ] Test landscape, enlarged display/font, TalkBack, one-hand modes, theme, number row, emoji, Commands, clipboard, cursor swipe, sounds, haptics and Gboard switching.
- [ ] Tune dimensions from real device feedback. Do not call the keyboard usable until this acceptance passes.


## Phase 11 — recommendation quality and keyboard refinement (0.11.0)

- [x] Add a deterministic local EN / PL / TR prefix lexicon so useful completions do not depend entirely on the phone's installed dictionary.
- [x] Add conservative Damerau–Levenshtein typo candidates for nearby common words; keep exact known words out of the correction path.
- [x] Add curated next-word suggestions for common English, Polish and Turkish contexts; unknown contexts remain empty rather than inventing candidates.
- [x] Keep the typed word visible in the candidate strip and show platform spell-checker alternatives only when Android flags a likely typo.
- [x] Invalidate queued spell-check callbacks when the word changes, the user clears the current word, or the input field ends.
- [x] Make selecting a completion commit the word and one trailing space consistently.
- [x] Refine keyboard and candidate-strip colors, spacing, keycap radius and toolbar surfaces.
- [x] Add unit tests for EN / PL / TR prefix completion, context suggestions, unknown contexts and short-prefix behavior.
- [x] Run hosted 0.11.0 CI in run `42047330196`: unit tests, lint, debug/release APK assembly, persistent signing verification and all three artifact uploads passed.
- [ ] Verify actual recommendation relevance and visual quality on Redmi Note 12 Pro 5G in ChatGPT, Chrome, WhatsApp and Notes.
- [ ] Confirm no unwanted candidate insertion, duplicate spaces or stale spell-check replacement while typing quickly.


## Phase 12 — adaptive local typing assistant (0.12.0)

- [x] Add a private local vocabulary store, separate from clipboard history and Vault content.
- [x] Keep automatic learning off by default and require explicit opt-in from the keyboard More menu.
- [x] Store individual words and adjacent-word counts only; do not persist sentences or complete editor contents.
- [x] Add a long-press action on the current word to save it to the personal dictionary.
- [x] Merge personal words into prefix completions and offline typo candidates.
- [x] Rank next-word suggestions using locally learned adjacent-word frequencies before static fallbacks.
- [x] Add a confirmation before clearing the local typing-memory store.
- [x] Add sentence capitalization and double-space period, each independently configurable from More.
- [ ] Verify learned words persist after restart and remain isolated by EN / PL / TR.
- [ ] Verify learning is excluded from password, email, URL, numeric, autocomplete, filter and no-suggestions fields.
- [ ] Verify clearing typing memory leaves clipboard history and Vault content untouched.
- [ ] Verify punctuation and capitalization in ChatGPT, Chrome, WhatsApp and Notes.
- [ ] Evaluate recommendation relevance after several days of normal use on Redmi Note 12 Pro 5G.

- [x] Run hosted CI for 0.12.0 (run `42054296162`): unit tests, lint, signed debug/release APKs and all artifact uploads passed.

- [x] Route every personal-learning write through a safe-editor-field guard, including punctuation and Enter boundaries.
- [x] Complete 0.12.1 hosted CI in run `42079070087`: unit tests, lint, debug/release APK assembly, signing verification and all three artifact uploads passed.
- [ ] Complete physical Redmi privacy/prediction acceptance.


## Phase 13 — usability reset (0.13.0)

- [x] Reduce the primary app bottom navigation to Home, Library, Files, Organize and More.
- [x] Move Installed apps and Settings & permissions into a dedicated More hub.
- [x] Show an actionable all-files-access onboarding card on Home when permission is missing; explain that initial indexing preserves existing source files.
- [x] Render global search results and distinct empty/no-match states from any current navigation destination; show result count and item actions.
- [x] Rename ambiguous keyboard Cmd / dots controls to Vault / Tools.
- [x] Replace the flat keyboard Tools popup with grouped sections, larger rows and display-bounded scroll behavior.
- [x] Increase held-key scale/depth feedback and popup preview size; add visible spacebar down/up feedback.
- [x] Use transient pre-cursor context for next-word suggestions only in safe text fields.
- [x] Broaden offline EN / PL / TR vocabulary and context examples.
- [x] Add unit regression tests for new multilingual vocabulary and context pairs.
- [x] Confirm hosted CI run [42261718395](https://github.com/ysfclc/VaultOS/actions/runs/42261718395) passes unit tests, lint, signed debug/release assembly, checksum preparation and private draft release publishing.
- [x] Verify both APK variants against the retained signing certificate SHA-256 digest `c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23`.
- [x] Publish signed release APK, debug APK, checksums and build metadata in [the private VaultOS 0.13.0 draft release](https://github.com/ysfclc/VaultOS/releases/tag/untagged-04b4086aa81185969695).
- [ ] Install the APK on the phone and complete physical usability acceptance.
- [ ] Install the signed 0.13.0 APK as an in-place update on Redmi Note 12 Pro 5G.
- [ ] Verify navigation discoverability, initial storage permission setup, keyboard touch accuracy, suggestions after cursor movement/paste, menu reachability, TalkBack, large text and landscape.
- [ ] Only mark keyboard/app usability accepted after the physical-device checklist passes.


## Phase 14 — adaptive predictive keyboard V2

The 0.13.0 keyboard is a functional baseline, not a complete predictive experience. See docs/KEYBOARD-V2.md and requirement R-013 in docs/SPEC.md.

- [x] KBD-1: Reuse one IME-lifetime typing-memory instance; cache parsed personal word/pair snapshots and dictionary lists with preference-change invalidation.
- [x] KBD-1: Reuse candidate strip cells and separators instead of rebuilding the entire row for each keystroke.
- [x] KBD-1: Bound local fuzzy correction to 120 length-compatible candidates; prioritize likely same-initial matches and use bounded-memory edit distance with early exit.
- [x] KBD-1: Run local correction ranking on one reusable worker thread using immutable vocabulary snapshots; keep IME/editor/UI operations on the main thread.
- [x] KBD-1: Reject stale worker results with per-render generation tokens plus input-session, typed-word, language, editor-safety and candidate-row checks; clear queued work and shut down the executor with the service.
- [x] KBD-1: Add and execute pure-engine correction limit, transposition and low-noise regression tests on Vercel (included in the 43 passing JVM tests).
- [x] KBD-1: Add a repeatable pure-engine microbenchmark for 500 personal words (p50/p95 output; no flaky timing threshold).
- [x] KBD-1: Execute the 500-word correction benchmark for 500 personal words; p50=105 μs / p95=160 μs on the build host (not a Redmi-device measurement).
- [x] KBD-2: Add opt-in bounded trigram/context learning without changing existing word/bigram preference keys.
- [x] KBD-2: Rank next-word candidates from a short transient two-word context, with EN/PL/TR isolation and one-word fallback.
- [x] KBD-2: Add pure tests for EN/PL/TR context priors, unknown-context fallback, trigram precedence and candidate limits.
- [x] KBD-2: Add Android-backed tests for opt-in, word/bigram/trigram persistence, opt-out, clear/reset, EN/PL/TR isolation and v1 bigram compatibility.
- [x] KBD-2: Compile the six Android-backed SharedPreferences tests with :app:assembleDebugAndroidTest on Vercel.
- [ ] KBD-2: Execute connectedDebugAndroidTest on an API 35 emulator. GitHub's API 35 emulator launch failed before boot because emulator 37.3.10 requires ANDROID_I_WANT_MY_TCG=yes when hardware acceleration is disabled; the environment fix is committed to both workflow copies and awaits rerun.
- [x] KBD-1: Add pure generation-gate tests for newest-request precedence and lifecycle invalidation.
- [ ] KBD-3: Redesign candidate strip hierarchy and interactions after latency and prediction quality are stable.
- [ ] KBD-4: Measure real typing latency and prediction usefulness on Redmi Note 12 Pro 5G across safe apps and languages.

Do not mark this phase complete from unit tests alone; device acceptance is mandatory.

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


## Vercel production APK pipeline

- [x] Configure the connected `vaultos` Vercel project to run the repository-owned Android release build and publish `dist/`.
- [x] Add a cached JDK 17 / Gradle 8.9 / Android SDK 35 provisioning script with unit tests, lint, signed release assembly and signing-certificate enforcement.
- [x] Add versioned APK + ZIP, SHA-256 checksums, build metadata and a mobile-friendly static download page.
- [ ] Verify the first connected Vercel production deployment reaches READY.
- [ ] Download the APK and ZIP from the stable project domain and verify checksums/signature.
- [ ] Complete physical Redmi Note 12 Pro 5G acceptance and API 35 instrumentation execution.


**Current blocker (2026-11-13):** Vercel refused the initial deployment with HTTP 402 `api-deployments-free-per-day` (more than 100 API deployments today; retry after 24 hours). The connected project settings and source pipeline are in place, but production build/download verification remains unchecked until Vercel accepts a deployment.


## Clipboard Core + Gallery — integrity increment (2026-11-21)

- [x] Validate the clipboard history event's own SHA-256, UTF-8 byte count and Unicode code-point count before returning saved text for copy or keyboard paste.
- [x] Fail closed when a text event has a missing/malformed hash or mismatched payload metadata.
- [x] Add five pure JVM regression cases covering exact multilingual UTF-8 payloads, same-length payload mutation, byte-count mismatch, Unicode code-point mismatch and missing/malformed hashes.
- [x] Compile and run a standalone Kotlin smoke check for the integrity helper; exact payload accepted and all four mismatch classes rejected.
- [x] Run the five new JVM integrity regression tests through Gradle; GitHub Actions PR run 44882973823 passed the clean testDebugUnitTest, lintDebug, assembleDebug and assembleRelease job.
- [x] Add four Room in-memory instrumentation tests for separate timestamped duplicate events, pin/delete behavior, clear-unpinned preservation and search metadata.
- [x] Add an explicit ANDROID_I_WANT_MY_TCG=yes environment setting to both Android workflow YAML copies so API 35 emulator 37.3.10 can boot without hardware acceleration.
- [x] Add four repository-level instrumentation tests for long multilingual capture/readback, separate history events sharing one archived item, event hash/byte/code-point mismatch rejection, and same-length archive tampering plus search beyond the preview.
- [x] Require clipboard text readback to resolve a canonical archive file strictly inside filesDir/vault; add a repository instrumentation case rejecting an external archive path.
- [x] Expand full copied text inline in its clipboard history card with bounded scrolling, selectable text and animated expand/collapse; keep Copy full text available.
- [x] Keep ClipboardHistoryEntry and VaultItem as the canonical persisted event/payload entities; keep expansion state transient and document the global entity boundary.
- [x] Fix emulator-runner script parsing by removing the multiline shell function/trap that was executed as a truncated command; retain separate always-run diagnostics.
- [ ] Verify API 35 clipboard DAO/repository instrumentation after the emulator-runner script fix.
- [ ] Build and verify signed VaultOS 0.13.1 / versionCode 15 APKs; deliver as a private milestone build for in-place update testing.
- [ ] Execute the expanded clipboard repository instrumentation suite on API 35 and confirm the external-path rejection case passes.
- [ ] Complete DAO and repository API 35 instrumentation after the TCG emulator boot fix. Latest run 44887048363 passed the separate build/lint/APK job; its instrumentation job remains in progress.
- [x] Add an Open captured file action for archived clipboard entries using the private FileProvider; resolve and require a real file strictly inside `filesDir/vault`, with no fallback to the original external content URI.
- [x] Extract the canonical private-archive path resolver and add four JVM tests for valid files, outside/traversal paths, missing/directories and symlink escapes; run a standalone Kotlin smoke check covering the same path boundaries successfully.
- [x] Add a selectable, scrollable full-text inspection dialog alongside full-text copy; both paths use integrity-checked readback.
- [ ] Add Room-backed gallery/repository tests and verify file opening on the target phone without changing non-destructive archive semantics.
- [ ] Complete Redmi Note 12 Pro 5G clipboard acceptance across Chrome, ChatGPT, WhatsApp, Notes and password fields.
- [x] Scope both mirrored workflow concurrency groups by commit SHA, so verification of a newer PR head can run independently without cancelling or waiting behind an older commit.
- [x] Raise API 35 emulator boot timeout to 900 seconds, explicitly select SwiftShader/no-snapshot/wipe-data options, wait for Android and Package Manager readiness before Gradle, and upload logcat/package/process diagnostics even on failure.

**Boundary:** automatic monitoring still requires VaultOS to be the selected Android input method. This increment does not claim universal background clipboard interception.


## Phase 15 — backup and restore integrity

- [x] Resolve every restored archive manifest path as a canonical path strictly beneath the private vault; reject absolute, drive-prefixed, traversal, empty-segment and symlink-escape paths.
- [x] Reject an unsafe `trashOriginalRelativePath` instead of persisting an arbitrary filesystem destination into the Recycle Bin metadata.
- [x] Add JVM regression tests for nested restore paths, missing-but-valid restore destinations, absolute/traversal/drive-prefixed paths, symlink-parent escapes and vault-root aliases.
- [ ] Add fixture-based v1/v2 backup and restore round-trip tests for payload hashes, missing payloads, collision handling, trashed items, tags, collections, saved searches and metadata.
- [ ] Prove malformed-manifest and checksum failures do not leave partially restored catalogue state or orphaned payloads.
- [ ] Add Room migration regression tests for every supported schema version.
- [ ] Run the complete API 35 instrumentation suite and retain the Redmi physical acceptance gate.


## Phase 16 — USB-OTG detection and read-only import

On-device procedure: [USB-OTG testing guide](USB-OTG-TESTING.md).

- [x] Distinguish device USB Host capability from live USB device enumeration in diagnostics; clearly report when Android exposes no device instead of presenting this as a filesystem/import failure.
- [x] Add passive Android USB Host device enumeration and expose VID/PID, product/manufacturer, interface count and permission state in Settings.
- [x] Declare USB Host as an optional device feature so VaultOS remains installable on devices without USB Host support.
- [x] Add API 35 instrumentation coverage proving diagnostics mirror Android's current device list without requesting USB permissions.
- [ ] Reproduce the user's USB-C flash drive on Redmi Note 12 Pro 5G; record whether Android exposes a UsbDevice, its descriptors, and any system mount/notification behavior.
- [x] Add a user-initiated Android USB permission prompt for a currently detected device; enumerate interface class/subclass/protocol/endpoints only after permission is granted. No device handle is opened and no endpoint is read or written.
- [ ] Verify permission grant/denial and interface descriptor display on the Redmi with the actual USB-C drive.
- [x] Add a user-selected Android Storage Access Framework folder picker and bounded recursive importer (2,000-file / 16-directory-depth safety limits); copy files into the private VaultOS library without modifying source files.
- [ ] Verify the SAF picker exposes the actual USB volume on Redmi and test representative files, nested folders, permission denial and source-file hashes before/after import.
- [ ] Investigate a Rust read-only filesystem fallback only if the device is detected but Android does not expose a usable mounted/document-tree path.
- [ ] Complete physical verification on Redmi Note 12 Pro 5G with the user's actual USB-C drive and adapter.

**Boundary:** CI/emulator enumeration is not evidence that the user's physical flash drive is detected. No USB writes, formatting or destructive filesystem experiments are permitted.
