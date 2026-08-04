# Firebase App Distribution

The Android client (`:web`) can be shipped to testers with
[Firebase App Distribution](https://firebase.google.com/docs/app-distribution). The wiring
follows this repo's Android philosophy: it is an **explicit opt-in** that stays completely
out of the default JVM/iOS/Wasm build, so regular development and CI are unaffected.

Two Gradle flags gate it:

| Flag                       | Turns on                                                       |
|----------------------------|---------------------------------------------------------------|
| `-Ppredictor.android=true` | The Android target + app (requires the Android SDK)            |
| `-Ppredictor.firebase=true`| The `com.google.firebase.appdistribution` plugin + its tasks  |

Firebase requires Android (it ships the Android app), so `-Ppredictor.firebase=true` on its
own fails fast with a clear message. With both off — the default — neither the Android
Gradle plugin nor the Firebase plugin is ever resolved.

## Where the pieces live

- **`build.gradle.kts`** (root) — puts the `firebase-appdistribution-gradle` plugin on the
  buildscript classpath, but only when both opt-ins are on.
- **`web/build.gradle.kts`** — applies the `com.google.firebase.appdistribution` plugin and
  the config script below when Firebase is on.
- **`gradle/android-firebase-app-distribution.gradle.kts`** — the `firebaseAppDistribution { }`
  config. It reads every value from a Gradle property or an environment variable, so **no
  secrets live in the repo**. It distributes the **debug** APK (auto-signed with the debug
  keystore), so testers can install without a release signing config.
- **`.github/workflows/distribute-android.yml`** — a manually-triggered workflow that builds
  and uploads the APK from CI.

## One-time Firebase setup

1. Create a Firebase project (or reuse one) at <https://console.firebase.google.com>.
2. Add an **Android app** with package name `com.predictor.web`. Copy its **App ID** — it
   looks like `1:1234567890:android:0a1b2c3d4e5f`. (No `google-services.json` is needed;
   the App ID is supplied directly.)
3. In the console, open **App Distribution**, and create one or more **tester groups**
   (note each group's *alias*, e.g. `qa`).
4. Create a Google Cloud **service account** with the **Firebase App Distribution Admin**
   role and download its JSON key. This is what CI authenticates with.

## Distribute from your machine

With the Android SDK installed and the service-account JSON on disk:

```bash
./gradlew :web:assembleDebug :web:appDistributionUploadDebug \
  -Ppredictor.android=true -Ppredictor.firebase=true \
  -Pfirebase.appId="1:1234567890:android:0a1b2c3d4e5f" \
  -Pfirebase.serviceCredentialsFile="/path/to/service-account.json" \
  -Pfirebase.groups="qa" \
  -Pfirebase.releaseNotes="Local test build"
```

Every `-Pfirebase.*` value has an environment-variable equivalent, so you can export them
instead of passing flags:

| Gradle property                   | Environment variable             | Meaning                                        |
|-----------------------------------|----------------------------------|------------------------------------------------|
| `firebase.appId`                  | `FIREBASE_APP_ID`                | The Android app id (required)                  |
| `firebase.serviceCredentialsFile` | `GOOGLE_APPLICATION_CREDENTIALS` | Path to the service-account JSON               |
| `firebase.groups`                 | `FIREBASE_GROUPS`                | Comma-separated tester group aliases           |
| `firebase.testers`                | `FIREBASE_TESTERS`               | Comma-separated individual tester emails       |
| `firebase.releaseNotes`           | `FIREBASE_RELEASE_NOTES`         | Notes shown to testers                         |

Anything left unset falls back to the plugin's own defaults (the plugin natively reads
`FIREBASE_APP_ID` and `GOOGLE_APPLICATION_CREDENTIALS`, or an interactive `firebase login`).

## Distribute from CI

The **Distribute Android (Firebase App Distribution)** workflow
(`.github/workflows/distribute-android.yml`) runs on demand from the Actions tab
(*Run workflow*). Optional inputs let you set the tester groups and release notes for that
run. It stamps a fresh `versionCode`/`versionName` from the run number.

Add these repository secrets (**Settings → Secrets and variables → Actions**):

| Secret                     | Value                                                                          |
|----------------------------|--------------------------------------------------------------------------------|
| `FIREBASE_APP_ID`          | The Android app id from step 2 above                                           |
| `FIREBASE_SERVICE_ACCOUNT` | The service-account JSON, base64-encoded: `base64 -w0 service-account.json`    |
| `FIREBASE_GROUPS`          | *(optional)* default tester group aliases, used when the workflow input is blank |

The workflow decodes the credentials into a temp file, points
`GOOGLE_APPLICATION_CREDENTIALS` at it, then runs the same `assembleDebug` +
`appDistributionUploadDebug` tasks shown above.

## Notes

- **Debug vs. release.** We ship the debug variant so no release keystore is required. To
  distribute a signed release build instead, add a `signingConfig` to
  `gradle/android-web-app.gradle.kts` and run `assembleRelease` +
  `appDistributionUploadRelease`.
- **Plugin version** is pinned in the root `build.gradle.kts`
  (`firebase-appdistribution-gradle:5.1.1`), alongside the Android Gradle plugin version.
