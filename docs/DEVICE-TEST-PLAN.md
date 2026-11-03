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


## Phase 2 — file manager hardening

12. **Recycle Bin:** archive a test file, move it to Recycle Bin from the library detail and Vault archive browser, verify it disappears from normal library/search counts, restore it, and confirm the file and contents return to the original archive path.
13. **Permanent deletion:** move a second archived file to Recycle Bin, permanently delete it, and verify both the stored payload and item record are gone. Confirm device-storage originals are never sent to the VaultOS Recycle Bin.
14. **Multi-select:** select several files and folders in phone storage and Vault archive; verify selection counts, copy/move to a different folder, archive selected device files, create ZIP, and batch delete/Recycle Bin behavior.
15. **ZIP creation:** create ZIPs from multiple files and a nested folder; verify names, nested paths, file bytes, and collision-safe naming.
16. **ZIP extraction:** extract a normal ZIP and verify file contents. Try an archive containing ../escape.txt; verify extraction is rejected and no file appears outside the extraction folder. Also exercise the 2 GiB expanded-size and 20,000-entry guards with generated test archives where practical.
17. **Progress and cancellation:** copy, move, archive, extract and delete a large test directory. Confirm progress changes while working, Cancel stops cancellable work, temporary .partial outputs are removed, and a cancelled move never loses the original source.
18. **Image thumbnails/previews:** import several large JPEG/PNG images and open the library; verify thumbnails render without full-resolution decoding, and item detail shows the image preview.
19. **Backup recycle-bin state:** back up a vault containing both active and trashed entries, restore to a test install, verify trash entries remain trashed, and restore one from the Recycle Bin.

## Phase 2 checks

12. **Recycle Bin:** archive a test file, move it to Recycle Bin, confirm it disappears from Library/search/size totals, restore it, and verify bytes and original path. Repeat with an occupied original path and confirm a collision-safe restored name.
13. **Permanent deletion:** trash a disposable archive file, permanently delete it, and confirm both payload and catalogue/search entry are gone. Confirm a device-storage original is not affected by the archive Recycle Bin.
14. **Multi-select:** select several device files and several Vault files; batch copy, move, archive, create ZIP, and delete/recycle. Verify all resulting bytes and that sources remain until each operation commits.
15. **ZIP safety:** create a ZIP from nested files, extract it, compare file bytes, then try a ZIP containing `../` and absolute-path entries; confirm extraction is rejected and no outside file is created.
16. **Progress/cancel:** run a large copy, move, archive, ZIP and extraction; observe progress and cancel each. Confirm temporary `.partial` outputs are cleaned and original data remains intact.
17. **Previews:** inspect image thumbnails and image/PDF/video/audio previews; scroll a large folder and confirm the UI remains responsive and thumbnails do not exhaust memory.
18. **Trash backup:** back up a vault containing a trashed item, restore to a clean app install, and confirm the item remains in Recycle Bin and can be restored to its original relative path.
