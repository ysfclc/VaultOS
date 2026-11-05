# VaultOS signing key management

## Current status — security remediation in progress

The former signing identity at `signing/vaultos-stable.keystore` was committed while this repository was public. Treat that identity and its credentials as exposed. Making the repository private is useful containment, but it does not remove copies from Git history, detached public forks, prior clones, or downloaded ZIPs.

**Do not use the exposed identity for new release signing once a replacement/update strategy is ready. Do not delete or rotate it blindly:** existing VaultOS installations may require the original signer to accept an update.

## Existing installation continuity

- Application ID: `com.vaultos.app`
- Minimum Android version: API 26
- The historical build used the same signing certificate for debug and release.
- A different signing certificate normally cannot update an existing installation in place.
- Any signing-certificate rotation must be validated with Android's supported signing lineage and tested by installing over the currently installed APK while verifying app-private data remains intact.
- API-level compatibility must be checked against the supported Android range before adopting a rotated signer. Do not assume a v3.1-only strategy covers API 26–32.

## Required remediation sequence

1. Keep the repository private while cleanup is underway.
2. Provision the replacement keystore and its passwords as GitHub Actions secrets. Never commit the keystore or passwords.
3. Establish and verify a signing lineage from the exposed certificate to the replacement certificate, with a compatibility strategy for every supported Android API level.
4. Update Gradle and the canonical GitHub Actions workflow to consume secrets. Keep debug signing separate from release signing.
5. Build and verify the APK signatures and lineage.
6. Test an in-place update from the last distributed APK on representative Android versions; verify app data remains present.
7. Only after the new pipeline and update path are proven, remove the keystore from the current tree and rewrite exposed Git history. Review detached public forks and previously distributed archives separately.
8. Verify the repository, ZIP import process, and all generated release artifacts contain no private signing material.

## Current build warning

The current Gradle configuration and workflow still reference the historical committed keystore. This is intentionally documented as an unresolved blocker rather than silently changing signing and risking an un-installable update.

The replacement credentials must be configured in GitHub Actions before switching release signing. The GitHub connector cannot read or verify the values of Actions secrets. A successful build alone is not proof of signing-key rotation; inspect the resulting certificate and lineage.

## Local development

Never commit a keystore. Keep local signing material outside the repository and provide it through local Gradle properties or environment variables. Use a separate debug identity for ordinary development.
