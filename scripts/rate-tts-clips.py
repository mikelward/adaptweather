#!/usr/bin/env python3
"""
Plays each .wav in a directory via aplay, prompts for a pair of ratings
(yours + Eva's) and optional notes, then prints a summary you can paste back
for recommendations.

Usage
-----
  python3 scripts/rate-tts-clips.py /tmp/tts-compare/
  python3 scripts/rate-tts-clips.py /tmp/tts-compare/ --pattern "en-GB"

Rating format
-------------
  <you> <eva>            e.g.  8 9    (space-separated)
  <you>+<eva>            e.g.  8+9    (plus-separated)
  <you>+<eva> - <notes>  e.g.  8+9 - sounds a bit robotic
  s                      skip (no rating recorded)
  r                      replay the clip, then re-prompt
  q                      quit early (prints results so far)
"""

import argparse
import pathlib
import re
import subprocess
import sys


def play(path: pathlib.Path) -> None:
    subprocess.run(["aplay", "-q", str(path)], check=False)


def parse_pair(raw: str) -> tuple[str, str] | None:
    """Returns (you, eva) if raw parses as two 1–10 integers; else None."""
    parts = re.split(r"[+\s/,]+", raw.strip())
    if len(parts) != 2:
        return None
    a, b = parts
    if a.isdigit() and b.isdigit() and 1 <= int(a) <= 10 and 1 <= int(b) <= 10:
        return (a, b)
    return None


def prompt_rating(path: pathlib.Path) -> tuple[str, str, str] | None:
    """Returns (score_you, score_eva, notes) or None if skipped/quit."""
    while True:
        try:
            raw = input("  Rating [you eva (1-10 each) / s=skip / r=replay / q=quit]: ").strip()
        except (EOFError, KeyboardInterrupt):
            return None
        if raw.lower() == "q":
            return None
        if raw.lower() == "s":
            return ("s", "s", "")
        if raw.lower() == "r":
            print("  Replaying...")
            play(path)
            continue
        score_part, _, notes = raw.partition("-")
        pair = parse_pair(score_part)
        if pair is not None:
            return (pair[0], pair[1], notes.strip())
        print("  Enter two numbers 1–10 (e.g. '8 9' or '8+9'), 's', 'r', or 'q'.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", help="Directory containing .wav files")
    parser.add_argument(
        "--pattern", default="", help="Only play files whose path contains this string (locale, candidate, or voice)"
    )
    args = parser.parse_args()

    directory = pathlib.Path(args.directory)
    files = sorted(f for f in directory.rglob("*.wav") if args.pattern in str(f))

    if not files:
        sys.exit(f"No .wav files found in {directory}" + (f" matching '{args.pattern}'" if args.pattern else ""))

    results: list[tuple[str, str, str, str]] = []  # (relpath, score_you, score_eva, notes)

    print(f"\nFound {len(files)} clip(s). Press Enter after aplay finishes.\n")

    for i, path in enumerate(files, 1):
        relpath = str(path.relative_to(directory))
        print(f"[{i}/{len(files)}] {relpath}")
        play(path)
        result = prompt_rating(path)
        if result is None:
            print("\n--- quit ---")
            break
        score_you, score_eva, notes = result
        if score_you != "s":
            results.append((relpath, score_you, score_eva, notes))
        print()

    if not results:
        return

    print("\n" + "=" * 60)
    print("RESULTS — your ratings:")
    print("=" * 60)
    for relpath, score_you, _, notes in results:
        line = f"  {relpath}: {score_you}"
        if notes:
            line += f" — {notes}"
        print(line)
    print("=" * 60)
    print("RESULTS — Eva's ratings:")
    print("=" * 60)
    for relpath, _, score_eva, _ in results:
        print(f"  {relpath}: {score_eva}")
    print("=" * 60)


if __name__ == "__main__":
    main()
