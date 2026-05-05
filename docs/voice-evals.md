# Gemini TTS Voice Evaluation

Results from a manual listening session conducted against real app output
(en-AU and en-GB, Gemini 2.5 Flash Preview TTS). All scores are subjective
1–10 ratings by the primary user unless noted.

> **Note (post-PR #323):** the newsreader directive this eval was
> conducted under is now opt-in via the new "Style" picker, with `Normal`
> (the v505-equivalent studio-voice preamble) as the default. The default
> voice has moved from Erinome to Leda alongside that change. A
> re-audition under Normal is pending — sections below remain the
> historical record of the newsreader-only eval, not the current
> recommendation.

---

## Problem statement

The production directive at the start of the session was:

> "Read the following in a clean, crisp studio voice:"

Two separate issues were observed on different voices:

1. **Theatrical / villain delivery** — mostly on male voices (Orus, some
   Kore clips). The word "studio" likely pushes the model toward a dramatic
   broadcast register.
2. **Vinyl crackle / lo-fi texture** — an audio artifact baked into some
   clips by the model, audible as faint surface noise under the voice.

---

## Directive evaluation

Tests were run via `scripts/compare-tts-directives.py` against neutral
weather copy in en-GB, using Kore and Erinome as reference voices.

| ID | Directive | Erinome | Kore | Notes |
|---|---|---|---|---|
| A1 | `newsreader style — articulate but not theatrical, no audio effects` | 10 | 10 | Best overall; applied to production |
| A2 | `conversational — educated but not formal or posh` | 2 | 10 | Erinome sounds flat/unengaged |
| B1 | Current production (`clean, crisp studio voice`) | 10 | 10 | Identical text to A1 (sanity check) |
| B2 | `with a modern, approachable accent` | 5 | 7 | Loses clarity benefit |
| B3 | No style prefix (accent only) | 1 | — | Defaults to American English |

**Chosen directive:**

```
Read the following in a clear, educated newsreader style — articulate but
not theatrical or exaggerated. No audio effects, background noise, or
vinyl-style texture:
```

Rationale: "Newsreader" is the clearest single-word register signal the
model understands across locales. It suppresses theatrical delivery and
crackle without pushing into stiff/posh territory. It applies globally
before any per-locale accent clause.

---

## Voice evaluation

### en-AU testing

Tested with `scripts/compare-gemini-voices.py` and in the real app with
accent set to "Cultivated Australian — clear and educated, not broad."

| Voice | Score | Notes |
|---|---|---|
| **Erinome** | 10 | Perfect. Zero regional artifacts. |
| Kore | 7 | ABC regional news feel — acceptable |
| Aoede | 7 | Acceptable fallback |
| Iapetus | 6 | Slightly unnatural but clear |
| Orus | 5 | Play School / condescending register; acceptable if nothing better |
| Vindemiatrix | tested pre-newsreader | See en-GB row below |
| Achird | — | Bad; both testers agreed, dropped |
| Remaining ~9 voices | — | Theatrical or vowel issues; not listed |

After the newsreader directive was applied, Iapetus improved and Erinome
became clearly the best choice for en-AU.

### en-GB testing (Kore and Erinome)

`scripts/compare-tts-directives.py` with en-GB locale, newsreader directive.

| Voice | Score | Notes |
|---|---|---|
| **Erinome** | 10 | Crisp, educated, zero crackle |
| Kore | 10 | Clean newsreader; slightly regional on some clips |
| Aoede | — | "Alan Rickman" effect with newsreader + en-GB — dropped |

### en-GB testing (daughter's ratings, conducted on older "studio voice" directive)

The secondary tester evaluated the full picker list before the directive
change. These scores reflect the voice's character rather than directive
interaction.

| Voice | Score | Notes |
|---|---|---|
| **Aoede** | fave | Warm, smooth |
| Despina | 2nd fave | Gentle/smooth; added to picker |
| Vindemiatrix | liked | Gentle; added to picker |
| Achird | liked | Conflicts with primary tester's "bad" — not added |

Note: these ratings were on the old `studio voice` directive. Despina and
Vindemiatrix were added to the picker without re-testing on newsreader;
they may sound different. Aoede was excluded from the picker due to the
Alan Rickman issue under the production directive.

### Male voices

| Voice | Score | Notes |
|---|---|---|
| Charon | ok | Tested briefly in early sweep; Informative label fits weather |
| Sadaltager | untested in-app | Knowledgeable label; added alongside Charon |
| Orus | 4 | Theatrical affectations; dropped |

---

## Final picker list (shipped in PR #291)

```kotlin
val GEMINI_VOICES = listOf(
    TtsVoiceOption("Erinome",       "Erinome — Clear"),       // default
    TtsVoiceOption("Iapetus",       "Iapetus — Clear"),
    TtsVoiceOption("Kore",          "Kore — Firm"),
    TtsVoiceOption("Charon",        "Charon — Informative"),
    TtsVoiceOption("Sadaltager",    "Sadaltager — Knowledgeable"),
    TtsVoiceOption("Vindemiatrix",  "Vindemiatrix — Gentle"),
    TtsVoiceOption("Despina",       "Despina — Smooth"),
)
```

**Default: Erinome.** Scored 10/10 on neutral weather copy in both en-AU and
en-GB with the newsreader directive. Replaced previous default (Kore).

---

## Notes on voice reliability

- The directive suppresses the theatrical delivery register but **cannot
  override a voice's phonetic anchor.** Iapetus and Erinome went
  Queensland-broad on en-AU without the accent clause; the cultivated accent
  directive fixed it for Erinome but only partially for Iapetus.
- TTS responses are **not cached.** Emotionally charged weather copy
  ("dangerous icy conditions", "severe storm warning") may push voices toward
  dramatic delivery regardless of the directive. The newsreader directive
  reduces but doesn't eliminate this.
- **Voice Locale must be set explicitly** (not "System") to get the accent
  clause. With System/System, the locale falls back to `Locale.getDefault()`
  and Aoede or similar voices can sound unexpectedly regional.

---

## Weather-report style eval (2026-05)

Tests whether an explicit weather-broadcast directive improves on the
newsreader baseline. Run via `scripts/eval-weather-report-style.py` against
real InsightFormatter output:

> "Today will be cold to cool. Wear a jumper and jacket."

Seven voices × seven candidates × nine locales. en-GB and en-AU-general ran
all candidates; en-US ran B1/B2 only. The original en-AU (Cultivated) run is
stale — the prod accent was already changed to General Australian before that
eval; use en-AU-general results instead.

### Candidate averages

| Candidate | en-US | en-AU-gen | en-GB | Notes |
|---|---|---|---|---|
| weather-B2-with-emphasis *(→ prod)* | **9.1** | **7.9** | — | B1 + "gentle emphasis on clothing" |
| weather-B1-basic | 9.0 | 7.6 | **8.3** | "national news service, measured speed" |
| tweak-C1-authoritative | — | 7.4 | 7.6 | |
| tweak-C3-with-pauses | — | 7.1 | 7.3 | |
| tweak-C2-cadence *(was prod)* | — | 6.7 | 6.9 | |
| baseline-A2-newsreader | — | 6.3 | 5.9 | |
| baseline-A1-normal | — | 5.1 | 5.0 | |

B1 and B2 beat the old production directive (C2) by ~1 point consistently.
B2 wins en-US and en-AU-gen; B1 wins en-GB under the old accent directive.

### en-GB accent tuning

The original en-GB directive ("Standard Southern British") caused B2 to
underperform vs B1 (7.6 vs 8.3). Three replacements were tested with B2:

| Accent variant | B2 avg | Notes |
|---|---|---|
| en-GB-measured — "Standard British — clear and natural" | **8.3** | Matches B1; no degenerate cases |
| en-GB-presenter — "clear accent of a British television news presenter" | 7.6 | Charon collapses to 3 |
| en-GB-bbc — "Standard Southern British, as spoken by a BBC news presenter" | 6.9 | Flat across the board |

**en-GB-measured fixes B2 in en-GB.** Updated in prod.

### Final scores (B2 + updated accent directives)

| Locale | B2 avg |
|---|---|
| en-US | 9.1 |
| en-AU-general | 7.9 |
| en-GB-measured | 8.3 |

### Top voices

| Rank | Voice | Notes |
|---|---|---|
| 1 | **Aoede** | Consistent; one dip on old en-GB B2 (now fixed) |
| 1 | **Charon** | Rock-solid except en-GB-presenter (avoid that variant) |
| 1 | **Kore** | Consistent |
| 4 | **Despina** | Strong on B2; one degenerate on B1/en-AU-general |
| 5 | Erinome | Solid mid-tier |
| 6 | Leda | Degenerate on old en-AU (Cultivated) + B2; fine under current accents |
| 7 | Iapetus | Weakest overall |

### Shipped

- `GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER` → B2 wording
- en-GB accent directive → "Speak with a Standard British accent — clear and natural."

---

## Scripts

| Script | Purpose |
|---|---|
| `scripts/compare-tts-directives.py` | Generate clips for multiple directives × 2 voices in en-GB |
| `scripts/compare-gemini-voices.py` | Generate one clip per voice for a single directive |
| `scripts/eval-weather-report-style.py` | Generate clips for 7 candidates × 7 voices × 9 locales |
| `scripts/rate-tts-clips.py` | Play each clip with `aplay`, prompt for a score + notes |
