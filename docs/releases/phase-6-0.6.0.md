# VaultOS 0.6.0 — filesystem safety hardening

## Changes

- Refuse symbolic links in recursive copy, move, ZIP creation and delete operations.
- Use Java NIO NOFOLLOW_LINKS checks so recursive directory traversal does not follow symbolic links.
- Reject unsupported non-regular filesystem entries during recursive statistics.
- Calculate selected-source ZIP statistics once and reuse the totals for progress reporting.
- Add regression tests proving that linked external directories cannot be deleted through symlinks, nested linked files cannot be copied or moved as part of a directory, deletion preflights the complete tree before removing siblings, and a symlink cannot be selected as a ZIP root.
- Bump versionCode to 6 / versionName to 0.6.0 while preserving application ID, Room schema and stable sideload signing identity.

## Local verification

- 18 unit tests passed (0 failures, 0 errors, 0 skipped).
- Lint completed with 0 errors and 16 warnings.
- Debug and release APKs assembled successfully.
- Both APK signatures verify with the preserved stable certificate SHA-256 digest: c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23.
- Release metadata: com.vaultos.app, versionCode 6, versionName 0.6.0, minSdk 26, targetSdk 35.

## Not claimed as verified

- GitHub-hosted Actions has not been run for this release.
- Physical Redmi Note 12 Pro 5G clipboard/IME, Android-version permission, Xiaomi background, backup/restore, accessibility and landscape acceptance remain pending.
- The app remains local-first; no network permission or cloud service was added.

## Workflow upload

Replace both protected workflow files (.github/workflows/android-build.yml and root android-build.yml) with the matching 0.6.0 YAML before importing incoming/VaultOS-source.zip.
