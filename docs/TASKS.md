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
- [ ] Add recycle bin for managed archive deletions.
- [ ] Add robust progress/cancel UI for large file operations.
- [ ] Add thumbnail loading and file-type previews.
- [ ] Add multi-select and batch operations.
- [ ] Add archive extraction and creation.

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
