#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
CACHE="$ROOT/.vercel/cache"
ANDROID_HOME="$CACHE/android-sdk"
ANDROID_SDK_ROOT="$ANDROID_HOME"
GRADLE_USER_HOME="$CACHE/gradle"
mkdir -p "$CACHE" "$GRADLE_USER_HOME" "$ANDROID_HOME"
log(){ printf '\n[VaultOS Vercel] %s\n' "$*"; }
for c in curl tar unzip python3; do command -v "$c" >/dev/null || { echo "Missing command: $c" >&2; exit 1; }; done

# Reuse Java 17 if present, otherwise provision and cache Temurin 17.
JH="${JAVA_HOME:-}"
if [ -z "$JH" ] || [ ! -x "$JH/bin/javac" ]; then
  if command -v javac >/dev/null 2>&1; then JH="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"; fi
fi
JM=""
if [ -n "$JH" ] && [ -x "$JH/bin/javac" ]; then JM="$("$JH/bin/java" -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')"; fi
if [ "$JM" != "17" ]; then
  JH="$CACHE/temurin-17"
  if [ ! -x "$JH/bin/javac" ]; then
    log "Downloading Temurin JDK 17"
    TMP="$CACHE/temurin-17.tmp"; rm -rf "$TMP"; mkdir -p "$TMP"
    curl -fL --retry 3 --connect-timeout 20 "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o "$CACHE/temurin-17.tar.gz"
    tar -xzf "$CACHE/temurin-17.tar.gz" -C "$TMP" --strip-components=1
    test -x "$TMP/bin/javac"; rm -rf "$JH"; mv "$TMP" "$JH"
  fi
fi
export JAVA_HOME="$JH" PATH="$JH/bin:$PATH" ANDROID_HOME ANDROID_SDK_ROOT
log "Using $(java -version 2>&1 | head -1)"

# Provision the pinned Android SDK toolchain.
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "Downloading Android command-line tools"
  TMP="$CACHE/android-cmdline-tools.tmp"; rm -rf "$TMP"; mkdir -p "$TMP" "$ANDROID_HOME/cmdline-tools"
  curl -fL --retry 3 --connect-timeout 20 "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$CACHE/android-command-line-tools.zip"
  unzip -q "$CACHE/android-command-line-tools.zip" -d "$TMP"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"; mv "$TMP/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"; rm -rf "$TMP"
fi
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
log "Accepting Android SDK licences"
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
set -o pipefail
log "Installing Android SDK Platform 35 and Build Tools 35.0.0"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# This repository pins Gradle 8.9 and does not track a Gradle wrapper JAR.
GRADLE_HOME="$CACHE/gradle-8.9"; GRADLE="$GRADLE_HOME/bin/gradle"
if [ ! -x "$GRADLE" ]; then
  log "Downloading Gradle 8.9"
  curl -fL --retry 3 --connect-timeout 20 "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -o "$CACHE/gradle-8.9-bin.zip"
  TMP="$CACHE/gradle-unpack"; rm -rf "$TMP"; mkdir -p "$TMP"
  unzip -q "$CACHE/gradle-8.9-bin.zip" -d "$TMP"; mv "$TMP/gradle-8.9" "$GRADLE_HOME"; rm -rf "$TMP"
fi

EXPECTED_CERT="c5b0c6c49ff6ca525558c2fecfa11dff54fba50f9083482cd869af4759e78b23"
test -s "$ROOT/signing/vaultos-stable.keystore" || { echo "Stable VaultOS signing keystore missing." >&2; exit 1; }
log "Running JVM unit tests, lint and signed release build"
"$GRADLE" --no-daemon --max-workers=2 clean testDebugUnitTest lintDebug assembleRelease
APK_SOURCE="$ROOT/app/build/outputs/apk/release/app-release.apk"
test -s "$APK_SOURCE"
APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
AAPT="$ANDROID_HOME/build-tools/35.0.0/aapt"
SIGNATURE="$("$APKSIGNER" verify --verbose --print-certs "$APK_SOURCE" 2>&1)"
printf '%s\n' "$SIGNATURE"
CERT="$(printf '%s\n' "$SIGNATURE" | sed -nE 's/.*certificate SHA-256 digest: *([0-9a-fA-F:]+).*/\1/p' | head -1 | tr -d ':')"
if [ "$CERT" != "$EXPECTED_CERT" ]; then
  echo "Signing certificate mismatch; refusing to publish an update-incompatible APK." >&2
  echo "Expected $EXPECTED_CERT, got ${CERT:-missing}" >&2; exit 1
fi
BADGING="$("$AAPT" dump badging "$APK_SOURCE")"
APP_ID="$(printf '%s\n' "$BADGING" | sed -nE "s/^package: name='([^']+)'.*/\1/p" | head -1)"
VERSION="$(printf '%s\n' "$BADGING" | sed -nE "s/^package:.*versionName='([^']+)'.*/\1/p" | head -1)"
CODE="$(printf '%s\n' "$BADGING" | sed -nE "s/^package:.*versionCode='([^']+)'.*/\1/p" | head -1)"
TARGET="$(printf '%s\n' "$BADGING" | sed -nE "s/^targetSdkVersion:'([^']+)'.*/\1/p" | head -1)"
test "$APP_ID" = "com.vaultos.app" && test -n "$VERSION" && test -n "$CODE" && test -n "$TARGET"
DIST="$ROOT/dist"; rm -rf "$DIST"; mkdir -p "$DIST"
APK="VaultOS-$VERSION.apk"; ZIP="VaultOS-$VERSION.zip"
cp "$APK_SOURCE" "$DIST/$APK"
python3 - "$DIST/$APK" "$DIST/$ZIP" <<'PYZIP'
import pathlib, sys, zipfile
apk, archive = map(pathlib.Path, sys.argv[1:])
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_STORED) as z: z.write(apk, apk.name)
with zipfile.ZipFile(archive) as z:
    bad = z.testzip()
    if bad or z.namelist() != [apk.name]: raise SystemExit("ZIP verification failed: " + str(bad))
print("ZIP integrity verified:", archive.name)
PYZIP
(cd "$DIST" && sha256sum "$APK" "$ZIP" > SHA256SUMS.txt)
COMMIT="${VERCEL_GIT_COMMIT_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
cat > "$DIST/BUILD-INFO.txt" <<INFO
VaultOS Android release
Version: $VERSION
Version code: $CODE
Application ID: $APP_ID
Target SDK: $TARGET
Source commit: $COMMIT
Build host: Vercel project vaultos
Java: 17
Gradle: 8.9
Android SDK: 35
Build Tools: 35.0.0
Signing certificate SHA-256: $CERT
Tasks: testDebugUnitTest, lintDebug, assembleRelease
INFO
cat > "$DIST/index.html" <<HTML
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#0b0d10"><title>VaultOS $VERSION — Android release</title>
<style>
:root{color-scheme:dark;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;background:#0b0d10;color:#edf2ef}*{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(ellipse at 50% 0%,#17352b 0,transparent 55%),#0b0d10}main{width:min(100%,560px);border:1px solid #263c33;border-radius:24px;padding:28px;background:#111815;box-shadow:0 24px 80px #0008}.mark{width:52px;height:52px;border-radius:16px;display:grid;place-items:center;background:#9ae6c1;color:#0b0d10;font-weight:900;font-size:24px}h1{font-size:30px;letter-spacing:-.04em;margin:22px 0 6px}p{color:#aab9b1;line-height:1.55;margin:8px 0 22px}.version{display:inline-block;border:1px solid #365b49;border-radius:999px;padding:5px 10px;color:#9ae6c1;font-size:12px}a{display:flex;justify-content:space-between;gap:12px;padding:16px 18px;margin-top:12px;border:1px solid #416b56;border-radius:15px;color:#effff5;text-decoration:none;background:#16271f}a.primary{background:#9ae6c1;color:#0b0d10;border-color:#9ae6c1;font-weight:750}small{display:block;color:#8ea397;margin-top:18px;line-height:1.5}footer{border-top:1px solid #263c33;margin-top:22px;padding-top:16px;color:#819188;font-size:12px}
</style></head><body><main><div class="mark">V</div><h1>VaultOS</h1><span class="version">Android release $VERSION · build $CODE</span><p>Your VaultOS Android installer, built and signed by the connected Vercel project.</p><a class="primary" href="/$APK" download><span>Download APK</span><span>↓</span></a><a href="/$ZIP" download><span>Download ZIP package</span><span>↓</span></a><small>Install over your existing VaultOS app. Do not uninstall first, or you may lose app-private data. Android may ask you to allow installs from your browser or file manager.</small><footer>Source: $COMMIT<br>Signing identity verified · SHA-256 checksums included</footer></main></body></html>
HTML
log "Release ready: $APK and $ZIP"
cat "$DIST/SHA256SUMS.txt"
du -h "$DIST"/*
