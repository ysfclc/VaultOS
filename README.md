# VaultOS for Android

**A local-first Android file library and manager.** This repository contains a native Kotlin/Jetpack Compose application, an optional capture keyboard, a DocumentsProvider, a private archive, local indexing, and manual external backup/restore.

## What is implemented

See `docs/PROJECT-STATUS.md`, `docs/TASKS.md`, and `docs/RELEASE-PLAN.md` for the locked architecture, implementation status, phased task ledger, and next milestones.

- **Android share target:** receive `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, selected text (`ACTION_PROCESS_TEXT`), and incoming content URIs. Shared files are streamed into the private VaultOS archive and catalogued.
- **Manual import:** import one or many documents through Android's system picker.
- **Private archive:** incoming files are copied into app-private storage. The original shared-storage file is not moved or deleted.
- **Existing-storage index:** after the user grants all-files access, the first scan records accessible existing files without copying them. Later discoveries are copied into the archive; originals remain in their existing folders.
- **Resumable scanning:** the initial device-library baseline scans readable shared storage in bounded batches and stores its pending directory queue. Routine catch-up uses MediaStore generation numbers on Android 11+ (and modification timestamps on older versions) rather than repeatedly walking every folder. MediaStore notifications and supported DownloadManager completion events request a scan; a 15-minute periodic job is the catch-up mechanism.
- **Unified library:** categories, full-text search, pinned items, recent items, archive status, file details, and device/vault folder browsing.
- **Long text:** complete UTF-8 text is stored as a file. Search uses chunked text indexing; the Room row only holds a small preview. The capture keyboard has a **Paste saved** action which inserts the complete most-recent saved text directly into the focused input connection, bypassing the normal clipboard preview/history UI.
- **File operations:** browse, open, share, archive a copy, create folders, copy, cut/move, rename and permanently delete files in accessible shared storage or the private vault.
- **Application catalog:** list visible installed packages and archive the base APK plus any split APK components exposed by Android. This preserves the package payload but does not include another app's private data, sessions, or databases; a split package is not necessarily installable by opening one component on its own.
- **Native DocumentsProvider:** exposes the private VaultOS archive in Android's document picker so other applications can browse and open its files.
- **External backup/restore:** choose a SAF folder (for example, a USB drive or cloud-drive provider), copy the archive and a JSON item manifest, and restore from that backup later.
- **Offline by design:** no `INTERNET` permission, account, analytics SDK, or cloud AI dependency.

## Android boundaries (important)

Android intentionally does not allow an ordinary background application to intercept every copy/cut operation in every other app. Clipboard monitoring is available to the currently selected default input method. Therefore:

- The VaultOS keyboard must be enabled **and selected as the active keyboard** for automatic clipboard-change capture. When Gboard is active, VaultOS cannot monitor its clipboard events.
- Automatic clipboard capture is enabled by default once VaultOS is selected as the active keyboard. You can switch it off in VaultOS Settings. Selecting a third-party keyboard is a deliberate system-level choice; Android displays its standard keyboard privacy warning. The keyboard skips clipboard items marked sensitive by their source app and avoids capture when the active editor is a password field. Not all apps mark sensitive content correctly, so review the capture policy before using it for confidential material.
- VaultOS also has a manual **Capture** action in the app and a keyboard capture button. Shared text and files do not require changing your keyboard.
- Android file-system changes are not universally observable. MediaStore notifications trigger scans while the VaultOS process is alive, the DownloadManager receiver can trigger a scan for supported downloads, and WorkManager performs periodic catch-up scans. On Android 11+, MediaStore generation tracking identifies changed indexed items. Android/MIUI may defer background work; this is not an instantaneous system-wide file hook.
- Full shared-storage access is requested because file management and device-wide indexing are core features. Even with that permission, Android protects other apps' private data under locations such as `Android/data` and `Android/obb`.
- APK export here archives the package's base APK. It does not include another app's private data, sessions, or databases. Split APKs are not automatically combined into a universal installable package.

## Redmi Note 12 Pro 5G setup

1. Install the debug APK from the GitHub Actions artifact (or build it locally).
2. Open VaultOS → **Settings & permissions** → **Grant all-files access**. In Android's special app access screen, enable *Allow access to manage all files* for VaultOS.
3. Return to VaultOS and use **Scan storage now**. The first scan indexes existing accessible storage; it does not duplicate it into the archive. It can take time on a device with many files and continues in resumable background batches.
4. For automatic clipboard capture, open **Manage keyboards**, enable **VaultOS Capture Keyboard**, then select it as the active keyboard. This replaces Gboard while selected. Android will show its normal security warning for third-party keyboards. Enable **Automatically save copied text** only when you are comfortable with that behavior. Gboard remains installed and can be selected again at any time.
5. The keyboard toolbar includes **Switch** to move to the next installed keyboard (normally Gboard), and **Hide** to dismiss the keyboard. You can also change keyboards through Android settings at any time.
6. For better Xiaomi background reliability, allow VaultOS autostart if your ROM exposes that setting and set its battery policy to *No restrictions*. This improves the chance of timely scans but cannot override Android/MIUI scheduling decisions.
7. Choose an external backup folder in VaultOS Settings and use **Back up now**. **Restore backup** can rebuild the archive and catalogue from that backup.

## Build

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and Gradle 8.9. The GitHub Actions workflow installs these dependencies for you.

```bash
gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The supplied workflow is available in both places:

- standalone copy: `android-build.yml`
- GitHub Actions location: `.github/workflows/android-build.yml`

For the Memo-style ZIP update flow, first add the standalone YAML at .github/workflows/android-build.yml (this alone does not start a build). Then create an incoming/ folder and upload the archive as incoming/VaultOS-source.zip. The workflow validates the archive, extracts the complete project over the repository, creates an import note under docs/updates/, commits and pushes the imported files to the current branch, then tests and builds them. The workflow definition at .github/workflows/android-build.yml is kept authoritative during ZIP imports so an older archive cannot roll back a newer build fix. Keep the filename stable; replacing the ZIP with new contents is detected by SHA-256. Alternatively, upload/extract the source ZIP yourself and run **Actions → VaultOS Android Build → Run workflow**. The workflow runs unit tests and lint before building and publishing the debug APK as a downloadable Actions artifact (GitHub packages uploaded artifacts in a ZIP). It also publishes the SHA-256 checksum and build metadata.

## Data and backup notes

- Archived content is stored under the app's private `files/vault/` directory. Android removes this private data when the application is uninstalled unless the user restores from an external backup.
- Backups copy files and a metadata manifest to the user-selected destination. The backup is **not encrypted by VaultOS**; protect the chosen drive/provider accordingly.
- The app does not silently upload or sync files to a server.
- Deleting from the file browser is permanent and requires confirmation. Removing an item from the library is deliberately not wired to destructive deletion.

## Project layout

```text
app/src/main/java/com/vaultos/app/
  MainActivity.kt                  Compose application and file browser
  VaultApplication.kt              MediaStore change observer
  data/VaultDatabase.kt             Room catalogue and chunked search index
  storage/VaultRepository.kt        Import, archive, hash and long-text storage
  storage/StorageScanWorker.kt      Resumable shared-storage scan
  storage/VaultBackupManager.kt     SAF backup and restore
  storage/DownloadCompletedReceiver.kt
  keyboard/VaultKeyboardService.kt  Optional IME and direct saved-text paste
  provider/VaultDocumentsProvider.kt Android Storage Access Framework provider
  util/FileClassifier.kt            Deterministic content classification
```
