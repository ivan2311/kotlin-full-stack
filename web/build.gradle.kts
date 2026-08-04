import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose.compiler)
}

// This module holds the Compose Multiplatform client. The UI (in commonMain) is shared
// verbatim by three targets: Android and iOS — the stable, primary targets — plus the
// browser (Wasm) as a bonus. The Android target needs the Android SDK + Gradle plugin,
// so it is an explicit opt-in (`-Ppredictor.android=true`, see the root build); the
// JVM/iOS/Wasm build is unaffected when it's off (the default, including in CI).
val androidEnabled: Boolean = rootProject.extra["androidEnabled"] as Boolean
if (androidEnabled) {
    pluginManager.apply("com.android.application")
}

// Firebase App Distribution — a further opt-in (`-Ppredictor.firebase=true`, see the root
// build) layered on the Android app. It adds `appDistributionUpload<Variant>` tasks that ship
// a built APK to testers. Off by default, so it never touches the JVM/iOS/Wasm build.
val firebaseEnabled: Boolean = rootProject.extra["firebaseEnabled"] as Boolean
if (firebaseEnabled) {
    pluginManager.apply("com.google.firebase.appdistribution")
}

kotlin {
    // Primary mobile target #1 — Android (only when opted in). Pin the Kotlin JVM target to
    // 17 to match the Android compileOptions (Java 17); otherwise Kotlin defaults to the
    // JDK-21 toolchain and AGP fails with an inconsistent-JVM-target error. JvmTarget comes
    // from the Kotlin Gradle plugin (always on the classpath), so this is safe when off.
    if (androidEnabled) {
        androidTarget {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }

    // Primary mobile target #2 — iOS. Each target exposes the shared UI as a framework
    // that the Xcode app links. Compiles on macOS only; configured (harmlessly) elsewhere.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PredictorApp"
            isStatic = true
        }
    }

    // Bonus target — the same UI in the browser as WebAssembly.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "web"
        browser {
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "web.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        // The entire UI, view model, theme and API client — written once, compiled for
        // every target below.
        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }

        // Each target contributes only its launcher + its Ktor engine + its base URL.
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }

    // The iOS (and, when present, Android) intermediate source sets are created by the
    // default hierarchy template; configure them lazily so they're resolved after that.
    sourceSets.configureEach {
        when (name) {
            "iosMain" -> dependencies {
                implementation(libs.ktor.client.darwin)
            }
            "androidMain" -> dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}

// The `android { }` application config uses Android-Gradle-plugin types, so — like the
// shared module — it lives in a separate script applied only when Android is opted in.
if (androidEnabled) {
    apply(from = rootProject.file("gradle/android-web-app.gradle"))
}

// The `firebaseAppDistribution { }` config uses the Firebase plugin's types, so — same as
// the Android config above — it lives in its own script applied only when Firebase is on.
if (firebaseEnabled) {
    apply(from = rootProject.file("gradle/android-firebase-app-distribution.gradle"))
}
