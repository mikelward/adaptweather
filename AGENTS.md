# Agent guide for adaptweather

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
- Be frugal with PR comments. Only post when there's something the reviewer
  needs that isn't already in the diff or the PR description.
- **Always link every open PR in the stack.** Any time you push, summarise
  CI, or invite the user to review, list every currently-open PR on the
  feature by URL — one per line — not just the topmost one. The Claude Code
  mobile UI only renders the first PR card in a message and treats later
  links as plain text, so a single link can hide the rest of the stack
  (and may surface an already-merged PR while obscuring the live one).
  Worth the extra two lines.

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

## Domain conventions

- Wardrobe rules and outfit suggestions both look at *feels-like*
  temperatures (apparent, wind-chill / humidity adjusted) — never raw 2 m
  air temperature. That's what the user actually experiences stepping
  outside.
- The `:app` module owns Android concerns; LLM choice (which Gemini model
  to call) is `:app`'s problem. The `:core:domain` module is pure Kotlin
  and must stay that way — it's where the wardrobe / insight logic lives
  and must remain testable on a JVM.
