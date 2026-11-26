# VaultOS architecture decisions

_This is a reconstructed ADR baseline recorded on 2026-11-12. The decisions below are grounded in current code and existing architecture/status documents; original decision dates are not inferred._

## ADR-001 — Local-first, no remote content service

**Status:** Accepted  
**Context:** VaultOS handles personal files, clipboard content and typed text.  
**Options:** Remote processing/sync; local-only storage and processing.  
**Decision:** Keep content processing on-device. No account, analytics, cloud AI or INTERNET permission is part of the product contract.  
**Consequences:** Privacy and offline use are primary; backup is manual and user-directed; no cloud recovery is available.  
**Evidence:** AndroidManifest.xml; README.md “Offline by design”; ARCHITECTURE.md “Privacy boundary”.

## ADR-002 — Preserve original shared-storage files

**Status:** Accepted  
**Context:** A file library must not surprise users by relocating existing device content.  
**Options:** Move all content into a managed VaultOS tree; index originals in place and privately archive new/imported content.  
**Decision:** The first scan indexes accessible existing files without copying them. New imports and later discovered content can be copied into app-private storage; original paths remain untouched.  
**Consequences:** The library spans indexed references and archived payloads. UI and backup logic must distinguish those states.  
**Evidence:** VaultRepository.kt:135,173,243; README.md “Existing-storage index”; ARCHITECTURE.md “Storage model”.

## ADR-003 — Clipboard capture is implemented as an optional Android IME

**Status:** Accepted  
**Context:** Android does not grant ordinary background applications universal clipboard-change visibility.  
**Options:** Claim universal monitoring; use an optional selected input method and provide manual capture when not selected.  
**Decision:** Use InputMethodService. Automatic clipboard callbacks are only available while VaultOS is the selected IME; keep Gboard installed and provide switching/manual capture. Skip password editor fields and clips explicitly marked sensitive.  
**Consequences:** Users must deliberately enable/select VaultOS for automatic capture. Source applications may fail to mark sensitive content; the UI/docs must communicate this boundary.  
**Evidence:** VaultKeyboardService.kt:56,108–141; README.md “Android boundaries”; DEVICE-TEST-PLAN.md clipboard matrix.

## ADR-004 — Separate long payload storage from the Room catalogue

**Status:** Accepted  
**Context:** Large text and files must not be truncated or inflate a database row.  
**Options:** Store full payloads in Room; store archive payloads as files and keep metadata/small previews plus bounded full-text search chunks in Room.  
**Decision:** Keep full UTF-8 text and archived bytes in app-private files. Persist metadata, hashes and bounded search chunks in Room.  
**Consequences:** Reads must verify payload integrity and index updates must track file commits. Backup/restore must preserve both payload and catalogue state.  
**Evidence:** VaultRepository.kt:105,393,419; VaultDatabase.kt:24–46,176–191; README.md “Long text”.

## ADR-005 — Android 9 (API 28) minimum support

**Status:** Accepted  
**Context:** A compatibility and signing/update policy change intentionally dropped Android 8/8.1.  
**Options:** Retain API 26 support or require API 28+.  
**Decision:** minSdk is 28; target/compile SDK is 35.  
**Consequences:** Android 8/8.1 devices cannot install current updates; migration/export guidance is required for those users.  
**Evidence:** app/build.gradle.kts:8–18; docs/updates/2026-11-05-governance-handoff.md; README.md “Android compatibility”.

## ADR-006 — File operations stage output and reject symlinks

**Status:** Accepted  
**Context:** Cancellation, partial writes, ZIP path traversal and linked directory traversal can corrupt or expose data.  
**Options:** Direct writes and permissive recursive traversal; temporary outputs, bounded archive extraction and no-follow checks.  
**Decision:** Stage copy/ZIP/extraction output, commit only after successful completion, check cancellation, reject symbolic links, reject ZIP traversal, and enforce entry/expanded-size limits.  
**Consequences:** Operations may fail closed on links or unusual filesystem entries; cleanup and source preservation are tested.  
**Evidence:** FileOperations.kt:31–318; FileOperationsTest.kt; README.md Phase 2 and Phase 6.

## ADR-007 — Versioned external backup manifest with backward restore support

**Status:** Accepted  
**Context:** Catalogue evolution must not make older exported backups unreadable.  
**Options:** One evolving undocumented manifest; explicit v1/v2 formats with compatibility logic.  
**Decision:** Current backup format is v2 and restore accepts v1 and v2.  
**Consequences:** Each future schema change must define a compatible restore/migration path and have round-trip tests before release. Backups remain unencrypted by VaultOS.  
**Evidence:** VaultBackupManager.kt:22,136; PROJECT-STATUS.md Phase 4; README.md “Data and backup notes”.

## ADR-008 — Preserve signing continuity until a verified key migration exists

**Status:** Accepted as a temporary constraint; security remediation remains open  
**Context:** Existing in-place installs depend on the current signing identity. The historical keystore is tracked in the private repository and signing configuration contains static credentials. A casual key replacement or deletion would break update continuity; continued retention is also a security risk.  
**Options:** Delete/replace immediately; keep it indefinitely; provision replacement material, establish Android signing lineage, test upgrade installs, then remove historical material under an approved migration.  
**Decision:** Do not regenerate, delete, or rotate the current identity until replacement key material, CI secrets, signing lineage and in-place update tests are ready. GOV-001 remains blocked, not resolved.  
**Consequences:** Existing update path is preserved short-term, but the tracked signing secret remains a high-severity risk. No signing changes are allowed as part of unrelated increments.  
**Evidence:** app/build.gradle.kts:14–25; .github/workflows/android-build.yml signing check; docs/updates/2026-11-05-governance-handoff.md GOV-001.

## ADR change rule

Append a new ADR for any later change to storage semantics, Room schema strategy, backup format, signing identity, supported Android baseline, permission model, or remote-service/privacy boundary. Do not silently edit an accepted decision; append a superseding ADR and record migration consequences.


## ADR-009 — Keep keyboard prediction local and make the typing path latency-first

**Status:** Accepted for the V2 keyboard increment  
**Context:** The 0.13.0 IME uses a small static lexicon and sparse one-word next-word maps. Candidate rendering reads personal-memory JSON, computes corrections and rebuilds views on the main thread. User feedback reports slow typing and poor prediction.  
**Options:** Add a remote LLM/API; bundle a large neural runtime/model; improve a small local contextual model and remove synchronous work from the keystroke path.  
**Decision:** Keep all prediction on-device and dependency-light. First remove avoidable typing-path work; then add bounded, opt-in contextual n-gram learning while retaining existing v1 word/bigram data. Use short transient context only in safe editor fields.  
**Consequences:** The first release will be a responsive adaptive statistical predictor, not a claim of Gboard-level neural prediction. Learning remains opt-in, language-separated, bounded and clearable. No network permission or heavyweight inference dependency is introduced. Physical latency and quality must be measured on the target Redmi.  
**Evidence:** docs/KEYBOARD-V2.md; KeyboardSuggestionEngine.kt; LocalTypingMemory.kt; VaultKeyboardService.kt.


## ADR-010 — Additive private trigram learning for keyboard context

**Status:** Accepted — 2026-11-12

**Decision:** Extend the existing opt-in local typing memory with a bounded per-language three-word context counter. Keep the v1 word and adjacent-pair preference keys unchanged. When the current two-word context has learned candidates, prefer those; otherwise fall back to the existing adjacent-pair counts and then the small built-in EN/PL/TR phrase priors.

**Privacy and limits:** Learn only after a safe editor commits a word and only while the user has enabled private typing memory. Persist counts keyed by three tokens, never sentences or full editor text. Cap each language at 1,200 contexts. Clear/reset removes the new context keys while preserving the user's learning toggle. No network or new inference dependency.

**Consequences:** Existing v1 dictionaries and bigram counts remain readable. Android-backed persistence/opt-out/clear tests and device quality measurements are still required before acceptance.

## ADR-011 — Android 13 minimum for signing-key rotation

**Status:** Accepted — 2026-11-26; supersedes ADR-005 for future releases.

**Context:** The historical release signer was exposed. Android v3.1 supports rotated signing identities from Android 13 (API 33), while its default compatibility behavior on Android 9–12L can leave the historical signer active. Android warns against relying on key rotation on Android 12 and earlier.

**Decision:** Future VaultOS releases require Android 13 / API 33 or newer (`minSdk=33`). Use APK Signature Scheme v3.1 with the replacement signer and validated proof-of-rotation lineage. Do not lower the minimum without a new security and compatibility review.

**Consequences:** Existing installations on Android 9–12L can continue running their currently installed version but cannot install future VaultOS releases. They must export their vault data before moving to Android 13+. The current target Redmi Note 12 Pro 5G on Android 14 remains supported. This decision does not itself authorize release signing or publication; replacement-key provisioning, lineage validation and in-place update/data-preservation acceptance remain mandatory. Last distributed versionCode 14 remains the baseline; the next release must use a greater versionCode.

**Evidence:** `app/build.gradle.kts`; `docs/KEY-MANAGEMENT.md`; Android official `apksigner`, APK Signature Scheme v3 and v3.1 documentation.

