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
- **`gradle/android-firebase-app-distribution.gradle`** — the `firebaseAppDistribution { }`
  config. It reads every value from a Gradle property or an environment variable, so **no
  secrets live in the repo**. It distributes the **debug** APK (auto-signed with the debug
  keystore), so testers can install without a release signing config. (Groovy, not Kotlin
  DSL, like the other `gradle/android-*.gradle` scripts — a `.gradle.kts` applied via
  `apply(from = …)` can't resolve the plugin's DSL types at compile time.)
- **`.github/workflows/distribute-android.yml`** — a manually-triggered workflow that builds
  and uploads the APK from CI.

## The configured project

This repo is already wired to the Firebase project **`predictor-5f15e`** — its Android app
id (`1:300648501675:android:5e07835ebb6baf1ee3c2ee`, for package `com.predictor.web`) is the
default `appId` in `gradle/android-firebase-app-distribution.gradle`. That app id is the
public identifier from `google-services.json` (it ships inside every APK, so it's safe to
commit); the sensitive service-account credentials are **not** in the repo. To point at a
different project, override with `-Pfirebase.appId=…` or the `FIREBASE_APP_ID` env/secret.

## One-time Firebase setup

Two things still have to be done in the Firebase console / Google Cloud before a build can
be uploaded — neither can be committed:

1. In **App Distribution**, create a **tester group**. The `qa` group is already the default
   in the Gradle config; use that alias (or override with `-Pfirebase.groups` / the workflow
   input / `FIREBASE_GROUPS`) and add testers to it in the console.
2. Create a Google Cloud **service account** with the **Firebase App Distribution Admin**
   role and download its JSON key. This is what authenticates the upload.

(To use a different Firebase project entirely, also create/register an Android app for
package `com.predictor.web` there and override the app id as noted above.)

## Distribute from your machine

With the Android SDK installed and the service-account JSON on disk:

```bash
./gradlew :web:assembleDebug :web:appDistributionUploadDebug \
  -Ppredictor.android=true -Ppredictor.firebase=true \
  -Pfirebase.serviceCredentialsFile="/path/to/service-account.json" \
  -Pfirebase.releaseNotes="Local test build"
```

The app id (`predictor-5f15e`) and tester group (`qa`) are the defaults, so you only need to
supply credentials. Override either with `-Pfirebase.appId` / `-Pfirebase.groups` when needed.

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
| `FIREBASE_SERVICE_ACCOUNT` | **Required.** The service-account JSON — paste the raw file contents, or a base64 blob (`base64 -w0 service-account.json`). Both are accepted. |
| `FIREBASE_GROUPS`          | *(optional)* overrides the default `qa` tester group when the workflow input is blank |
| `FIREBASE_APP_ID`          | *(optional)* overrides the app id baked into the Gradle config                 |

The workflow decodes the credentials into a temp file, points
`GOOGLE_APPLICATION_CREDENTIALS` at it, then runs the same `assembleDebug` +
`appDistributionUploadDebug` tasks shown above.

## Notes

- **Debug vs. release.** We ship the debug variant so no release keystore is required. To
  distribute a signed release build instead, add a `signingConfig` to
  `gradle/android-web-app.gradle` and run `assembleRelease` +
  `appDistributionUploadRelease`.
- **Plugin version** is pinned in the root `build.gradle.kts`
  (`firebase-appdistribution-gradle:5.1.1`), alongside the Android Gradle plugin version.
