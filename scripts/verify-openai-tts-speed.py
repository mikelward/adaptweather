#!/usr/bin/env python3
"""
Verifies whether OpenAI's `gpt-4o-mini-tts` model honours the wire `speed`
parameter shipped in PR #223.

Why this exists
---------------
PR #223 added a Speed × 0.7–1.2 slider for the OpenAI engine, mirroring
the ElevenLabs one. The slider sends `speed` on the `audio/speech`
request body — documented as supported on `tts-1` / `tts-1-hd`, but
OpenAI's docs are inconsistent about whether `gpt-4o-mini-tts` (our
default) honours it or whether pace must be steered through the
`instructions` field. ClothesCast's CLAUDE.md says don't trust web-
search guesses; verify against the live endpoint.

What it does
------------
Synthesises the same short phrase three times (speed=0.7 / 1.0 / 1.2)
with `gpt-4o-mini-tts`, voice=`alloy`, response_format=`pcm`. Compares
the byte sizes of the three responses:

  - PCM at 24 kHz mono 16-bit is ~48 kB/sec, so byte size is a direct
    proxy for audio duration.
  - If the model honours the param: the 0.7× clip is ~1.7× the byte
    size of the 1.2× clip (the audio is ~70% longer at 0.7× than at
    1.2×).
  - If the model ignores the param: all three sizes are within ~5% of
    each other (just per-call variance from sampling/silence-trim).

Optionally writes the three PCMs to `out/` so you can listen and
sanity-check by ear.

Usage
-----
    export OPENAI_API_KEY=sk-...
    python3 scripts/verify-openai-tts-speed.py
    python3 scripts/verify-openai-tts-speed.py --save out/

Exit code is 0 on a clean verdict (clear honour or clear ignore), 1 if
the result is ambiguous (e.g. mid-range size differences) or the
network call failed.
"""

from __future__ import annotations

import argparse
import os
import sys
import urllib.request
import urllib.error
import json
from pathlib import Path

API_URL = "https://api.openai.com/v1/audio/speech"
MODEL = "gpt-4o-mini-tts"
VOICE = "alloy"
# Same phrase the in-app preview uses (broadly), so the test mirrors what
# users actually hear. ~80 chars, exercises consonants + vowels enough
# that pacing is audible.
TEXT = (
    "Today will be cold. It will be seven degrees cooler than yesterday. "
    "Wear a sweater and jacket."
)
# Picker range. 0.7 → 1.2 should give a 1.71× duration ratio if speed
# lands; if it doesn't, all three will cluster near 1×.
SPEEDS = (0.7, 1.0, 1.2)
# 24 kHz, 16-bit, mono → 48000 bytes/sec. Used to print durations.
PCM_BYTES_PER_SEC = 24_000 * 2

# Margins for the verdict. Field reports of ElevenLabs's similar param
# show actual ratios slightly under the linear ideal (a 0.7× speed clip
# is ~1.5–1.7× the 1.2× one, not exactly 1.71×), so HONOURED is the
# "clearly responding" zone, IGNORED is "all clips identical to within
# variance", and AMBIGUOUS is the muddle in between.
RATIO_HONOURED_MIN = 1.30  # 0.7-clip / 1.2-clip ratio at or above this = honoured
RATIO_IGNORED_MAX = 1.10   # 0.7-clip / 1.2-clip ratio at or below this = ignored


def synthesize(api_key: str, speed: float) -> bytes:
    body = json.dumps(
        {
            "model": MODEL,
            "input": TEXT,
            "voice": VOICE,
            "response_format": "pcm",
            "speed": speed,
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        API_URL,
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def fmt_duration(byte_len: int) -> str:
    seconds = byte_len / PCM_BYTES_PER_SEC
    return f"{seconds:.2f}s"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--save",
        metavar="DIR",
        help="Write the three PCMs to DIR/ (raw 24kHz s16le mono) for ear-checking.",
    )
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("ERROR: set OPENAI_API_KEY env var.", file=sys.stderr)
        return 1

    save_dir: Path | None = None
    if args.save:
        save_dir = Path(args.save)
        save_dir.mkdir(parents=True, exist_ok=True)

    sizes: dict[float, int] = {}
    for speed in SPEEDS:
        try:
            audio = synthesize(api_key, speed)
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")[:200]
            print(f"ERROR speed={speed}: HTTP {e.code} — {body}", file=sys.stderr)
            return 1
        except urllib.error.URLError as e:
            print(f"ERROR speed={speed}: {e.reason}", file=sys.stderr)
            return 1
        sizes[speed] = len(audio)
        if save_dir:
            out = save_dir / f"speed-{speed:.2f}.pcm"
            out.write_bytes(audio)
            print(f"  wrote {out}")
        print(f"speed={speed}: {len(audio):>9,} bytes  ≈ {fmt_duration(len(audio))}")

    ratio = sizes[0.7] / sizes[1.2] if sizes[1.2] else float("inf")
    print()
    print(f"0.7 / 1.2 byte-size ratio: {ratio:.2f}× (honoured ≥ {RATIO_HONOURED_MIN:.2f}×, ignored ≤ {RATIO_IGNORED_MAX:.2f}×)")

    if ratio >= RATIO_HONOURED_MIN:
        print("VERDICT: gpt-4o-mini-tts HONOURS the wire `speed` param. Slider works as-is.")
        return 0
    if ratio <= RATIO_IGNORED_MAX:
        print("VERDICT: gpt-4o-mini-tts IGNORES the wire `speed` param.")
        print("         Consider varying the `instructions` field by slider position instead,")
        print("         or relabelling the slider to set expectations.")
        return 0
    print("VERDICT: ambiguous. Re-run a few times — TTS sampling has per-call variance.")
    if save_dir:
        print(f"         Listen to the saved files in {save_dir} to call it.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
