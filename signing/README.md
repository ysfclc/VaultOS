# Signing material

`vaultos-stable.keystore` is the historical sideload signing identity carried forward from the 0.2.0 APK. It was committed while the repository was public and is considered compromised.

**Do not use this keystore or its credentials to sign any new APK.** It is no longer referenced by Gradle. Debug and instrumentation builds use Android's generated default debug signing key; the release variant is intentionally unsigned until the replacement key and v3 lineage are verified.

Retain the historical file temporarily only because it may be needed to generate the old-to-new signing lineage. Keep a protected offline copy before any history rewrite. Read `docs/KEY-MANAGEMENT.md`.
