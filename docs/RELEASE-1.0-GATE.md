# VaultOS 1.0.0 release gate

1.0.0 means device-verified, not merely buildable. The gate is intentionally not marked complete by local JVM tests, a hosted Android build, or emulator assumptions.

## Current source and automated gate

Current release line: 0.13.0 / versionCode 14 / application ID com.vaultos.app / minSdk 33 for future releases (Android 13+). The last distributed 0.13.0 / versionCode 14 artifact remains API 28+.

- [x] GitHub-hosted workflow run 42261718395 completed for the 0.13.0 source.
- [x] testDebugUnitTest passed in that run.
- [x] lintDebug passed in that run.
- [x] Signed debug and release APKs assembled.
- [x] Both APK variants passed signature verification against the retained VaultOS signing certificate.
- [x] Checksums and build metadata were prepared; signed APKs and checksums were published to the private draft release documented in README.md and PROJECT-STATUS.md.
- [ ] Future signed release APK reports minSdk 33 and uses the verified v3.1 replacement signer lineage; API 28–32 are intentionally unsupported by future releases.
- [ ] Add automated Room migration regression coverage for the supported schema history.
- [ ] Add automated backup/restore compatibility and failure-path coverage for manifest v1 and v2, including payload hash mismatch, missing/corrupt data, collision handling, and organization/trash state.

Source inspection on 2026-11-12 found four JVM test classes with 31 @Test methods: classifier (4), file operations (10), metadata parser (4), and keyboard suggestion engine (13). The hosted run is recorded as passing; its summary does not separately report the method count. Repository/Room/scan/clipboard/backup/provider/UI integration coverage remains limited. See SPEC.md for the traceability matrix.

## Redmi Note 12 Pro 5G gate

Run the current 0.13.0 signed release APK as an in-place update over the existing installation. Do not uninstall first. Record Android/API and MIUI/HyperOS versions, app version, free storage, display/font scaling and observed outcomes.

- [ ] Update installation preserves app data, archive files, clipboard history, tags, collections, saved searches, settings and keyboard preferences.
- [ ] Backup/restore round-trip preserves payload hashes, index-only references, Recycle Bin state, tags, collections, metadata and saved searches.
- [ ] Restore accepts a known manifest v1 backup and a current v2 backup.
- [ ] Clipboard/IME behavior passes Chrome, ChatGPT, WhatsApp and Notes with normal text, long text, multi-item content, sensitive clips and password-field cases.
- [ ] Gboard switching and full saved-text paste work; automatic capture stops when another keyboard is active.
- [ ] Android permission deny/grant/revoke behavior is correct on the installed OS version.
- [ ] Xiaomi MIUI/HyperOS catch-up behavior is measured with default and relaxed battery settings; no exact schedule is assumed.
- [ ] TalkBack, enlarged text, display scaling, portrait, landscape and gesture navigation pass.
- [ ] Large scan/import, batch operations, thumbnail scrolling, backup/restore and relaunch stability pass.
- [ ] No data loss, repeat captures, crashes or ANRs remain.

Use synthetic content only. Never test with real passwords or private conversations. Follow docs/DEVICE-TEST-PLAN.md, including the detailed Phase 13 navigation, keyboard and global-search checks.

## Device record

No physical-device acceptance results were present in the repository at the time of this review. Do not infer them from CI. Record the exact device/OS/build and per-test observations here or in a linked dated acceptance report.

## Release decision

- [ ] All automated migration/restore and failure-path tests passed.
- [ ] All required physical-device tests passed and evidence was recorded.
- [ ] Known limitations and data-handling boundaries are documented.
- [ ] Release 1.0.0 approved.

Until every required item is evidenced, VaultOS remains 0.13.0 CI-verified but not 1.0.0 device-verified.
