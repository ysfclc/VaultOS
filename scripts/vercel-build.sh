#!/usr/bin/env bash
set -Eeuo pipefail

# Security hold: the historical VaultOS sideload signing identity was committed
# while the repository was public. Never create or publish another APK until
# the replacement key, API 28+ v3 lineage and in-place update path are verified.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
DIST="$ROOT/dist"
rm -rf "$DIST"
mkdir -p "$DIST"

cat > "$DIST/index.html" <<'HTML'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#0b0d10">
  <meta name="robots" content="noindex, nofollow">
  <title>VaultOS — downloads temporarily paused</title>
  <style>
    :root {
      color-scheme: dark;
      font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
      background: #0b0d10;
      color: #edf2ef;
    }
    * { box-sizing: border-box; }
    body {
      min-height: 100vh;
      margin: 0;
      display: grid;
      place-items: center;
      padding: 24px;
      background: radial-gradient(ellipse at 50% 0%, #17352b 0, transparent 55%), #0b0d10;
    }
    main {
      width: min(100%, 560px);
      padding: 30px;
      border: 1px solid #365044;
      border-radius: 24px;
      background: #111815;
      box-shadow: 0 24px 80px #0008;
    }
    .mark {
      width: 52px;
      height: 52px;
      display: grid;
      place-items: center;
      border-radius: 16px;
      background: #9ae6c1;
      color: #0b0d10;
      font-size: 24px;
      font-weight: 900;
    }
    h1 {
      margin: 24px 0 12px;
      font-size: clamp(25px, 6vw, 32px);
      line-height: 1.15;
      letter-spacing: -.04em;
    }
    p {
      color: #c0cec6;
      font-size: 16px;
      line-height: 1.6;
    }
    .status {
      display: inline-block;
      margin-top: 10px;
      padding: 6px 11px;
      border: 1px solid #527260;
      border-radius: 999px;
      color: #b9efd0;
      font-size: 12px;
      font-weight: 650;
    }
    footer {
      margin-top: 24px;
      padding-top: 16px;
      border-top: 1px solid #293b32;
      color: #9aaba1;
      font-size: 13px;
      line-height: 1.5;
    }
  </style>
</head>
<body>
  <main>
    <div class="mark" aria-hidden="true">V</div>
    <h1>VaultOS downloads are temporarily paused</h1>
    <span class="status">Signing migration in progress</span>
    <p>
      We are securing the application's signing identity and verifying that
      existing installations can receive safe in-place updates.
    </p>
    <p>
      No new APK is being distributed during this process. Keep your existing
      VaultOS installation; do not uninstall it to troubleshoot this pause.
    </p>
    <footer>
      Downloads will return after the replacement signing lineage and
      application-data preservation checks have passed.
    </footer>
  </main>
</body>
</html>
HTML

printf '%s\n' "VaultOS APK publication is intentionally paused; no signing key was read and no APK was built."
