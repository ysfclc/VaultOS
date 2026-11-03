# VaultOS 0.4.0 — smarter library

## Changes

- Bumped Android versionCode to 4 / versionName to 0.4.0.
- Updated both workflow YAML copies to report the 0.4.0 release and versionCode 4; the import step intentionally protects those files, so the matching YAML is delivered separately.
- Added Room 2→3 migration and tables for user tags, tag assignments, user collections, collection membership, saved searches and extracted item metadata. Existing item rows and search chunks are retained.
- Added user collection creation, item assignment/removal, tag creation/assignment/removal, and deletion controls.
- Added related-item results from shared tags, shared collections and category membership.
- Added duplicate groups based on the existing SHA-256 catalogue values; duplicate inspection does not trigger a full-library rehash.
- Expanded unified search to include tags and extracted title/author/subject metadata. Added saved-search capture and reopening.
- Added user-triggered local metadata extraction for image dimensions/EXIF, PDF info/page count, DOCX/ODT core properties, audio/video media tags, APK package/version and text title. Extraction runs off the UI thread and leaves source data unchanged.
- Upgraded the external backup manifest to `vaultos-backup-v2`, preserving tags, assignments, collections, saved searches and metadata. Restore accepts both v1 and v2.
- Added unit tests for PDF and office-document metadata parsing.

## Intentionally deferred

On-device semantic indexing remains disabled until a specific embedding runtime/model is selected and tested on the target device class. No model download, network processing or cloud service is introduced. Physical Xiaomi behavior and Android-version acceptance testing remain part of Phase 5 / 1.0.0.

## Workflow correction — 2026-11-03

The GitHub Actions artifact-preparation step now invokes Android Build Tools 35.0.0 `apksigner` through its absolute path under `ANDROID_HOME` (falling back to `ANDROID_SDK_ROOT`). It checks that the executable exists and prints installed Build Tools locations if not. This avoids relying on the versioned Build Tools directory being present on the runner `PATH`. Both workflow copies are kept identical. The corrected signature command was exercised locally against both 0.4.0 APK variants; both verified with the retained VaultOS signing certificate.

## Verification — 2026-11-03

- Clean Gradle build: `clean` completed, followed by `testDebugUnitTest`, `lintDebug`, `assembleDebug` and `assembleRelease`; all tasks passed.
- Unit tests: 13 passed, 0 failures (5 file-operation, 4 metadata-parser and 4 file-classifier tests).
- Android lint: 0 errors, 16 warnings.
- Both APK variants verify with `apksigner`; debug and release signatures match each other, the persistent keystore, and the retained 0.3.0 release artifact (certificate SHA-256 `c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23`).
- Release APK metadata: application ID `com.vaultos.app`, versionCode 4, versionName 0.4.0, min SDK 26, target SDK 35.
- SQLite migration DDL was independently exercised for table creation, normalized-name uniqueness, foreign-key integrity and delete cascades.
- Physical Redmi testing and GitHub-hosted Actions execution are not claimed.
