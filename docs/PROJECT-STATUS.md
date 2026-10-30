# VaultOS — project status

**Release line:** 0.1.0 · **Application ID:** `com.vaultos.app` · **Target:** Redmi Note 12 Pro 5G

## Product contract

VaultOS is a local-first Android file manager and personal content library. It combines a unified catalogue with the normal Android filesystem rather than moving existing user files into an opaque replacement structure.

### Locked decisions

- Automatically archive incoming shared files, clipboard text and newly detected files in readable shared storage.
- Index the existing device library on first scan without copying existing media.
- Preserve original folders and paths; expose a unified smart library over the originals and the private archive.
- Include an optional custom keyboard from the first release. Clipboard monitoring is available only while VaultOS is the selected input method.
- Keep Gboard installed and provide a keyboard switch action.
- Keep all content local. No network permission, account, analytics, or cloud AI.
- Provide manually configured SAF backup/restore to USB, external storage, or a user-selected document provider.

## Implemented in 0.1.0 source

- [x] Kotlin + Jetpack Compose Android application shell.
- [x] Share target for single/multiple files and selected text.
- [x] Manual import through Android's document picker.
- [x] Private archive with streaming copy, SHA-256 deduplication and temporary-file commit.
- [x] First-run shared-storage index without copying existing files.
- [x] Incremental MediaStore generation scanning on Android 11+, with timestamp fallback.
- [x] WorkManager catch-up scan and supported DownloadManager completion receiver.
- [x] Optional VaultOS IME with clipboard listener, sensitive-clip check, password-field exclusion, capture action, saved-text paste and keyboard switch action.
- [x] Full-text files and chunked search index; no app-defined text-length cutoff.
- [x] Device and archive browsing; copy, cut/move, rename, delete, create folder, share and open.
- [x] Installed package catalogue and APK component archiving.
- [x] Android DocumentsProvider for browsing the private archive in system pickers.
- [x] Manual SAF backup/restore; manifest preserves archived records and index-only references, with archived-payload hash verification on restore.
- [x] Offline classifier unit tests.
- [x] Memo-style incoming ZIP extraction, validation, SHA-256 replay guard, import-note generation and automatic repository commit in GitHub Actions.

## Verification status

- [x] Kotlin compiler successfully compiled the standalone classifier source.
- [x] Standalone classifier smoke test passed 10 classification cases and filename sanitization.
- [x] Android manifest and resource XML parse checks are part of the delivery verification.
- [ ] Full Gradle unit-test + lint + APK build: requires Android SDK Platform 35 and Gradle 8.9. The current build container has JDK 21 but no Android SDK/Gradle installation and cannot resolve external hosts, so a local APK cannot be truthfully marked verified here. The included GitHub Actions workflow provisions the toolchain and performs this build.
- [ ] Physical Redmi Note 12 Pro 5G test: must be completed after installing the Actions APK. See `DEVICE-TEST-PLAN.md`.

## Release acceptance criteria

A release is considered device-verified only after the complete Actions workflow succeeds and the device test plan passes. In particular, test large shares, full clipboard capture with VaultOS selected as the IME, file-provider interoperability, Xiaomi background scheduling and external backup/restore.
