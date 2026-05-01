# ClothesCast — TODO

Living to-do list. Items are roughly ordered by priority within each section.
Code TODOs in source files are linked from here when they exist.

## Pre-publishing blockers

- [x] **Pick a stable namespace + applicationId.** Pinned to `app.clothescast`
      (reverse-DNS of the planned `clothescast.app` domain). Renamed from
      `app.adaptweather` as part of the product rename — Android treats the
      new applicationId as a different app, so existing FAD testers had to
      uninstall + reinstall and lose their stored settings.
- [x] **Pick a stable product name.** Settled on "ClothesCast" (user-visible
      strings, icon, and applicationId all updated to `app.clothescast`).

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

- [x] **Read today's calendar events** (`CalendarContract`) so the daily
      insight can suggest items keyed to events: *"Bring an umbrella for your
      3pm park run."* Opt-in via Settings → Calendar (off by default), runtime
      `READ_CALENDAR` granted from the same card. The reader projects only
      titles, times, and locations; the rendered summary uses only title and
      time. The 6th sentence in `RenderInsightSummary` fires only when a
      clothes rule + a precip-peak event window both apply, preferring
      "umbrella" when on the clothes list. Reader failures degrade silently
      to no events.
  - Privacy disclosure update in `docs/privacy.md` (file not yet created).

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
- [ ] **Clothes rule presets** ("Cyclist", "Commuter", "Dog walker") — pick a
      preset, customise from there.
- [ ] **Quiet hours** — don't fire if the device is in DND.
- [ ] **Per-locale defaults** — Fahrenheit / miles when the system locale is
      en-US.
- [ ] **Multiple schedule profiles** — weekday vs weekend.
- [x] **Gemini model picker** — Flash Lite (cheapest), Flash (default), Pro
      (highest quality, slowest, costliest). User picks from Settings; the
      Worker passes the chosen id into a per-call `DirectGeminiClient`.

## Analytics & telemetry — under consideration

The post-crash share banner (this PR) covers "tell us when it broke" via
strictly user-initiated sharing. The questions still open are about
**product analytics** — which features are worth keeping, which defaults
serve users best, and which TTS engines are too underused to maintain.
Decide before wiring anything; the wiring is the easy part.

- [ ] **Decide whether to ship product analytics at all.** If yes, decide
      delivery shape: opt-out-with-toggle vs. required-first-launch
      question. PRIVACY.md → "Under consideration" section names both
      options; resolve and update there before any code lands.
- [ ] **Lock the event schema before any SDK is added.** Aggregate counts
      only — never free text. Candidate events: TTS engine in use,
      schedule cadence (number of slots, weekday vs. weekend), delivery
      mode (notification vs. widget vs. TTS), clothes-rule customisation
      (any thresholds tweaked from default? how many rules added?), region
      / unit settings. Bucketed times rather than exact local times.
- [ ] **Hard "do not transmit" list — codify in review checklist + tests:**
      - Calendar event data (titles, times, locations, attendee data).
        Not aggregated, not hashed, not bucketed — *not at all*.
      - User names, account identifiers, email addresses, contact info.
      - Location coordinates or geocoded place names.
      - Insight prose, notification text, or any free-form rendered
        content (the same prose that goes to TTS — that's already a
        bounded, user-controlled flow; analytics must not duplicate it).
      - Persistent device / install identifiers beyond what's strictly
        required to honour the opt-out choice.
- [ ] **Pick a backend before the SDK.** Firebase Analytics pulls Google
      Play Services telemetry into the app; PostHog / Plausible-style
      self-hosted avoids that but needs a server. Picking the SDK first
      and the backend second is how privacy boundaries quietly slip.
- [ ] **Crash reporting → reconsider remote** after analytics is decided.
      The local-only banner covers reachability; a remote service (Sentry
      self-hosted, Bugsnag, etc.) would cover crashes from users who
      never tap Share. Same opt-in / privacy-policy bar as analytics.

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
| `app/src/main/kotlin/app/clothescast/tts/AndroidTtsSpeaker.kt:30` | Evaluate higher-quality TTS alternatives. (Gemini + OpenAI now landed.) |
