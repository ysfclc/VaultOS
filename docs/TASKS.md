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
- [ ] Add signed release build and key-management instructions.
- [x] Add integrity verification for every restored archived payload.
- [ ] Add app package version comparison and split-package export guidance.

## Phase 4 — intelligent organization

- [x] Deterministic categories and unified search.
- [x] Pinned items and category collections.
- [x] Preserve index-only item references in backup manifest.
- [ ] User-defined tags and collections.
- [ ] Related-item and duplicate-group views.
- [ ] Optional local metadata extraction (PDF/document title, image metadata, APK package metadata).
- [ ] Optional on-device semantic indexing, disabled unless explicitly configured.

## Phase 5 — device integration and polish

- [x] Share-sheet target and IME registration.
- [x] MediaStore observer and periodic catch-up scan.
- [ ] Xiaomi autostart/battery optimization onboarding.
- [ ] Test clipboard behavior across Chrome, ChatGPT, WhatsApp, Notes and password fields.
- [ ] Test Android 13/14/15 behavior and permission flows.
- [ ] Motion, accessibility, large-font and landscape polish.

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
