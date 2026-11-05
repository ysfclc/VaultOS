# VaultOS for Android

**A local-first Android file library and manager (release 0.12.1).** This repository contains a native Kotlin/Jetpack Compose application, an optional capture keyboard, a DocumentsProvider, a private archive, local indexing, and manual external backup/restore.

## What is implemented

See `docs/PROJECT-STATUS.md`, `docs/TASKS.md`, and `docs/RELEASE-PLAN.md` for the locked architecture, implementation status, phased task ledger, and next milestones.

- **Android share target:** receive `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, selected text (`ACTION_PROCESS_TEXT`), and incoming content URIs. Shared files are streamed into the private VaultOS archive and catalogued.
- **Manual import:** import one or many documents through Android's system picker.
- **Private archive:** incoming files are copied into app-private storage. The original shared-storage file is not moved or deleted.
- **Existing-storage index:** after the user grants all-files access, the first scan records accessible existing files without copying them. Later discoveries are copied into the archive; originals remain in their existing folders.
- **Resumable scanning:** the initial device-library baseline scans readable shared storage in bounded batches and stores its pending directory queue. Routine catch-up uses MediaStore generation numbers on Android 11+ (and modification timestamps on older versions) rather than repeatedly walking every folder. MediaStore notifications and supported DownloadManager completion events request a scan; a 15-minute periodic job is the catch-up mechanism.
- **Unified library:** categories, full-text search, pinned items, recent items, archive status, file details, and device/vault folder browsing.
- **Personal organization:** create tags and collections, assign them to items, surface related files, inspect duplicate groups using existing SHA-256 hashes, and save/reopen searches.
- **Local metadata:** on request, extract available metadata from images, PDFs, DOCX/ODT, audio/video, APKs and text. Extraction runs on-device and does not alter source files.
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

1. Install the **VaultOS-release-APK** from the GitHub Actions workflow artifact. Keep using this workflow-built release APK for later updates; it is signed with the persistent VaultOS certificate.
2. Open VaultOS → **Settings & permissions** → **Grant all-files access**. In Android's special app access screen, enable *Allow access to manage all files* for VaultOS.
3. Return to VaultOS and use **Scan storage now**. The first scan indexes existing accessible storage; it does not duplicate it into the archive. It can take time on a device with many files and continues in resumable background batches.
4. For automatic clipboard capture, open **Manage keyboards**, enable **VaultOS Capture Keyboard**, then select it as the active keyboard. This replaces Gboard while selected. Android will show its normal security warning for third-party keyboards. Enable **Automatically save copied text** only when you are comfortable with that behavior. Gboard remains installed and can be selected again at any time.
5. The keyboard toolbar includes **Switch** to move to the next installed keyboard (normally Gboard), and **Hide** to dismiss the keyboard. You can also change keyboards through Android settings at any time.
6. For better Xiaomi background reliability, allow VaultOS autostart if your ROM exposes that setting and set its battery policy to *No restrictions*. This improves the chance of timely scans but cannot override Android/MIUI scheduling decisions.
7. Choose an external backup folder in VaultOS Settings and use **Back up now**. **Restore backup** can rebuild the archive and catalogue from that backup.

## Build

Requirements: JDK 17, Android SDK Platform 35, Build Tools 35.0.0, and Gradle 8.9. The GitHub Actions workflow installs these dependencies for you.

```bash
gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The supplied workflow is available in both places:

- standalone copy: `android-build.yml`
- GitHub Actions location: `.github/workflows/android-build.yml`

For the Memo-style ZIP update flow, first add the standalone YAML at .github/workflows/android-build.yml (this alone does not start a build). Then create an incoming/ folder and upload the archive as incoming/VaultOS-source.zip. The workflow imports the archive only when incoming/VaultOS-source.zip itself is added or modified (or when you manually dispatch the workflow); ordinary code and documentation pushes never replay a stale ZIP. It validates the archive, rejects private signing material, extracts the project while preserving the repository's signing/ directory and protected workflow files, creates an import note under docs/updates/, safely rebases its import commit onto the latest branch head, then tests and builds. If the import conflicts with newer edits, it stops without overwriting them and asks for a refreshed ZIP. Both workflow copies (.github/workflows/android-build.yml and the root android-build.yml mirror) are exact mirrors and are excluded from ZIP extraction. Keep the filename stable; replacing the ZIP with new contents is detected by SHA-256. When a release changes the workflow YAML itself, replace both YAML copies separately before uploading the source ZIP; the importer intentionally excludes these protected workflow files. The current source package includes the matching updated copies. Alternatively, upload/extract the source ZIP yourself and run **Actions → VaultOS Android Build → Run workflow**. The workflow runs unit tests and lint before building both signed variants. It publishes `VaultOS-release-APK` (recommended for normal installation/update), `VaultOS-debug-APK`, and a `VaultOS-build-metadata` artifact containing checksums and build information. GitHub packages each uploaded artifact in a ZIP.

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
  keyboard/VaultKeyboardService.kt  Daily-use IME, spellcheck and clipboard access
  provider/VaultDocumentsProvider.kt Android Storage Access Framework provider
  util/FileClassifier.kt            Deterministic content classification
```


## Phase 2 — hardened file manager (0.2.0)

The file browser now supports checkbox multi-selection with batch copy, move, archive, ZIP creation, and delete/recycle-bin actions. ZIP files can be extracted in place. Long copy, move, archive, extraction, and delete operations expose progress and cancellation; temporary copy/ZIP/extraction outputs are cleaned up if cancelled. Same-filesystem moves use rename, while cross-volume moves commit a full copy before removing the source.

Managed archive deletions go to the Recycle Bin rather than immediately removing data. Items can be restored or permanently deleted. The database is upgraded through a Room migration, and external backup manifests retain recycle-bin state. Image items show downsampled thumbnails and a larger item-detail preview; other categories retain type-specific previews/icons.

ZIP extraction rejects path traversal, limits archives to 20,000 entries and caps expanded data at 2 GiB. See docs/TASKS.md and docs/DEVICE-TEST-PLAN.md for implementation and on-device validation status.


## Phase 4 — smarter library (0.4.0)

VaultOS 0.4.0 adds user-defined tags and collections, related-item navigation, SHA-256 duplicate groups, saved searches, optional-on-request local metadata extraction, and backup manifest v2. Version 2 backups preserve these organization records; the restore path continues to accept version 1 manifests.

Semantic indexing is not enabled in 0.4.0. It remains gated until an on-device embedding runtime/model is selected and tested; the app does not download a model or send content to a service.


## Phase 5 — device integration and polish (0.5.0)

VaultOS 0.5.0 adds Xiaomi MIUI/HyperOS background-work onboarding in Settings. The guide links to Android app-info and battery-optimization settings and explains the optional Autostart, No restrictions and Recents-lock steps without pretending those vendor controls can be changed silently. Settings now scrolls independently so lower controls remain reachable on short displays and with enlarged system text.

The app remains orientation-flexible. Phase 5 still requires physical verification of clipboard behavior in Chrome, ChatGPT, WhatsApp, Notes and password fields, plus Android 13/14/15 permission and large-font/landscape acceptance checks. WorkManager scheduling is best-effort; Xiaomi battery settings cannot guarantee exact background execution.


## Phase 6 — filesystem safety hardening (0.6.0)

File operations now refuse symbolic links rather than following them. This protects copy, move, ZIP creation and recursive delete from traversing outside the selected tree through a linked file or directory. Directory traversal uses no-follow filesystem checks; archive statistics are collected once per selected source. Regression tests cover directory-link deletion, nested file-link copying and ZIP creation from a linked root.

The 1.0.0 device-verification gate remains open: physical clipboard/IME, Android 13–15 permissions, Xiaomi background behavior, backup/restore on external storage, accessibility and landscape acceptance still require the actual phone.


The 1.0.0 device acceptance checklist is in docs/RELEASE-1.0-GATE.md. Until those real-device checks pass, the latest source build remains unverified on physical hardware.


## Android compatibility

VaultOS 0.7.0 requires Android 9 (API 28) or newer. Android 8 and 8.1 devices (API 26–27) are no longer supported. This is a deliberate security and signing-rotation policy decision; users on those older versions should export their VaultOS data before moving to a supported device.

## Phase 7 — clipboard history foundation (0.7.0)

Clipboard capture now has its own persistent event history, separate from the deduplicated file library. Each copy event gets its own timestamp and metadata record even when the same text payload has already been archived; repeated payloads can share one verified stored file. The gallery supports search, full-text copy, pinning, individual removal and clearing unpinned history. Open it from the clipboard-history icon in the main app or the History button in the VaultOS keyboard.

Clipboard history stores the foreground editor package as context, the clipboard label and MIME types, a SHA-256 digest, character/byte counts and capture time. Text payloads are checked against their recorded byte count and digest when written, reused or read back. Multiple text/URI items are recorded separately. Sensitive clips marked by the source app on Android 13+ and password editor fields are excluded from automatic capture.

**Android limitation:** automatic capture requires VaultOS to be the selected input method. A normal Android app cannot silently monitor the clipboard while another keyboard is active. Manual capture remains available from inside VaultOS. The foreground editor package is context, not guaranteed origin attribution.

CI for 0.7.0 passed; physical-device acceptance remains pending.
## Phase 8 — keyboard integration (0.8.0)

VaultOS introduced a persistent EN / PL / TR selector and an inline Clips mode. Search clipboard history from the keyboard, pin or unpin entries, and insert the complete verified text into the focused field without opening the app.

## Phase 10 — keyboard redesign (0.10.0)

The previous custom keyboard was not accepted as a daily-use keyboard. This phase addresses the usability failure directly: every key now has a high-contrast keycap, a pressed-state color, a ripple, a tactile scale response and a large popup preview while held. The toolbar is a single compact row: Type, Clips, Cmd, language and More.

The keyboard now includes:

- Standard QWERTY with larger 50 dp default keys, adjustable 46/50/54/58 dp sizing and optional number row.
- Clear pressed-state keycaps, ripple, key popup previews, optional system key sounds and haptics.
- English, Polish and Turkish switching; long-press diacritics and punctuation alternatives.
- Live Android spell-checker suggestions with request sequencing; autocorrect remains opt-in and only applies when the spell-checker marks a word as a likely typo.
- Cursor movement by arrow keys or sliding horizontally across the spacebar.
- Emoji panel, clipboard search/history/pinning/full-text paste, and a Cmd panel that searches VaultOS records and inserts complete saved text/code.
- Dark/light themes and off/left/right one-handed layout.
- A More menu for capture, paste, history, keyboard size, haptics, sound, key previews, number row, autocorrect, theme, one-hand mode and switching back to Gboard.

The implementation is local-first. It does not claim to include glide typing or a proprietary next-word prediction engine. Spell-check quality still depends on installed Android dictionaries. Hosted CI passed in run `42041620803` (unit-test task, lint, debug/release assembly, signing verification and APK artifact uploads). Build success is not proof of touch ergonomics; the Redmi Note 12 Pro 5G acceptance matrix remains mandatory.


## Phase 11 — useful multilingual suggestions (0.11.0)

The keyboard's previous recommendation strip depended almost entirely on the Android system spell-checker. That produced weak or empty results on devices without a matching dictionary and did not offer next-word suggestions. Version 0.11.0 adds a small deterministic on-device EN / PL / TR lexicon for prefix completions and a curated set of common next-word pairs. A small edit-distance matcher also catches nearby common mistypes from the bundled vocabulary; the Android spell-checker supplements it when it flags a likely typo. Ordinary dictionary alternatives are no longer presented as corrections. The suggestion strip now keeps the word being typed visible and separates it from alternatives with a quieter visual treatment.

This is deliberately a lightweight deterministic baseline, not a neural prediction model or Gboard-level language modelling. No typed text is sent off-device. Suggestions remain suppressed in password, email, URL, numeric, autocomplete, filter and no-suggestions fields. Hosted CI passed in run `42047330196` (unit tests, lint, debug/release APK assembly, signing verification and all artifact uploads); physical Redmi acceptance remains open.


## Phase 12 — adaptive local typing assistant (0.12.0)

The keyboard now has an optional on-device personal vocabulary and word-pair model. Learning is **off by default** and must be enabled from More. When enabled, it records only individual words and adjacent word-pair counts after a word is committed in a safe text field; it does not store sentences, full field contents or clipboard data, and it sends nothing off-device. A long-press on the current word adds it to the personal dictionary even when automatic learning is off. Learned words contribute to prefix completions and local typo matching; learned word pairs influence next-word suggestions. A confirmation action clears this separate local typing store.

Everyday typing also gains sentence capitalization and optional-by-default-on double-space period punctuation. Both can be switched off in More. The feature remains deterministic and local; it is not a neural language model or a full Gboard replacement. Device acceptance is required for language quality and punctuation edge cases.


Hosted CI for 0.12.0 passed in run `42054296162`: unit tests, lint, debug/release APK builds, signing verification and all APK/metadata artifact uploads succeeded. Physical Redmi acceptance remains outstanding.


### 0.12.1 privacy hardening

Personal word learning now passes through one safe-field guard at every commit boundary, including punctuation and Enter. Password, email, URL, numeric, autocomplete and other excluded editor fields cannot contribute words or word-pair counts. Automatic learning remains off by default. This patch does not change the Room schema or existing vault data.


**0.12.1 verification:** hosted CI run [42079070087](https://github.com/ysfclc/VaultOS/actions/runs/42079070087) passed unit tests, lint, signed debug/release APK assembly, signing verification and all artifact uploads. Physical Redmi acceptance remains outstanding.
