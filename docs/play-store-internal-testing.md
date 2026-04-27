# Play Store internal testing — auto-publish setup

One-time Play Console + GitHub Secrets setup so every push to `main` produces a
signed AAB that lands on the Play Store **Internal testing** track without you
clicking anything in the Play Console.

## What this gets you

- Push to `main` → CI builds + signs the release AAB → uploads to Play Console
  on the `internal` track with `status: completed` → testers in the internal
  list get the new version on next Play Store check (typically minutes to a
  few hours, depending on Play caching on each device).
- Independent of Firebase App Distribution, which keeps shipping the **debug**
  APK to the same testers via push notification — different signing key,
  different package id (`app.clothescast.debug`), so the two coexist on the
  same device.

## Prerequisites

- The Play Console app listing exists (you've created the app in Play Console
  and uploaded a first AAB by hand at least once *or* the API call below
  successfully creates the first internal-track release for you — Google has
  flipped this requirement back and forth; in practice the very first upload
  sometimes needs to be a manual browser upload).
- Internal testing track has at least one tester email or list.
- The upload AAB signing chain is set up (`UPLOAD_KEYSTORE_BASE64` + companion
  secrets — see `app/build.gradle.kts` and the existing CI step). Play App
  Signing re-signs with the actual signing key on download.

## Setup checklist (one-time, ~15 minutes)

### 1. Create a Google Cloud service account

[console.cloud.google.com](https://console.cloud.google.com) → pick the
project linked to your Play Console (Play Console → **Setup → API access**
shows which GCP project, and lets you link one if none yet).

- IAM & Admin → **Service Accounts → Create service account**.
- Name: `play-publisher-ci` (anything sensible).
- Skip the optional role-grant step — Play permissions are granted in Play
  Console, not via GCP IAM.
- Open the new service account → **Keys → Add key → Create new key → JSON**.
  A JSON file downloads. Copy its entire contents (curly braces and all).
  **Don't commit it.**

### 2. Grant the service account release permission in Play Console

Play Console → **Setup → API access**.

- If you haven't already, click the prompt to **link** your GCP project. The
  service account from step 1 should appear in the list.
- Next to the service account, click **Grant access**.
- App permissions: add this app (ClothesCast).
- Account permissions: at minimum, **Release apps to testing tracks** and
  **Release to production, exclude devices, and use Play App Signing**
  (the latter is needed to upload AABs that Play re-signs). The "Admin (all
  permissions)" preset also works but is more access than needed.
- **Invite user** → **Send invite**. Permissions take effect within a minute.

### 3. Add the GitHub Secret

GitHub repo → **Settings → Secrets and variables → Actions → New repository
secret**.

| Name | Value |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | The full JSON content from step 1 (paste the whole `{ … }` block). |

### 4. Trigger a build

Push any commit to `main` (or merge a PR). The CI run on `main` should now
finish with an "Upload AAB to Play Store internal track" step. Watch for
green.

## Troubleshooting

- **"Upload AAB to Play Store internal track" is skipped** → `PLAY_SERVICE_ACCOUNT_JSON`
  isn't set, or you pushed to a feature branch (only `main` publishes).
- **`APK specifies a version code that has already been used`** → Play
  rejects re-uploads with the same `versionCode`. The repo derives
  `versionCode` from `git rev-list --count HEAD`, so a force-push to `main`
  that doesn't increase the count would collide. Land a real new commit.
- **`The caller does not have permission`** → step 2 didn't take effect, or
  the service account in step 1 doesn't match the one granted access. Check
  Play Console → API access shows the same email as the JSON's
  `client_email`.
- **`Package not found: app.clothescast`** → Play Console listing doesn't
  exist yet, or `applicationId` doesn't match. Verify in Play Console that
  the package id is exactly `app.clothescast`.
- **`Changes cannot be sent for review automatically`** → can happen on the
  very first internal release if Play Console is mid-review of the listing.
  Either wait for the listing review to complete, or set
  `changesNotSentForReview: true` on the action (then push the green
  "Send for review" button manually in Play Console once).

## Relationship with Firebase App Distribution

Both auto-publish on push to `main` and they're complementary, not redundant:

| | Firebase App Distribution | Play Store internal track |
|---|---|---|
| What ships | debug APK (`app.clothescast.debug`) | release AAB (`app.clothescast`) |
| Signing | debug keystore | upload key → Play App Signing |
| Install path | App Tester app, push notification | Play Store, internal-testing opt-in URL |
| Audience | engineers iterating on builds | shape-of-prod testing, store-flow QA |
| Latency | ~30s after CI green | minutes to hours (Play cache) |

If you only want one of them on a given push, the other can be disabled by
removing the relevant secret — both steps no-op cleanly when their secret is
unset.
