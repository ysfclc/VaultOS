# VaultOS — project status

**Release line:** 0.13.0 · **Application ID:** `com.vaultos.app` · **Target:** Redmi Note 12 Pro 5G

## Product contract

VaultOS is a local-first Android file manager and personal content library. It combines a unified catalogue with the normal Android filesystem rather than moving existing user files into an opaque replacement structure.

### Locked decisions

- Automatically archive incoming shared files, clipboard text and newly detected files in readable shared storage.
- Index the existing device library on first scan without copying existing media.
- Preserve original folders and paths; expose a unified smart library over the originals and the private archive.
- Include an optional custom keyboard from the first release. Clipboard monitoring is available only while VaultOS is the selected input method.
- Keep Gboard installed and provide a keyboard switch action.
- Keep all content local. No network permission, account, analytics, or cloud AI.
- Provide manually configured SAF backup/restore to USB, external storage, or a user-selected document provider.

## Implemented in 0.1.0 source

- [x] Kotlin + Jetpack Compose Android application shell.
- [x] Share target for single/multiple files and selected text.
- [x] Manual import through Android's document picker.
- [x] Private archive with streaming copy, SHA-256 deduplication and temporary-file commit.
- [x] First-run shared-storage index without copying existing files.
- [x] Incremental MediaStore generation scanning on Android 11+, with timestamp fallback.
- [x] WorkManager catch-up scan and supported DownloadManager completion receiver.
- [x] Optional VaultOS IME with clipboard listener, sensitive-clip check, password-field exclusion, capture action, saved-text paste and keyboard switch action.
- [x] Full-text files and chunked search index; no app-defined text-length cutoff.
- [x] Device and archive browsing; copy, cut/move, rename, delete, create folder, share and open.
- [x] Installed package catalogue and APK component archiving.
- [x] Android DocumentsProvider for browsing the private archive in system pickers.
- [x] Manual SAF backup/restore; manifest preserves archived records and index-only references, with archived-payload hash verification on restore.
- [x] Offline classifier unit tests.
- [x] Memo-style incoming ZIP extraction, validation, SHA-256 replay guard, import-note generation and automatic repository commit in GitHub Actions.

## Verification status

- [x] Kotlin compiler successfully compiled the standalone classifier source.
- [x] Standalone classifier smoke test passed 10 classification cases and filename sanitization.
- [x] Android manifest and resource XML parse checks are part of the delivery verification.
- [ ] Full Gradle unit-test + lint + APK build: requires Android SDK Platform 35 and Gradle 8.9. The current build container has JDK 21 but no Android SDK/Gradle installation and cannot resolve external hosts, so a local APK cannot be truthfully marked verified here. The included GitHub Actions workflow provisions the toolchain and performs this build.
- [ ] Physical Redmi Note 12 Pro 5G test: must be completed after installing the Actions APK. See `DEVICE-TEST-PLAN.md`.

## Release acceptance criteria

A release is considered device-verified only after the complete Actions workflow succeeds and the device test plan passes. In particular, test large shares, full clipboard capture with VaultOS selected as the IME, file-provider interoperability, Xiaomi background scheduling and external backup/restore.

## CI build diagnosis — 2026-10-30, run 5

The Android SDK setup fix worked: the latest run installed the SDK, configured Gradle 8.9, and reached Kotlin compilation. The build then exposed three source-level issues, now corrected:

- The optional keyboard read the active input subtype from `InputMethodService`; that property belongs to `InputMethodManager`. Locale selection now queries the manager and falls back to the device locale.
- The DocumentsProvider used an index-based `MatrixCursor.RowBuilder.add` overload that does not exist. Rows now add values by column name, so arbitrary requested projections remain supported.
- Backup restore called Room and repository suspend functions from a non-suspending function. `VaultBackupManager.restore` is now suspending; the existing caller already invokes it inside `withContext(Dispatchers.IO)`.

These are compile fixes only. A fresh Actions run is still required to confirm the complete test, lint, and APK build. Physical-device behavior remains unverified.


## CI build diagnosis — 2026-11-02, run 10

The latest Android Actions run reached Android resource linking and failed with AAPT: resource style/Theme (aka com.vaultos.app:style/Theme) not found.

Cause: values/styles.xml defined Theme.VaultOS with the explicit parent android:style/Theme.Material.Light.NoActionBar, but the values-v27/styles.xml variant redeclared the same style without a parent. Android therefore inferred the nonexistent @style/Theme parent for the API-27+ variant. The qualified variant also needed to preserve the base theme items because Android selects the qualified style resource as a whole.

Correction in this source:
- values-v27/styles.xml now declares the same explicit Android parent as the base style.
- It repeats every base theme item and retains android:windowLightNavigationBar=false.
- Both workflow copies preflight the uploaded ZIP before import and validate the checked-out XML after import. They reject a missing/wrong parent, missing base items, or missing navigation override.
- ZIP import excludes both workflow YAML copies so a stale archive cannot overwrite either the authoritative workflow or its root mirror.

The resource XML and workflow checks are verified locally. A clean Gradle build was then completed successfully using JDK 17, Gradle 8.9, Android SDK Platform 35, and Build Tools 35.0.0:

- gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug — BUILD SUCCESSFUL.
- Unit tests: 4 passed, 0 failed.
- lintDebug passed.
- assembleDebug produced app/build/outputs/apk/debug/app-debug.apk (17 MB).
- APK SHA-256: fdd641a3d4bd2ed9711c22f418c0342f54468313325c4eccb66f6c6730640042.

GitHub-hosted Actions confirmation and physical Redmi Note 12 Pro 5G verification remain pending.


## Implemented in 0.2.0 — Phase 2 file manager hardening

- [x] Room 1→2 migration adds recycle-bin state and original-path metadata without clearing existing catalogue rows.
- [x] Managed archive files can be moved to a private recycle-bin folder, restored to their prior location (with collision-safe naming), or permanently deleted.
- [x] Recycle-bin entries are excluded from library/search/count/archived-size views and from archive reconciliation.
- [x] Backup manifests preserve recycle-bin state and the original restore path.
- [x] File operations use chunked I/O, temporary outputs, cooperative cancellation and progress callbacks.
- [x] ZIP creation and extraction support selected files/folders, path-traversal rejection, a 20,000-entry limit, and a 2 GiB expanded-size limit.
- [x] File browser supports multi-select and batch copy, move, archive, ZIP creation and delete/recycle-bin operations.
- [x] ZIP files can be extracted to a collision-safe folder from the file browser.
- [x] Image items show downsampled thumbnails in lists and a larger preview in item details; text previews and category-specific file icons remain available.
- [x] Unit tests cover ZIP round-trip, Zip Slip rejection, copy integrity/progress, and cancellation cleanup.

**Verification:** testDebugUnitTest, lintDebug, and assembleDebug pass in the clean Android build environment. Manual Redmi Note 12 Pro 5G testing is not claimed; follow DEVICE-TEST-PLAN.md.

## Phase 2 verification — 2026-11-03

- Clean local build passed with JDK 17, Gradle 8.9 and Android SDK 35: `clean testDebugUnitTest lintDebug assembleDebug`.
- Unit tests: 9 passed, 0 failed (5 file-operation tests and 4 file-classifier tests).
- Android lint: 0 errors, 15 warnings. Warnings include the intentional all-files permission policy warning and existing API/dependency deprecations; they do not fail the lint task.
- Debug APK assembled at `app/build/outputs/apk/debug/app-debug.apk`.
- GitHub Actions and physical-device behavior remain unverified.


## Phase 3 and stability update — 2026-11-03

- Version `0.3.0` / versionCode `3`; Room schema remains at v2 because this phase adds no persisted database fields. Existing app data is preserved by an in-place Android package update.
- The stable signing certificate is the exact certificate used by the 0.2.0 APK (`C5:B0:C6:C4:9F:F6:CA:52:55:58:C2:FE:CF:A1:1D:FF:54:FB:A5:0F:90:83:48:2C:D8:69:AF:47:59:E7:8B:23`). Both debug and release variants use this key so they can update one another.
- App catalogue package-manager queries now run on `Dispatchers.IO`; installed versions are compared against archived APK metadata, and the catalogue displays split APK component counts. Export archives the base APK and every installed split separately.
- Stability changes: routine device scans no longer reconcile/hash the entire private archive; MediaStore observer requests are debounced; foreground-triggered scans are rate-limited; initial scans checkpoint every 10 directories, stop after 90 seconds per worker run, report progress every 50 files, and yield between batches; cancellation is no longer swallowed for individual files.
- A bounded LRU cache prevents repeated preview decoding while scrolling.
- Signing key is a local sideload key. Keep it private and backed up; never regenerate it or updates will fail to install over existing copies. It is not intended as a Google Play production signing identity.
- GitHub Actions and physical-device performance remain unverified.


## Implemented in 0.4.0 — smarter library

- User-created tags and collections can be created, assigned to items, removed, and deleted.
- Saved searches can be saved from the search bar and reopened from Collections. Unified search includes tag names and extracted title/author/subject values.
- Duplicate groups are calculated from existing SHA-256 catalogue values; opening a group shows all matching items without re-hashing the device library.
- Related items are surfaced from shared tags, shared collections and category membership.
- Metadata extraction is explicitly user-triggered and runs locally: image dimensions/EXIF, PDF title/author/subject/page count, DOCX/ODT core properties, audio/video media tags, APK package/version and text title. Unsupported or unavailable metadata remains blank; source files are not modified.
- External backup manifest v2 preserves tags, relationships, collections, saved searches and extracted metadata. Restore remains compatible with v1 backups.
- Room 2→3 migration adds the new tables and indexes without destructive fallback.

### Phase 4 boundaries

On-device semantic indexing is not included in this release. It remains disabled until an embedding runtime and model are explicitly selected and validated for the supported phone class. No model is downloaded and no content is sent to a service. GitHub-hosted Actions and physical Redmi Note 12 Pro 5G verification remain pending.


## Implemented in 0.5.0 — device integration and polish

- Settings includes a Xiaomi MIUI/HyperOS background-work guide with explicit Autostart, No restrictions and optional Recents-lock instructions. Vendor menu names vary by OS build.
- Added safe Android intents for the app details screen and the system battery-optimization app list. VaultOS does not silently modify device settings or request exemption from battery optimization.
- Settings content now scrolls independently, with bottom spacing for gesture navigation and access to controls on shorter screens / at larger system font sizes. Main page content has a subtle size transition, and the auto-capture switch has an explicit screen-reader label.
- Version is 0.5.0 / versionCode 5; application ID and stable signing key remain unchanged.
- Local verification: 13 unit tests passed; lint reports 0 errors / 16 warnings; debug and release APKs assembled; both signatures verify with the preserved SHA-256 certificate digest c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23.
- APK metadata verified: com.vaultos.app, versionCode 5, versionName 0.5.0, minSdk 26, targetSdk 35.
- Physical clipboard compatibility, Xiaomi background execution, Android 13/14/15 permission behavior, and full accessibility/landscape review are still unverified and remain acceptance gates.


## Implemented in 0.6.0 — filesystem safety hardening

- Recursive copy, move, ZIP creation and deletion refuse symbolic links. This prevents file operations from following linked files/directories outside the selected tree.
- Directory checks use NOFOLLOW_LINKS; recursive statistics reject symlinks and non-regular filesystem entries.
- ZIP creation now calculates each selected source's statistics once and reuses the result for progress totals.
- Regression coverage: deleting a directory symlink leaves its external target intact; copying and moving folders containing linked files is rejected; deletion preflights nested links before removing sibling files; creating an archive from a linked root is rejected.
- Local verification: 18 unit tests passed (0 failures); lint 0 errors / 16 warnings; debug and release APKs assembled; both APK signatures match the preserved certificate SHA-256 digest c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23.
- APK metadata: com.vaultos.app, versionCode 6, versionName 0.6.0, minSdk 26, targetSdk 35.
- GitHub Actions and all physical-device acceptance remain outstanding.

## Current product phase — 2026-11-05 (0.7.0 clipboard history)

Implemented in source:
- Room schema v4 clipboard-history event table and non-destructive migration from v3.
- Separate history events from deduplicated payload storage, so repeated identical copies retain distinct recency/history.
- Clipboard metadata: foreground editor package context, label, MIME types, SHA-256, character count, byte count and timestamp.
- Multi-item clipboard capture with per-item failure isolation.
- Searchable clipboard gallery with full-text search through existing chunk index, full-text copy, pinning, individual event deletion and clear-unpinned actions.
- Gallery entry points in the main app and VaultOS keyboard.
- SHA-256 and byte-count checks for text archive writes, duplicate reuse and readback; latest paste uses the latest clipboard event.
- Password-field exclusion and Android 13+ sensitive-clip exclusion.

Not yet accepted:
- Run `42025657002` exposed Kotlin import errors; these were corrected in commit `3def5b4f98b357b778613b5561a9fec148e98a99`. Corrected build run `42026208055` is in progress.
- Physical Redmi Note 12 Pro 5G clipboard, keyboard lifecycle and long-text acceptance remains outstanding.
- Automatic capture still requires VaultOS to be the selected IME; manual capture is available inside the app.
- Signing migration GOV-001 is deferred until the user provisions replacement key material and Actions secrets. Keep the historical key in place until update continuity is proven.
## Phase 8 — keyboard integration (0.8.0)

The keyboard gained persistent EN / PL / TR selection and inline clipboard Clips mode with full-text search, pinning and complete-text insertion. Hosted CI passed in run `42027643930`; device acceptance remains open.

## Phase 10 — keyboard redesign (0.10.0)

The user rejected 0.9.0 as unusable and specifically reported that key presses were not visually apparent. The redesign uses a compact one-row mode bar (Type / Clips / Cmd / language / More), high-contrast keycaps, stateful pressed color, ripple, touch-scale response and a popup key preview. The default key height is 50 dp and is adjustable. Additional functional controls include optional number row, light/dark themes, one-handed alignment, haptics, key sounds, emoji panel and spacebar cursor swipe.

VaultOS-specific utility remains central: Clips searches/pins/pastes complete saved text; Cmd searches the local Vault catalogue and inserts full Text/Code records. English/Polish/Turkish switching and long-press alternatives remain. Spell-check suggestions request live words with stale callback sequencing; autocorrect is off by default and now requires the installed spell-checker to flag a likely typo.

The redesign borrows interaction patterns from mature keyboards (immediate key feedback, compact toolbar, resize/theme controls, multilingual support and clipboard utility) without claiming proprietary glide typing or prediction. Version 0.10.0 / versionCode 10 is in source. Hosted CI passed in run `42041620803` (unit-test task, lint, debug/release assembly, signing verification and debug/release/metadata artifact uploads). Physical Redmi acceptance remains pending; do not call it usable until the device matrix is completed.


## Phase 11 — recommendation quality and keyboard refinement (0.11.0)

Version 0.11.0 adds a deterministic on-device EN / PL / TR word-completion lexicon, conservative edit-distance typo candidates, and a compact set of common next-word suggestions. The Android spell-checker remains the typo-correction fallback, but its alternatives are only displayed when Android marks the input as a likely typo. The candidate strip retains the current word, displays alternatives separately, and commits a selected completion with a single trailing space. Stale spell-check callbacks are invalidated when the active word or editor changes. The keyboard palette and candidate strip have been softened for a more coherent, less boxed-in appearance.

The lexicon is intentionally small and deterministic. This is not neural next-word prediction, a personal learned dictionary, or a claim of Gboard-equivalent recommendation quality. All processing remains on-device. New unit tests cover language-specific prefix, context and typo-correction behavior. Hosted CI passed in run `42047330196` (unit tests, lint, debug/release APK assembly, signing verification and artifact uploads). Physical Redmi acceptance remains pending.


## Phase 12 — adaptive local typing assistant (0.12.0)

Implemented: opt-in local personal vocabulary (off by default), long-press to add a personal dictionary word, learned prefix completions, learned adjacent-word next predictions, personal-word typo candidates, a confirmation-gated clear action, sentence capitalization and double-space period. The typing store contains individual words and word-pair counts only; it does not store sentences, full editor contents or clipboard text, and has no network dependency. Device-level persistence, sensitive-field exclusions, suggestion relevance and punctuation edge cases remain to be accepted on the Redmi Note 12 Pro 5G. Hosted CI passed in run `42054296162` (unit tests, lint, signed debug/release APKs and all artifact uploads). Physical-device acceptance remains pending.


## Phase 12.1 — privacy hardening (0.12.1)

All personal typing-memory writes now use one guard that checks the active editor type before persisting a word or adjacent-word count. This closes punctuation and Enter commit paths that could otherwise bypass the safe-field check. Automatic learning remains off by default; personal word pinning is still an explicit user action. Version 0.12.1 / versionCode 13 passed hosted CI in run `42079070087`: unit tests, lint, signed debug/release APK assembly, signing verification and all artifact uploads succeeded. Physical Redmi acceptance remains pending.


## Current product phase — 2026-11-06 (0.13.0 usability reset)

Implemented on branch `ux-reset/0.13.0`:

- Five-item primary navigation: Home, Library, Files, Organize and More. Installed apps and Settings are accessible through the More hub.
- Contextual home setup card routes directly to Android's all-files-access screen while explaining the non-destructive initial index.
- Global search now takes over the content area from every tab, displays the actual matching library items, shows a result count, and distinguishes an empty query from no matches.
- Keyboard toolbar labels are explicit (Type / Clips / Vault / language / Tools); the Tools panel is grouped, scrollable and sized to the available display.
- Increased keyboard press scale/depth feedback, held-key preview size, toolbar height and touch targets in Clips mode.
- Word-pair suggestions now use the word immediately before the active cursor, reading at most 80 characters transiently from safe text fields; that context is not persisted.
- Expanded offline EN / PL / TR lexicon/context pairs and regression cases.

Version: 0.13.0 / versionCode 14. Application ID, Room schema, user data and existing signing identity are unchanged.

Verification state: hosted CI run [42261718395](https://github.com/ysfclc/VaultOS/actions/runs/42261718395) passed unit tests, lint, signed debug/release APK assembly, checksum preparation and private draft release publishing. APK signer SHA-256 matches the retained certificate: `c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23`. The signed release APK is attached at [VaultOS-release.apk](https://github.com/ysfclc/VaultOS/releases/download/untagged-04b4086aa81185969695/VaultOS-release.apk). Physical phone acceptance is still pending; this is not yet a device-verified usability release. The existing signing identity is intentionally retained to preserve update installation continuity.


## Keyboard quality reassessment — 2026-11-12

User feedback reports that the keyboard still feels slow and does not provide useful prediction. Source inspection supports this concern: the current predictor is a small static EN/PL/TR lexicon with sparse single-word context maps; personal dictionary JSON is read during suggestion rendering; local edit-distance correction and candidate-row reconstruction occur on the typing path. The 0.13.0 unit tests verify helper examples, not perceived prediction quality or real-device latency.

The keyboard must therefore be treated as a partial product surface, not complete. See docs/KEYBOARD-V2.md, requirement R-013 and ADR-009. KBD-1 responsiveness and KBD-2 contextual prediction take priority over visual redesign. No cloud text processing or heavyweight model dependency is approved.


## Keyboard V2 — KBD-1 responsiveness implementation (2026-11-12)

Implemented in source:

- `VaultKeyboardService` now reuses one `LocalTypingMemory` instance for the lifetime of the IME instead of constructing it during each candidate render.
- `LocalTypingMemory` caches parsed SharedPreferences word/pair snapshots and the dictionary word list; changed preference keys invalidate the affected cached data. Existing JSON keys and stored values are preserved.
- `KeyboardSuggestionEngine` filters by compatible word length, caps the correction pool at 120, prioritizes same-initial candidates while retaining a fallback pool, and uses bounded-memory Damerau–Levenshtein with early exit.
- `VaultKeyboardService.renderSuggestions()` now reuses its three candidate cells and separators instead of recreating the candidate row on each keystroke.
- Added regression cases for candidate limits, adjacent transposition and unrelated input noise.

Verification: source changes are committed. Hosted unit tests/lint/build have not yet been confirmed from the available GitHub Actions inspection surface; no test-pass or APK claim is made. The Redmi Note 12 Pro 5G latency and typing-quality measurements remain pending. KBD-1's repeatable microbenchmark is still open.

- Added `KeyboardSuggestionEngineBenchmarkTest`: repeatable JVM correction benchmark with 500 personal words, 150 warmups, 1,000 samples and p50/p95 output. Execution and measurement are pending; no performance result is claimed.


## Keyboard V2 — KBD-2 contextual prediction implementation (2026-11-12)

Implemented in source:

- `LocalTypingMemory` now learns bounded three-word context counts only while private learning is enabled, preserving the existing word and adjacent-pair keys and limits. Trigram storage is capped at 1,200 contexts per language and cleared with personal typing data.
- The keyboard tracks a transient two-word context, reads at most 120 characters before the cursor in safe text editors, and refreshes its fallback context after cursor navigation. It does not store editor text or sentences.
- The predictor prefers a learned trigram for the exact two-word context, then falls back to existing v1 bigram counts. Static phrase priors are separated by EN/PL/TR locale.
- Added pure-engine tests for two-word context in all three languages and fallback when the two-word context is unknown.

Verification remains pending: no Android-backed persistence tests or executed Android/JVM suite are available in this session. The build and physical Redmi acceptance gates remain open.


## Keyboard V2 — asynchronous correction ranking (2026-11-12)

Implemented in source:

- Local fuzzy correction no longer runs synchronously inside `renderSuggestions()`. The IME takes a copy of the current personal vocabulary on the main thread and submits bounded ranking to one reusable single-thread executor.
- The existing current-word and prefix-completion candidates remain available immediately while local corrections are calculated.
- A per-render generation gate rejects results from older requests. The apply path also verifies the input session, exact typed word, selected language, current candidate-row identity, safe-editor status and absence of newer SpellChecker suggestions.
- Pending queued ranking is cleared when a newer request arrives; the worker is shut down and pending tokens invalidated when the IME service is destroyed. No editor text or new network/model dependency is introduced.
- Added `SuggestionRequestGateTest` with three cases covering newest-request precedence and invalidation.

Verification: the generation-gate helper compiled and passed a standalone Kotlin execution. The full Android project build, lint, JUnit suite and 500-word benchmark have not been run here. The GitHub status currently reports a successful Vercel check only; the available workflow-run query returns no Android Actions run for this commit. Redmi device acceptance remains open.


## Keyboard V2 — Android-backed private learning coverage (2026-11-12)

Implemented:

- Added six Android instrumentation tests against the app sandbox's real SharedPreferences: opt-in default, word/bigram/trigram persistence across service-memory instances, opt-out behavior, clear/reset semantics, EN/PL/TR isolation and legacy v1 JSON fallback.
- Added AndroidX instrumentation dependencies and an API 35 Google APIs emulator step to the Android Actions workflow. The existing build/test/lint/signed APK steps remain in place.
- Fixed a context-learning edge case: one-letter words such as English "I" are now valid learned context tokens, while punctuation-only and over-32-character values remain rejected.

Verification status: test source, Gradle dependencies and workflow were committed and re-fetched. GitHub Actions run [162](https://github.com/ysfclc/VaultOS/actions/runs/43226760701) and its retry failed before job steps or downloadable logs were exposed, so the cause is not yet diagnosable from the available evidence and API 35 instrumentation remains unverified. Do not claim tests, Android build, lint or APK succeeded. Physical Redmi acceptance remains open.
