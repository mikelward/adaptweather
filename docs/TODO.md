# AdaptWeather — TODO

Living to-do list. Items are roughly ordered by priority within each section.
Code TODOs in source files are linked from here when they exist.

## Pre-publishing blockers

- [x] **Pick a stable namespace + applicationId.** Pinned to `app.adaptweather`
      (reverse-DNS of the owned `adaptweather.app` domain). Renaming after
      sideload destroys settings — Android treats the new applicationId as a
      different app — so this had to land before any Firebase App Distribution
      rollout.
- [ ] **Pick a stable product name.** "AdaptWeather" is a working title.

## Distribution

- [x] **Firebase App Distribution setup.** Push to `main` triggers a debug
      APK build signed with the stable keystore, uploaded to FAD with the
      commit message as release notes. Setup steps in
      [docs/firebase-app-distribution.md](firebase-app-distribution.md).
- [ ] **`.github/workflows/release.yml`** — tag-triggered, runs Maestro on
      Firebase Test Lab + cuts a GitHub Release with a release-signed APK.

## Voice / TTS

- [x] Gemini TTS as opt-in voice engine (PR #27)
- [x] Diagnostic "Test Gemini voice" button in Settings (PR #28)
- [ ] **OpenAI TTS** as a third voice engine. BYOK pattern, same as Gemini.
- [ ] **Voice picker** for both Gemini and OpenAI. Currently Gemini is
      hardcoded to `Kore`.
- [ ] ElevenLabs / Azure as further options (only after the abstractions are
      proven by OpenAI).

## Calendar integration (next-up after TTS)

- [ ] **Read today's calendar events** (`CalendarContract`) so the daily
      insight can suggest items keyed to events: *"Bring an umbrella for your
      3pm at the park."*
  - Opt-in toggle in Settings + runtime `READ_CALENDAR` grant.
  - New `:core:domain` model `CalendarEvent`.
  - Worker reads events for today and feeds them into `BuildPrompt`.
  - Privacy disclosure update in `docs/privacy.md`.

## Forecast & alerts

- [x] **Severe weather alerts.** Open-Meteo's `/v1/warnings` is now wired up:
      alerts feed into `BuildPrompt`, and SEVERE / EXTREME alerts also fire a
      separate high-priority notification on a dedicated channel.
- [x] **Hourly forecast UI** on Today. Vico chart of temperature + feels-like
      across today's hours. Multi-day extension still possible.
- [ ] **Forecast accuracy ideas** — end-of-day accuracy survey, user-flagged
      incorrect forecasts, background multi-provider comparison. Sketched in
      [docs/MODELS.md](MODELS.md) (ideas 2-4). Idea 1 (confidence badge)
      shipped — see below.
- [x] **Multi-model confidence badge** (MODELS.md idea #1) — Today shows
      a chip indicating how much ECMWF / GFS / ICON disagree about today's
      apparent high and peak precip probability.

## Feature ideas (queued)

- [ ] **Multiple daily insights** — morning + evening, configurable per slot.
      Needs a second alarm slot and the calendar reader above for the evening
      "what to bring tomorrow" briefing.
- [ ] **Notification actions** — "read aloud" / "snooze for today" buttons in
      the notification.
- [ ] **Tap-to-replay TTS** on Today.
- [ ] **Past 7 days history** on Today — pull from `InsightCache`, persist
      beyond the current single slot.
- [ ] **Wardrobe rule presets** ("Cyclist", "Commuter", "Dog walker") — pick a
      preset, customise from there.
- [ ] **Quiet hours** — don't fire if the device is in DND.
- [ ] **Per-locale defaults** — Fahrenheit / miles when the system locale is
      en-US.
- [ ] **Multiple schedule profiles** — weekday vs weekend.
- [x] **Gemini model picker** — Flash Lite (cheapest), Flash (default), Pro
      (highest quality, slowest, costliest). User picks from Settings; the
      Worker passes the chosen id into a per-call `DirectGeminiClient`.

## Testing & quality

- [ ] **Robolectric tests** for `NotificationBuilder`, `DailyAlarmScheduler`,
      `BootReceiver`. Currently zero coverage on those error-prone bits.
- [ ] **Compose UI tests** for `SettingsScreen` (state transitions, dialog
      flow). No `app/src/androidTest/` exists today.
- [ ] **Maestro flows** — `.maestro/first_launch.yaml`,
      `.maestro/daily_insight_debug_fire.yaml`. Plan called for both; need
      Firebase Test Lab in CI to run them automatically.
- [ ] **`detekt` + `ktlintCheck` in CI.** Neither plugin applied today.
- [ ] **JaCoCo coverage** — plan target ≥85% on `:core:domain` +
      `:core:data`. No coverage measurement wired up.
- [ ] **`docs/acceptance.md`** — manual checklist (TTS audio, real 7am fire,
      lock-screen visibility, OEM background-killer).

## Deferred to v2 (out of scope for v1)

- iOS port (needs a Mac + KMP-promotion of the core modules).
- Backend Gemini proxy (interface in place; swap before Play Store).
- Google Home / alarm-clock-app integration.
- Play Store submission. Sideload + FAD only for v1.

## Code TODOs

| File | Note |
|---|---|
| `app/build.gradle.kts:9` | Pin namespace + applicationId before first distribution. |
| `app/src/main/kotlin/app/adaptweather/tts/AndroidTtsSpeaker.kt:30` | Evaluate higher-quality TTS alternatives. (Gemini + OpenAI now landed.) |
