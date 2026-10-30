# VaultOS architecture

## Data flow

`Android share / document picker / clipboard IME / storage scan` → `VaultRepository` → `private files/vault` + `Room catalogue` → `chunked text search` → `Compose library / DocumentsProvider / share intents`.

## Storage model

- Original shared-storage locations are never moved during indexing or automatic archiving.
- The first complete scan indexes existing accessible files only.
- Later discoveries are copied into app-private storage with a streaming 128 KiB buffer and SHA-256 digest.
- Temporary copies use `.part` suffixes and are renamed after the copy completes.
- SHA-256 is used for content-level duplicate detection.
- A `VaultItem` stores metadata and a small text preview. The complete text body is a file; long bodies are indexed as bounded chunks in Room.
- Backup contains archived payloads plus a JSON manifest of archived records. Restore copies payloads into the private archive and rehydrates Room records.

## Android integration

- `MANAGE_EXTERNAL_STORAGE` is requested from the user because broad shared-storage browsing and indexing are core file-manager functions.
- Android private app directories are excluded from recursive scans.
- `MediaStore` observer and download-completed broadcast enqueue unique scan work. WorkManager's periodic scan is a catch-up mechanism; Android background work is not exact-time guaranteed.
- `VaultKeyboardService` is a real InputMethodService. It receives clipboard-change callbacks only when selected as the default IME. The keyboard has basic QWERTY entry plus Capture, Paste saved and Vault actions. It has no network permission.
- `VaultDocumentsProvider` exposes the app-private archive through `ACTION_OPEN_DOCUMENT` / system document UI.

## Privacy boundary

No internet permission is declared. Automatic clipboard capture is enabled by default once the user explicitly enables and selects the VaultOS keyboard; it can be switched off in Settings at any time. Selecting a third-party keyboard is itself a deliberate Android system-level choice. Password-editor fields and clips marked sensitive are skipped. The source app may omit the sensitive flag, so users should still avoid copying credentials while automatic capture is enabled. The keyboard must be trusted because Android input methods can observe keystrokes by design.

## Version 0.1 scope

This release implements a reliable local capture/archive/index foundation and the core file-manager operations. It does not intercept private app-to-app copy operations universally, inspect other apps' private sandboxes, restore application data, merge split APKs into one installable package, or guarantee instant background scans on all MIUI power states.
