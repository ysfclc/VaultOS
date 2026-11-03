# VaultOS release plan

## 0.1.0 — usable local foundation

Native Android app, share/import, private archive, baseline device index, incremental scans, optional capture keyboard, long-text preservation, file browsing/operations, DocumentsProvider, app catalogue and manual backup/restore.

## 0.2.0 — file-manager completeness

Recycle bin, multi-select, batch move/copy, archive compression/extraction, previews/thumbnails, progress and cancellation, improved storage usage and error recovery.

## 0.3.0 — application and backup management

Signed sideloadable debug/release APKs with a persistent signing identity, installed-versus-archived app version comparison, and base/split APK export guidance.

## 0.4.0 — smarter library

User-defined collections and tags, related-item and SHA-256 duplicate groups, saved searches, user-triggered local metadata extraction, and backup manifest v2. Optional local semantic search remains gated on selecting and validating an on-device embedding runtime/model.

## 1.0.0 — device-verified product

Signed release process, migration/restore tests, accessibility and performance review, tested Xiaomi background behavior, and documented Android-version compatibility.

## Compatibility baseline

- Minimum SDK: 26 (Android 8.0).
- Compile/target SDK: 35.
- Java/Kotlin bytecode target: 17.
- Build: Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin 2.0.21.
- Device focus: Xiaomi Redmi Note 12 Pro 5G, expected Android 13/14 family. Confirm exact OS version on device during acceptance testing.
