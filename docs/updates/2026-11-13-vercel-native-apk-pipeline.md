# Connected Vercel Android release pipeline

The `vaultos` Vercel project is configured to build this Android repository directly from its linked GitHub source. `scripts/vercel-build.sh` provisions/caches JDK 17, Gradle 8.9 and Android SDK 35 in `.vercel/cache/`, runs JVM unit tests and lint, assembles the signed release APK, verifies the preserved signing certificate, and writes a versioned APK, ZIP, checksums, build metadata and mobile download page into `dist/`.

`vercel.json` selects the script and `dist/` as the static deployment output. The first connected production deployment must still be verified before the pipeline is considered operational. Device acceptance remains separate.
