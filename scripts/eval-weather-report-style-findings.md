# eval-weather-report-style findings

Tracks results from `eval-weather-report-style.py` so conclusions survive
across sessions. Add new locale results as they come in.

## Sample text

Real InsightFormatter output used for all clips:

> "Today will be cold to cool. Wear a jumper and jacket."

(German: "Heute wird es kalt bis kühl. Trag Pullover und Jacke.")

## Candidate averages (all locales)

Scores are 1–10. en-GB and en-AU-general ran all candidates; en-AU and en-US
ran B1/B2 only in the focused retest.

| Candidate | en-US | en-AU | en-AU-gen | en-GB | Notes |
|---|---|---|---|---|---|
| weather-B1-basic | **9.0** | **8.4** | 7.6 | **8.3** | "national news service, measured speed" |
| weather-B2-with-emphasis | **9.1** | **8.4** | **7.9** | 7.6 | B1 + "gentle emphasis on clothing" |
| tweak-C1-authoritative | — | — | 7.4 | 7.6 | "authoritative yet approachable" |
| tweak-C3-with-pauses | — | — | 7.1 | 7.3 | C2 + "pause briefly between segments" |
| tweak-C2-cadence *(production)* | — | — | 6.7 | 6.9 | current WEATHER_FORECASTER |
| baseline-A2-newsreader | — | — | 6.3 | 5.9 | |
| baseline-A1-normal | — | — | 5.1 | 5.0 | |

en-GB column is the focused retest. First-run en-GB averages were B1 7.9, B2 7.7.

## B1 vs B2 per voice

Scores across four data points: en-US, en-AU, en-AU-general, en-GB (retest).

### B1 — weather-B1-basic

| Voice | en-US | en-AU | en-AU-gen | en-GB | Avg |
|---|---|---|---|---|---|
| Aoede   | 9 | 9 | 8 | 9 | **8.8** |
| Kore    | 9 | 8 | 9 | 9 | **8.8** |
| Leda    | 9 | 9 | 9 | 8 | **8.8** |
| Charon  | 9 | 8 | 8 | 9 | 8.5 |
| Erinome | 9 | 8 | 8 | 9 | 8.5 |
| Despina | 9 | 9 | 5 | 9 | 8.0 |
| Iapetus | 9 | 8 | 6 | 5 | 7.0 ⚠️ |

### B2 — weather-B2-with-emphasis

| Voice | en-US | en-AU | en-AU-gen | en-GB | Avg |
|---|---|---|---|---|---|
| Despina | 10 | 9 | 7 | 9 | **8.8** |
| Charon  | 10 | 9 | 8 | 8 | **8.8** |
| Aoede   | 10 | 9 | 9 | 6 | 8.5 |
| Erinome | 9  | 9 | 7 | 8 | 8.3 |
| Iapetus | 9  | 9 | 8 | 7 | 8.3 |
| Kore    | 9  | 9 | 8 | 8 | 8.5 |
| Leda    | 7  | 5 | 8 | 7 | 6.75 ⚠️ |

## Top voices (B1 + B2 combined)

Ranked by mean across all 8 data points (4 locales × 2 candidates).

| Rank | Voice | Combined avg | Notes |
|---|---|---|---|
| 1 | **Aoede** | 8.6 | Consistent; one dip (B2/en-GB: 6) |
| 1 | **Charon** | 8.6 | Rock-solid across all locale+directive combos |
| 1 | **Kore** | 8.6 | Consistent; best on B1 |
| 4 | **Despina** | 8.4 | Strong on B2; unreliable on B1/en-AU-general (5) |
| 5 | Erinome | 8.4 | Solid mid-tier; no standout highs or lows |
| 6 | Leda | 7.75 | Good on B1; degenerate on B2/en-AU (5) |
| 7 | Iapetus | 7.6 | Weakest overall; degenerate on B1/en-GB (5) |

## Degenerate cases (score ≤ 5)

| Voice | Candidate | Locale | Score |
|---|---|---|---|
| Leda | B2 | en-AU | 5 — over-emphasises clothing, sounds strained |
| Iapetus | B1 | en-GB | 5 — accent+directive interaction breaks the read |
| Despina | B1 | en-AU-general | 5 — directive mismatch with this accent variant |

## Decision: B1 vs B2

**Overall averages tie at 8.3.** The clothing-emphasis clause in B2 adds real
variance: voices that nail it (Charon, Despina) score 10s; voices that don't
(Leda) produce the worst clips in the dataset (5/10). B1 is tighter — no voice
averages below 7.0, and the only degenerate case (Iapetus/en-GB) appears on
both candidates.

**Verdict: use B1 if you want reliability; B2 if you accept that Leda will
occasionally sound strained.** Since both Leda and Iapetus are weak overall,
excluding them and running B2 on the top 4 (Aoede, Charon, Kore, Despina) would
actually be fine — those four average 8.7 on B2 with no degenerate cases.

## Next steps

- [x] Generate and rate en-US B1/B2 clips
- [x] Re-rate en-AU (production) and en-GB focusing on B1 vs B2
- [ ] Decide B1 vs B2 (see above)
- [ ] Open a PR updating `GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER`
