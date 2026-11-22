# Keyboard responsiveness — 0.13.4

## Additional hot-path reduction
- Snapshot the key-preview preference once when the keyboard view is built. The keyboard is rebuilt when the setting changes, so it does not need to be read for each key touch.
- When previews are disabled, do not create a delayed main-thread callback on every key release just to dismiss a popup that was never shown.
- Key press scaling, haptics, optional sounds, and the preview toggle remain available.


- Reuse the cached immutable personal-vocabulary snapshot instead of cloning up to 500 entries on each keypress.
- Do not schedule fuzzy correction for two-character prefixes when the correction engine requires at least three characters.

## Acceptance still required
- Compare typing responsiveness with 0.13.2 on the Redmi Note 12 Pro 5G.
- Verify EN, PL and TR, backspace, spaces, punctuation, candidate selection and cursor movement.
- Re-enable key previews and verify the option still behaves correctly.
