# VaultOS product north star

## The reason this exists

VaultOS started with a concrete failure on the Redmi Note 12 Pro 5G: copied long text could be truncated by the normal clipboard path. The user needed a way to keep the **entire original content** and paste it back later without relying on a fragile clipboard preview.

That problem grew into a broader product: one dependable, private place on Android to capture, preserve, find and reuse information and files gathered across everyday apps and device storage.

VaultOS is not a keyboard experiment that later acquired a file browser. The keyboard is one optional capture and retrieval surface inside a larger personal vault.

## Product definition

**VaultOS is a local-first personal capture and retrieval layer for Android.**

It brings together:
- capture from Android share sheets, selected text, document pickers and manual in-app actions;
- optional clipboard capture while VaultOS is the selected input method, with explicit privacy controls and Android's real platform limits respected;
- non-destructive indexing of accessible device storage and catch-up for newly detected files;
- a private archive that preserves complete payload bytes, with integrity checks and deduplication;
- one searchable library for text, documents, images, media, code, APKs and other supported files;
- organization and retrieval through folders, categories, tags, collections, saved searches, pins and duplicate relationships;
- useful reuse surfaces: full-text paste, Android's DocumentsProvider, file operations and user-directed backup/restore.

The product should feel like a reliable extension of the user's device memory, not a collection of disconnected utilities.

## The core loop

1. **Capture** — accept content through whichever Android entry point is actually available: share, import, selected-IME clipboard, manual capture or storage indexing.
2. **Preserve** — keep full content, not just a preview. Verify byte count and digest where the format supports it. Never replace corrupted or unavailable content with a truncated preview.
3. **Connect** — associate source references, capture events, metadata and user organization without confusing a content object with each time it was copied or found.
4. **Retrieve** — let the user find content globally, including text beyond the visible preview, then open, share, paste or manage it.
5. **Recover** — make the private archive and its catalogue portable through tested backups and restores. Never imply that an unverified backup is safe.

## Product priorities

These priorities govern trade-offs and sequencing:

1. **No silent data loss.** Exact capture, complete readback, integrity checks, safe file operations, migration safety and recoverable backups come first.
2. **Low-friction capture.** Capture should work through the Android mechanisms available to us without pretending to have forbidden system-wide access.
3. **Fast, dependable retrieval.** Search and organization must work across the library, not only the current screen or the preview text.
4. **Safe control and interoperability.** Users can inspect, move, share, restore and expose their own archived files through Android-supported mechanisms.
5. **Polished optional input tools.** The custom keyboard should be comfortable, responsive and useful for clips and full-text insertion. Prediction and visual effects are supporting capabilities, not the product's definition.

## Non-negotiable principles

- **Local-first and user-owned.** Content remains on-device. No account, analytics, cloud AI, remote text processing or silent sync is part of the product contract.
- **Preserve originals.** Initial indexing is non-destructive. Capture and archive operations copy rather than unexpectedly moving or deleting a source.
- **Full fidelity.** A preview is for display only. The complete payload remains the source of truth; byte-count/hash failure means unavailable content, not a best-effort substitute.
- **Separate events from content.** Repeated clipboard captures may share one stored payload while retaining separate timestamps and capture context.
- **Honest Android boundaries.** Automatic clipboard callbacks require VaultOS to be the active IME. We cannot inspect other apps' private data or promise exact OEM background scheduling.
- **One coherent library.** Do not create parallel histories, vaults or domain-specific mini-products when an existing general primitive solves the problem.
- **Generalize from examples.** Examples and test scenarios must not become hard-coded product categories or special-case workflows.
- **Recovery is part of the feature.** Backup, restore, migrations and in-place app updates are product behavior, not optional release housekeeping.

## What VaultOS is not

- Not just a custom keyboard.
- Not a cloud clipboard or cross-device sync service.
- Not a universal background clipboard interceptor; Android does not expose that capability to an ordinary app.
- Not an app-data extractor for other applications' private storage.
- Not a generic dashboard whose purpose is to display metrics instead of helping people capture and retrieve content.

## How we measure whether it is working

The core journey is only accepted when evidence demonstrates that:
- long UTF-8 text containing Polish, Turkish, emoji and mixed scripts round-trips byte-for-byte;
- text beyond the preview window can still be found through library search and pasted in full;
- modified or missing payloads fail closed rather than returning misleading partial content;
- duplicate payload storage does not erase distinct capture events or source references;
- indexing leaves existing source files where they were;
- backups restore the payloads and organization state they claim to contain, with corruption and failure paths tested;
- updates preserve the installed user's vault and settings;
- the experience is usable on the Redmi Note 12 Pro 5G, verified on the device rather than inferred from CI.

## Roadmap rule

When deciding what to build next, prioritize the weakest link in **capture → preserve → retrieve → recover**. Improve the keyboard when it blocks those jobs; otherwise, do not let keyboard polish displace the data-integrity, archive, search and recovery foundations.
