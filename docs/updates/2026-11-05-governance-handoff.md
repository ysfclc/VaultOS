# VaultOS governance handoff — 2026-11-05

## Repository state

- Repository: `ysfclc/VaultOS`
- Default branch: `main`
- Visibility: private
- Latest verified source commit at handoff: `50dc7b31786ab33519a43db74628b8fadc0fa5ed`

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

## GOV-001 — still open

The historical keystore remains tracked and Gradle still uses it for both debug and release. No replacement keystore or GitHub Actions signing secrets have been provisioned or verified. The old key has not been deleted and Git history has not been rewritten.

A compatibility decision is required before release signing changes:
- Keep API 26–27: those devices still require the exposed original signer; this preserves legacy update continuity but leaves that legacy path compromised.
- Raise minSdk to API 28: use v3 lineage rotation for supported devices, but API 26–27 users need a data-export/reinstall migration rather than an in-place update.

The exact Android rotation behavior and decision points are documented in `docs/KEY-MANAGEMENT.md`.

## Verification

- Confirmed repository visibility is private.
- Confirmed both workflow files are exact mirrors.
- Confirmed the importer gate, private-key archive rejection, signing-directory exclusion, and safe rebase logic are present in both copies.
- A previous build run (`41974529337`) failed in the ZIP-import push because the remote branch moved during the run; it did not fail in Gradle.
- A subsequent build run (`41974664174`) passed ZIP import, lint-critical source checks, Java/SDK/Gradle setup, and persistent-key verification; the Gradle test/lint/build step was still running at the time of this handoff. Do not treat it as a passing build until GitHub reports a completed success.
- The current repository still contains the exposed signing identity. The security remediation is not complete.

## Next actions

1. Decide whether VaultOS continues supporting API 26–27.
2. Provision and verify the replacement signing material and required Actions secrets.
3. Implement and test the v3 signing-lineage pipeline before removing the historical signer.
4. Remove private signing material from the current tree and ZIP workflow, then rewrite exposed Git history after update continuity is proven.
5. Refresh the source ZIP from the current repository and exclude `signing/` before the next import.
