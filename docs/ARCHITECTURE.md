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
- `VaultKeyboardService` is a real InputMethodService. It receives clipboard-change callbacks only when selected as the default IME. Version 0.10.0 provides QWERTY with visible pressed/preview states, EN/PL/TR switching, live spell-check, optional autocorrect, spacebar cursor movement, emoji, one-handed/theme/size controls, Clips history and a local Vault Cmd search/insert panel. It has no network permission.
- `VaultDocumentsProvider` exposes the app-private archive through `ACTION_OPEN_DOCUMENT` / system document UI.

## Privacy boundary

No internet permission is declared. Automatic clipboard capture is enabled by default once the user explicitly enables and selects the VaultOS keyboard; it can be switched off in Settings at any time. Selecting a third-party keyboard is itself a deliberate Android system-level choice. Password-editor fields and clips marked sensitive are skipped. The source app may omit the sensitive flag, so users should still avoid copying credentials while automatic capture is enabled. The keyboard must be trusted because Android input methods can observe keystrokes by design.

## Version 0.1 scope

This release implements a reliable local capture/archive/index foundation and the core file-manager operations. It does not intercept private app-to-app copy operations universally, inspect other apps' private sandboxes, restore application data, merge split APKs into one installable package, or guarantee instant background scans on all MIUI power states.


## Recycle Bin and file operations (0.2.0)

Room schema version 2 adds isTrashed, trashedAt, and trashOriginalPath. A managed archive payload is moved into the private vault/.trash directory; all catalogue rows referencing that same payload are updated together. Normal queries exclude trashed rows, and vault reconciliation deliberately skips the .trash subtree. Restore moves the payload back to its original relative path or a collision-safe sibling. Permanent deletion removes the payload and all related catalogue/search rows.

FileOperations performs copy, move, ZIP create/extract, and recursive delete on Dispatchers.IO with chunk-level progress and cancellation checks. Writes go to hidden temporary paths and are committed by rename only after completion. ZIP extraction canonicalizes each destination path before writing and enforces entry-count and expanded-size limits. Cross-filesystem moves finish the destination copy before deleting the source in a non-cancellable section.

## Phase 2 file operations and recycle bin

- Room schema v2 adds `isTrashed`, `trashedAt` and `trashOriginalPath` through the explicit v1→v2 migration.
- Recycle Bin is limited to files managed inside the private VaultOS archive. A trashed payload is moved into `vault/.trash`; catalogue rows sharing that payload are updated together. Device-storage originals are not removed by the Recycle Bin action.
- Restore returns the payload to its recorded path, choosing a collision-safe name if that path is occupied. Permanent deletion removes the payload and all catalogue/search rows that reference it.
- Library/search/count/archived-size queries exclude trashed rows. Backup manifests retain trash state and the original relative restore path.
- `FileOperations` performs buffered IO on `Dispatchers.IO`, reports byte/file progress, checks coroutine cancellation, and stages copy/ZIP/extraction output in temporary paths before commit. Cross-filesystem move deletes the source only after the full copy has committed.
- ZIP extraction rejects canonical paths outside the temporary extraction root and enforces 20,000 entries / 2 GiB declared expanded-size limits.
- Browser previews decode image/PDF/video/audio artwork off the main thread; image decoding is sampled to bound memory.
