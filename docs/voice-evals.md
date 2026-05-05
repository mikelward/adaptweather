# Gemini TTS Voice Evaluation

Results from a manual listening session conducted against real app output
(en-AU and en-GB, Gemini 2.5 Flash Preview TTS). All scores are subjective
1–10 ratings by the primary user unless noted.

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

## Scripts

| Script | Purpose |
|---|---|
| `scripts/compare-tts-directives.py` | Generate clips for multiple directives × 2 voices in en-GB |
| `scripts/compare-gemini-voices.py` | Generate one clip per voice for a single directive |
| `scripts/rate-tts-clips.py` | Play each clip with `aplay`, prompt for a score + notes |
