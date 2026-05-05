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

---

## Weather-report style eval (2026-05)

Tests whether an explicit weather-broadcast directive improves on the
newsreader baseline. Run via `scripts/eval-weather-report-style.py` against
real InsightFormatter output:

> "Today will be cold to cool. Wear a jumper and jacket."

Seven voices × seven candidates × nine locales. en-GB and en-AU-general ran
all candidates; en-US and en-AU (production) ran B1/B2 only in a focused
retest.

### Candidate averages

| Candidate | en-US | en-AU | en-AU-gen | en-GB | Notes |
|---|---|---|---|---|---|
| weather-B1-basic | **9.0** | **8.4** | 7.6 | **8.3** | "national news service, measured speed" |
| weather-B2-with-emphasis | **9.1** | **8.4** | **7.9** | 7.6 | B1 + "gentle emphasis on clothing" |
| tweak-C1-authoritative | — | — | 7.4 | 7.6 | "authoritative yet approachable" |
| tweak-C3-with-pauses | — | — | 7.1 | 7.3 | C2 + "pause briefly between segments" |
| tweak-C2-cadence *(production)* | — | — | 6.7 | 6.9 | current WEATHER_FORECASTER |
| baseline-A2-newsreader | — | — | 6.3 | 5.9 | prior production directive |
| baseline-A1-normal | — | — | 5.1 | 5.0 | original "clean, crisp studio voice" |

B1 and B2 beat the production directive (C2) by ~1 point consistently.
The ranking order is nearly identical across locales, suggesting the
directive wording matters more than locale interaction.

### B1 vs B2 per voice

Scores across four data points: en-US, en-AU, en-AU-general, en-GB (retest).

**B1 — weather-B1-basic**

| Voice | en-US | en-AU | en-AU-gen | en-GB | Avg |
|---|---|---|---|---|---|
| Aoede   | 9 | 9 | 8 | 9 | **8.8** |
| Kore    | 9 | 8 | 9 | 9 | **8.8** |
| Leda    | 9 | 9 | 9 | 8 | **8.8** |
| Charon  | 9 | 8 | 8 | 9 | 8.5 |
| Erinome | 9 | 8 | 8 | 9 | 8.5 |
| Despina | 9 | 9 | 5 | 9 | 8.0 |
| Iapetus | 9 | 8 | 6 | 5 | 7.0 ⚠️ |

**B2 — weather-B2-with-emphasis**

| Voice | en-US | en-AU | en-AU-gen | en-GB | Avg |
|---|---|---|---|---|---|
| Despina | 10 | 9 | 7 | 9 | **8.8** |
| Charon  | 10 | 9 | 8 | 8 | **8.8** |
| Aoede   | 10 | 9 | 9 | 6 | 8.5 |
| Kore    | 9  | 9 | 8 | 8 | 8.5 |
| Erinome | 9  | 9 | 7 | 8 | 8.3 |
| Iapetus | 9  | 9 | 8 | 7 | 8.3 |
| Leda    | 7  | 5 | 8 | 7 | 6.75 ⚠️ |

### Top voices (B1 + B2 combined)

| Rank | Voice | Avg | Notes |
|---|---|---|---|
| 1 | **Aoede** | 8.6 | Consistent; one dip (B2/en-GB: 6) |
| 1 | **Charon** | 8.6 | Rock-solid across all locale+directive combos |
| 1 | **Kore** | 8.6 | Consistent; best on B1 |
| 4 | **Despina** | 8.4 | Strong on B2; one degenerate on B1/en-AU-general |
| 5 | Erinome | 8.4 | Solid mid-tier |
| 6 | Leda | 7.75 | Good on B1; degenerate on B2/en-AU |
| 7 | Iapetus | 7.6 | Weakest; degenerate on B1/en-GB |

### Degenerate cases (score ≤ 5)

| Voice | Candidate | Locale | Score |
|---|---|---|---|
| Leda | B2 | en-AU | 5 — over-emphasises clothing, sounds strained |
| Iapetus | B1 | en-GB | 5 — accent+directive interaction breaks the read |
| Despina | B1 | en-AU-general | 5 — directive mismatch with this accent variant |

### Decision: B1 vs B2

Overall averages tie at 8.3. B2's clothing-emphasis clause adds real variance:
voices that nail it (Charon, Despina) score 10s; Leda produces the worst clip
in the dataset (5/10). B1 is tighter with no voice averaging below 7.0.

**If restricted to the top 4 voices (Aoede, Charon, Kore, Despina), B2 has no
degenerate cases and averages 8.7** — a viable choice if you want the
day-to-day variability without bad clips.

Pending: PR to update `GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER`.

---

## Scripts

| Script | Purpose |
|---|---|
| `scripts/compare-tts-directives.py` | Generate clips for multiple directives × 2 voices in en-GB |
| `scripts/compare-gemini-voices.py` | Generate one clip per voice for a single directive |
| `scripts/eval-weather-report-style.py` | Generate clips for 7 candidates × 7 voices × 9 locales |
| `scripts/rate-tts-clips.py` | Play each clip with `aplay`, prompt for a score + notes |
