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
- [ ] Verify actual recommendation relevance and visual quality on Redmi Note 12 Pro 5G in ChatGPT, Chrome, WhatsApp and Notes.
- [ ] Confirm no unwanted candidate insertion, duplicate spaces or stale spell-check replacement while typing quickly.
