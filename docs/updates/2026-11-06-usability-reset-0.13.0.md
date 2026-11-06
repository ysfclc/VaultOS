# VaultOS usability reset — 2026-11-06

## Why this phase exists

Previous releases repeatedly added capabilities while the user's daily experience remained difficult to use. CI success was mistaken for usability acceptance even though physical touch behavior and keyboard relevance had not been verified on the target Redmi device.

This phase narrows the work to the app's everyday entry/navigation flow and the optional keyboard's daily controls and feedback. It is not a declaration that VaultOS is now device-accepted.

## Changes

### App navigation and first-run setup

- Replaced six permanent bottom destinations with Home, Library, Files, Organize and More.
- Moved Installed apps and Settings & permissions into a dedicated More hub.
- Added a Home setup card when all-files access has not been granted. It opens the correct Android special-access settings screen and explains that initial indexing does not move existing source files.

### Keyboard controls and touch response

- Renamed ambiguous Cmd and dots controls to Vault and Tools.
- Replaced the long ungrouped More popup with a grouped, scrollable Tools panel: Capture & Vault, Personal Typing, Typing Behaviour, Keyboard Look & Feel and Android.
- Popup dimensions are constrained by the available display width/height; rows are at least 48 dp.
- Increased key-down scale/depth response and held-key preview size; added press/release feedback for the cursor/spacebar key.
- Enlarged Clips rows and their Paste/Open and pin targets, and aligned their colors with the selected keyboard theme.

### Suggestions

- Expanded the offline EN / PL / TR word vocabulary and context examples.
- Next-word candidates now use the last word before the actual cursor, reading no more than 80 characters transiently from a safe text editor. This accommodates cursor movement and paste. The surrounding text is not saved by this lookup.
- Selecting a context candidate learns the pair against the context word actually used for that candidate, not stale in-memory typing state.
- Added regression tests for added EN / PL / TR prefix and phrase examples.

## Compatibility and data safety

- Application ID remains `com.vaultos.app`.
- Version moves to 0.13.0 / versionCode 14.
- Room schema is unchanged. No database migration is required.
- Existing archive data, clipboard history, settings and keyboard preferences are preserved by normal in-place updates.
- The existing signing identity is retained. No signing-key rotation or private-key cleanup is part of this UX phase.
- Offline/local-first architecture and sensitive-field suggestion exclusions remain unchanged.

## Verification

- Changes are on branch `ux-reset/0.13.0`.
- The build workflow is triggered by application-source changes and runs unit tests, lint, signed debug/release APK assembly and signing verification.
- Hosted CI run: [42259973306](https://github.com/ysfclc/VaultOS/actions/runs/42259973306).
- CI is not a substitute for physical device testing. The new 0.13.0 Redmi acceptance matrix is in `docs/DEVICE-TEST-PLAN.md`; touch accuracy, large-font layout, TalkBack, cursor-context usefulness and real app navigation must be observed before accepting the product as usable.
