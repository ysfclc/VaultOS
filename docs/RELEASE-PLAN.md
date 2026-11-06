# VaultOS release plan

## 0.1.0 — usable local foundation

Native Android app, share/import, private archive, baseline device index, incremental scans, optional capture keyboard, long-text preservation, file browsing/operations, DocumentsProvider, app catalogue and manual backup/restore.

## 0.2.0 — file-manager completeness

Recycle bin, multi-select, batch move/copy, archive compression/extraction, previews/thumbnails, progress and cancellation, improved storage usage and error recovery.

## 0.3.0 — application and backup management

Signed sideloadable debug/release APKs with a persistent signing identity, installed-versus-archived app version comparison, and base/split APK export guidance.

## 0.4.0 — smarter library

User-defined collections and tags, related-item and SHA-256 duplicate groups, saved searches, user-triggered local metadata extraction, and backup manifest v2. Optional local semantic search remains gated on selecting and validating an on-device embedding runtime/model.

## 0.5.0 — device integration and polish

Xiaomi MIUI/HyperOS background-work onboarding, app-info and battery-optimization shortcuts, and scrollable Settings for constrained screens and larger system text. Physical clipboard, Android-version, accessibility and landscape acceptance remains required.

## 0.6.0 — filesystem safety hardening

Reject symbolic links in recursive copy, move, ZIP creation and delete operations; use no-follow directory checks; avoid repeated archive-stat traversals; add regression tests proving linked targets outside a selected tree remain untouched.

## 0.11.0 — multilingual suggestions and visual refinement

Deterministic EN / PL / TR prefix completion, conservative offline typo candidates, short context-aware next-word suggestions, and Android spell-checker fallback. Candidate strip keeps the current word visible, avoids showing platform alternatives unless marked as likely typos, and uses a quieter visual treatment. Local-only; no network or personal typing-history collection. Physical-device relevance and touch acceptance remain open.

## 0.12.0 — adaptive local typing assistant

Opt-in local personal vocabulary and adjacent-word learning; long-press to add a personal dictionary entry; learned words used in prefix completions and typo candidates; learned word pairs influence next-word ranking; confirmation-gated clear-memory action; sentence capitalization; double-space period punctuation. Learning is off by default and stores only words and pair counts, never sentences or full editor contents. Physical-device quality, persistence and sensitive-field exclusions remain acceptance gates.

## 0.13.0 — daily usability reset

Five-item primary navigation with a More hub, contextual first-run storage onboarding, and a more understandable keyboard control bar. The keyboard Tools panel is grouped and constrained to the available display; key-down response and touch targets are stronger; next-word candidates use transient pre-cursor context in safe fields; offline EN / PL / TR coverage and regression tests are expanded. Keep the existing application ID, Room data and signing identity for in-place updates. Physical-device acceptance remains mandatory.

## 1.0.0 — device-verified product

Signed release process, GitHub-hosted CI verification, migration/restore tests, accessibility and performance review, measured Xiaomi background behavior, and documented Android-version compatibility. See docs/RELEASE-1.0-GATE.md; 1.0.0 is not complete until physical-device checks pass.

## Compatibility baseline

- Minimum SDK: 28 (Android 9).
- Compile/target SDK: 35.
- Java/Kotlin bytecode target: 17.
- Build: Android Gradle Plugin 8.7.3, Gradle 8.9, Kotlin 2.0.21.
- Device focus: Xiaomi Redmi Note 12 Pro 5G, expected Android 13/14 family. Confirm exact OS version on device during acceptance testing.
