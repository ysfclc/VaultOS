# VaultOS 0.5.0 — device integration and polish

## Changes

- Bumped Android versionCode to 5 / versionName to 0.5.0 without changing the application ID, Room schema or stable sideload signing identity.
- Added Xiaomi MIUI/HyperOS background-work onboarding to Settings. It explains Autostart, Battery saver / No restrictions and optional locking in Recents, with wording that acknowledges vendor menu differences.
- Added safe shortcuts to Android app details and the system battery-optimization app list. The app does not silently alter system settings or request a battery-optimization exemption.
- Made Settings independently vertically scrollable and added bottom spacing so controls remain reachable on short displays and with gesture navigation / larger system text.
- Added a subtle page-content size transition and an explicit screen-reader label to the automatic clipboard-capture switch.
- Kept orientation unlocked; physical landscape and accessibility review remains pending.

## Not claimed as verified

- Clipboard behavior in Chrome, ChatGPT, WhatsApp, Notes and password fields has not been exercised on a physical device.
- Xiaomi autostart and battery restrictions have not been measured on the Redmi Note 12 Pro 5G.
- Android 13/14/15 permission flows and large-font/screen-reader acceptance have not been completed.
- WorkManager remains best-effort; no exact background execution interval is promised.

## Local verification

- 13 unit tests passed (0 failures).
- Lint completed with 0 errors and 16 warnings.
- Debug and release APKs assembled successfully.
- Both APK signatures verify with the preserved stable certificate SHA-256 digest: c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23.
- Release metadata: com.vaultos.app, versionCode 5, versionName 0.5.0, minSdk 26, targetSdk 35.

## Build gate

GitHub-hosted Actions and physical Redmi testing have not yet been run for this release. The workflow copies at .github/workflows/android-build.yml and android-build.yml must both be replaced with the matching 0.5.0 workflow before running Actions.
