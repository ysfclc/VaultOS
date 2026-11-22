# Keyboard responsiveness — 0.13.3

## Changes
- Android spell-checker requests are no longer issued for every letter or backspace. Local prefix candidates remain live; platform spelling alternatives are requested after a word is committed.
- Local fuzzy-correction ranking is debounced until 180 ms of typing inactivity. Pending work is cancelled when another character arrives.
- Background ranking retains its worker thread and generation, session, language, editor-safety and candidate-row checks.
- Popup key previews are opt-in by default to avoid creating and dismissing a separate popup window on every key press. They remain available in Tools.

## Acceptance still required
- Test EN, PL and TR typing, backspace, word boundaries, autocorrect and candidate selection on the Redmi Note 12 Pro 5G.
- Compare keystroke-to-render latency against 0.13.2 on the same device.
- Confirm re-enabling key previews does not reintroduce noticeable input lag.

This increment is not considered accepted until physical-device testing is complete.
