# VaultOS 1.0.0 release gate

1.0.0 means device-verified, not merely buildable. The gate is intentionally not marked complete by local JVM tests or emulator assumptions.

## Automated gate

- [x] Local unit tests: 18 passed, 0 failures.
- [x] Lint: 0 errors (16 warnings remain; mostly expected broad-storage / dependency / platform compatibility notices).
- [x] Debug and release APKs assemble.
- [x] Both APKs verify against the persistent sideload certificate.
- [ ] GitHub-hosted Actions completes for the delivered source ZIP and uploads the release artifact.
- [ ] Hosted artifact checksum and certificate match the local verification record.

## Redmi Note 12 Pro 5G gate

Use the GitHub Actions VaultOS-release-APK artifact. Do not uninstall the existing installation when checking the 0.5.0 → 0.6.0 update path.

- [ ] Update installation preserves app data and signing identity.
- [ ] Existing indexed and archived items remain correct after update.
- [ ] Backup/restore round-trip preserves payloads and all manifest v2 organization data.
- [ ] Restore of a legacy v1 backup remains compatible.
- [ ] Clipboard and IME behavior passes Chrome, ChatGPT, WhatsApp, Notes, sensitive clips and password-field checks.
- [ ] Gboard switching and saved-text paste work.
- [ ] Android permission deny/grant/revoke behavior is correct on the installed OS version.
- [ ] Xiaomi MIUI/HyperOS background catch-up is measured with default and relaxed battery settings.
- [ ] TalkBack, enlarged text, display scaling, portrait, landscape and gesture navigation pass.
- [ ] Large scan/import, batch operations, thumbnail scrolling, backup/restore and relaunch stability pass.
- [ ] No data loss, repeat captures, crashes or ANRs remain.

## Device record

Record model, Android version/API, MIUI/HyperOS version, VaultOS version, free storage, storage provider used, and observed result for each test group. Use synthetic test content; never test with real passwords or private conversations.

## Release decision

- [ ] All required tests passed and evidence recorded.
- [ ] Known limitations documented.
- [ ] Release 1.0.0 approved.

Until all required device checks are complete, the product status remains 0.6.0 locally verified / device verification pending.
