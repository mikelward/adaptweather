#!/usr/bin/env python3
"""
Despina-vs-Erinome head-to-head across the six shipping-config locales.

Tracks the TODO next to DEFAULT_GEMINI_TTS_VOICE in
core/data/.../GeminiTtsClient.kt — Erinome was rescued by the clarity-trim
(#351) on en-GB and the B3 routing (#353) on en-AU, plus a 10/10 single-roll
en-US test. This script generates the head-to-head data needed before any
default-voice switch.

Mirrors the live ACCENT_DIRECTIVES strings from GeminiTtsClient.kt and the
per-locale directive routing rule (en-AU → B3, everywhere else → B2). Update
the strings here if the shipped values change.

Usage
-----
  export GEMINI_API_KEY=your_key_here
  python3 scripts/eval-default-voice.py
  python3 scripts/eval-default-voice.py --rolls 4   # default is 2

  # only one locale or voice (substring match on the slug):
  python3 scripts/eval-default-voice.py --filter en-AU
  python3 scripts/eval-default-voice.py --filter despina

Output
------
  /tmp/tts-default-eval/<locale>/<voice>-roll<N>.wav

Rate with:
  python3 scripts/rate-tts-clips.py /tmp/tts-default-eval/
  python3 scripts/rate-tts-clips.py /tmp/tts-default-eval/ --pattern despina
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

SAMPLE_TEXT_EN = "Today will be cold to cool. Wear a jumper and jacket."
SAMPLE_TEXT_DE = "Heute wird es kalt bis kühl. Trag Pullover und Jacke."

# Directives — mirror GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER and
# GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER_REGISTER from GeminiTtsClient.kt.
WEATHER_B2 = (
    "Read the following weather forecast in the style of a weather report on a "
    "national news service. Enunciate clearly and use a measured speed. "
    "Accentuate the ends of sentences and give a gentle emphasis to clothing "
    "recommendations. No audio effects, background noise, or vinyl-style "
    "texture.\n\n"
)

WEATHER_B3 = (
    "Read the following weather forecast in the style of a weather report on a "
    "national news service. Use the language's standard variety, not a "
    "regional dialect. Enunciate clearly and use a measured speed. "
    "Accentuate the ends of sentences and give a gentle emphasis to clothing "
    "recommendations. No audio effects, background noise, or vinyl-style "
    "texture.\n\n"
)

# (locale, accent, directive, sample) — exactly mirrors the shipping config.
# en-AU is the only locale that takes the B3 register-in-directive variant
# (per directiveFor() in GeminiTtsClient.kt).
SHIPPING_CONFIG: list[tuple[str, str, str, str]] = [
    ("en-GB", "Speak with a Standard British accent.",                     WEATHER_B2, SAMPLE_TEXT_EN),
    ("en-AU", "Speak with an Australian accent, not broad.",               WEATHER_B3, SAMPLE_TEXT_EN),
    ("en-US", "Speak with a General American accent.",                     WEATHER_B2, SAMPLE_TEXT_EN),
    ("de-DE", "Sprich auf Deutsch in einem hochdeutschen Akzent.",         WEATHER_B2, SAMPLE_TEXT_DE),
    ("de-AT", "Sprich auf Deutsch mit einem österreichischen Akzent.",     WEATHER_B2, SAMPLE_TEXT_DE),
    ("de-CH", "Sprich auf Deutsch mit einem deutschschweizerischen Akzent.", WEATHER_B2, SAMPLE_TEXT_DE),
]

VOICES = ["Despina", "Erinome"]


def synthesize(directive: str, accent: str, voice: str, sample: str) -> bytes:
    prompt = directive + accent + "\n\n" + sample
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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--rolls",
        type=int,
        default=2,
        help="Rolls per (locale, voice) cell (default: 2).",
    )
    parser.add_argument(
        "--filter",
        default="",
        metavar="SUBSTRING",
        help="Only generate clips whose slug contains this string.",
    )
    args = parser.parse_args()

    if not API_KEY:
        raise SystemExit("Set GEMINI_API_KEY env var before running.")

    out = pathlib.Path("/tmp/tts-default-eval")
    clips = [
        (locale, voice, roll, accent, directive, sample)
        for locale, accent, directive, sample in SHIPPING_CONFIG
        for voice in VOICES
        for roll in range(1, args.rolls + 1)
        if args.filter in f"{locale}-{voice.lower()}-roll{roll}"
    ]
    if not clips:
        raise SystemExit(f"No clips match filter '{args.filter}'.")

    print(f"Writing {len(clips)} clip(s) to {out}/<locale>/\n")

    for locale, voice, roll, accent, directive, sample in clips:
        path = out / locale / f"{voice.lower()}-roll{roll}.wav"
        if path.exists():
            print(f"  {locale}/{voice}-roll{roll} ... skip (already exists)")
            continue
        print(f"  {locale}/{voice}-roll{roll} ...", end=" ", flush=True)
        try:
            pcm = synthesize(directive, accent, voice, sample)
            write_wav(path, pcm)
            print(f"ok  ({len(pcm) // 2 / SAMPLE_RATE:.1f}s)")
        except Exception as exc:
            print(f"FAIL  {exc}")

    print(f"\nDone. Rate with:")
    print(f"  python3 scripts/rate-tts-clips.py {out}/")
    print(f"\nBy locale:")
    for locale, *_ in SHIPPING_CONFIG:
        print(f"  python3 scripts/rate-tts-clips.py {out}/{locale}/")
    print(f"\nBy voice:")
    for v in VOICES:
        print(f"  python3 scripts/rate-tts-clips.py {out}/ --pattern {v.lower()}")


if __name__ == "__main__":
    main()
