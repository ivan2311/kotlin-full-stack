// Firebase App Distribution config for the :web Android app. Applied from web/build.gradle.kts
// only when BOTH opt-ins are on (`-Ppredictor.android=true -Ppredictor.firebase=true`), so the
// `com.google.firebase.appdistribution` plugin — and its resolution from Google's Maven — is
// never required by the default JVM/iOS/Wasm build (nor by a plain Android build). Because the
// plugin's DSL type is only on the classpath here, this config lives in its own script, exactly
// like the AGP-typed android { } configs in this directory.
//
// Nothing here is a secret. Every value is read from a Gradle property (`-Pfirebase.*`) or an
// environment variable, so the Firebase app id and the service-account credentials stay out of
// the repo. Anything left unset falls back to the plugin's own defaults — including the
// FIREBASE_APP_ID and GOOGLE_APPLICATION_CREDENTIALS env vars the plugin already reads natively.
//
// Once configured, ship a build with e.g.:
//   ./gradlew :web:assembleDebug :web:appDistributionUploadDebug \
//     -Ppredictor.android=true -Ppredictor.firebase=true
import com.google.firebase.appdistribution.gradle.AppDistributionExtension

// A Gradle property `firebase.<name>` (or `-Pfirebase.<name>=…`) wins; otherwise the env var;
// otherwise null so the plugin default applies. Blank values are treated as unset.
fun distSetting(propName: String, envName: String): String? =
    (findProperty(propName) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

extensions.configure<AppDistributionExtension>("firebaseAppDistribution") {
    // Which Firebase app to upload to, e.g. "1:1234567890:android:0a1b2c3d4e5f". Required; the
    // plugin also reads FIREBASE_APP_ID on its own, but we thread it through a property too.
    distSetting("firebase.appId", "FIREBASE_APP_ID")?.let { appId = it }

    // Service-account JSON used to authenticate in CI. If unset, the plugin falls back to
    // GOOGLE_APPLICATION_CREDENTIALS or an interactive `firebase login`.
    distSetting("firebase.serviceCredentialsFile", "GOOGLE_APPLICATION_CREDENTIALS")
        ?.let { serviceCredentialsFile = it }

    // Recipients: comma-separated Firebase tester group aliases and/or individual emails.
    distSetting("firebase.groups", "FIREBASE_GROUPS")?.let { groups = it }
    distSetting("firebase.testers", "FIREBASE_TESTERS")?.let { testers = it }

    // Optional notes shown to testers with the build.
    distSetting("firebase.releaseNotes", "FIREBASE_RELEASE_NOTES")?.let { releaseNotes = it }

    // We distribute the APK. The debug variant is auto-signed with the debug keystore, so
    // testers can install it without a release signing config having to be wired up.
    artifactType = "APK"
}
