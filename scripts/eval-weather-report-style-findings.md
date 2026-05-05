# eval-weather-report-style findings

Tracks results from `eval-weather-report-style.py` so conclusions survive
across sessions. Add new locale results as they come in.

## Sample text

Real InsightFormatter output used for all clips:

> "Today will be cold to cool. Wear a jumper and jacket."

(German: "Heute wird es kalt bis kühl. Trag Pullover und Jacke.")

## Candidate averages

Scores are 1–10. Higher = better.

| Candidate | en-GB | en-AU-general | Notes |
|---|---|---|---|
| weather-B1-basic | **7.9** | 7.6 | "national news service, measured speed" |
| weather-B2-with-emphasis | 7.7 | **7.9** | B1 + "gentle emphasis on clothing" |
| tweak-C1-authoritative | 7.6 | 7.4 | "authoritative yet approachable" |
| tweak-C3-with-pauses | 7.3 | 7.1 | C2 + "pause briefly between segments" |
| tweak-C2-cadence *(production)* | 6.9 | 6.7 | current WEATHER_FORECASTER |
| baseline-A2-newsreader | 5.9 | 6.3 | |
| baseline-A1-normal | 5.0 | 5.1 | |

en-US and production en-AU: pending.

## Top voices per locale

| Locale | Top voices | Avoid |
|---|---|---|
| en-GB | Erinome (7.7), Charon (7.4) | Despina (6.6, inconsistent) |
| en-AU-general | Charon (8.0), Kore (7.9) | Aoede (6.1, drops vs en-GB) |

## Key findings so far

**B1 and B2 consistently beat the production directive (C2) by ~1 point** across
both locales tested. The ranking order is almost identical between en-GB and
en-AU-general, suggesting the directive wording matters more than locale
interaction.

**B1 vs B2 is too close to call.** B1 wins en-GB by 0.3 points; B2 wins
en-AU-general by 0.3. The "gentle emphasis on clothing" clause in B2 neither
hurts nor clearly helps.

**Implication for the app:** strong case to switch `WEATHER_FORECASTER` from
C2 to B1 or B2. Waiting on en-US results to confirm.

## Next steps

- [ ] Generate and rate en-US B1/B2 clips
- [ ] Re-rate en-AU (production) and en-GB focusing on B1 vs B2
- [ ] Decide B1 vs B2 — if still a coin-flip, prefer B1 (simpler directive)
- [ ] Open a PR updating `GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER`
