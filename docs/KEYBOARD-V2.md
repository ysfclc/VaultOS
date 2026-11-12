# VaultOS Neural Keyboard — V2 product increment

_Status: KBD-1 responsiveness and KBD-2 contextual prediction source implemented, including asynchronous local correction ranking and stale-result rejection; hosted verification, Android-backed learning tests and device acceptance pending._

## Problem statement

The 0.13.0 keyboard is visually more polished than its prediction engine. Its current engine is a small hand-written EN/PL/TR lexicon with sparse one-word next-word maps. The service previously performed local fuzzy correction on the IME main thread. KBD-1 now moves that bounded correction scan to a reusable worker; the static language model and the quality of its predictions remain limited, and real-device latency still needs measurement.

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
- [x] Keep the three candidate cells and separators alive across keystrokes; update content and visibility rather than rebuilding the suggestion row.
- [x] Bound correction ranking to 120 length-compatible candidates, prioritize same-initial candidates, and use bounded-memory Damerau–Levenshtein with an early exit.
- [x] Add deterministic correction-limit, transposition and low-noise regression cases (execution pending hosted CI).
- [x] Add a repeatable JVM microbenchmark for correction ranking with the maximum 500-word personal vocabulary; it reports p50/p95 without a flaky hard threshold.
- [x] Move local fuzzy correction ranking to a reusable single-thread worker using an immutable personal-vocabulary snapshot; retain immediate typed/completion candidates while ranking runs.
- [x] Guard worker results with a generation token and active input/session checks; discard queued stale work and shut down the worker with the IME service.
- [x] Add pure generation-gate tests for latest-request precedence and lifecycle invalidation.
- [ ] Execute the benchmark on hosted CI and record its output; compare against a pre-change baseline only if the prior source can be built reproducibly. Do not claim device latency improvement until measured on the Redmi.

### KBD-2 — Context-aware prediction
- [x] Add opt-in, bounded trigram learning (1,200 contexts per language) while retaining v1 bigram compatibility.
- [x] Use a short, transient two-word context from the active safe editor to rank predictions, with one-word fallback.
- [x] Add built-in EN/PL/TR two-word phrase priors and language-specific regression tests.
- [x] Add Android-backed tests for opt-in, persistence across instances, opt-out, clear/reset, language isolation and v1 bigram fallback.
- [ ] Execute the instrumentation suite on the API 35 CI emulator and record the run.

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
- Android-backed SharedPreferences tests cover opt-in, persistence, opt-out, clear/reset and v1 bigram compatibility (execution still pending).
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

- `KeyboardSuggestionEngine.kt` contains a compact static EN/PL/TR vocabulary, one-word next-word maps and two-word phrase priors.
- `LocalTypingMemory.kt` now adds opt-in, bounded per-language trigram counts while preserving the v1 word/bigram keys; exact two-word context predictions fall back to v1 adjacent-word counts when no trigram exists.
- `LocalTypingMemory.kt` stores opt-in word and adjacent-word counts in SharedPreferences JSON; KBD-1 now caches parsed immutable snapshots for the IME lifetime and invalidates changed keys.
- `VaultKeyboardService.renderSuggestions()` keeps cached personal vocabulary/completion lookup and candidate rendering on the IME thread, while local fuzzy correction now runs on a reusable worker from an immutable vocabulary snapshot. The UI keeps immediate typed/completion candidates while the worker ranks corrections.
- `requestWordSuggestions()` relies on the Android SpellCheckerSession for asynchronous typo candidates.
- Added `SuggestionRequestGate` and three JVM tests for latest-request precedence and lifecycle invalidation. The pure gate was compiled and exercised independently; the Android service integration, full project tests, benchmark, typing latency and adaptive prediction quality remain unverified.

The first implementation increment is KBD-1. Do not start with visual polish or a neural runtime.


## Android-backed private typing-memory tests (2026-11-12)

Added `LocalTypingMemoryInstrumentedTest` using AndroidX instrumentation and the real app sandbox SharedPreferences implementation. Coverage includes learning disabled by default, enabled word/bigram/trigram persistence across instances, opt-out without deleting existing data, clear preserving the user's opt-in choice, EN/PL/TR model separation, and v1 word/bigram JSON fallback when no trigram key exists.

A source review exposed that the shared normalizer rejected one-letter words. That prevented common context tokens such as English "I" from participating in learned pairs/trigrams. The minimum accepted word length is now one character; punctuation-only tokens remain rejected and the 32-character maximum remains unchanged.

The Android build workflow runs the API 35 Google APIs emulator suite as an independent job, so emulator failures cannot block APK publication. Run 163 showed both jobs failing before steps with runner_id=0, empty runner_name and empty steps. A Vercel Sandbox fallback subsequently built the current 0.13.0 source: all 43 JVM tests passed, lint passed, the Android instrumentation test APK compiled, and the signed release APK was assembled and apksigner-verified. The Vercel sandbox had no /dev/kvm, so the six instrumentation tests were not executed. GitHub hosted-runner allocation and Redmi device acceptance remain open.

## 500-word correction benchmark — Vercel host (2026-11-12)

The repeatable JVM benchmark ran 1,000 measured correction-ranking iterations after 150 warm-ups with a 500-word personal vocabulary. Result: **p50 105 μs, p95 160 μs**. This is a Vercel Linux host measurement of the pure correction-ranking path only; it is not an IME end-to-end or Redmi device latency result. All 43 JVM tests passed in the same forced run. The six Android-backed SharedPreferences tests compiled but were not executed because the Vercel sandbox exposed no /dev/kvm.