# Keyboard responsiveness — 0.13.4

## Additional hot-path reduction
- Snapshot the key-preview preference once when the keyboard view is built. The keyboard is rebuilt when the setting changes, so it does not need to be read for each key touch.
- When previews are disabled, do not create a delayed main-thread callback on every key release just to dismiss a popup that was never shown.
- Key press scaling, haptics, optional sounds, and the preview toggle remain available.


- Reuse the cached immutable personal-vocabulary snapshot instead of cloning up to 500 entries on each keypress.
- Do not schedule fuzzy correction for two-character prefixes when the correction engine requires at least three characters.
- Cache the personal dictionary in score-ranked order, so prefix completions filter the cached ranking instead of sorting up to 500 learned words on every keystroke.
- Snapshot haptic and sound preferences when building the keyboard view, removing two SharedPreferences reads from every key tap while preserving immediate setting changes through the existing view refresh.
- Add an Android SharedPreferences regression test proving that the ranking cache invalidates immediately when a word is explicitly added to the personal dictionary.

## Acceptance still required
- Compare typing responsiveness with 0.13.2 on the Redmi Note 12 Pro 5G.
- Verify EN, PL and TR, backspace, spaces, punctuation, candidate selection and cursor movement.
- Re-enable key previews and verify the option still behaves correctly.
