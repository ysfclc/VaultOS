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
| Keyboard suggestions | 🟡 partial | 0.13 baseline has 13 helper tests, but user reports slow typing and poor prediction; V2 direction documented, implementation not started |
| Metadata extraction | 🟡 partial | Parser tests exist; end-to-end extraction/provider behavior lacks integration tests |
| Backup and restore | 🟡 partial | v1/v2 paths implemented; no automated round-trip, corruption, or migration test suite identified |
| DocumentsProvider | 🟡 partial | Implemented; no provider interoperability test evidence |
| Signed CI/release | ✅ done | 0.13.0 hosted run 42261718395 passed and published private draft-release assets |
| Physical-device acceptance | ⚪ not started | 1.0.0 gate remains open for Redmi Note 12 Pro 5G |
| Signing-material remediation | 🔴 blocked | GOV-001 needs user-provisioned replacement material and verified update-lineage plan |

## 3. Currently in flight
- 1.0.0 acceptance — automated CI is green; Redmi physical-device matrix and data migration/restore evidence remain open.
- GOV-001 signing migration — blocked on replacement keystore/secrets and an update-continuity decision; preserve the existing key until migration is proven.
- Keyboard V2 — KBD-1 typing-path responsiveness and KBD-2 contextual prediction are now the next product increments; see docs/KEYBOARD-V2.md.
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
1. KBD-1: remove avoidable disk/JSON and fuzzy-search work from the keyboard typing path; add deterministic tests and a pure-engine microbenchmark.
2. KBD-2: add opt-in, bounded contextual trigram learning while preserving existing word/bigram data and privacy boundaries.
3. KBD-3/KBD-4: refine candidate UX, then measure EN/PL/TR latency and prediction usefulness on the Redmi Note 12 Pro 5G. Resume backup/restore integrity tests after the first keyboard slice.

## 7. Open questions for the human
- [ ] When will replacement signing material and GitHub Actions secrets be available for GOV-001? Default: do not rotate or delete the existing identity; keep the remediation blocked.
- [ ] Can the Redmi Note 12 Pro 5G be used for the physical acceptance matrix, and what Android/MIUI build is installed? Default: device verification remains pending; do not infer results.

## Session Handoff

**Done this session**
- Reassessed keyboard quality against actual source and user feedback; documented the adaptive predictive keyboard V2 direction and its staged delivery plan.

**State changed**
- docs/KEYBOARD-V2.md — V2 product direction, technical approach, KBD-1 through KBD-4 and DoD.
- docs/SPEC.md — R-006 corrected to partial; R-013 added for responsive contextual prediction.
- docs/DECISIONS.md — ADR-009 keeps prediction local and latency-first.
- docs/TASKS.md — Phase 14 KBD-1 through KBD-4.
- docs/PROJECT-STATUS.md — records user-reported keyboard quality gap and source evidence.
- README.md — links the keyboard V2 plan.
- docs/STATE.md — updated status and next increments.

**Verified**
- Build: not run; documentation-only planning increment, no application source changed.
- Tests: not run; current baseline remains 13 keyboard helper tests from the recorded 0.13.0 CI run. No performance or IME integration claim is made.

**Not done / next**
1. Implement KBD-1: cache local typing-memory snapshots, bound correction work, move expensive ranking off the keystroke path, and add tests/benchmark.
2. Implement KBD-2: opt-in contextual prediction with bounded trigram counts and backward compatibility.

**Open questions**
- None blocking for KBD-1. Existing private-learning opt-in and clear controls remain the defaults.

**Risks / landmines**
- Do not add network permission, cloud text processing or a heavyweight model dependency.
- Preserve v1 SharedPreferences typing-memory data and existing password/email/URL/autocomplete exclusions.
- Do not claim latency improvement until measured on the Redmi Note 12 Pro 5G.
- The current keyboard is not complete merely because the existing helper tests pass.
