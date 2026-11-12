# Changelog

This changelog records product milestones. The detailed implementation ledger remains in docs/TASKS.md and docs/PROJECT-STATUS.md.

## 0.13.0 — 2026-11-06

- Simplified app navigation to Home, Library, Files, Organize and More; added contextual first-run storage onboarding.
- Made search global across navigation destinations with real library results and distinct empty/no-match states.
- Reworked keyboard toolbar and grouped Tools panel; improved touch feedback and Clips targets.
- Added transient pre-cursor context for next-word suggestions and expanded offline EN/PL/TR vocabulary.
- Hosted CI passed; signed debug/release APKs, checksums and build metadata were published as private draft-release assets.
- Physical Redmi acceptance remains open.

## 0.12.1 — keyboard privacy patch

- Routed all personal typing-memory writes through a safe-editor-field guard, including punctuation and Enter commits.
- Automatic learning remains off by default; hosted CI and signed APK verification passed.

## 0.12.0 — adaptive local typing assistant

- Added opt-in on-device personal vocabulary and adjacent-word learning; stores words/pairs, not sentences or full editor contents.
- Added sentence capitalization and configurable double-space period behavior.

## 0.11.0 — multilingual suggestions

- Added deterministic EN/PL/TR prefix completion, conservative typo candidates and context suggestions.
- Improved candidate-strip behavior without adding remote language services.

## 0.10.0 — keyboard usability redesign

- Rebuilt the keyboard around visible key feedback, QWERTY rows, language switching, spell-check, cursor movement, themes, sizing, one-handed layout, emoji and Vault/clipboard utilities.

## 0.8.0 — keyboard integration

- Added persistent EN/PL/TR selection and inline clipboard Clips mode with search, pinning and full-text insertion.

## 0.7.0 — clipboard history

- Added timestamped clipboard events separate from deduplicated payload storage, integrity metadata, searchable history, pinning and full-text paste.

## 0.6.0 — filesystem safety

- Refused symlinks in recursive file operations and added regression tests for linked paths, cancellation and data preservation.

## 0.5.0 — device integration

- Added Xiaomi MIUI/HyperOS background-work guidance and improved Settings reachability on constrained displays.

## 0.4.0 — smarter library

- Added user tags, collections, related items, duplicate groups, saved searches, local metadata extraction and backup manifest v2 with v1 restore compatibility.

## 0.3.0 — application and backup management

- Added installed-app inventory and base/split APK archiving; preserved a stable sideload signing identity and added package-version guidance.

## 0.2.0 — file manager hardening

- Added Recycle Bin, batch operations, ZIP create/extract, previews, progress/cancellation and safety limits.

## 0.1.0 — local-first foundation

- Added Android share/import, private archive, non-destructive initial indexing, incremental scans, optional capture IME, long-text preservation, file browsing, DocumentsProvider and manual SAF backup/restore.
