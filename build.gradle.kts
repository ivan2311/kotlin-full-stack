// Put the Android Gradle plugin on the buildscript classpath ONLY when Android is opted
// in (`-Ppredictor.android=true`). Applying it by id in the subprojects
// (`pluginManager.apply("com.android.…")`) needs it here first. Guarding it this way
// means that with Android off — the default, and what CI runs — AGP is never resolved
// (it lives on Google's Maven, which some environments block), so the JVM/iOS/Wasm build
// is completely unaffected.
buildscript {
    val androidEnabled = (providers.gradleProperty("predictor.android").orNull ?: "false").toBoolean()
    // Firebase App Distribution is a further opt-in on top of Android: it's only useful for
    // shipping the Android app to testers, and its plugin also resolves from Google's Maven.
    // Requesting it without Android makes no sense, so it's gated on Android being on too.
    val firebaseEnabled = androidEnabled &&
        (providers.gradleProperty("predictor.firebase").orNull ?: "false").toBoolean()
    if (androidEnabled) {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:8.5.2")
            if (firebaseEnabled) {
                // Registers the `com.google.firebase.appdistribution` plugin (and its
                // `appDistributionUpload<Variant>` tasks) so :web can apply it by id below.
                classpath("com.google.firebase:firebase-appdistribution-gradle:5.1.1")
            }
        }
    }
}

// Root build file. All real configuration lives in the per-module build files;
// this only wires up the plugin versions so they can be applied without a version
// in each subproject.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ktor) apply false
}

// Whether to build the Android target/app. It's an explicit opt-in (default off) rather
// than auto-detected, because CI runners often have an Android SDK on PATH yet only run
// the JVM/iOS/Wasm tasks — auto-detecting there would apply the Android Gradle plugin
// (and its whole configuration) for no reason and break those builds. Enable it with
// `-Ppredictor.android=true` (or set `predictor.android=true` in gradle.properties) on a
// machine that has the Android SDK. Read by the subprojects via `extra`.
val androidEnabled: Boolean =
    (findProperty("predictor.android") as String?)?.toBoolean() ?: false

extra["androidEnabled"] = androidEnabled

// Whether to wire Firebase App Distribution into the Android app (`-Ppredictor.firebase=true`).
// It's a strict add-on to the Android opt-in: distribution only ever ships the Android build,
// and the plugin resolves from Google's Maven, so — like AGP — it stays off by default and out
// of the JVM/iOS/Wasm build entirely. Requesting it without the Android opt-in is a mistake we
// surface loudly rather than silently ignore.
val firebaseRequested: Boolean =
    (findProperty("predictor.firebase") as String?)?.toBoolean() ?: false
if (firebaseRequested && !androidEnabled) {
    throw GradleException(
        "predictor.firebase=true requires predictor.android=true — Firebase App Distribution " +
            "ships the Android app, which needs the Android SDK opt-in. Add -Ppredictor.android=true."
    )
}
extra["firebaseEnabled"] = firebaseRequested && androidEnabled
