// Put the Android Gradle plugin on the buildscript classpath ONLY when Android is opted
// in (`-Ppredictor.android=true`). Applying it by id in the subprojects
// (`pluginManager.apply("com.android.…")`) needs it here first. Guarding it this way
// means that with Android off — the default, and what CI runs — AGP is never resolved
// (it lives on Google's Maven, which some environments block), so the JVM/iOS/Wasm build
// is completely unaffected.
buildscript {
    val androidEnabled = (providers.gradleProperty("predictor.android").orNull ?: "false").toBoolean()
    if (androidEnabled) {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:8.5.2")
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
