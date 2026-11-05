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
