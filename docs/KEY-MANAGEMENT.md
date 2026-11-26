# VaultOS signing key management

## Current status — release signing and distribution paused

The historical VaultOS signing identity at `signing/vaultos-stable.keystore` was committed while this repository was public. Treat its private key and credentials as compromised. The repository is still public; no visibility change or Git-history rewrite has been performed.

**The historical signer has been removed from Gradle build configuration.** Debug and instrumentation builds use Android's generated default debug signer. The release variant is intentionally unsigned. Do not use the exposed identity to sign a new release APK. Retain it only as a temporary migration input until the replacement lineage has been created and verified; it must not remain the long-term release signer.

Application identity and current build baseline:
- Application ID: `com.vaultos.app`
- Current source version: `0.13.0` / versionCode `14`
- Minimum Android version for future releases: API 33 (Android 13)
- Target/compile SDK: 35
- Historical distributed release certificate SHA-256: `c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23`
- Release signing: disabled; no replacement signer or lineage has been provisioned.

The candidate versionCode 19 was not published. Any migration build must use a versionCode higher than the last distributed release (14), and must be checked against the exact baseline APK installed by existing users.

## Selected compatibility and signing policy — Android 13 minimum

**Selected policy:** future VaultOS releases require Android 13 (API 33) or newer. The Gradle `minSdk` is set to 33. This aligns the supported release range with APK Signature Scheme v3.1, allowing the replacement signer to be the active signer for every supported installation instead of leaving the historical signer active on Android 9–12L.

What this means:
- Android 13 and newer: eligible for future VaultOS releases and the v3.1 rotated-signer update path.
- Android 9–12L (API 28–32): the currently installed version may continue to run, but future VaultOS APKs will not install on those devices. Those users must export their VaultOS data from the existing app before moving to an Android 13+ device.
- The user's Redmi Note 12 Pro 5G on Android 14 remains within the supported range.

This policy avoids shipping a new release that continues using the exposed signer as the active update identity on older Android versions. It is a deliberate compatibility reduction. It does **not** by itself complete the signing migration: an old-to-new lineage, replacement key, API 33+ in-place update test and app-data preservation test are still mandatory.

The Android documentation warns against key rotation on Android 12 and earlier. By setting the future release minimum to API 33, VaultOS does not claim to support the caveat-heavy API 28–32 rotation path. Test the actual v3.1 result with the exact Android Build Tools version selected for the release.

A successful `apksigner verify` only verifies APK signature validity. It does **not** prove Package Manager will accept the update, that signature permissions behave correctly, or that VaultOS data survives. The final test must update the exact last distributed APK (versionCode 14) in place without uninstalling or clearing data.



Official references:
- https://developer.android.com/tools/apksigner
- https://source.android.com/docs/security/features/apksigning/v3
- https://source.android.com/docs/security/features/apksigning/v3-1

## Secure migration procedure

1. **Containment:** keep public distribution paused. Repository visibility is currently public; changing it requires a separate explicit decision. Do not rewrite history or delete the old keystore before the migration evidence and secure backups exist.
2. **Provision replacement material:** generate a new, unique release key in a trusted local/offline environment controlled by the owner. Use a strong unique keystore and key password. Keep the keystore and password manager backup outside Git, build logs, artifacts, chat, and ordinary workspace backups. Record only the new certificate's public SHA-256 fingerprint in project documentation.
3. **Protect CI access:** only after the owner has securely provisioned the key, transfer it into a narrowly scoped secret-backed release workflow. Never print secret values, decode them into logs, or expose them to pull-request builds from forks. The GitHub connector cannot read back or verify Actions secret values.
4. **Enforce the selected platform boundary:** future builds must declare `minSdk=33`. Confirm the merged source and final APK metadata both report API 33. API 28–32 devices are intentionally outside the future release/update path.
5. **Create lineage:** use the exact Android SDK Build Tools `apksigner` version selected for release. Generate a lineage from the historical certificate to the new certificate using `apksigner rotate`. The historical key is used only to authorize this transition; do not use it as the current release signer.
6. **Validate signing behavior:** sign a disposable candidate with the replacement key and v3.1 lineage. Inspect the resulting certificate, proof-of-rotation and signing schemes. Verify on API 33, API 34 and API 35. Confirm API 32 and lower reject the future APK because of minSdk, as intended. Do not infer signer ordering or flags from an untested command copied into CI.
7. **Prove in-place update:** install the exact last distributed old-signed APK on an Android 13+ test device/emulator. Populate representative app-private data first: Room catalogue, archive payloads, clipboard history, tags/collections and keyboard settings. Install the candidate directly over it without uninstalling or clearing data. Confirm package update success, expected v3.1 signing lineage, app launch, readable records and payload hashes. Test the actual Redmi Note 12 Pro 5G (Android 14) separately. Record API/build/device and evidence.
8. **Release pipeline:** only after the above succeeds, add release signing to a protected workflow. Require a versionCode greater than 14 and minSdk 33, signer-fingerprint allowlisting, lineage verification, artifact checksum, clean tests/lint/instrumentation, and a manual production-release approval. Keep PR and fork workflows unable to access signing secrets.
9. **Cleanup:** after the owner has verified and backed up the replacement key and lineage, remove the historical keystore from the current tree and rewrite exposed Git history under an explicitly approved operation. Review public forks, clones, cached archives and previously distributed APKs separately. Verify no private key material remains in source, history after rewrite, ZIPs, artifacts or deployment outputs.

## Explicit release blockers

- No replacement key has been provisioned or securely handed off.
- No signing lineage has been generated or inspected.
- No candidate release APK has been signed with the replacement identity.
- No API 33+ in-place update has been executed; API 28–32 are intentionally excluded from future releases by the selected minimum SDK.
- No app-private data-preservation test has passed on an actual Android 13+ device.
- The repository remains public and historical key material remains in the current tree and Git history.
- The production download page remains paused; no APK is being distributed.

Do not re-enable release signing, remove the historical migration input, rewrite Git history, or publish an APK while any required blocker remains.

## Local development

Never commit a keystore. Keep local signing material outside the repository and provide it through local Gradle properties or environment variables. Use Android's generated debug signing identity for ordinary development; never sign debug APKs with a production or release key.

