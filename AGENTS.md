# Agent guide for clothescast

Rules and gotchas for AI coding agents (Claude Code, Codex, etc.) working in
this repo. Keep this file short and concrete — one-liners over essays. Add a
new rule the first time something bites you, not the third.

## Working in this repo

- This repo is an Android app (Kotlin + Compose) with two pure-Kotlin
  sub-modules: `:core:domain` and `:core:data`. Only `:core:domain` builds
  without the Android SDK; `:core:data` and `:app` need AGP loadable.
- The Android SDK is generally **not** available in agent sandboxes, and the
  Google Maven repo is often blocked. If you can't run `:app:assembleDebug`
  or `:app:testDebugUnitTest` locally, **say so explicitly** and rely on CI
  as the validation surface. Do not claim "the build passes" when you only
  ran the domain tests.
- `:core:domain:test` is the one thing you can usually run locally — use it
  to validate any pure-Kotlin domain change before pushing.

## Commits and PRs

- **Linear history.** Never merge — rebase. The repo's PRs land as a linear
  chain on `main`. A merge commit in a PR is a sign something went wrong.
- **One concern per PR.** If you're tempted to add infra (test framework, CI
  step, build wiring) alongside a feature, split it: infra PR first, feature
  PR rebased on top. Reviewers read smaller PRs faster and the feature PR's
  diff actually shows the feature.
- **Restructure unmerged commits freely.** Anything still on a feature
  branch (not yet merged to `main`) is fair game to reshape — amend, squash,
  reorder, split, drop, rebase onto a different base. When you iterate on
  the same branch and the result is a tidier story (review fixes squashed
  into the commit they fix, infra ahead of feature, no "fix typo" noise),
  do that locally and force-push instead of piling fixup commits on the
  end. Only `main`'s history is sacred — feature branches are scratch space
  until they merge.
- **Stacked PRs:** the lower PR (infra) targets `main`; the upper PR
  (feature) targets the lower PR's branch. When the lower PR merges to
  `main`, rebase the upper one onto `main` — its diff cleanly shrinks to
  just the feature work.
- **Force-pushes are routine on feature branches** (per the rule above) and
  don't need confirmation. Do still confirm before anything destructive on
  shared / merged branches: force-pushing `main`, dropping commits already
  on `main`, rewriting another author's branch.
- **Commit message style:** scope prefix, em-dash, lowercase imperative —
  e.g. `:app + :core — glanceable outfit-preview icons on Today screen`.
  Match the existing `git log` style, don't invent a new one.

## GitHub

- Use the `mcp__github__*` MCP tools for *all* GitHub operations. The `gh`
  CLI is **not** available in this sandbox.
- Open PRs as **draft** by default. Un-draft only after CI is green and you
  (or the user) have eyeballed the change.
- **Never leave a review comment thread silently dismissed.** Either reply on
  the thread *or* resolve it — the user wants every thread to end in one of
  those two states, not "left open and ignored." When you think a comment is
  a false positive, say *why* on the thread (one or two sentences is fine):
  the reasoning is exactly what the user wants surfaced, and "Linux-only CI,
  doesn't apply" is more useful on the PR than buried in chat history.
  Acknowledgement noise ("good catch, will do") is fine and preferred over
  silence; the discipline is "say something or resolve," not "say nothing."
- **Always link every open PR in the stack.** Any time you push, summarise
  CI, or invite the user to review, list every currently-open PR on the
  feature by URL — one per line — not just the topmost one. The Claude Code
  mobile UI only renders the first PR card in a message and treats later
  links as plain text, so a single link can hide the rest of the stack
  (and may surface an already-merged PR while obscuring the live one).
  Worth the extra two lines.
- **Report when Copilot finishes reviewing a fresh push.** Copilot's
  review runs asynchronously after each push; once its review event lands
  for the latest commit, surface a one-liner naming the SHA and comment
  count — e.g. `Copilot reviewed 87d9f02 — 0 comments` or `Copilot
  reviewed 87d9f02 — 3 comments, addressing now`. Tie it to the *latest*
  pushed SHA so a stale review of a superseded commit isn't conflated with
  the current state. The user uses this to know when the automated pass
  is done vs. still pending.
- **Report Android versionCode after every merge to `main`.** When a PR
  merges, fetch `main` and run `git rev-list --count origin/main` to get
  the versionCode (`app/build.gradle.kts` derives it from this count).
  Report it as e.g. `Need versionCode 72 (b81c23d) or higher to test PR
  #52's HTTP-error surfacing` — number, short SHA, and a one-clause
  summary of what the change gates. The user uses this to know which
  Firebase / locally-built APK contains their fix.
  **Sandbox clones are usually shallow** (`git rev-parse --is-shallow-repository`
  returns `true`), which silently truncates `rev-list --count` and makes the
  reported number lower than the real APK's. Run `git fetch --unshallow origin
  main` once at the start of any session that will report versionCodes — the
  user has been bitten by an under-by-15 count.
- **Keep watching merged PRs for late review comments.** Reviewers and
  bots routinely comment *after* merge (Copilot review, human follow-up).
  Stay subscribed to the PR's activity after the merge and handle each
  new comment per the "say something or resolve" rule above — reply,
  resolve, or open a follow-up PR with the fix. Stop watching once every
  comment posted on or after the merge commit has been answered or
  resolved, or after ~24h of silence with no new activity, whichever
  comes first. Don't drop the watch the moment the merge button is
  clicked.

## CI

- Two jobs: `JVM unit tests` (~2m) runs `:core:*:test` + `:app:testDebugUnitTest`;
  `Android debug build` (~3.5m) runs `:app:assembleDebug`. Both upload
  artifacts; Roborazzi PNG snapshots upload as `ui-preview-snapshots` from
  the JVM-tests job.
- After pushing, **wait for CI** before claiming a change works on Android.
  The webhook subscription delivers events; don't poll.
- **Report significant CI timing regressions.** After CI finishes on a push,
  compare the new timings against recent runs of the same job. Only call
  out *significant* slowdowns (rule of thumb: >25% or >30s on a job under
  ~5min) — don't narrate routine wobble. When you do report one, name the
  likely cause: a new heavy dependency (Robolectric cold start, a
  build-tools download), a slow new test, cache invalidation. Spotting a
  real regression early lets the user decide whether to invest in
  mitigation before more tests pile on.

## Kotlin / Compose gotchas

- **`/*` inside KDoc opens a nested block comment.** Kotlin counts pairs, so
  a literal `/*` inside `/** … */` (e.g. a path like `dir/*.png`) opens an
  inner comment that the outer `*/` then closes — leaving the outer comment
  unterminated through to EOF. Compiler reports "Unclosed comment" at the
  end of the file, far from the actual cause. Avoid literal `/*` in doc
  text: write `dir/` or `<path>.png` instead of `dir/*.png`.
- **Compose `@Preview`s in tests.** Snapshot tests live in `app/src/test`
  (Roborazzi/Robolectric). Preview wrappers live in `app/src/main/.../*Previews.kt`
  with `internal` visibility so they're reachable from tests in the same
  module. Don't make screen-internal composables `public` just for tests —
  `internal` is enough.
- **JUnit 4 + JUnit 5 coexistence.** The repo uses the JUnit 5 platform
  (`useJUnitPlatform()`); Robolectric needs `@RunWith(AndroidJUnit4::class)`
  which is JUnit 4. The bridge is `junit-vintage-engine` as `testRuntimeOnly`.
  If you see "no tests found" after adding a `@Test`-annotated JUnit 4
  class, check that `vintage-engine` is on the test classpath.

## Privacy

- **Surface any change to what we send off device.** When a change touches
  data that crosses the device boundary — anything in the rendered insight
  prose (it's fed to Gemini's TTS endpoint over the BYOK key),
  weather / geocoding requests, or future analytics / error reporting —
  call it out explicitly in the PR description and commit message. Calendar
  event titles, locations, contacts, identifiers: default is "less, not
  more" — if you're broadening what leaves the device, flag it for review
  even if it seems harmless. The TTS endpoints log requests; "the user's
  3pm standup" landing in someone's logs is exactly the surprise the user
  doesn't want.

## Domain conventions

- Clothes rules and outfit suggestions both look at *feels-like*
  temperatures (apparent, wind-chill / humidity adjusted) — never raw 2 m
  air temperature. That's what the user actually experiences stepping
  outside.
- The `:app` module owns Android concerns; LLM choice (which Gemini model
  to call) is `:app`'s problem. The `:core:domain` module is pure Kotlin
  and must stay that way — it's where the clothes / insight logic lives
  and must remain testable on a JVM.
- **Don't rename Gemini models from web-search guesses.** When a TTS / text
  model ID seems "deprecated" or "promoted to GA", verify against the live
  `ListModels` endpoint (`GET /v1beta/models?key=…`) before changing
  defaults — search snippets routinely fabricate confident-sounding GA
  names (`gemini-2.5-flash-tts`) for models that only exist as previews
  (`gemini-2.5-flash-preview-tts`). PR #59 → #60 was a same-day
  rename-and-revert because of this.
