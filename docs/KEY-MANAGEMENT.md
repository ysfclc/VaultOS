# VaultOS signing key management

## Current status — release signing and distribution paused

The historical VaultOS signing identity at `signing/vaultos-stable.keystore` was committed while this repository was public. Treat its private key and credentials as compromised. The repository is still public; no visibility change or Git-history rewrite has been performed.

**The historical signer has been removed from Gradle build configuration.** Debug and instrumentation builds use Android's generated default debug signer. The release variant is intentionally unsigned. Do not use the exposed identity to sign a new release APK. Retain it only as a temporary migration input until the replacement lineage has been created and verified; it must not remain the long-term release signer.

Application identity and current build baseline:
- Application ID: `com.vaultos.app`
- Current source version: `0.13.0` / versionCode `14`
- Minimum Android version: API 28
- Target/compile SDK: 35
- Historical distributed release certificate SHA-256: `c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23`
- Release signing: disabled; no replacement signer or lineage has been provisioned.

The candidate versionCode 19 was not published. Any migration build must use a versionCode higher than the last distributed release (14), and must be checked against the exact baseline APK installed by existing users.

## Compatibility finding — unresolved rotation boundary decision

Android APK Signature Scheme v3 supports proof-of-rotation beginning with Android 9 (API 28). Android 13 (API 33) introduced v3.1, which can target the rotated signer at API 33+ while retaining the original signer in the v3 block for older platform versions by default.

**Important caveat:** Android's current documentation explicitly warns that key rotation is not recommended on Android 12 (API 31) and earlier. The `apksigner` documentation says that if a rotated signer has already been used on Android 12L (API 32) or lower, subsequent signing must explicitly use `--rotation-min-sdk-version 28` to continue using that rotated signer from API 28. The v3.1 documentation also makes clear that the default rotation boundary leaves the original signer active on API 28–32.

This creates a product/security decision that must not be hidden inside a build flag:

| Policy | Android 28–32 | Android 33+ | Consequence |
|---|---|---|---|
| Keep `minSdk=28`, use default v3.1 boundary | Old signer remains active | New signer | The compromised key is not retired for supported API 28–32 users. |
| Keep `minSdk=28`, force rotation boundary 28 | Rotated signer is selected via v3 | Rotated signer via v3 | Matches the current minimum but Android warns against rotation on API 31 and earlier; requires real install/update tests and explicit acceptance of the platform caveat. |
| Raise `minSdk=33`, use v3.1 | Unsupported; users cannot update | New signer | Cleanest boundary for retiring the old signer across all supported devices, but drops Android 9–12L and requires a migration/export plan for those users. |

**No policy has been selected.** The previously approved minimum is API 28; changing it to API 33 is a product compatibility decision and requires explicit owner approval. Do not silently raise `minSdk`, and do not describe the API 28 rotation path as fully safe before it has been demonstrated on-device.

A successful `apksigner verify` only verifies APK signature validity for selected platform versions. It does **not** prove Package Manager will accept the update, that signature permissions behave correctly, or that VaultOS data survives.


Official references:
- https://developer.android.com/tools/apksigner
- https://source.android.com/docs/security/features/apksigning/v3
- https://source.android.com/docs/security/features/apksigning/v3-1

## Secure migration procedure

1. **Containment:** keep public distribution paused. Repository visibility is currently public; changing it requires a separate explicit decision. Do not rewrite history or delete the old keystore before the migration evidence and secure backups exist.
2. **Provision replacement material:** generate a new, unique release key in a trusted local/offline environment controlled by the owner. Use a strong unique keystore and key password. Keep the keystore and password manager backup outside Git, build logs, artifacts, chat, and ordinary workspace backups. Record only the new certificate's public SHA-256 fingerprint in project documentation.
3. **Protect CI access:** only after the owner has securely provisioned the key, transfer it into a narrowly scoped secret-backed release workflow. Never print secret values, decode them into logs, or expose them to pull-request builds from forks. The GitHub connector cannot read back or verify Actions secret values.
4. **Select the rotation boundary:** obtain explicit owner approval for one of the three policies in the compatibility table. The existing API 28 minimum remains in force unless superseded by that decision.
5. **Create lineage:** use the exact Android SDK Build Tools `apksigner` version selected for release. Generate a lineage from the historical certificate to the new certificate using `apksigner rotate`. The historical key is used only to authorize this transition; do not use it as the current release signer.
6. **Validate signing behavior:** sign disposable candidates for the selected policy. Inspect the resulting certificate(s), proof-of-rotation and signing schemes. Verify the APK separately for every API version that policy claims to support. Do not infer the correct signer ordering or flags from an untested command copied into CI.
7. **Prove in-place update:** install the exact last distributed old-signed APK on clean test devices/emulators for each supported API boundary. Populate representative app-private data first: Room catalogue, archive payloads, clipboard history, tags/collections and keyboard settings. Install the candidate directly over it without uninstalling or clearing data. Confirm package update success, expected signing lineage, app launch, readable records and payload hashes. Test the actual Redmi separately. Record API/build/device and evidence.
7. **Release pipeline:** only after the above succeeds, add release signing to a protected workflow. Require a versionCode greater than 14, signer-fingerprint allowlisting, lineage verification, artifact checksum, clean tests/lint/instrumentation, and a manual production-release approval. Keep PR and fork workflows unable to access signing secrets.
8. **Cleanup:** after the owner has verified and backed up the replacement key and lineage, remove the historical keystore from the current tree and rewrite exposed Git history under an explicitly approved operation. Review public forks, clones, cached archives and previously distributed APKs separately. Verify no private key material remains in source, history after rewrite, ZIPs, artifacts or deployment outputs.

## Explicit release blockers

- No replacement key has been provisioned or securely handed off.
- No signing lineage has been generated or inspected.
- No candidate release APK has been signed with the replacement identity.
- No API 28 / API 32 / API 33+ in-place update has been executed.
- No app-private data-preservation test has passed on an actual device.
- The repository remains public and historical key material remains in the current tree and Git history.
- The production download page remains paused; no APK is being distributed.

Do not re-enable release signing, remove the historical migration input, rewrite Git history, or publish an APK while any required blocker remains.

## Local development

Never commit a keystore. Keep local signing material outside the repository and provide it through local Gradle properties or environment variables. Use Android's generated debug signing identity for ordinary development; never sign debug APKs with a production or release key.

