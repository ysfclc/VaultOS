# VaultOS product specification

_Last reconstructed: 2026-11-21 from the running source tree and the existing project documents._

## Product definition

VaultOS is a local-first Android file manager and personal content library. It gives one device a searchable view across existing shared storage and a private archive for imported or newly discovered content. It also provides optional clipboard/keyboard utilities, file management, metadata, installed-app inventory, a system DocumentsProvider, and user-directed external backup/restore.

The primary device acceptance target is the Xiaomi Redmi Note 12 Pro 5G. The supported Android baseline is API 28 (Android 9); compile/target SDK is 35.

## Product invariants

1. Existing shared-storage files are not moved or deleted by indexing or automatic archive capture.
2. The first device scan indexes accessible existing files without copying them; later detected additions may be copied into the private archive while originals remain in place.
3. Content stays on-device. No account, analytics, cloud AI, sync service, or INTERNET permission is part of the product contract.
4. Clipboard monitoring is an Android input-method capability, not a universal background clipboard hook. Automatic capture requires VaultOS to be the selected IME and remains subject to sensitive-clip/password-field exclusions.
5. Private archive content is app-private and is lost on uninstall unless separately backed up. User-selected SAF backups are not encrypted by VaultOS.
6. Build success is necessary but not sufficient for release acceptance. Device, accessibility, migration, backup/restore and update continuity must be verified independently.


## Entity and data-boundary rules

- Persisted concepts have one canonical Room entity each; screens and composables render those entities rather than inventing competing persisted models.
- Relationships use stable entity IDs, not copied mutable snapshots. Repository methods own integrity checks and storage boundaries.
- Clipboard history is an event entity (ClipboardHistoryEntry) linked to a canonical archived VaultItem. Repeated captures remain distinct events while identical payloads may share one archive item.
- Full clipboard payloads are loaded on demand from the private archive. Preview text is only a list summary and must never be treated as the complete payload.
- UI-only state such as expanded/collapsed cards, loading indicators and selection is transient and must not be persisted as domain data.

## Explicit non-goals / platform boundaries

- Read another application's private app data, sessions, or databases.
- Intercept every copy/cut action while another keyboard is active.
- Guarantee exact background scan timing across Android/OEM battery policies.
- Merge split APK components into a universal installable APK or export another app's private state.
- Upload, synchronize, or analyze user content with remote services.
- Claim neural/Gboard-equivalent glide typing or prediction.

## Requirement traceability

A requirement is ✅ only when its implementation, passing test evidence, and documentation are all traceable. 🟡 means source functionality exists but one or more automated/device verification obligations remain. This matrix records the evidence found in the current default branch, not assumptions from chat.

| Req ID | Requirement | Implementation | Test evidence | Documentation | Status |
|---|---|---|---|---|---|
| R-001 | Accept single/multiple shares, document-picker imports and selected text; preserve complete long text. | MainActivity.kt:122; VaultRepository.kt:24,105,173,393 | No repository/import integration tests identified. Manual acceptance: DEVICE-TEST-PLAN.md steps 4–7. | README.md sections “What is implemented” and “Long text”; PROJECT-STATUS.md | 🟡 |
| R-002 | Index existing accessible storage non-destructively, then catch up incrementally and resumably. | VaultRepository.kt:135,173,243; StorageScanWorker.kt:18 | No worker/repository scan tests identified. Manual acceptance: DEVICE-TEST-PLAN.md steps 1–3,11. | ARCHITECTURE.md “Storage model” and “Android integration”; README.md | 🟡 |
| R-003 | Provide a unified catalogue, global search, tags, collections, saved searches, duplicates and related items. | VaultDatabase.kt:55–144,176,218–293,319; MainActivity.kt:170–175 | No Room/UI integration tests identified. Manual global-search acceptance: DEVICE-TEST-PLAN.md Phase 13 step 3. | README.md “Unified library” and “Personal organization”; TASKS.md Phase 4/13 | 🟡 |
| R-004 | Make copy/move, ZIP create/extract and recursive delete cancellation-safe, atomic where applicable, bounded and resistant to path traversal/symlinks. | FileOperations.kt:20–31,36,99,125,195,256–298 | FileOperationsTest.kt:19–166 — 10 tests covering round-trip, cancellation cleanup, move, Zip Slip, symlink refusal, preflight and atomic copy. Latest hosted testDebugUnitTest passed in run 42261718395. | ARCHITECTURE.md “Recycle Bin and file operations”; README.md “Phase 2” and “Phase 6” | ✅ |
| R-005 | Keep timestamped clipboard events distinct from deduplicated payloads; validate text bytes/hash; expose search, pin, delete and full-text paste. | ClipboardHistoryRepository.kt; ClipboardPayloadIntegrity.kt; ClipboardHistoryActivity.kt; VaultDatabase.kt clipboard DAO; VaultKeyboardService.kt capture/paste paths | ClipboardPayloadIntegrityTest.kt — 5 JVM tests; ClipboardHistoryDaoInstrumentedTest.kt — 4 Room in-memory tests for duplicate events, pin/delete, clear-unpinned and search. Standalone Kotlin smoke check passed. Gradle/Android test execution and repository/IME integration tests remain pending. Manual acceptance: DEVICE-TEST-PLAN.md steps 6–7 and Phase 7 checks. | README.md “Phase 7”; docs/TASKS.md Clipboard Core increment; ARCHITECTURE.md privacy boundary | 🟡 |
| R-006 | Offer deterministic, on-device EN/PL/TR prefix, typo and next-word suggestions without persisting surrounding text. | KeyboardSuggestionEngine.kt:12,176–247; VaultKeyboardService.kt:56,108–154 | KeyboardSuggestionEngineTest.kt:8–84 — 13 tests for current helper behavior. Latest hosted testDebugUnitTest passed in run 42261718395; this does not validate useful real-world prediction or latency. | README.md Phases 10–13; TASKS.md Phases 11–13; KEYBOARD-V2.md | 🟡 implemented baseline; user-reported prediction/latency quality is inadequate |
| R-007 | Extract requested metadata locally without modifying source content; preserve organization and metadata in the catalogue. | VaultDatabase.kt:55–108,218–287; metadata extraction implementation documented in PROJECT-STATUS.md | MetadataTextParserTest.kt:8–34 — 4 parser tests; no end-to-end extraction/database tests identified. | README.md “Local metadata”; TASKS.md Phase 4 | 🟡 |
| R-008 | Back up archive payloads and catalogue manifest through SAF; restore v1 and v2 formats with payload integrity checks. | VaultBackupManager.kt; VaultBackupPath.kt canonical private-vault path containment | Four JVM tests cover safe relative-path resolution and reject traversal, absolute/drive paths, symlink escapes and root aliases. End-to-end v1/v2 round-trip, corrupt-manifest, checksum-failure, migration and rollback tests remain open. Manual acceptance: DEVICE-TEST-PLAN.md step 10. | README.md “External backup/restore” and “Data and backup notes”; RELEASE-PLAN.md 1.0.0 | 🟡 |
| R-009 | Expose the private archive through Android's DocumentsProvider for other apps' document pickers. | VaultDocumentsProvider.kt:26,33–135; AndroidManifest.xml provider declaration | No automated provider interoperability tests identified. Manual acceptance: DEVICE-TEST-PLAN.md step 9. | README.md “Native DocumentsProvider”; ARCHITECTURE.md “Android integration” | 🟡 |
| R-010 | Keep content processing local and avoid network/account/analytics dependencies. | AndroidManifest.xml:1–14 has no INTERNET permission; README.md privacy and Android-boundary sections | No automated manifest policy test identified; source/manifest inspection only. | README.md “Offline by design” and “Data and backup notes”; ARCHITECTURE.md “Privacy boundary” | 🟡 |
| R-011 | Produce signed debug/release APKs, verify signatures/checksums, and publish a durable private release asset. | app/build.gradle.kts:8–37; .github/workflows/android-build.yml and root android-build.yml | Hosted CI run 42261718395 passed tests, lint, signed APK assembly and signature verification; release assets were published. | README.md “Build”; PROJECT-STATUS.md 0.13.0 verification | ✅ for current CI pipeline |
| R-012 | Accept 1.0.0 only after update, backup/restore, clipboard/IME, permissions, OEM background, accessibility, layout and performance checks pass on the actual target phone. | DEVICE-TEST-PLAN.md; RELEASE-1.0-GATE.md | No physical-device results recorded. | RELEASE-PLAN.md 1.0.0; DEVICE-TEST-PLAN.md Phase 13 and release gate | 🟡 not device-verified |
| R-013 | Provide responsive, adaptive EN/PL/TR next-word prediction using short transient context and optional bounded local learning; no typing-path disk I/O or expensive unbounded work on the main thread. | Planned: docs/KEYBOARD-V2.md; current baseline in KeyboardSuggestionEngine.kt and VaultKeyboardService.kt | Planned KBD-1/KBD-2 pure-engine, memory-compatibility and stale-result tests; device latency tests remain required. | docs/KEYBOARD-V2.md | ⚪ planned |

| R-014 | Detect USB-OTG mass-storage devices through Android USB Host APIs and enable safe, read-only browsing/import without modifying source media. Rust may provide a read-only filesystem fallback only if Android cannot mount/expose the detected device. | Phase 16: UsbOtgDiagnostics.kt, user-triggered UsbManager permission, permission-gated interface descriptors, and bounded read-only SAF importer; Android USB Host / Storage Access Framework | API 35 enumeration and permission-gated descriptor instrumentation; Redmi physical-device/actual-drive detection, permission prompt, SAF volume access and source-integrity import tests remain required. | docs/TASKS.md Phase 16; docs/DEVICE-TEST-PLAN.md | 🟡 implementation started; physical detection unverified |

## Current test inventory

The current source tree exposes five JVM test classes and 36 @Test methods: FileClassifierTest (4), FileOperationsTest (10), MetadataTextParserTest (4), KeyboardSuggestionEngineTest (13), and ClipboardPayloadIntegrityTest (5). The last documented hosted run 42261718395 predates the clipboard integrity addition; the new five-test class has not yet been executed through Gradle.

Important missing automated coverage: repository import/readback, scan worker/resume state, Room migrations, clipboard repository/IME integration and actual capture-to-paste flow, backup/restore compatibility and corruption recovery, DocumentsProvider interoperability, and Compose/IME end-to-end behavior. The new Room clipboard DAO instrumentation tests are written but have not yet been executed.

## Release acceptance

Release 0.13.0 is the latest source/release line. Version 1.0.0 remains blocked on the physical-device acceptance matrix and the data-safety verification gaps above. Do not mark any device-only requirement complete from CI or source inspection alone.
