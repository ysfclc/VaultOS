# VaultOS governance handoff — 2026-11-05

## Repository state

- Repository: `ysfclc/VaultOS`
- Default branch: `main`
- Visibility: private
- Latest verified source commit before the API policy change: `db5d43c8f51980f38a126dac2192dea294bfc1bf`

## Completed in this work session

### GOV-002 — workflow mirror reconciliation

- `.github/workflows/android-build.yml` and root `android-build.yml` now have identical contents.
- Both paths reference the same Git blob: `44ef3d7b60eb7225e065d96024690e40cc932c5b` at the initial synchronization; the later importer hardening was also written to both paths in the same commits.
- ZIP extraction excludes both workflow files so an uploaded source archive cannot silently roll back the active workflow.

### ZIP importer safeguards

- Ordinary pushes no longer replay the previously uploaded ZIP. Import runs on a push only when `incoming/VaultOS-source.zip` itself is added or modified, or when the workflow is manually dispatched.
- Archives containing a `signing/` directory or private signing-key file extensions are rejected.
- ZIP extraction excludes `signing/`, preserving the repository's existing signing directory until a verified migration is ready.
- Import commits fetch and rebase against the latest branch head, retry non-conflicting push races, and stop without pushing if newer edits conflict.
- README build/import instructions were updated to describe these safeguards.

## API compatibility decision — implemented

- Approved policy: minimum supported Android version is API 28.
- `app/build.gradle.kts` now declares `minSdk = 28`.
- README and signing documentation explain that Android 8/8.1 users are no longer supported and need to export data before moving to a supported device.
- Both workflow copies report minimum SDK 28 in build metadata.
- The first workflow attempt after the change (`41976573899`) failed before creating a job because the YAML file had a malformed duplicate tail. The tail was removed in commit `2b4865b45b19ffb1382d295983d88411d5ae35a6`; the workflow files are again exact mirrors (332 lines).
- A clean CI run for that commit (`41976641135`) has been accepted and started. ZIP import and source checks passed; Java/SDK setup passed; Gradle setup was still in progress at the latest check. Do not call the API 28 build green until the run completes successfully.

## GOV-001 — still open

The historical keystore remains tracked and Gradle still uses it for both debug and release. No replacement keystore or GitHub Actions signing secrets have been provisioned or verified. The old key has not been deleted and Git history has not been rewritten.

**Decision approved and implemented in source:** minSdk is raised from API 26 to API 28. Android 8/8.1 users are outside the supported update path and need to export their data before moving to a supported device. The v3 lineage migration itself remains pending.

## Verification

- Confirmed repository visibility is private.
- Confirmed both workflow files are exact mirrors.
- Confirmed the importer gate, private-key archive rejection, signing-directory exclusion, and safe rebase logic are present in both copies.
- A previous build run (`41974529337`) failed in the ZIP-import push because the remote branch moved during the run; it did not fail in Gradle.
- A subsequent build run (`41974664174`) completed successfully: ZIP import, lint-critical source checks, Java/SDK/Gradle setup, unit tests, lint, APK assembly, signature verification, and artifact uploads all passed. The first run after raising minSdk (`41976573899`) failed before creating any jobs because the workflow file contained a malformed duplicate tail. That YAML defect is being removed in this commit; require a new successful run before treating the API 28 change as CI-verified.
- The current repository still contains the exposed signing identity. The security remediation is not complete.

## Next actions

1. Wait for CI run `41976641135` to finish and resolve any actual build failure.
2. Provision and verify replacement signing material and the required GitHub Actions secrets.
2. Generate the signing lineage from the historical certificate to the replacement certificate using Android Build Tools.
3. Implement the v3 signing-lineage pipeline and test in-place updates on API 28, API 32, and API 33+ devices.
4. Only after update continuity is proven, remove private signing material from the current tree and rewrite exposed Git history.
5. Refresh the source ZIP from the current repository and exclude `signing/` before the next import.

## 0.7.0 — clipboard history foundation

- Added Room schema v4 and a non-destructive 3→4 migration for clipboard event history.
- Each captured clipboard item creates its own timestamped history event. Identical text payloads can reuse one archived VaultItem without collapsing separate copy events.
- Captures retain foreground editor package context, clipboard label, MIME types, content hash, character/byte counts and capture time.
- Automatic IME capture now records every supported item in multi-item clips independently; one inaccessible URI no longer prevents later items from being saved.
- Added searchable history gallery with full-text search through existing chunk indexes, full-text copy, pinning, event deletion and clear-unpinned behavior.
- Gallery is accessible from the main app toolbar and the VaultOS keyboard's History action.
- Text archive writes and reads verify byte count and SHA-256; Paste saved now follows the most recent clipboard event rather than the original deduplicated item's creation time.
- Password editor fields and Android 13+ sensitive clipboard clips are excluded from automatic capture.
- Android's selected-IME restriction remains: automatic capture requires VaultOS to be the active input method. Manual capture remains available inside VaultOS.
- App version is now 0.7.0 / versionCode 7; minimum supported API is 28.

## Signing migration — deferred by user

- GOV-001 remains open and is explicitly deferred until the user provisions the replacement signing material and GitHub Actions secrets.
- Keep the historical signing keystore in place until the replacement lineage and in-place update path are validated. Do not rotate, delete or rewrite history prematurely.
- This deferred security task does not block clipboard, keyboard, gallery, command or connector product work.

## Current verification

- Earlier governance build `41976641135` completed successfully, including tests, lint, APK assembly, signing verification and artifact upload, for its then-current source commit.
- Clipboard build attempts for earlier intermediate commits were cancelled by subsequent pushes. The current 0.7.0 build run is `42025657002`, queued for source commit `6c7274fd92b23d4407703638eda9e899a0636e7e`. Do not mark the phase CI-verified until this run completes successfully.
- Physical Redmi Note 12 Pro 5G behavior is not yet verified. Follow the new clipboard acceptance section in `DEVICE-TEST-PLAN.md`.

## Next

1. Confirm run `42025505944` completes tests, lint, debug/release assembly and artifact uploads; fix any real failure before continuing.
2. Install the 0.7.0 APK on the Redmi Note 12 Pro 5G and complete the clipboard acceptance matrix.
3. Continue Phase 2 keyboard work: language selector, suggestions/autocorrect, clipboard mode/search/pins inside the keyboard, settings and lifecycle tests.
4. Resume GOV-001 only when the user has added the replacement keystore and signing secrets.
### CI compile correction — run 59

- Run `42025657002` reached Kotlin compilation and failed on three root causes: an invalid explicit Compose `Modifier.weight` import in `ClipboardHistoryActivity`, and a removed `VaultRepository` import still required by the keyboard's Paste saved action (the latter caused cascading type-inference errors).
- Corrected both source imports and synchronized the workflow release summary to 0.7.0 / versionCode 7 in the same atomic commit `3def5b4f98b357b778613b5561a9fec148e98a99`.
- Verified root `android-build.yml` and `.github/workflows/android-build.yml` are byte-identical after the correction.
- New CI run `42026208055` is in progress for the corrected source. The phase remains unverified until it completes successfully.
### CI verification — run 60 successful

- Run `42026208055` completed with conclusion `success` for source commit `3def5b4f98b357b778613b5561a9fec148e98a99`.
- `gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleRelease` completed with `BUILD SUCCESSFUL`.
- Signing-key verification passed; debug and release APKs were assembled and signed with the retained historical signer.
- Artifacts uploaded: `VaultOS-debug-APK` (15,438,915 bytes), `VaultOS-release-APK` (11,587,443 bytes), and `VaultOS-build-metadata` (539 bytes).
- Workflow run: https://github.com/ysfclc/VaultOS/actions/runs/42026208055
- This confirms hosted compilation, unit-test task, lint, APK assembly and artifact upload. It does not substitute for physical-device acceptance.
## Phase 8 — keyboard usability (0.8.0)

- Added persistent EN / PL / TR language selection from the keyboard toolbar and app Settings.
- Added an inline Clips mode that keeps the active input field, searches the full clipboard archive through the existing chunk index, shows recent/pinned matches, and pastes complete text directly into the focused field.
- Added pin/unpin actions inside the keyboard. Full gallery remains accessible through History.
- Build run `42027563663` for the intermediate keyboard source is in progress; release 0.8.0 run `42027643930` is queued for versionCode 8. Wait for the latter to verify the exact release source.
- Remaining Phase 2 items: spelling suggestions/autocorrect with sensitive-field exclusions, additional settings and physical lifecycle/accessibility acceptance.