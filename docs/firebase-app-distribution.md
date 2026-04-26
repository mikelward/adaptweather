# Firebase App Distribution setup

One-time Firebase + GitHub Secrets setup so every push to `main` produces an
APK that lands on your phone via push notification rather than waiting for you
to remember to grab the artifact from CI.

## What this gets you

- Push to `main` → CI builds + signs the debug APK → uploads to Firebase App
  Distribution → tester device gets a notification within ~30s of CI finishing.
- Tap the notification → install. First-time setup on the phone needs the
  **App Tester** app from Play Store, then a sign-in with the same Google
  account that was added as a tester in step 4.
- Subsequent installs upgrade in place because we sign with a stable debug
  keystore (see PR #40 / `app/build.gradle.kts:signingConfigs.debug`).

## Setup checklist (one-time, ~15 minutes)

### 1. Create the Firebase project

[console.firebase.google.com](https://console.firebase.google.com) → **Add
project**.

- Name: anything sensible — "ClothesCast". The display name is just for the
  Firebase console; it has no effect on the app.
- Skip Google Analytics when prompted (we don't use it).

### 2. Add the Android app

Inside the project, click the Android icon to add an app.

- **Android package name**: `app.clothescast.debug` *(this is the debug
  variant's applicationId — not `app.clothescast`. The `.debug` suffix
  matters; without it, FAD will reject uploads.)*
- **App nickname**: "ClothesCast Debug" or similar.
- **Debug signing certificate SHA-1**: optional, only needed for Firebase
  Auth / App Check. We don't use either, so skip.

When prompted to download `google-services.json`, **skip the download and
continue**. We don't bundle the Firebase SDK in the app — FAD doesn't need
the file on the device, only the upload pipeline needs the App ID and a
service-account key.

Skip the SDK setup steps for the same reason.

### 3. Enable App Distribution

Left nav → **Release & Monitor** → **App Distribution** → **Get started**.

### 4. Add yourself as a tester

App Distribution panel → **Testers & groups** tab.

- Click **Add group** → name it `testers` *(must match `groups: testers` in
  `.github/workflows/ci.yml`. Pick a different name only if you remember to
  change it in both places.)*
- In the new group, click **Add testers** → enter your own email address.
- Firebase will email you an invite. Accept it on your phone (the email
  links to install the **App Tester** app on Play Store).

### 5. Create a service-account key for CI

Project Settings (cog icon top-left) → **Service accounts** tab.

- Click **Generate new private key** → **Generate key**. A JSON file
  downloads.
- Open the JSON file in a text editor; copy its **entire contents** (curly
  braces and all).
- This is the credential CI uses to upload APKs. **Don't commit it.**

### 6. Get the App ID

Project Settings → **General** tab → scroll to "Your apps" → the Android app
you registered in step 2 → **App ID**.

Format: `1:1234567890:android:abcdef1234567890`. Copy it.

### 7. Add two GitHub Secrets

GitHub repo → **Settings → Secrets and variables → Actions → New repository
secret**.

| Name | Value |
|---|---|
| `FIREBASE_APP_ID` | The App ID from step 6, e.g. `1:1234567890:android:abcdef1234567890`. |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | The full JSON content from step 5 (paste the whole `{ … }` block). |

### 8. Trigger a build

Push any commit to `main` (or merge a PR). The CI run on `main` should now
finish with a "Distribute via Firebase App Distribution" step. Watch for
green; the tester device gets a push notification ~30s after CI finishes.

## Troubleshooting

- **CI says "Distribute via Firebase App Distribution skipped"** → secrets
  aren't set, or you pushed to a feature branch (only `main` distributes).
- **"FIREBASE_APP_ID is not a valid App ID"** → format must be
  `1:NNN:android:HASH`. Project Number alone (`1234567890`) is *not* enough.
- **"Service account does not have permission"** → in step 5, Firebase
  generated the right service account by default. If you re-used a
  pre-existing one, it must have the **Firebase App Distribution Admin**
  role (IAM in Google Cloud console).
- **"Tester group `testers` not found"** → you skipped step 4, or used a
  different group name. Either match `groups: testers` in
  `.github/workflows/ci.yml` or add the missing group to FAD.
- **No push notification on phone** → the App Tester app isn't installed or
  isn't signed in with the email from step 4. Email from Firebase still
  arrives; tapping the install link in the email also works.

## Future: release builds

This setup distributes the **debug** APK, signed with the debug keystore.
For release builds (minified, R8'd, signed with a release keystore), we
would:

1. Generate a release keystore + add four more secrets (same shape as the
   debug ones).
2. Add `signingConfigs.release { … }` reading those env vars.
3. Add a separate FAD upload step for `app/build/outputs/apk/release/*.apk`.

Deferred until we actually want release-grade builds in front of testers —
the debug build is fine for verifying behaviour and is closer to what
you're actively iterating on.
