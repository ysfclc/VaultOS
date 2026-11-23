# USB-OTG test guide

This is the first on-device acceptance pass for the USB diagnostics and SAF importer. It validates Android's detection/permission flow and VaultOS's read-only import behavior; it does not prove raw USB filesystem access.

## Before starting
- Use the Redmi Note 12 Pro 5G with Android 14.
- Use a known-good USB-C OTG adapter and a USB flash drive containing a small test folder.
- Put 3–5 files in the folder (including a photo and a text file); note their names and sizes.
- If Android has a system OTG toggle, turn it on. Keep the original files on the drive.

## Detection and permission
1. Connect the flash drive through the OTG adapter.
2. Open VaultOS → More → Settings & permissions → USB/OTG diagnostics.
3. Tap Refresh / Detect.
4. Confirm whether a USB host device appears. Record the device name, VID/PID, and interface count shown.
5. Tap the permission/request action and accept Android's prompt if shown.
6. Refresh and confirm the permission status. Interface details should only appear after permission is granted.

## Safe folder import
1. Tap **Browse / import USB folder**.
2. In Android's file picker, navigate to the USB drive and select the test folder (confirm folder access).
3. Wait for the import summary.
4. Open VaultOS Library/Files and confirm the expected files appear and open.
5. Compare names and file contents/sizes with the originals.
6. Re-open the USB drive in Android Files and confirm every source file still exists and is unchanged. The importer is intended to be read-only.

## Record results
- USB device detected by diagnostics: Yes / No
- Permission prompt appeared and was granted: Yes / No / Not shown
- Interface descriptors appeared after permission: Yes / No / N/A
- USB folder visible in Android picker: Yes / No
- Imported count vs expected:
- Text/photo opened correctly: Yes / No
- Original USB files unchanged: Yes / No
- Any error message (copy verbatim):

## What outcomes mean
- **Device detected + picker works + imports verify:** USB-OTG import path passes this basic test.
- **No diagnostics device, but USB folder appears in picker:** SAF import can still work; Android may expose mounted storage without usable host-device diagnostics.
- **Device detected, but no USB folder in picker:** diagnostics pass partially; filesystem access/import is not accepted yet. Capture the exact Android picker behavior.
- **No detection and no picker volume:** check the OTG adapter, drive, Android OTG setting, and drive compatibility before treating it as an app defect.

Import is deliberately bounded and read-only: no source delete, move, rename, format, or write operations. Current implementation limits traversal to 2,000 files and 16 directory levels. Physical acceptance is still pending.
