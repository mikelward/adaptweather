# AdaptWeather — TODO

Living to-do list. Items are roughly ordered by priority within each section.
Code TODOs in source files are linked from here when they exist.

## Pre-publishing blockers

- [ ] **Pick a stable namespace + applicationId.** Currently `com.adaptweather`,
      a placeholder. Reverse-DNS based on a domain you own (e.g.
      `dev.mikelward.adaptweather`) and a settled product name. Marked TODO in
      `app/build.gradle.kts:9`. *Renaming after sideload destroys settings —
      Android treats the new applicationId as a different app.*
- [ ] **Pick a stable product name.** "AdaptWeather" is a working title.

## Distribution

- [ ] **Firebase App Distribution setup** (in progress). Plan: keystore stays
      out of the repo, base64 + passwords go in GitHub Secrets, CI decodes on
      the runner. Six secrets total: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
      `KEY_ALIAS`, `KEY_PASSWORD`, `FIREBASE_APP_ID`,
      `FIREBASE_SERVICE_ACCOUNT_JSON`.
- [ ] **`.github/workflows/build.yml`** — push-to-main signed-release upload to
      FAD. Replaces the artifact-download workflow.
- [ ] **`.github/workflows/release.yml`** — tag-triggered, runs Maestro on
      Firebase Test Lab + cuts a GitHub Release.

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

- [ ] **Severe weather alerts.** Open-Meteo exposes a free alerts endpoint —
      layer onto the daily insight, post a separate notification on
      severe-grade events.
- [ ] **Hourly / multi-day forecast UI** on Today. Currently Today only shows
      the latest insight; useful to see the underlying data the LLM saw.

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
| `app/src/main/kotlin/com/adaptweather/tts/AndroidTtsSpeaker.kt:30` | Evaluate higher-quality TTS alternatives. (Gemini + OpenAI now landed.) |
