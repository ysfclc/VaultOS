# Keyboard evaluation and language-resource plan

_Status: protocol and seed fixture only. No new language corpus, prediction benchmark run, or measured quality result is claimed._

## Why this exists

The current EN/PL/TR engine combines a compact built-in word list, hand-authored one-word continuations, a few two-word phrase priors, Android SpellChecker suggestions, and optional personal word/bigram/trigram counts. This is a useful offline baseline, but it is not a broad frequency model. The next quality work must be measured against a fixed held-out set rather than judged by a few hand-picked examples.

## Evaluation layers

Keep three separate measurements; do not collapse them into one "accuracy" number.

1. **Next-word prediction**: given a language and preceding one- or two-word context, evaluate whether the next reference token appears in the top 1 and top 3 candidates.
2. **Prefix completion**: given a partial token, evaluate whether the intended completion appears in the top 1 and top 3 candidates. Also track empty/noisy suggestions for prefixes that should not trigger completion.
3. **Correction**: given a misspelled token and intended token, evaluate top-1 correction accuracy, top-3 recall, false-correction rate on already-correct words, and whether diacritics/case are preserved.

Report each locale (en-US, pl-PL, tr-TR) separately and provide a macro-average. Do not let English volume hide weak Polish or Turkish results.

## Metrics

- Top-1 accuracy = cases whose expected token is candidate 1 / eligible cases.
- Top-3 recall = cases whose expected token appears in candidates 1–3 / eligible cases.
- Correction precision = accepted correction candidates that are correct / all emitted correction candidates in labelled typo cases.
- False-correction rate = already-correct control tokens for which the engine emits an unsafe correction / all correct-token controls.
- Empty/noise rate = no-target cases where the engine emits one or more candidates / all no-target cases.
- Latency = p50 and p95 for candidate generation, measured separately for prefix, next-word and correction paths after warm-up. Include device, Android build, app commit, corpus version, vocabulary size, warm-up count and sample count.
- Memory = retained model/resource size and approximate in-memory snapshot size. Record cold initialization separately from steady-state lookup.

A benchmark report must include raw counts (correct/eligible), not percentages alone. Never set a hard CI timing threshold from an uncontrolled shared runner. Use deterministic correctness thresholds in CI; record latency as a diagnostic, then measure on the Redmi Note 12 Pro 5G.

## Corpus policy

- Keep the committed seed fixture small, synthetic/curated and transparently labelled. It is a smoke/regression fixture, not a representative natural-language corpus.
- Before adding broad frequency resources, choose a source per locale and record the exact dataset version, license, attribution requirements, download URL, filtering/transformation steps and checksums in `docs/KEYBOARD-LANGUAGE-RESOURCES.md`.
- Do not scrape private messages, contacts, clipboard history, user documents or user editor contents to construct a corpus.
- Do not commit personal typing data or raw sentences.
- Normalize tokens with the locale under test. Preserve Polish and Turkish diacritics. Keep casing tests separate from lowercase ranking tests.
- Split source data by document/sentence before token windows are generated. Do not allow overlapping windows from one source document into both tuning and held-out evaluation.
- Keep held-out test data out of ranking tables and tuning. Use a separate development set for weights and vocabulary decisions.
- Include code-switching and mixed-language tests, but report them separately from single-locale metrics.

## Proposed corpus shape

The checked-in `keyboard-eval-v1.tsv` columns are:
`id,locale,task,context,input,expected,notes`.

For `next`, context contains the preceding token(s) separated by one ASCII space and expected is the next token. For `prefix`, input is a partial token and expected is its full completion. For `correction`, input is the typo and expected is the intended spelling. For `noisy-control`, expected is empty and the test asserts that the engine should not offer an unsafe candidate.

## Language model architecture direction

1. **Frequency lexicon**: locale-tagged token counts, with source/license provenance and a compact on-device representation. Keep common inflections rather than assuming English-style stemming works for Polish or Turkish.
2. **Context counts**: frequency-ranked bigram/trigram estimates derived from permitted corpus data, with smoothing/backoff. A missing trigram backs off to bigram, then unigram; never invent a candidate from no evidence.
3. **Personal adaptation**: remain opt-in, local, bounded, language-separated and clearable. Personal counts should re-rank candidates, not silently replace global frequency evidence. Do not learn from sensitive editor fields.
4. **Correction**: combine locale-aware candidate generation, edit distance and frequency/context priors. Keep conservative acceptance thresholds; automatic correction stays opt-in and must pass cursor/editor safety checks.
5. **Runtime**: immutable snapshots, no disk I/O on the IME keystroke path, bounded candidate pools, generation-gated async updates, no network permission and no heavyweight inference runtime in the first language-resource increment.

## Acceptance gates before calling the keyboard improved

- Seed fixture is executed by deterministic JVM tests and outputs per-locale counts.
- A licensed, versioned held-out evaluation set is added for all three locales before any quality claim.
- Baseline and candidate implementation are evaluated on the same held-out set.
- No locale regresses materially in top-3 recall or false-correction rate.
- Correction false-positive rate is treated as a safety metric, not traded away for more aggressive correction.
- Full Android build, lint and relevant tests pass.
- Device latency and everyday typing acceptance are measured on the target Redmi. CI timing is not a substitute.

## Immediate sequence

1. Preserve current engine as the baseline; add deterministic evaluation harness and seed fixture.
2. Establish licensed EN/PL/TR language-resource sources and record provenance before committing bulk word lists.
3. Implement frequency-ranked unigram/prefix candidate generation behind the existing API.
4. Add smoothed context ranking and compare against baseline.
5. Tune conservative correction ranking, then evaluate multilingual/case/diacritic and editor safety.
6. Only then revisit candidate-strip UX and swipe typing.
