# VaultOS Neural Keyboard — V2 product increment

_Status: approved direction; KBD-1 responsiveness implementation started, hosted verification pending._

## Problem statement

The 0.13.0 keyboard is visually more polished than its prediction engine. Its current engine is a small hand-written EN/PL/TR lexicon with sparse one-word next-word maps. The service also performs personal-dictionary reads, fuzzy matching and suggestion-row reconstruction on the main thread during typing. This creates two user-visible failures: weak prediction and avoidable input latency.

This is a product-completeness issue, not a theme issue. Do not describe the existing suggestion engine as a complete predictive keyboard.

## Product direction

Build a fast, adaptive, privacy-preserving keyboard that anticipates the next word, learns the user's vocabulary and common phrasing, and presents useful candidates without interrupting typing.

### V2 experience

1. **Instant candidate strip** — candidate rendering must not wait for disk reads, spell-check callbacks or expensive ranking. Keep the last valid candidates visible while a newer request is evaluated.
2. **Real next-word prediction** — rank candidates using the current word plus a short transient context window, not only one hard-coded previous word.
3. **Personal language adaptation** — when the user explicitly enables private typing memory, learn bounded word and n-gram frequencies. Support deletion/reset and language-separated models.
4. **Useful corrections** — typo correction must be ranked and conservative; never replace text automatically unless the user explicitly enables auto-correct.
5. **Multilingual quality** — English, Polish and Turkish must each have locale-correct casing, diacritics and word boundaries. Language switching must not mix personal models.
6. **Futuristic interaction design** — refined candidate strip, subtle confidence hierarchy, contextual action affordances and tactile feedback. Visual treatment must not reduce key hit targets or readability.
7. **Offline by default** — no network permission, cloud inference, analytics or remote text processing. No new model download or large runtime dependency in this increment.
8. **Editor safety** — preserve password/email/URL/filter/autocomplete exclusions. Only read a short transient context window from safe text fields. Never persist sentences, full editor contents or clipboard text.

## Technical direction

### A. Remove typing-path work from the UI thread

- Keep a single `LocalTypingMemory` instance per IME service lifetime.
- Cache parsed personal dictionary/model snapshots in memory and invalidate them only after learning, pinning, clearing or language changes.
- Avoid running full-vocabulary edit-distance scans for every keystroke.
- Compute candidate ranking from immutable in-memory snapshots; use a generation token so stale async results cannot overwrite newer text state.
- Reuse/update candidate views where practical instead of destroying and recreating the entire row for every character.
- Keep platform SpellCheckerSession as an optional correction source, not the primary source of prediction.

### B. Build a contextual local predictor

- Preserve current v1 word and adjacent-pair data; migrate additively.
- Add bounded trigram/context counts only when opt-in private learning is enabled.
- Use a small built-in language prior combined with user-specific counts and current transient context.
- Rank deterministically with frequency, recency/decay, prefix compatibility and context confidence.
- Fall back gracefully when a language model is sparse. Do not fabricate candidates for unknown context.
- Keep language data separate for EN, PL and TR.

### C. Make the model testable outside Android

Extract candidate scoring and context selection into a pure Kotlin component. Unit-test ranking, ties, unknown contexts, language boundaries, casing, short prefixes, typo conservatism, stale-result protection and bounded model size.

## Incremental delivery plan

### KBD-1 — Typing-path responsiveness
- [x] Reuse one `LocalTypingMemory` instance for the IME service lifetime; cache parsed word/pair snapshots and the dictionary word list, invalidating them when SharedPreferences data changes.
- [x] Bound correction ranking to 120 length-compatible candidates, prioritize same-initial candidates, and use bounded-memory Damerau–Levenshtein with an early exit.
- [x] Add deterministic correction-limit, transposition and low-noise regression cases (execution pending hosted CI).
- [ ] Capture a repeatable baseline and after-change microbenchmark for the pure engine; do not claim device latency improvement until measured on the Redmi.

### KBD-2 — Context-aware prediction
- Add opt-in, bounded trigram learning while retaining v1 bigram compatibility.
- Use a short, transient two-word context to rank predictions.
- Add migration/compatibility and language-isolation tests.

### KBD-3 — Candidate UX
- Improve candidate strip hierarchy and interactions.
- Keep correction, completion and next-word states distinguishable.
- Validate touch targets, TalkBack, landscape and large font on-device.

### KBD-4 — Device acceptance
- Test EN/PL/TR typing in Notes, Chrome, WhatsApp and ChatGPT.
- Test safe-field exclusions, cursor movement, selection, paste, deletion, language switching and Gboard switching.
- Measure cold-start and steady-state candidate latency on Redmi Note 12 Pro 5G.
- Do not call V2 complete until the device matrix passes.

## Definition of done

- Every KBD requirement has implementation, JVM tests and documentation.
- Candidate ranking does not perform disk I/O or unbounded fuzzy search on the main thread.
- Stale asynchronous results cannot replace suggestions for newer input.
- Opt-in learning stores bounded word/ngram counts only; disable and clear controls work.
- Existing v1 personal dictionaries remain readable.
- EN/PL/TR unit tests pass.
- Android build, lint and full relevant unit suite pass in hosted CI.
- Physical device acceptance and latency measurements are recorded separately.

## Explicit non-goals for this increment

- Gboard parity claims.
- Cloud LLM predictions or remote text transmission.
- Automatic correction by default.
- Swipe/glide typing until the core prediction and latency work is accepted.
- Large neural model downloads or new heavyweight inference dependencies.

## Current source findings (2026-11-12)

- `KeyboardSuggestionEngine.kt` contains a compact static EN/PL/TR vocabulary and sparse one-word next-word maps.
- `LocalTypingMemory.kt` stores opt-in word and adjacent-word counts in SharedPreferences JSON; KBD-1 now caches parsed immutable snapshots for the IME lifetime and invalidates changed keys.
- `VaultKeyboardService.renderSuggestions()` reads the personal dictionary and completions, runs local correction, reads cursor context and rebuilds candidate views on the typing path.
- `requestWordSuggestions()` relies on the Android SpellCheckerSession for asynchronous typo candidates.
- Existing helper coverage has been extended with bounded-correction cases, but tests have not yet run against this code; latency, adaptive sequence prediction and IME integration remain unverified.

The first implementation increment is KBD-1. Do not start with visual polish or a neural runtime.
