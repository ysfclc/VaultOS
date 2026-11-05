# Redmi Note 12 Pro 5G device test plan

Run on the actual phone after installing the VaultOS-release-APK artifact from GitHub Actions. It is signed with the preserved update key. Record Android/API and MIUI/HyperOS versions and results.

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


## Phase 5 acceptance matrix — still requires physical-device execution

Record the exact MIUI/HyperOS build, Android API level, font/display scaling and test result for every row. Do not mark a row passed based on emulator-only behavior.

### Clipboard compatibility

- **Chrome:** copy ordinary page text and a URL; confirm one capture per distinct clip and no duplicate record on repeated callbacks.
- **ChatGPT:** copy ordinary prompt and response text; verify full text is preserved and the app remains usable while VaultOS is the selected IME.
- **WhatsApp:** copy a normal message and a multi-line message; verify text capture. Do not use private conversations as test data.
- **Notes:** copy multi-line text and a long note; verify line breaks and the complete end of the content.
- **Password fields:** focus password, visible-password, web-password and numeric-password inputs; copy test strings and verify the clipboard listener does not archive them while those fields are active.
- **IME boundary:** switch to Gboard and copy ordinary text; verify VaultOS does not claim to monitor or capture Gboard's clipboard. Switch back to VaultOS only when deliberately testing its IME capture feature.
- **Sensitive clip:** where the source app marks clipboard data sensitive, verify VaultOS skips it.

### Android permission compatibility

Run on Android 13 (API 33), Android 14 (API 34) and Android 15 (API 35):

- Fresh install: deny all-files access, open every tab, and verify the app remains usable without crashing or claiming device-wide access.
- Grant all-files access from the app's settings shortcut; return to VaultOS and verify the permission state refreshes and storage browsing/scanning works.
- Revoke all-files access while VaultOS is installed; reopen the app and verify restricted screens show the permission action rather than failing.
- Confirm app-info and battery-settings shortcuts open the correct Android settings surfaces on each version.
- On Android 14/15, verify any partial photo/media access state does not get mistaken for all-files access.

### Xiaomi, accessibility and layout

- Follow the Xiaomi background settings guide: Autostart (if present), Battery saver / No restrictions, and optional Recents lock. Test a download while the app is closed and record actual catch-up behavior; do not expect exact scheduling.
- Increase system font size and display size; visit every Settings card and confirm buttons and descriptions remain reachable without horizontal clipping.
- Test portrait and landscape on Home, Files, Collections, Apps and Settings; confirm content remains scrollable and no controls disappear behind system bars.
- With TalkBack enabled, navigate the top actions, bottom navigation, Settings shortcuts, switches and backup controls; confirm each actionable control has a useful spoken label and logical focus order.


## Phase 6 filesystem safety regression

These cases are covered by local JVM tests and should also be spot-checked on-device if the selected storage provider exposes symbolic links:

- Select a directory containing a symbolic link to a file outside it; copy must refuse the operation and leave the external file unchanged.
- Select a directory symlink pointing to an external directory; delete must refuse the operation and leave all external contents unchanged.
- Select a symbolic link as a ZIP source; archive creation must refuse it and leave the target untouched.


## Release 1.0.0 acceptance gate

Do not mark 1.0.0 device-verified until every required group below has an observed result on the actual Redmi Note 12 Pro 5G.

1. **Update install and data retention:** install the 0.6.0 release artifact over the existing 0.5.0 installation without uninstalling. Confirm the package updates in place and existing vault items, tags, collections, saved searches and keyboard settings remain.
2. **Backup compatibility:** create a fresh external backup; restore it into a clean test installation; verify archived payload hashes, index-only references, recycle-bin entries, tags, collections, metadata and saved searches. Also restore a known v1 backup and a v2 backup if available.
3. **Clipboard and IME:** complete steps 6–7 above in Chrome, ChatGPT, WhatsApp and Notes; test ordinary, long, sensitive and password-field clipboard cases; confirm the Gboard switch works.
4. **Android permissions:** complete the permission matrix on the device's installed Android version, including deny/grant/revoke and returning from Settings.
5. **Xiaomi background behavior:** test with default battery settings and again after following the optional Autostart / No restrictions guide. Record observed catch-up time; do not infer an exact schedule.
6. **Accessibility and layout:** test TalkBack, enlarged font/display size, portrait, landscape, gesture navigation and all Settings controls.
7. **Performance and stability:** cold start, large initial scan, incremental scan, large-file import, 100,000+ character text, thumbnail scrolling, batch operations, backup/restore and repeated app relaunch. Record crashes, ANRs, lost items and unusual battery use.

Record these device facts with the results: model, Android version/API, MIUI/HyperOS version, VaultOS version, free storage before testing, and whether the test used internal storage, SD/USB, or another SAF provider. Use synthetic/private-safe test content, not real passwords or private conversations.

## Clipboard history acceptance — 0.7.0

Run these tests on the Redmi Note 12 Pro 5G after selecting VaultOS Capture Keyboard. Do not infer device acceptance from CI alone.

1. **App coverage:** copy ordinary text from Chrome, ChatGPT, WhatsApp and Notes. Confirm each appears once in Clipboard history with a timestamp and foreground editor package context where available.
2. **Repeated identical copy:** copy the exact same text twice at different times. Confirm two history events exist, while the library does not need to store two identical payload files.
3. **Long text:** copy a payload larger than 100,000 characters. Confirm character/byte counts, search for a phrase located well beyond the 420-character preview, copy full text from history, and compare the pasted result with the source.
4. **Latest paste:** copy text A, then text B, then copy A again. Use the keyboard's Paste saved action and confirm A is inserted, not B.
5. **Multi-item clip:** capture a clipboard payload containing multiple text/URI items. Confirm each accessible item is recorded; an inaccessible URI must not prevent subsequent entries from being captured.
6. **Sensitive data:** test a password editor and an Android 13+ clip marked sensitive. Confirm automatic capture skips them. Also confirm a normal text field still captures.
7. **Keyboard boundary:** switch to Gboard and copy text. Confirm VaultOS does not claim to capture it automatically. Use manual Capture inside VaultOS and confirm the event is saved.
8. **History actions:** search, pin, unpin, copy full text, delete one event and clear unpinned history. Confirm pinned events remain and deleting/clearing history does not delete the corresponding library payload.
9. **Persistence:** force-stop VaultOS and reboot the phone. Confirm history remains available after reopening.
10. **Integrity failure:** in a test-only build, alter or remove an archived text payload. Confirm readback/paste refuses corrupted or incomplete content rather than silently pasting it.
11. **Lifecycle and background:** with VaultOS selected, lock/unlock, hide/show the keyboard, switch apps and repeat clipboard capture. Record any vendor-specific delays or missed events.

**Expected Android limitation:** automatic clipboard event capture is available only while VaultOS is the selected input method. Gboard-active copy events must not appear unless the user manually captures them from VaultOS.
## Keyboard usability acceptance — 0.8.0

1. **Language selector:** cycle EN → PL → TR → EN from the keyboard toolbar. Confirm the visible key row changes and the preference remains selected after hiding/reopening the keyboard and restarting VaultOS.
2. **Settings language:** set Polish in Settings, open a text field and verify Polish keys; repeat for Turkish and English. Confirm the toolbar selector and Settings remain synchronized.
3. **Polish typing:** type `zażółć gęślą jaźń` using the Polish key row; verify every character and uppercase behavior.
4. **Turkish typing:** type `İ ı ş Ş ğ Ğ ü Ü ö Ö ç Ç` and verify dotless/dotted I and Turkish-specific letters.
5. **Symbols and editing:** in every language, switch to symbols and back, type punctuation, use backspace on selected text and ordinary text, and test Enter in a multiline field and an editor with a Search/Done action.
6. **Inline Clips mode:** enter Clips mode while a text field is focused. Search a term that appears beyond the 420-character preview, select the matching result and verify the complete original text is inserted into that field.
7. **Pinning:** pin and unpin a clipboard item directly from Clips mode; confirm pinned results move above unpinned results and persist after reopening.
8. **Return to typing:** leave Clips mode with Keys and verify the active text field retains its cursor and selection.
9. **Latest paste:** verify Paste inserts the most recent clipboard event, including a repeated copy of an older identical payload.
10. **Input safety:** repeat keyboard tests in normal text, email, URL, numeric, password and no-suggestions fields. Confirm password fields never trigger automatic clipboard capture.
11. **Small screen and large font:** test keyboard toolbar, language selector, Clips results and letter rows at default and enlarged system font/display settings; no button should become unreachable.
12. **Lifecycle:** hide/show keyboard, switch applications, rotate device, lock/unlock, force-stop/reopen VaultOS and reboot; verify selected language and clipboard history persist and the keyboard recovers cleanly.