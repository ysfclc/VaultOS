# Redmi Note 12 Pro 5G device test plan

Run on the actual phone after installing the debug APK. Record Android/MIUI version and results.

1. **Cold start:** launch app, grant all-files access, start initial scan, verify existing files appear as Indexed and have no archive copy.
2. **New download:** download a PDF and an APK to public Downloads, wait for the download event or scan, verify each appears once and has an internal archive copy; confirm the source files remain in Downloads.
3. **New photo:** take a photo after the baseline scan, verify it is indexed and archived after a scan.
4. **Share import:** share a large PDF, photo and multi-file selection to VaultOS; verify names, sizes, categories and SHA-256 dedupe.
5. **Long text:** share or capture a text payload over 100,000 characters; verify stored size, full file content, search for a term near the end, and copy the full content back.
6. **Keyboard:** enable/select VaultOS IME; enable auto capture; copy ordinary text from another app; verify it is captured. Copy a clip marked sensitive and confirm it is skipped. Switch back to Gboard and verify VaultOS does not claim to monitor its clipboard.
7. **Paste saved:** focus a large text field, open VaultOS keyboard, tap Paste saved; verify full saved text is inserted without using Gboard clipboard history.
8. **File operations:** create folder, copy, cut/move, rename, share, open and permanently delete a test file; verify no source is changed by archive-only actions.
9. **DocumentsProvider:** from another app's Open document screen, browse VaultOS and open a stored file.
10. **Backup and restore:** select a USB or SAF-backed folder, run backup, confirm payloads and manifest; clear app data only after safely exporting a test vault, reinstall, restore, and verify the library.
11. **Background behavior:** enable Xiaomi autostart and no-restrictions battery mode; download while the app is closed; measure catch-up time. Do not expect an exact 15-minute schedule.
