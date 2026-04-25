# AdaptWeather

A daily weather insight app for Android. Each morning at a user-set time it
sends a brief, actionable notification — _"4°C warmer than yesterday, leave the
jumper at home"_ or _"50% chance of rain at 3pm — bring an umbrella"_ — generated
by Gemini from yesterday's and today's forecasts plus your wardrobe thresholds.

## Status

Early scaffolding. This branch lays down the pure-Kotlin domain module
(`:core:domain`) with the core data types and a set of DST-aware schedule tests.
The Android `:app` module, weather and Gemini clients, alarm/WorkManager
plumbing, and Compose UI land in subsequent PRs. See the
[plan](https://github.com/mikelward/adaptweather/blob/main/.docs/plan.md)
for the full roadmap (note: plan currently lives in the developer's local
notes; will be ported in).

## Tech stack

- **Android-only Kotlin** for v1. Domain + data modules are pure Kotlin so
  they can be promoted to Kotlin Multiplatform `commonMain` later for an
  iOS port without a rewrite.
- **Jetpack Compose** UI.
- **AlarmManager (`USE_EXACT_ALARM`) → BroadcastReceiver → WorkManager**
  for daily scheduling that survives Doze, reboot, timezone and locale changes.
- **Open-Meteo** for weather (no API key, free).
- **Gemini** for the comparative insight. v1 uses BYOK stored on-device with
  Tink + Android Keystore + DataStore.
- **Tests**: JUnit5 + MockK + Kotest assertions + Turbine. Robolectric for
  Android-framework units. Maestro for end-to-end flows.

## Modules

| Module | Notes |
|---|---|
| `:core:domain` | Pure Kotlin. Models, use cases, repository interfaces. ✅ landed |
| `:core:data` | Open-Meteo + Gemini clients, repository implementations. _planned_ |
| `:core:storage` | Tink + Keystore + DataStore for the API key. _planned_ |
| `:core:tts` | `TextToSpeech` wrapper. _planned_ |
| `:core:platform` | Locale / time / connectivity providers. _planned_ |
| `:app` | Compose UI, manifest, receivers, workers, DI. _planned_ |

## Building locally

Requires JDK 21. Pure-Kotlin modules build without Android SDK:

```sh
./gradlew :core:domain:test
```

The Android modules require the standard Android SDK setup; CI is the
expected way to build the APK while the project is sideload-only.

## Roadmap

- v0.x: daily 7am notification with Gemini-generated insight, wardrobe
  thresholds, optional TTS, sideload via Firebase App Distribution.
- v1.0: Play Store internal track, Gemini key proxy backend.
- v2.0: hourly / 3-hourly forecast UI, Google Home integration.
- iOS: deferred until a Mac is available _and_ a small APNs backend
  is in place (iOS cannot self-wake at a precise local time).

## OEM background restrictions

Some Android OEMs (Xiaomi, Oppo, OnePlus, aggressive Samsung profiles)
will kill the app and prevent the alarm from firing. See
[dontkillmyapp.com](https://dontkillmyapp.com) for per-OEM workarounds.
The Settings screen will link there once landed.
