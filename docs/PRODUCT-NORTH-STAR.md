# VaultOS product north star

## Product definition

VaultOS is a **local-first personal memory and content-rescue layer for Android**. It helps a person capture fragmented information from across the phone, preserve the original content faithfully, find it again by name, text, type or source, and safely recover, export or reuse it later.

VaultOS is not primarily a keyboard, clipboard manager, conventional file manager, cloud-AI assistant, or replacement for the user's existing apps. It is the durable local layer that prevents useful personal information from getting lost between apps, files, messages, links and temporary surfaces.

## The core promise

If the user deliberately gives VaultOS information, or explicitly enables a supported capture source, VaultOS should be able to answer:

- What did I capture or import?
- Where did it come from and when?
- What exact content or file did I preserve?
- Can I inspect the full original, not only a preview?
- Can I find it again by words, filename, type or source?
- Can I export or restore it without silent loss or modification?

The product must prefer exact preservation and transparent provenance over speculative summaries or opaque automation.

## Core capabilities

1. **Capture**
   - Android share intents for text, links and files.
   - Explicit clipboard capture and clipboard history when VaultOS is the selected input method, respecting Android's input-method and sensitive-field boundaries.
   - Long-text capture without truncation.
   - User-selected imports through Android's Storage Access Framework.
   - Optional keyboard actions as one convenient capture/retrieval entry point.

2. **Preserve**
   - Keep exact UTF-8 text and file bytes.
   - Record source, capture time, filename/type and relevant metadata.
   - Use content hashes, byte counts and integrity checks for archived payloads.
   - Deduplicate safely without losing separate capture events or provenance.
   - Never silently overwrite, truncate or mutate a source file during import.

3. **Retrieve**
   - One searchable library across captured content and indexed device files.
   - Search full content, not only preview snippets.
   - Filter and organize by source, type, time, tags and collections.
   - Open and inspect the complete preserved item with clear provenance.

4. **Recover**
   - Reliable backup, restore and export with manifest validation, path containment, checksums and failure-safe behavior.
   - Recycle Bin and reversible operations where applicable.
   - Make restore limitations and failures visible; never claim a successful restore without verification.

5. **Connect**
   - Bridge the private VaultOS archive and the user's existing phone storage.
   - Support Android SAF providers and removable USB storage where Android exposes them.
   - Keep imports read-only against source media: never format, repair, repartition, rename, move or delete files on a source drive.
   - Treat USB detection and import as observable, diagnosable states rather than assuming a drive is available.

6. **Intelligence**
   - Add deterministic local commands and retrieval only after capture, preservation, retrieval and recovery are dependable.
   - Any future assistant-like behavior must show the source records it used and the exact action taken.
   - No cloud AI, account, analytics or network dependency is required for the core product.

## Boundaries with adjacent projects

- **VaultOS** owns local personal content capture, preservation, retrieval and recovery.
- **Memo** owns broader meaning-making, frontier-model assistance and cross-app automation.
- **Locco** owns app observation and event inspection.
- VaultOS may expose reliable local data to other tools through explicit, permissioned interfaces, but it must not absorb their missions.

## Delivery order

**Capture → Preserve → Retrieve → Recover → Connect → Intelligence.**

Do not skip ahead to assistant features while the earlier layers are incomplete. A feature is not complete because a screen or button exists: its end-to-end behavior, data integrity, failure states, tests and target-device usability must be demonstrated.

## Product-completeness gates

- Every capture path records the exact payload and provenance.
- Long multilingual text round-trips byte-for-byte and remains searchable beyond preview limits.
- Archived files are integrity-checked and safe to open only from the app-private vault.
- Duplicate content does not erase distinct capture events.
- Backup manifests and all restore destinations are validated before writes; failed restore does not leave a partially corrupted library.
- Search results can be opened and inspected in full.
- SAF and USB imports are bounded, resumable where practical, and never mutate source media.
- Room migrations, archive compatibility and backup versions have regression coverage.
- The release updates an existing installation without uninstalling or clearing data.
- The final APK is signed with a verified update lineage and passes real-device acceptance on the Redmi Note 12 Pro 5G.

## Explicit non-goals

- Replacing Gboard or requiring the user to adopt the VaultOS keyboard.
- Moving the user's existing shared-storage files into an opaque private-only structure.
- Cloud synchronization, account systems, telemetry or cloud-AI requirements for core operation.
- Destructive USB utilities or filesystem repair.
- Declaring product completeness from a successful compile, a screenshot or a debug APK.

## Current milestone focus

Complete the **data-rescue foundation**: clipboard capture/readback integrity, safe archive access, backup/restore path and manifest validation, and read-only USB/SAF import. Then close integration-test gaps and verify the full experience on the target Redmi device.
