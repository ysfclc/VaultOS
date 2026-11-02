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

## CI build diagnosis — 2026-10-30, run 5

The Android SDK setup fix worked: the latest run installed the SDK, configured Gradle 8.9, and reached Kotlin compilation. The build then exposed three source-level issues, now corrected:

- The optional keyboard read the active input subtype from `InputMethodService`; that property belongs to `InputMethodManager`. Locale selection now queries the manager and falls back to the device locale.
- The DocumentsProvider used an index-based `MatrixCursor.RowBuilder.add` overload that does not exist. Rows now add values by column name, so arbitrary requested projections remain supported.
- Backup restore called Room and repository suspend functions from a non-suspending function. `VaultBackupManager.restore` is now suspending; the existing caller already invokes it inside `withContext(Dispatchers.IO)`.

These are compile fixes only. A fresh Actions run is still required to confirm the complete test, lint, and APK build. Physical-device behavior remains unverified.


## CI build diagnosis — 2026-11-02, run 10

The latest Android Actions run reached Android resource linking and failed with AAPT: resource style/Theme (aka com.vaultos.app:style/Theme) not found.

Cause: values/styles.xml defined Theme.VaultOS with the explicit parent android:style/Theme.Material.Light.NoActionBar, but the values-v27/styles.xml variant redeclared the same style without a parent. Android therefore inferred the nonexistent @style/Theme parent for the API-27+ variant. The qualified variant also needed to preserve the base theme items because Android selects the qualified style resource as a whole.

Correction in this source:
- values-v27/styles.xml now declares the same explicit Android parent as the base style.
- It repeats every base theme item and retains android:windowLightNavigationBar=false.
- Both workflow copies preflight the uploaded ZIP before import and validate the checked-out XML after import. They reject a missing/wrong parent, missing base items, or missing navigation override.
- ZIP import excludes both workflow YAML copies so a stale archive cannot overwrite either the authoritative workflow or its root mirror.

The resource XML and workflow checks are verified locally. A clean Gradle build was then completed successfully using JDK 17, Gradle 8.9, Android SDK Platform 35, and Build Tools 35.0.0:

- gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug — BUILD SUCCESSFUL.
- Unit tests: 4 passed, 0 failed.
- lintDebug passed.
- assembleDebug produced app/build/outputs/apk/debug/app-debug.apk (17 MB).
- APK SHA-256: fdd641a3d4bd2ed9711c22f418c0342f54468313325c4eccb66f6c6730640042.

GitHub-hosted Actions confirmation and physical Redmi Note 12 Pro 5G verification remain pending.
