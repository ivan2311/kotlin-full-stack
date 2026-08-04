import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The Android target needs the Android Gradle plugin and a local SDK. It is an explicit
// opt-in (`-Ppredictor.android=true`, see the root build) so the JVM/Wasm build works
// unchanged on machines and CI that don't ask for Android. When it's off, the plugin is
// never applied and none of its (Google-hosted) artifacts are fetched.
val androidEnabled: Boolean = rootProject.extra["androidEnabled"] as Boolean
if (androidEnabled) {
    pluginManager.apply("com.android.library")
}

kotlin {
    // Consumed by the Ktor backend.
    jvm()

    // Consumed by the Android app (only when Android is opted in). Pin the Kotlin JVM target
    // to 17 to match the Android compileOptions (Java 17); otherwise Kotlin defaults to the
    // JDK-21 toolchain and AGP fails with an inconsistent-JVM-target error. JvmTarget comes
    // from the Kotlin Gradle plugin (always on the classpath), so this is safe when off.
    if (androidEnabled) {
        androidTarget {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            }
        }
    }

    // Consumed by the iOS app. These configure on any host but compile only on
    // macOS; on other hosts their compile tasks are simply never invoked, so the
    // JVM/Wasm build is unaffected.
    iosArm64()
    iosSimulatorArm64()

    // Consumed by the Compose Multiplatform web frontend.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// The `android { }` block references Android-Gradle-plugin types, so it lives in a
// separate script that is compiled and applied only when Android is opted in (and the
// plugin is therefore present). Referencing those types from this always-compiled file
// would break the build when Android is off.
if (androidEnabled) {
    apply(from = rootProject.file("gradle/android-shared-library.gradle"))
}
