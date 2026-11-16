# VaultOS keyboard UX redesign

_Status: source audit and design direction; implementation and Redmi acceptance not yet complete._

## Purpose

The keyboard should feel like a dependable everyday Android keyboard first, with VaultOS capture and retrieval capabilities available as a coherent second layer. It must be fast, legible, comfortable and predictable. Visual polish must not add work to the per-keystroke path or reduce hit targets.

This is a focused evolution of the existing native IME, not a rewrite and not a Gboard-parity claim.

## Source audit

Reviewed on `fix/keyboard-typing-and-ux-0.13.1`:

- The top bar contains six peers: Type, emoji, Clips, Vault, language and Tools. Each is a small text control in a 46 dp row; there is no strong grouping between typing, capture and settings.
- The candidate strip is 40 dp high and presents up to three equally weighted cells. The current word, correction/completion and next-word suggestion states are inferred from behavior and subtle styling rather than clearly communicated.
- The keyboard uses fixed row geometry: 50 dp default letter keys (44–60 dp setting), 6 dp vertical row allowance, 42 dp toolbar controls and a 40 dp candidate strip. A number row is optional and off by default.
- The normal layout always reserves bottom-row width for comma, cursor-left, space, cursor-right, period and enter. Cursor movement is also available by sliding on the spacebar.
- Polish and Turkish diacritics are mainly exposed through long-press alternates rather than dedicated visible keys in the base layout.
- Many everyday settings are in a scrolling Tools popup: number row, key size, theme, one-hand mode, key preview, haptics and sounds.
- Mode changes rebuild the input view. Clips and Vault modes retain the typing keys while showing a small search/results area.
- Key preview popups are off by default to avoid creating a popup window on every key press. Preserve this performance-conscious default unless device testing demonstrates otherwise.

## Experience goals

1. Typing remains the dominant, uncluttered default state.
2. Every visible control has a clear purpose and an adequate touch target.
3. Candidate types and their actions are understandable without guessing.
4. EN / PL / TR input feels intentional, including casing, punctuation and diacritics.
5. VaultOS capture features are accessible in one or two deliberate actions, without crowding the typing surface.
6. The keyboard adapts to short screens, landscape, enlarged fonts and one-handed use.
7. Motion, haptics and pressed states communicate touch without adding typing latency.
8. Accessibility semantics are complete enough for TalkBack and switch/navigation users.

## Proposed design

### 1. Rework the top action bar

- Establish a clear primary typing state and a compact set of frequent actions.
- Group capture/retrieval actions separately from language and settings.
- Move less frequent actions into a deliberate overflow panel rather than squeezing six labels into one row.
- Prefer concise icons with meaningful content descriptions and visible labels where ambiguity would otherwise result.
- Keep language selection directly reachable; do not make cycling through languages the only discoverable path.
- Show active mode clearly and consistently.

Do not simply replace every label with an icon. Recognition and accessibility matter more than visual minimalism.

### 2. Give the candidate strip semantic hierarchy

- Preserve the typed word as a stable, predictable action.
- Distinguish correction, completion and next-word prediction states visually and semantically.
- Make the primary candidate easier to identify without making alternatives look disabled.
- Provide a restrained fallback state when there are no useful candidates; avoid presenting the language name as though it were a suggestion.
- Keep candidate views persistent and update them in place.
- Do not add labels, animations or ranking work that causes layout shifts or delays during active typing.

### 3. Improve typing comfort and key geometry

- Measure actual available IME height and width before changing key sizes.
- Test default, short-height, landscape, large-font and one-handed layouts.
- Review key spacing and edge hit areas for missed and accidental presses.
- Keep the spacebar visually dominant and easy to hit.
- Reconsider whether both cursor-arrow keys need permanent space in the bottom row when spacebar cursor movement exists. Any removal must preserve an obvious cursor-control route.
- Make Shift, Caps Lock, Backspace repeat and editor-specific Enter states visually unambiguous.
- Avoid shrinking letter keys to fit additional features.

### 4. Make EN / PL / TR feel native

- Audit direct access to Polish and Turkish characters, including locale-correct uppercase/lowercase behavior.
- Decide whether each locale needs a dedicated layout, visible alternate-key affordance, or a carefully designed long-press interaction. Test actual typing frequency and accuracy before choosing.
- Ensure punctuation, apostrophes, smart spacing and sentence capitalization behave appropriately for each language.
- Keep language-specific personal typing models isolated.
- Never silently switch the active language based on uncertain detection.

### 5. Make VaultOS features feel integrated

- Clips should make search, recent items, pinning and full-text insertion obvious.
- Vault should communicate that it searches saved content and inserts complete Text/Code entries; other file types should have a clear open-in-Vault action.
- Keep these modes visually related to the keyboard rather than presenting unrelated mini-app panels.
- Preserve the active editor text and keyboard state safely when entering and leaving modes.
- Do not turn the typing surface into a dashboard.

### 6. Add discoverability and personalisation

- Offer a small number of understandable presets: balanced, compact and comfortable, rather than exposing many unrelated numeric settings.
- Keep one-hand mode, key height, number row, theme, haptics and key preview accessible from a clear settings surface.
- Add subtle first-use hints for long-press diacritics, spacebar cursor movement and candidate actions. Hints must be dismissible and must not reappear on every session.
- Make private word learning and its opt-in status explicit; keep clear/reset controls available.
- Do not add swipe typing, cloud prediction, remote text processing or heavyweight models as part of this UX increment.

### 7. Accessibility and feedback

- Give every toolbar and candidate action a meaningful content description.
- Ensure TalkBack can identify candidate state and action, not merely read the candidate word.
- Respect system font/display scaling and verify contrast in both themes.
- Keep haptic and sound feedback optional and independently configurable.
- Press feedback should remain immediate and should not depend on popup-window creation.

## Delivery order

### UX-1 — baseline and interaction audit
- [ ] Record current behavior and screenshots on Redmi Note 12 Pro 5G in EN, PL and TR.
- [ ] Measure available IME height in portrait and landscape, with and without number row.
- [ ] Record missed taps, accidental presses, key repeat behavior and candidate latency during real typing.
- [ ] Review current screen-reader labels and enlarged-font behavior.

### UX-2 — information architecture
- [ ] Redesign the action bar hierarchy and overflow.
- [ ] Make language choice explicit and easy to reach.
- [ ] Keep Clips and Vault actions accessible without crowding the typing layout.
- [ ] Define a consistent mode-transition and return-to-typing behavior.

### UX-3 — candidate strip
- [ ] Define explicit correction, completion, current-word and next-word visual states.
- [ ] Improve candidate touch affordance and accessibility semantics.
- [ ] Keep the strip stable under rapid typing and asynchronous result arrival.

### UX-4 — adaptive key layout
- [ ] Validate key geometry and bottom-row priorities on the target device.
- [ ] Add adaptive compact/comfortable behavior based on available height.
- [ ] Review EN / PL / TR diacritic access and locale-specific punctuation.
- [ ] Preserve cursor movement, backspace repeat, Shift and editor action behavior.

### UX-5 — integrated polish and acceptance
- [ ] Apply a coherent visual system across typing, emoji, Clips, Vault and overflow.
- [ ] Add restrained first-use discoverability for non-obvious gestures.
- [ ] Verify TalkBack, contrast, large font, landscape, one-hand mode and both themes.
- [ ] Re-run typing-path performance checks and confirm no new per-keystroke disk, popup or expensive layout work.
- [ ] Complete physical-device acceptance on Redmi Note 12 Pro 5G before calling the redesign done.

## Acceptance principles

- No UI change is accepted solely because it looks better in source or an emulator screenshot.
- Do not claim typing-speed improvement without repeatable physical-device measurements.
- Do not remove a feature or shortcut without documenting its replacement and testing it.
- Keep data preservation, local-first privacy and installed-user update continuity as higher priorities than keyboard cosmetics.


## First implementation pass — action bar and candidate affordance

Implemented on the 0.13.1 branch:
- The default typing state no longer reserves a toolbar slot for a redundant Type tab. Type appears while in Clips, Vault or emoji mode so returning to typing remains explicit.
- The toolbar's Tools label is now a compact overflow glyph with a meaningful accessibility content description. Clips, Vault, emoji and language remain directly reachable.
- Candidate cells now expose semantic accessibility descriptions for current word, spelling correction, word completion and next-word prediction.
- Non-primary candidate cells have a restrained outlined surface, while the current word retains the existing accent pill.

Still open: physical-device layout review, TalkBack validation, adaptive geometry, locale-specific key access and the remaining KBD-3 interaction work. This is not a claim of completed keyboard UX or improved typing speed.

## Typing interaction pass — 2026-11-16

The user reports that typing speed and overall feel remain substantially below Gboard. The source audit found that every normal letter was being sent with `commitText()`; the editor therefore did not receive a live composing word.

A first interaction correction is now committed on the 0.13.1 branch:
- Use `setComposingText()` for an active word where supported by the editor.
- Finish composition at punctuation, space, Enter, cursor movement, language/mode changes and paste boundaries.
- Keep backspace grapheme-aware while updating the composing word.
- Preserve the same behavior for Polish/Turkish long-press alternate letters.
- Fall back to committed character input when an editor rejects composition.

This is a source-level improvement hypothesis only. It does not provide glide typing, Gboard's neural prediction, or a measured speed result.

## Second implementation pass — touch accuracy and input responsiveness

Committed on the 0.13.1 branch:
- Letter taps are resolved against the nearest letter-key centre at release, correcting edge-of-key slips without changing the visual key dimensions. Modifier and punctuation keys keep their dedicated hit areas.
- Long-press alternate-character selection takes precedence over nearest-key rerouting, so a held key cannot accidentally type a neighboring base letter.
- Horizontal and vertical gaps between key surfaces were reduced from 4/6 dp to 2/4 dp, respectively, to reduce dead zones without enlarging the keyboard.
- Candidate-strip rendering is coalesced to one short UI-frame update. Text is still sent to the editor immediately; repeated rapid key taps no longer trigger a full candidate-strip rebuild for every event.
- Added pure JVM tests for nearest-key selection, outer-edge tolerance, out-of-range touches and empty rows.

Still required: CI execution, physical Redmi tests for missed taps and perceived latency, plus candidate quality work. No claim of Gboard parity or measured speed gain is made.
