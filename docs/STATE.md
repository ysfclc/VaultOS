# State Ledger
_Last updated: 2026-11-12 by Continuity Engineer — session 1_

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
| Keyboard suggestions | ✅ done | Deterministic EN/PL/TR engine has 13 JVM tests; keyboard usability still needs device acceptance |
| Metadata extraction | 🟡 partial | Parser tests exist; end-to-end extraction/provider behavior lacks integration tests |
| Backup and restore | 🟡 partial | v1/v2 paths implemented; no automated round-trip, corruption, or migration test suite identified |
| DocumentsProvider | 🟡 partial | Implemented; no provider interoperability test evidence |
| Signed CI/release | ✅ done | 0.13.0 hosted run 42261718395 passed and published private draft-release assets |
| Physical-device acceptance | ⚪ not started | 1.0.0 gate remains open for Redmi Note 12 Pro 5G |
| Signing-material remediation | 🔴 blocked | GOV-001 needs user-provisioned replacement material and verified update-lineage plan |

## 3. Currently in flight
- 1.0.0 acceptance — automated CI is green; Redmi physical-device matrix and data migration/restore evidence remain open.
- GOV-001 signing migration — blocked on replacement keystore/secrets and an update-continuity decision; preserve the existing key until migration is proven.
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
1. Add automated backup/restore integrity coverage for v1 and v2 manifests, payload SHA-256 failures, invalid/corrupt input, and trashed/organization records without breaking existing restore behavior.
2. Add Room migration regression tests for schema versions 1→4 and validate preservation/cascade invariants.
3. Complete the Redmi Note 12 Pro 5G acceptance matrix and record observed OS/build/device results before calling 1.0.0 device-verified.

## 7. Open questions for the human
- [ ] When will replacement signing material and GitHub Actions secrets be available for GOV-001? Default: do not rotate or delete the existing identity; keep the remediation blocked.
- [ ] Can the Redmi Note 12 Pro 5G be used for the physical acceptance matrix, and what Android/MIUI build is installed? Default: device verification remains pending; do not infer results.

## Session Handoff

**Done this session**
- Governance bootstrap and source-of-truth reconciliation — created the project ledger, spec/traceability matrix, reconstructed ADRs, risk register and changelog; corrected the stale 1.0.0 gate against 0.13.0 source/CI evidence.

**State changed**
- docs/STATE.md — session ledger and handoff.
- docs/SPEC.md — product contract and 12-row requirement traceability matrix.
- docs/DECISIONS.md — reconstructed architecture decision records.
- docs/RISKS.md — current risk register, including GOV-001 and data-safety risks.
- CHANGELOG.md — release milestone history through 0.13.0.
- docs/RELEASE-1.0-GATE.md — reconciled automated status; device and migration/restore gates remain open.
- README.md — entry points to the new governance docs.

**Verified**
- Build: not run; documentation-only increment, no application source/build files changed, and no local checkout/Android SDK is mounted in this runtime.
- Tests: not run this session; latest hosted CI run 42261718395 is recorded as passing in the repository's 0.13.0 status/README. Source inspection found 31 test methods across four JVM test classes; CI summary does not separately report the method count.

**Not done / next**
1. Add automated backup/restore integrity tests for manifest v1/v2, malformed input, missing/corrupt payloads and SHA-256 mismatch.
2. Follow with Room migration tests; keep physical-device acceptance separate and evidence-based.

**Open questions**
- Replacement signing material/secrets and physical Redmi acceptance remain user-dependent; defaults are to keep both gates open.

**Risks / landmines**
- Do not delete or regenerate the tracked signing identity until an approved update-lineage migration is tested.
- External backups are not encrypted by VaultOS.
- A green hosted build does not prove physical keyboard, filesystem, background scheduling, accessibility, or restore behavior.
