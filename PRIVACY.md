# Privacy Policy

_Last updated: 2026-04-27_

ClothesCast is a daily weather-insight app for Android. This policy
describes what data the app handles, where it goes, and what control you
have over it.

## TL;DR

- ClothesCast has no backend servers and no analytics.
- Your **approximate location** is sent to [Open-Meteo](https://open-meteo.com)
  to fetch the weather forecast and a place name. That is the only data the
  app sends off your device by default.
- If you opt in to **online text-to-speech**, the short spoken sentence
  (e.g. _"50% chance of rain at 3pm — take an umbrella"_) is sent to
  Google Gemini so it can return the audio.
- If you opt in to **calendar tie-in**, the app reads today's events on
  your device. The event title may appear inside the spoken sentence
  (e.g. _"Bring a jacket for your concert tonight"_), and is therefore
  also sent to Gemini in that one case — only when both calendar tie-in
  and online TTS are enabled.
- Nothing is sold, no advertising, no profiling, no tracking.

## Who we are

ClothesCast is an open-source Android app developed by Mikel Ward.
The source code is at <https://github.com/mikelward/clothescast>.

## Data the app handles

### Approximate location

- **What:** Coarse latitude / longitude (city-block level, never precise
  GPS — the app declares only `ACCESS_COARSE_LOCATION`).
- **Why:** To fetch the weather forecast for where you are and to look up
  a human-readable place name to show in the UI.
- **Where it goes:** [Open-Meteo](https://open-meteo.com) (weather +
  geocoding APIs). Open-Meteo receives only a coordinate — no account,
  no device identifier.
- **Stored on device:** Your chosen location is saved in app settings so
  the daily refresh can run without re-prompting you.
- **Retention by us:** Until you uninstall the app or change locations.
- **Retention by Open-Meteo:** See <https://open-meteo.com/en/terms>.

### Calendar events _(optional, off by default)_

- **What:** Today's events — title, start/end time, location, all-day
  flag — read via Android's `CalendarContract` only when you grant
  `READ_CALENDAR` and enable the calendar tie-in setting.
- **Why:** To pair a clothes recommendation with a meeting that overlaps
  bad weather (e.g. _"Bring a jacket for your concert tonight"_).
- **Where it goes:** Calendar reading happens entirely on your device.
  The only way calendar data leaves the device is if (a) the tie-in fires
  for today's forecast _and_ (b) you have online TTS enabled — in which
  case the rendered sentence (which can include the event title) is sent
  to Google Gemini for vocalization.
- **Stored on device:** The most recent rendered insight (which may
  contain that sentence) is cached for up to one day so the app doesn't
  recompute it on every launch.
- **Retention by us:** Replaced on the next daily refresh; cleared on
  uninstall.

### Notifications

- ClothesCast posts a local notification at the time you choose. The
  notification is generated on your device. Nothing is sent to a push
  service.

### API keys you provide

- If you use online TTS, you supply your own Google Gemini API key. The
  key is stored on your device encrypted at rest — the ciphertext lives
  in Android's DataStore Preferences and is wrapped by a Tink AEAD
  primitive whose own keyset is sealed by an Android Keystore master
  key. The key is sent only to Gemini on requests you initiate, and is
  never shared with us or any third party.

## Third-party services

ClothesCast talks to these services on your behalf. Their own privacy
policies apply to anything they receive:

| Service | What we send | When |
|---|---|---|
| [Open-Meteo](https://open-meteo.com/en/terms) | Coarse coordinate | Always (forecast + geocoding) |
| [Google Gemini API](https://ai.google.dev/gemini-api/terms) | The short rendered insight sentence | Only if you enable Gemini TTS |

These providers act as service providers fulfilling a single request and
returning the result. Gemini receives only the sentence to be spoken, with
no user identifier attached beyond the API key you supplied. Gemini API
does not retain prompts for training by default. See the policy linked
above for the authoritative terms.

## What we do _not_ collect

- No accounts, no sign-in.
- No advertising identifiers, no ad networks.
- No analytics, crash reporting, or telemetry.
- No precise GPS location.
- No contacts, photos, microphone, or files.
- No data is sold or shared for advertising, profiling, or model training
  by us. (Provider terms govern what they may do; the linked policies
  above describe their commitments for API access.)

## Your controls

- **Location:** Revoke the Location permission in Android Settings, or
  change / clear it in ClothesCast's Settings.
- **Calendar:** Revoke `READ_CALENDAR` in Android Settings, or turn off
  "Use calendar events" in ClothesCast's Settings.
- **Online TTS:** Switch the voice engine to "Device" in Settings → Voice
  to keep all spoken text on-device.
- **API keys:** Clear them from Settings → API Keys at any time.
- **Everything:** Uninstalling the app deletes all locally stored data
  (settings, cached insight, API keys).

## Children

ClothesCast is not directed at children under 13 and does not knowingly
collect personal data from them.

## Changes

If this policy changes, the updated version will be committed to
<https://github.com/mikelward/clothescast/blob/main/PRIVACY.md>; the
"Last updated" date at the top will reflect the change. Material changes
to data handling will also be noted in the app's release notes.

## Contact

Open an issue at <https://github.com/mikelward/clothescast/issues> or
email the address listed on the Play Store listing.
