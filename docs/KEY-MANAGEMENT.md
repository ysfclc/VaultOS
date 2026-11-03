# VaultOS signing key management

VaultOS 0.3.0 preserves the exact signing certificate used by the 0.2.0 APK. The stable sideload key is `signing/vaultos-stable.keystore`, alias `androiddebugkey`. Debug and release variants intentionally use the same certificate so either variant can update the existing install.

## Build

The key is a standard Android debug keystore, so Gradle uses the standard local debug credentials (`android`). The credentials are not repeated in application source because the keystore format itself expects them.

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

## Key safety and continuity

- Keep `signing/vaultos-stable.keystore` private and maintain an offline backup.
- Never generate a replacement key or let Gradle silently create a new `~/.android/debug.keystore` for VaultOS. Android will reject an APK signed by a different certificate as an update; uninstalling would then be required and app-private data would be lost.
- This is a personal sideload/debug signing identity, not a secure production key. Do not publish the keystore or use it for a public Google Play release.
- For a future production release, plan a deliberate signing-key transition before publishing; a different certificate cannot update this installed app directly.
