#!/usr/bin/env python3
"""
Dumps voices from the ElevenLabs API as `TtsVoiceOption(...)` Kotlin lines
ready to paste into `app/src/main/kotlin/app/clothescast/tts/TtsVoices.kt`.

Why this exists
---------------
`ELEVENLABS_VOICES` in TtsVoices.kt is currently English-only — the picker
filter empties out for every non-English `VoiceLocale` and falls back to
the unfiltered list, so the variants we offer in Settings don't actually
narrow the list. Curating non-English voices needs real voice IDs from the
ElevenLabs library, which the project's "verify against the live endpoint"
rule (see CLAUDE.md) says we shouldn't make up from web search.

What it does
------------
Hits the ElevenLabs API with your BYOK key, groups voices by detected
locale, maps each locale to a `VoiceLocale` enum value, and prints Kotlin
that you paste into TtsVoices.kt. Intended to be re-run when ElevenLabs
adds voices.

Usage
-----
    export ELEVENLABS_API_KEY=xi-...
    python3 scripts/dump-elevenlabs-voices.py [--source shared|user] \\
                                             [--per-locale N]

    --source shared  pulls /v1/shared-voices (the public community library —
                     much wider language coverage; default)
    --source user    pulls /v1/voices (voices in your account, including
                     cloned ones)
    --per-locale N   cap voices per locale (default 3) to keep the picker
                     scannable

Output is Kotlin to stdout; redirect to a file or paste directly. The script
prints a comment header naming the source endpoint and the date so the
provenance of the curated list is obvious in the diff.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from datetime import date

API_HOST = "https://api.elevenlabs.io"
DEFAULT_PER_LOCALE = 3
PAGE_SIZE = 100

# Maps detected (language, country) pairs to VoiceLocale enum names. Keys are
# lowercased — we lowercase the API response too. The country is optional;
# language-only entries (None country) are the fallback for that language.
#
# Mirrors the VoiceLocale enum in :core:domain — keep in sync. The script
# emits `VoiceLocale.<NAME>` literals, so a typo here surfaces at compile
# time when the user pastes the output.
LOCALE_MAP: dict[tuple[str, str | None], str] = {
    ("en", "us"): "EN_US",
    ("en", "gb"): "EN_GB",
    ("en", "au"): "EN_AU",
    ("en", "ca"): "EN_CA",
    ("en", "za"): "EN_ZA",
    ("en", None): "EN_US",  # unspecified English defaults to US
    ("de", None): "DE_DE",
    ("de", "de"): "DE_DE",
    ("fr", "fr"): "FR_FR",
    ("fr", "ca"): "FR_CA",
    ("fr", None): "FR_FR",
    ("it", None): "IT_IT",
    ("it", "it"): "IT_IT",
    ("es", "es"): "ES_ES",
    ("es", "mx"): "ES_MX",
    ("es", None): "ES_ES",
    ("ru", None): "RU_RU",
    ("ru", "ru"): "RU_RU",
    ("pl", None): "PL_PL",
    ("pl", "pl"): "PL_PL",
    ("hr", None): "HR_HR",
    ("hr", "hr"): "HR_HR",
    ("uk", None): "UK_UA",
    ("uk", "ua"): "UK_UA",
    ("pt", "br"): "PT_BR",
    ("pt", None): "PT_BR",  # ElevenLabs leans Brazilian for ambiguous pt
    ("nl", None): "NL_NL",
    ("nl", "nl"): "NL_NL",
    ("sv", None): "SV_SE",
    ("sv", "se"): "SV_SE",
    ("tr", None): "TR_TR",
    ("tr", "tr"): "TR_TR",
    ("id", None): "ID_ID",
    ("id", "id"): "ID_ID",
    ("fil", None): "FIL_PH",
    ("tl", None): "FIL_PH",  # Tagalog tag — same locale on Android
    ("vi", None): "VI_VN",
    ("vi", "vn"): "VI_VN",
    ("zh", "cn"): "ZH_CN",
    ("zh", None): "ZH_CN",
    ("hi", None): "HI_IN",
    ("hi", "in"): "HI_IN",
    ("bn", None): "BN_BD",
    ("bn", "bd"): "BN_BD",
    ("ja", None): "JA_JP",
    ("ja", "jp"): "JA_JP",
    ("ko", None): "KO_KR",
    ("ko", "kr"): "KO_KR",
    ("ar", "sa"): "AR_SA",
    ("ar", "eg"): "AR_EG",
    ("ar", "ae"): "AR_AE",
    ("ar", "ma"): "AR_MA",
    ("ar", None): "AR_SA",  # default unspecified Arabic to MSA / Saudi tag
    ("he", None): "HE_IL",
    ("he", "il"): "HE_IL",
    ("iw", None): "HE_IL",  # legacy code for Hebrew
    ("fa", None): "FA_IR",
    ("fa", "ir"): "FA_IR",
}


def fetch(endpoint: str, key: str, params: dict[str, str | int]) -> dict:
    qs = urllib.parse.urlencode(params)
    url = f"{API_HOST}{endpoint}?{qs}"
    req = urllib.request.Request(url, headers={"xi-api-key": key, "accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")[:500]
        sys.exit(f"HTTP {e.code} from {endpoint}: {body}")
    except urllib.error.URLError as e:
        sys.exit(f"Network error hitting {endpoint}: {e.reason}")


def fetch_shared(key: str) -> list[dict]:
    """Pulls the entire public library, paginated. Returns raw voice dicts."""
    out: list[dict] = []
    page = 0
    while True:
        # /v1/shared-voices takes `page` (0-indexed) + `page_size`. Field names
        # for the response have shifted historically — handle both the modern
        # `{"voices": [...], "has_more": bool}` envelope and the legacy
        # top-level list shape. Branch on the type first because list has no
        # `.get`, so probing that way blows up before the fallback runs.
        body = fetch("/v1/shared-voices", key, {"page": page, "page_size": PAGE_SIZE})
        if isinstance(body, list):
            chunk = body
            has_more = len(chunk) >= PAGE_SIZE
        else:
            chunk = body.get("voices", [])
            has_more = bool(body.get("has_more", False))
        if not chunk:
            break
        out.extend(chunk)
        if not has_more and len(chunk) < PAGE_SIZE:
            break
        page += 1
        if page > 50:  # safety stop — 5000 voices is plenty
            break
    return out


def fetch_user(key: str) -> list[dict]:
    body = fetch("/v1/voices", key, {})
    return body.get("voices", [])


def detect_locale(voice: dict) -> tuple[str, str | None] | None:
    """Best-effort language/country extraction from a voice record.

    The API has shipped several shapes over time:
      - `labels: { language: 'en', accent: 'american', ... }`
      - `verified_languages: [{ language: 'ar', accent: 'egyptian' }, ...]`
      - `language: 'en-US'`
    We try them in that order and lowercase everything.
    """
    labels = voice.get("labels") or {}
    lang = (labels.get("language") or "").lower().strip() or None
    accent = (labels.get("accent") or "").lower().strip() or None

    if not lang:
        verified = voice.get("verified_languages") or []
        if verified:
            v0 = verified[0]
            lang = (v0.get("language") or "").lower().strip() or None
            accent = accent or (v0.get("accent") or "").lower().strip() or None

    if not lang:
        raw = (voice.get("language") or "").lower().strip()
        if "-" in raw:
            lang, country = raw.split("-", 1)
            return (lang, country)
        if raw:
            return (raw, None)

    if not lang:
        return None

    # Map descriptive accent strings to ISO country codes where we can.
    accent_country = {
        "american": "us",
        "british": "gb",
        "australian": "au",
        "canadian": "ca",
        "south african": "za",
        "egyptian": "eg",
        "saudi": "sa",
        "emirati": "ae",
        "gulf": "ae",
        "moroccan": "ma",
        "maghrebi": "ma",
        "mexican": "mx",
        "castilian": "es",
        "parisian": "fr",
        "quebecois": "ca",
        "brazilian": "br",
    }
    return (lang, accent_country.get(accent or "", None))


def kotlin_string(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--source", choices=("shared", "user"), default="shared")
    p.add_argument("--per-locale", type=int, default=DEFAULT_PER_LOCALE)
    args = p.parse_args()

    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key:
        sys.exit("ELEVENLABS_API_KEY not set — export your BYOK key first.")

    voices = fetch_shared(key) if args.source == "shared" else fetch_user(key)
    if not voices:
        sys.exit(f"No voices returned from the {args.source} endpoint.")

    grouped: dict[str, list[dict]] = defaultdict(list)
    skipped_unmapped: list[tuple[str, str | None]] = []
    skipped_no_locale = 0
    for v in voices:
        loc = detect_locale(v)
        if loc is None:
            skipped_no_locale += 1
            continue
        # Try (lang, country) first, then language-only fallback.
        enum_name = LOCALE_MAP.get(loc) or LOCALE_MAP.get((loc[0], None))
        if not enum_name:
            skipped_unmapped.append(loc)
            continue
        grouped[enum_name].append(v)

    print(f"// Auto-generated by scripts/dump-elevenlabs-voices.py from")
    print(f"// /v1/{'shared-voices' if args.source == 'shared' else 'voices'} on {date.today().isoformat()}.")
    print(f"// Edit the script, not this list, when refreshing.")
    print()
    print("val ELEVENLABS_VOICES: List<TtsVoiceOption> = listOf(")

    for enum_name in sorted(grouped):
        bucket = grouped[enum_name][: args.per_locale]
        print(f"    // {enum_name}")
        for v in bucket:
            voice_id = v.get("voice_id") or v.get("id") or ""
            name = v.get("name") or ""
            labels = v.get("labels") or {}
            descriptor_bits = [
                labels.get("description") or labels.get("descriptive"),
                labels.get("gender"),
            ]
            descriptor = ", ".join(b for b in descriptor_bits if b)
            display = f"{name} — {descriptor}" if descriptor else name
            preview = v.get("preview_url") or ""
            if preview:
                print(f"    // preview: {preview}")
            print(
                f"    TtsVoiceOption({kotlin_string(voice_id)}, "
                f"{kotlin_string(display)}, VoiceLocale.{enum_name}),"
            )
    print(")")

    # Diagnostics on stderr so they don't pollute the Kotlin output.
    if skipped_no_locale:
        print(f"// {skipped_no_locale} voice(s) skipped: no language metadata", file=sys.stderr)
    if skipped_unmapped:
        unique = sorted(set(skipped_unmapped))
        print(f"// {len(skipped_unmapped)} voice(s) skipped: unmapped locales:", file=sys.stderr)
        for u in unique:
            print(f"//   {u[0]}-{u[1] or '*'}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
