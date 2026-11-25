# VaultOS signing key management

## Current status — remediation blocked on replacement credentials and compatibility decision

The former signing identity at `signing/vaultos-stable.keystore` was committed while this repository was public. Treat that identity and its credentials as exposed. Making the repository private is useful containment, but it does not remove copies from Git history, detached public forks, prior clones, or downloaded ZIPs.

**The historical signer has now been removed from Gradle build configuration.** Debug and instrumentation builds use Android's generated default debug signer; the release variant is intentionally unsigned. Do not use the exposed identity for any new APK signing. Do not delete or rotate it blindly: existing VaultOS installations may require the original signer to accept an update.

## Existing installation continuity

- Application ID: `com.vaultos.app`
- Minimum Android version: API 28 (approved policy; implementation now committed)
- Historical builds used the same signing certificate for debug and release. Current debug/instrumentation builds use Android's generated default debug signer; current release builds are unsigned.
- A different signing certificate normally cannot update an existing installation in place.
- APK Signature Scheme v3 key rotation is supported from Android 9 (API 28).
- With `apksigner sign --lineage ... --rotation-min-sdk-version 28`, the rotated signer is used from API 28 onward; the original signer remains necessary for platforms below API 28.
- Android 13 (API 33) introduced v3.1, whose default behavior targets the rotated signer at API 33+ and retains the original signer in the v3 block for older platforms. Do not rely on that default if the intended rotation boundary is API 28.

### API 26–27 support decision

Because VaultOS currently supports API 26 and 27, those devices cannot use the v3 proof-of-rotation mechanism. Keeping in-place updates for those installations requires continuing to sign their compatible APKs with the exposed original key. That preserves update continuity but does **not** fully remediate the compromised key for API 26–27 devices.

**Decision approved:** VaultOS now raises minSdk to API 28. This drops Android 8 and 8.1 from supported updates so the release signer can rotate from API 28 onward without retaining a legacy API 26–27 update path. Existing API 26–27 users cannot install the new build as an in-place update; they should export their VaultOS data and move to a supported Android device.

The rotation itself is still pending. API 28–32 have platform-specific key-rotation caveats; the lineage and signed APK must be validated with the exact Build Tools version and tested on representative Android 9, Android 12L, and Android 13+ devices before the release is declared safe. Android's current guidance warns about rotation-related behavior on Android 12 and earlier. Do not infer successful in-place update from `apksigner verify` alone.

## Required remediation sequence

1. Keep the repository private while cleanup is underway.
2. Provision a replacement keystore and keep it outside the repository. Store the required keystore bytes and passwords as GitHub Actions secrets; never commit them.
3. API 26–27 support has been dropped by raising minSdk to API 28.
4. Generate and verify a signing-certificate lineage from the exposed certificate to the replacement certificate. For an API 28 rotation boundary, use the documented `apksigner` rotation options and explicitly set `--rotation-min-sdk-version 28`.
5. [x] Remove the exposed signer from Gradle build configuration. Debug/instrumentation builds now use Android's default debug signer; release signing remains disabled. Add secret-backed release signing only after the replacement key and lineage are provisioned.
6. Build and verify APK signatures for the supported API range. Test an in-place update from the last distributed APK on representative Android versions and verify app-private data remains intact.
7. Only after the new pipeline and update path are proven, remove the committed keystore from the current tree and rewrite exposed Git history. Review detached public forks and previously distributed archives separately.
8. Verify the repository, ZIP import process, and all generated release artifacts contain no private signing material.

## Current build warning

Gradle no longer references the historical committed keystore. The normal CI build/lint job does not assemble APKs; API 35 instrumentation uses Android's generated default debug signer for test-only APKs. The release variant remains unsigned. The Vercel build script now publishes a temporary release-paused page and does not provision Android tooling, read signing material, build an APK or publish a download. The historical keystore is still present in the current tree and Git history for lineage migration, so public exposure is not remediated. The production alias `vaultos-iota.vercel.app` still serves the previous 0.13.0 download page with APK and ZIP links. A Vercel preview of the pause page is READY and returns HTTP 200, but production promotion has not been performed pending explicit owner approval. The app's minSdk is API 28. The legacy full-repository ZIP import step has been removed from both GitHub Actions workflow copies. CI now accepts direct repository changes only and every workflow job uses read-only repository permissions. This does not remove the already-committed key.

The GitHub connector cannot read or verify GitHub Actions secret values. A successful build alone is not proof of signing-key rotation; inspect the resulting certificate and lineage. Do not claim remediation complete until the lineage and in-place update have been verified on-device.

## Local development

Never commit a keystore. Keep local signing material outside the repository and provide it through local Gradle properties or environment variables. Use the Android default debug signing identity for ordinary development; never sign debug APKs with a production or release key.

## Deferred work status — 2026-11-05

The owner has explicitly deferred the replacement-key migration and GitHub Actions secret provisioning until a later session. GOV-001 remains open but is not a blocker for the clipboard/keyboard product roadmap. Keep the historical signer and signing configuration unchanged until the replacement certificate lineage is generated and in-place update compatibility is verified. Do not remove the tracked historical keystore or rewrite Git history as part of unrelated product phases.