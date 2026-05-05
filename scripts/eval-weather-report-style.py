#!/usr/bin/env python3
"""
Evaluates the effect of making the TTS prompt explicitly weather-broadcast
flavoured — "read this like a national news weather report, enunciate clearly,
measured pace, accentuate clothing recommendations."

Candidates move in three steps:
  A) baselines — current NORMAL and NEWSREADER directives
  B) core proposal — the user's explicit weather-report idea, with and without
     the sentence/clothing-emphasis clause
  C) tweaks — minor wording changes that may sharpen or soften the effect

Each candidate is tested across three locales (en-US, en-AU, en-GB) using the
same accent directives the app injects, so style × accent interactions are
visible.

Usage
-----
  export GEMINI_API_KEY=your_key_here
  python3 scripts/eval-weather-report-style.py

  # only one locale:
  python3 scripts/eval-weather-report-style.py --filter en-AU

  # one candidate across all locales/voices:
  python3 scripts/eval-weather-report-style.py --filter weather-B2

Output
------
Writes .wav files to /tmp/tts-weather-style/:
  en-US-<candidate>-<voice>.wav
  en-AU-<candidate>-<voice>.wav
  en-GB-<candidate>-<voice>.wav

Rate with:
  python3 scripts/rate-tts-clips.py /tmp/tts-weather-style/
  python3 scripts/rate-tts-clips.py /tmp/tts-weather-style/ --pattern en-AU
  python3 scripts/rate-tts-clips.py /tmp/tts-weather-style/ --pattern en-GB-weather-B2
"""

import argparse
import base64
import json
import os
import pathlib
import urllib.request
import wave

API_KEY = os.environ.get("GEMINI_API_KEY", "")
MODEL = "gemini-2.5-flash-preview-tts"
SAMPLE_RATE = 24_000

# A realistic clip: one temperature read + one clothing call-to-action so we
# can hear whether the emphasis on the recommendation actually fires.
SAMPLE_TEXT = (
    "Feels like 9 degrees this morning, with a brisk northerly wind and patchy "
    "cloud. Grab a warm coat and a waterproof layer — showers are likely through "
    "midday before clearing by late afternoon. Temperatures recover to around 13 "
    "degrees this evening, so a light jacket should be fine heading out tonight."
)

# ── locales — same strings as GeminiTtsClient.kt ACCENT_DIRECTIVES ────────────
#
# en-AU has multiple variants to explore whether "Cultivated" and "educated"
# push the register too formal. The production value is en-AU; the others test
# lighter touches:
#   en-AU-general   — General Australian (the mid-tier between Broad and
#                     Cultivated; most ABC/SBS presenters sit here)
#   en-AU-natural   — drops the class label, just suppresses broad/nasal
#   en-AU-presenter — anchors to a concrete reference rather than a
#                     phonetic/social descriptor
#   en-AU-minimal   — bare minimum; useful to see how much the extra clauses
#                     are actually doing vs. the model's default

LOCALES: dict[str, str] = {
    "en-US": "Speak with a General American accent.",
    "en-AU":           "Speak with a Cultivated Australian accent — clear and educated, not broad.",
    "en-AU-general":   "Speak with a General Australian accent — clear and natural, not broad.",
    "en-AU-natural":   "Speak with a natural Australian accent — clear, not broad or nasal.",
    "en-AU-presenter": "Speak with the clear, natural accent of an Australian broadcast news presenter.",
    "en-AU-minimal":   "Speak with an Australian accent — clear and natural.",
    "en-GB": "Speak with a Standard Southern British accent.",
}

# ── A: baselines (current production directives) ─────────────────────────────

BASELINE_NORMAL = (
    "Read the following in a clean, crisp studio voice with no audio effects, "
    "background noise, or vinyl-style texture:\n\n"
)

BASELINE_NEWSREADER = (
    "Read the following in a clear, educated newsreader style — articulate but "
    "not theatrical or exaggerated. No audio effects, background noise, or "
    "vinyl-style texture:\n\n"
)

# ── B: user's explicit weather-broadcast proposal ─────────────────────────────
#
# B1 — basic form: national-news framing + enunciation + measured speed.
# B2 — full form: adds "accentuate ends of sentences and clothing
#      recommendations" per the original suggestion.

WEATHER_B1_BASIC = (
    "Read the following weather forecast in the style of a weather report on a "
    "national news service. Enunciate clearly and use a measured speed. "
    "No audio effects, background noise, or vinyl-style texture:\n\n"
)

WEATHER_B2_WITH_EMPHASIS = (
    "Read the following weather forecast in the style of a weather report on a "
    "national news service. Enunciate clearly and use a measured speed. "
    "Accentuate the ends of sentences and give a gentle emphasis to clothing "
    "recommendations. No audio effects, background noise, or vinyl-style "
    "texture:\n\n"
)

# ── C: suggested tweaks ───────────────────────────────────────────────────────
#
# C1 — "authoritative yet approachable": real broadcast meteorologists sit
#      between newsreader gravity and friendly presenter warmth; naming this
#      register explicitly may get closer than either baseline alone.
#
# C2 — "deliberate cadence" instead of "measured speed": Gemini steers on
#      prosodic cues like "cadence" and "pace" better than bare "speed", and
#      "deliberate" also nudges it toward clearer consonants. Keeps the
#      sentence-final uplift from B2.
#
# C3 — adds a "pause briefly between forecast segments" note; the sample text
#      has two natural breaks (morning conditions / noon showers / evening
#      recovery) and explicit pause direction may help the model breathe them.

TWEAK_C1_AUTHORITATIVE = (
    "Read the following weather forecast as a presenter on a national broadcast "
    "news service — authoritative yet approachable, as if speaking to a large "
    "general audience. Enunciate clearly at a measured pace. No audio effects, "
    "background noise, or vinyl-style texture:\n\n"
)

TWEAK_C2_CADENCE = (
    "Read the following weather forecast in the style of a national-news "
    "weather report. Use a deliberate cadence: unhurried, clearly enunciated, "
    "with a natural lift at the end of each sentence. Give a gentle extra "
    "emphasis to clothing advice. No audio effects, background noise, or "
    "vinyl-style texture:\n\n"
)

TWEAK_C3_WITH_PAUSES = (
    "Read the following weather forecast in the style of a national-news "
    "weather report. Enunciate clearly at a measured pace, pause briefly "
    "between forecast segments, and give a gentle extra emphasis to clothing "
    "recommendations. No audio effects, background noise, or vinyl-style "
    "texture:\n\n"
)

# ── candidate table ───────────────────────────────────────────────────────────

CANDIDATES: list[tuple[str, str]] = [
    ("baseline-A1-normal",          BASELINE_NORMAL),
    ("baseline-A2-newsreader",      BASELINE_NEWSREADER),
    ("weather-B1-basic",            WEATHER_B1_BASIC),
    ("weather-B2-with-emphasis",    WEATHER_B2_WITH_EMPHASIS),
    ("tweak-C1-authoritative",      TWEAK_C1_AUTHORITATIVE),
    ("tweak-C2-cadence",            TWEAK_C2_CADENCE),
    ("tweak-C3-with-pauses",        TWEAK_C3_WITH_PAUSES),
]

VOICES = ["Erinome", "Kore", "Aoede", "Despina", "Iapetus", "Charon", "Leda"]


# ── helpers ───────────────────────────────────────────────────────────────────

def synthesize(directive: str, accent: str, voice: str) -> bytes:
    prompt = directive + accent + "\n\n" + SAMPLE_TEXT
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "responseModalities": ["AUDIO"],
            "speechConfig": {
                "voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voice}}
            },
        },
    }
    url = (
        f"https://generativelanguage.googleapis.com/v1beta/models/"
        f"{MODEL}:generateContent?key={API_KEY}"
    )
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        body = json.loads(resp.read())
    inline = body["candidates"][0]["content"]["parts"][0]["inlineData"]
    return base64.b64decode(inline["data"])


def write_wav(path: pathlib.Path, pcm: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm)


# ── main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--filter",
        default="",
        metavar="SUBSTRING",
        help=(
            "Only generate clips whose slug contains this string. "
            "Examples: 'en-AU', 'weather-B2', 'en-GB-baseline', 'erinome'"
        ),
    )
    parser.add_argument(
        "--reverse",
        action="store_true",
        help=(
            "Process clips in reverse order. Run two instances in parallel — "
            "one without --reverse, one with — to work from both ends toward "
            "the middle without treading on each other."
        ),
    )
    args = parser.parse_args()

    if not API_KEY:
        raise SystemExit("Set GEMINI_API_KEY env var before running.")

    # Build full clip list, then apply filter
    clips: list[tuple[str, str, str, str]] = [  # (slug, directive, accent, voice)
        (f"{locale}-{label}-{voice.lower()}", directive, accent, voice)
        for locale, accent in LOCALES.items()
        for label, directive in CANDIDATES
        for voice in VOICES
        if args.filter in f"{locale}-{label}-{voice.lower()}"
    ]
    if not clips:
        raise SystemExit(f"No clips match filter '{args.filter}'.")

    if args.reverse:
        clips = list(reversed(clips))

    out = pathlib.Path("/tmp/tts-weather-style")
    print(f"Writing {len(clips)} clip(s) to {out}/\n")

    for slug, directive, accent, voice in clips:
        path = out / f"{slug}.wav"
        if path.exists():
            print(f"  {slug} ... skip (already exists)")
            continue
        print(f"  {slug} ...", end=" ", flush=True)
        try:
            pcm = synthesize(directive, accent, voice)
            write_wav(path, pcm)
            print(f"ok  ({len(pcm) // 2 / SAMPLE_RATE:.1f}s)")
        except Exception as exc:
            print(f"FAIL  {exc}")

    print(f"\nDone. Rate with:")
    print(f"  python3 scripts/rate-tts-clips.py {out}/")
    print(f"\nBy locale:")
    for locale in LOCALES:
        print(f"  python3 scripts/rate-tts-clips.py {out}/ --pattern {locale}")
    print(f"\nBy voice:")
    for v in VOICES:
        print(f"  python3 scripts/rate-tts-clips.py {out}/ --pattern {v.lower()}")


if __name__ == "__main__":
    main()
