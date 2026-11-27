# USB-C OTG and external-drive integration plan

**Status:** Planned only — documentation/roadmap work; no application code or USB behavior is implemented by this plan.  
**Target device:** Xiaomi Redmi Note 12 Pro 5G.  
**Product:** VaultOS.  
**Created:** 2026-11-16.

## 1. Why this belongs in VaultOS

A USB-C flash drive that works on a PC but produces no visible response when connected to the target phone is a useful real-world test case for external-storage support. VaultOS already provides a local file catalogue, a private archive, SHA-256 integrity checks, duplicate detection, file browsing, and user-directed imports. USB storage should therefore become another controlled source for the existing VaultOS import/library flow, not a separate utility app.

The current observation is not yet diagnosed. “Nothing happens” does not establish whether the phone fails to enter USB host mode, whether the USB device is enumerated, whether Android rejects its interface, or whether Android sees the device but cannot mount its partition/file system.

## 2. Product goal

When the phone and attached device support USB host/OTG operation, VaultOS should help the user:

1. Understand whether Android detects the attached USB device.
2. Identify the likely failure layer when no external volume appears.
3. Browse an external volume when Android has mounted it or exposes it through a document provider.
4. Explicitly import selected files into the existing VaultOS archive and catalogue.
5. Preserve the original external-drive contents and verify imported bytes.

This capability is intended to improve removable-storage diagnostics and import reliability. It is not a promise that an application can enable OTG on hardware that does not establish a USB host connection.

## 3. Platform and feasibility boundaries

### Layer A — physical USB-C / host-mode negotiation

Before Android can expose a USB device, the phone, connector, cable/adapter, USB-C role negotiation, power budget, and device must establish a working host connection. An ordinary Android application cannot force the USB controller into host mode, repair a damaged port, change USB-C CC wiring, or supply missing electrical power.

If the device is absent from Android's USB device enumeration, an app may report that no USB device is currently visible to Android; it must not claim to know the exact electrical cause.

### Layer B — Android USB device detection

A diagnostic screen can use Android's USB Host APIs, including `UsbManager.deviceList`, attach/detach events, USB device/interface descriptors, and the user-mediated USB permission flow.

The diagnostic should distinguish at least:

- No USB device enumerated by Android.
- A USB device enumerated, but no supported storage interface identified.
- A storage-like USB interface detected, awaiting user permission or further inspection.
- Android exposes a mounted/removable volume or document-provider location.
- The device is present but access or mounting failed.

The app should show only information useful for diagnosis. Do not persist USB serial numbers or other device identifiers by default. USB permission is per-device/user-mediated; detection does not itself grant access to the interface.

### Layer C — use Android-mounted storage first

When Android mounts the drive, VaultOS should prefer supported system volume/document-picker access (for example, the Storage Access Framework) rather than bypassing Android's storage stack.

The user chooses a file or folder. VaultOS imports the selected content through the existing repository/import pipeline, using streaming I/O, SHA-256 verification and existing duplicate handling. The source drive remains unchanged. The app must not assume that every removable volume has a stable filesystem path; URI-based access is the preferred contract.

### Layer D — optional raw USB mass-storage fallback (research gate only)

If Android enumerates a USB Mass Storage device but does not expose a mounted volume, a future feasibility spike may investigate direct read-only access through `UsbDeviceConnection`, claimed USB interfaces/endpoints, USB Mass Storage Bulk-Only Transport, SCSI block-read commands, partition discovery, and a narrowly selected filesystem reader.

This is a separate high-risk workstream, not part of the initial diagnostic feature. It must not begin until the device/drive combination is reproducible and the mounted-volume route is proven unavailable. Support would need to be explicitly limited to tested transport, partition and filesystem combinations. UASP, unusual bridges, encrypted volumes, unsupported filesystems, damaged media, and power-sensitive devices may remain unsupported.

No raw block writes, filesystem repair, formatting, repartitioning, or silent modification of a USB drive is approved. Do not bundle an arbitrary kernel driver or claim universal NTFS/exFAT/FAT support.

## 4. Intended user experience

Add an **External USB** entry within the existing Files experience, not a separate top-level product.

Suggested states:

- **No device detected:** explain that Android currently exposes no USB device; offer a short physical checklist (direct connection/known-good OTG adapter, another USB device, power requirements, phone OTG setting if present).
- **Device detected:** display its basic class/vendor/product descriptors, connection state, and a clear Inspect action that requests USB permission only when required.
- **System volume available:** offer Browse / Choose files using Android's document UI.
- **Detected but not mounted:** explain that detection succeeded but Android has not exposed a browsable volume; collect a local diagnostic report and mark any custom reader as future/unsupported until separately implemented.
- **Import in progress/completed:** reuse VaultOS progress, cancellation, hash verification and duplicate reporting. Cancellation must not modify the source drive or leave a partial catalogue item.

Do not show a false “drive connected” state merely because a cable is inserted; base status on Android's observable USB and storage APIs.

## 5. Proposed architecture (subject to implementation review)

- `UsbDeviceMonitor`: small Android boundary around `UsbManager`, attach/detach events, current device enumeration and permission state. It must not own catalogue or payload storage.
- `UsbDiagnosticReport`: a local, redacted summary of observable device/interface states and app/Android versions. Never infer electrical facts unavailable to the API.
- `ExternalVolumePicker`: system-mediated document/volume access for mounted drives. Keep URI grants scoped to the user's selection and explain persisted access if VaultOS later needs it.
- Existing `VaultRepository` / import pipeline: stream selected source bytes into the private archive, hash, deduplicate and add catalogue entries. Keep source URI and import outcome traceable without assuming raw filesystem paths.
- Optional later `UsbMassStorageReader`: isolated, read-only prototype with transport/filesystem tests. It must not be mixed into the normal import implementation before its own security and compatibility review.

The architecture remains local-first. Do not add INTERNET, accounts, remote diagnostics, analytics, cloud processing, or an external dependency that sends device/file metadata off-device.

## 6. Delivery phases and acceptance gates

### USB-1 — Reproduce and classify the current device case

- Record phone model, Android/API level, MIUI/HyperOS build, VaultOS version, flash-drive make/model/capacity, connector type, and any adapter/hub.
- Check whether the phone exposes an OTG setting on the installed software; do not assume the toggle exists on every build.
- Compare direct USB-C connection, a known-good OTG adapter where appropriate, a low-power USB device such as a mouse, another flash drive, and (where available) a powered hub.
- Preserve the drive contents. Do not format or repartition it as a diagnostic step.
- Record whether Android shows a USB notification, a volume in system file browsing, or a USB device in a diagnostic app.

**Exit gate:** a reproducible baseline identifies what Android exposes; no hardware cause is guessed from “nothing happens”.

### USB-2 — Build USB diagnostics

- Enumerate currently visible USB devices and interfaces.
- Handle attach/detach and permission denial/grant paths.
- Present the detection states defined in Layer B.
- Add tests for empty device lists, multiple devices, detach during inspection, permission denial, unsupported interfaces and malformed descriptor data.
- Redact serial numbers and avoid persistent device tracking.

**Exit gate:** the app accurately reports observable Android USB state without claiming that it can fix absent host-mode negotiation.

### USB-3 — Import from Android-mounted external volumes

- Add an External USB entry to Files.
- Use system document/volume access when Android exposes the drive.
- Import selected files through the existing VaultRepository pipeline.
- Preserve filenames and useful metadata; verify byte counts and SHA-256; reuse duplicate detection.
- Handle permission revocation, drive removal, read errors, cancellation, name collisions and large files.
- Verify that import never deletes, renames, formats or writes to the source drive.

**Exit gate:** selected test files are imported intact and the originals on the USB drive remain unchanged.

### USB-4 — Raw read-only USB Mass Storage feasibility spike (conditional)

- Proceed only if USB-1/USB-2 show that Android enumerates the target storage device but does not expose a usable mounted volume.
- Confirm interface class, endpoint layout, transport protocol and whether the device can be safely claimed by an app.
- Prototype read-only device identification and bounded block reads against disposable test media.
- Evaluate partition tables and a deliberately narrow filesystem compatibility target.
- Threat-model malformed media, short reads, unplug during I/O, oversized metadata, integer overflow, malicious filesystem structures and device permission revocation.
- Produce an ADR and stop/go recommendation before integrating any reader into the app.

**Exit gate:** independent review and reproducible tests establish a safe, limited read-only capability. Otherwise document the device as unsupported and retain the system-mounted import path.

### USB-5 — Redmi physical-device acceptance

- Run the USB section in `docs/DEVICE-TEST-PLAN.md` on the actual Redmi Note 12 Pro 5G.
- Record Android/API and MIUI/HyperOS versions for each run.
- Test at least one known-good USB-C storage device and one low-power USB peripheral.
- Exercise attach/detach, permission, mounted-volume browsing, large-file import, cancellation and unplug failure.
- Re-check source-drive hashes or file contents after imports.
- Keep emulator/CI results separate from physical USB/OTG acceptance.

## 7. Security and data-safety requirements

- Read-only access to the source drive is the product default.
- No format, repair, repartition, delete, move, rename, or write action is included.
- Require clear user action before opening a USB device or importing its files.
- Request only the USB permission and document URI access needed for the current action.
- Treat every USB descriptor, partition table, filesystem structure, filename and file payload as untrusted input.
- Bound buffers, metadata sizes, recursion, entry counts and total import sizes; make operations cancellable.
- On detach or I/O failure, stop safely and clean up VaultOS temporary files; never imply an incomplete file was imported successfully.
- Continue to apply VaultOS SHA-256 checks, duplicate handling and local-only privacy rules.
- Do not silently auto-import all USB contents on attachment.

## 8. Explicit non-goals

- Enabling OTG or USB host mode when the phone/hardware/connection does not expose it.
- Fixing a damaged connector, non-compliant cable, insufficient power, or USB-C role-negotiation failure.
- A universal USB driver, universal filesystem reader, or guaranteed support for every USB flash drive.
- USB formatting, partitioning, repair, write access, or data recovery from damaged media.
- Replacing Android's mounted-volume/document-provider stack when it already works.
- Background or automatic USB harvesting without user action.

## 9. Current status

No USB-related application code, manifest filter, permission request, UI, raw reader, or device test has been added by this planning increment. The existing case is still undiagnosed. The next practical step is USB-1 reproduction, followed by a diagnostic-only prototype; raw mass-storage reading remains conditional and should not be treated as committed scope.
