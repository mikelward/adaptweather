#!/usr/bin/env python3
"""
Single-shot Gemini TTS helper for prompt iteration.

Synthesises one clip with the given directive + accent + voice + text,
writes it to a WAV, and plays it via `aplay`. Designed for
tweak-in-a-loop work: change one flag, rerun, listen, change another.

The defaults mirror the strings shipped in
`core/data/.../tts/GeminiTtsClient.kt` (WEATHER_FORECASTER directive +
en-GB accent + Leda voice + the eval-script sample text), so a bare
`python3 scripts/say.py` reproduces the production prompt for one clip.

Usage
-----
  export GEMINI_API_KEY=your_key_here

  # Production prompt (en-GB Weather Forecaster, Leda):
  python3 scripts/say.py

  # Try a bare en-GB accent (the "trim clarity" candidate):
  python3 scripts/say.py --accent "Speak with a Standard British accent."

  # Drop the accent entirely (Gemini falls back to North American English):
  python3 scripts/say.py --accent ""

  # Different voice:
  python3 scripts/say.py --voice Charon

  # Custom text:
  python3 scripts/say.py --text "It's 12 degrees and overcast."

  # See the assembled prompt before the API call:
  python3 scripts/say.py --print-prompt

  # Try a different TTS model (e.g. a future GA replacement):
  python3 scripts/say.py --model gemini-2.5-flash-tts

  # Just write a file, skip playback:
  python3 scripts/say.py --no-play --out /tmp/foo.wav

Cost: each call is one Gemini TTS request (~$0.003 per short clip on
the standard tier; free tier covers light iteration).
"""

import argparse
import base64
import json
import os
import pathlib
import subprocess
import urllib.request
import wave

DEFAULT_MODEL = "gemini-2.5-flash-preview-tts"
SAMPLE_RATE = 24_000

# Defaults mirror GeminiTtsClient.kt:
#   GEMINI_TTS_STYLE_DIRECTIVE_WEATHER_FORECASTER  → DEFAULT_DIRECTIVE
#   ACCENT_DIRECTIVES["en-GB"]                     → DEFAULT_ACCENT
#   DEFAULT_GEMINI_TTS_VOICE                       → DEFAULT_VOICE
# Sample text matches scripts/eval-weather-report-style.py so clips from
# this helper are directly comparable to the eval rig's output.
DEFAULT_DIRECTIVE = (
    "Read the following weather forecast in the style of a weather report on a "
    "national news service. Enunciate clearly and use a measured speed. "
    "Accentuate the ends of sentences and give a gentle emphasis to clothing "
    "recommendations. No audio effects, background noise, or vinyl-style "
    "texture."
)
DEFAULT_ACCENT = "Speak with a Standard British accent — clear and natural."
DEFAULT_TEXT = "Today will be cold to cool. Wear a jumper and jacket."
DEFAULT_VOICE = "Leda"


def synthesize(prompt: str, voice: str, model: str, api_key: str) -> bytes:
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
        f"{model}:generateContent?key={api_key}"
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
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--directive",
        default=DEFAULT_DIRECTIVE,
        help="Style directive (default: shipped WEATHER_FORECASTER).",
    )
    parser.add_argument(
        "--accent",
        default=DEFAULT_ACCENT,
        help="Accent clause (default: shipped en-GB). Pass '' to omit.",
    )
    parser.add_argument(
        "--voice",
        default=DEFAULT_VOICE,
        help=f"Gemini voice name (default: {DEFAULT_VOICE}).",
    )
    parser.add_argument(
        "--text",
        default=DEFAULT_TEXT,
        help="Text to speak (default: cold/jumper sample).",
    )
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
        help=f"Gemini TTS model id (default: {DEFAULT_MODEL}).",
    )
    parser.add_argument(
        "--out",
        default="/tmp/say.wav",
        help="Output WAV path (default: /tmp/say.wav).",
    )
    parser.add_argument(
        "--no-play",
        action="store_true",
        help="Write the WAV but don't invoke aplay.",
    )
    parser.add_argument(
        "--print-prompt",
        action="store_true",
        help="Print the assembled prompt before calling the API.",
    )
    args = parser.parse_args()

    api_key = os.environ.get("GEMINI_API_KEY", "")
    if not api_key:
        raise SystemExit("Set GEMINI_API_KEY env var before running.")

    # Match GeminiTtsClient.kt's prompt assembly: directive + "\n\n" +
    # (accent + "\n\n" if accent) + text. We rstrip the directive/accent
    # in case the caller pastes a constant that already ends with "\n\n",
    # so we don't end up with quadruple newlines.
    prompt = args.directive.rstrip() + "\n\n"
    if args.accent:
        prompt += args.accent.rstrip() + "\n\n"
    prompt += args.text

    if args.print_prompt:
        print("--- prompt ---")
        print(prompt)
        print("--- end ---")

    pcm = synthesize(prompt, args.voice, args.model, api_key)
    out = pathlib.Path(args.out)
    write_wav(out, pcm)
    duration = len(pcm) // 2 / SAMPLE_RATE
    print(f"Wrote {out} ({duration:.1f}s, {args.voice} on {args.model}).")

    if not args.no_play:
        subprocess.run(["aplay", "-q", str(out)], check=False)


if __name__ == "__main__":
    main()
