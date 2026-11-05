# VaultOS signing key management

## Current status — remediation blocked on replacement credentials and compatibility decision

The former signing identity at `signing/vaultos-stable.keystore` was committed while this repository was public. Treat that identity and its credentials as exposed. Making the repository private is useful containment, but it does not remove copies from Git history, detached public forks, prior clones, or downloaded ZIPs.

**Do not use the exposed identity for new release signing once a replacement/update strategy is ready. Do not delete or rotate it blindly:** existing VaultOS installations may require the original signer to accept an update.

## Existing installation continuity

- Application ID: `com.vaultos.app`
- Minimum Android version: API 26
- The historical build used the same signing certificate for debug and release.
- A different signing certificate normally cannot update an existing installation in place.
- APK Signature Scheme v3 key rotation is supported from Android 9 (API 28).
- With `apksigner sign --lineage ... --rotation-min-sdk-version 28`, the rotated signer is used from API 28 onward; the original signer remains necessary for platforms below API 28.
- Android 13 (API 33) introduced v3.1, whose default behavior targets the rotated signer at API 33+ and retains the original signer in the v3 block for older platforms. Do not rely on that default if the intended rotation boundary is API 28.

### The API 26–27 security trade-off

Because VaultOS currently supports API 26 and 27, those devices cannot use the v3 proof-of-rotation mechanism. Keeping in-place updates for those installations requires continuing to sign their compatible APKs with the exposed original key. That preserves update continuity but does **not** fully remediate the compromised key for API 26–27 devices.

There are therefore two honest choices:

1. **Keep minSdk 26:** rotate to the replacement signer from API 28, while retaining the exposed signer for API 26–27. This protects the supported API 28+ update path but leaves legacy API 26–27 updates dependent on the compromised signer.
2. **Raise minSdk to 28:** stop supporting API 26–27 and use the replacement signer through a v3 lineage for supported devices. Existing API 26–27 users would need a data-export/reinstall migration; they cannot receive a normal in-place update signed only by the new key.

Do not choose between these options silently. The supported-device policy and the data-migration consequences must be accepted before release signing changes.

## Required remediation sequence

1. Keep the repository private while cleanup is underway.
2. Provision a replacement keystore and keep it outside the repository. Store the required keystore bytes and passwords as GitHub Actions secrets; never commit them.
3. Decide whether API 26–27 remain supported. If they do, document that their update signer remains compromised.
4. Generate and verify a signing-certificate lineage from the exposed certificate to the replacement certificate. For an API 28 rotation boundary, use the documented `apksigner` rotation options and explicitly set `--rotation-min-sdk-version 28`.
5. Update Gradle and the canonical GitHub Actions workflow to use the Android default debug signer for debug builds and secret-backed signing for release builds.
6. Build and verify APK signatures for the supported API range. Test an in-place update from the last distributed APK on representative Android versions and verify app-private data remains intact.
7. Only after the new pipeline and update path are proven, remove the committed keystore from the current tree and rewrite exposed Git history. Review detached public forks and previously distributed archives separately.
8. Verify the repository, ZIP import process, and all generated release artifacts contain no private signing material.

## Current build warning

The current Gradle configuration and workflow still reference the historical committed keystore. This is an unresolved blocker. The workflow also needs its source-ZIP import path hardened so a future archive cannot reintroduce private signing material.

The GitHub connector cannot read or verify GitHub Actions secret values. A successful build alone is not proof of signing-key rotation; inspect the resulting certificate and lineage. Do not claim remediation complete until the lineage and in-place update have been verified on-device.

## Local development

Never commit a keystore. Keep local signing material outside the repository and provide it through local Gradle properties or environment variables. Use the Android default debug signing identity for ordinary development; never sign debug APKs with a production or release key.
