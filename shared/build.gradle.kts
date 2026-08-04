import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// The Android target needs the Android Gradle plugin and a local SDK. To keep the
// JVM/Wasm build working on machines (and CI) without the Android SDK installed, the
// Android bits below are switched on only when an SDK is actually present. When it is
// not, the plugin is never applied and none of its (Google-hosted) artifacts are
// fetched — everything else builds exactly as before.
val androidSdkAvailable: Boolean = rootProject.extra["androidSdkAvailable"] as Boolean
if (androidSdkAvailable) {
    pluginManager.apply("com.android.library")
}

kotlin {
    // Consumed by the Ktor backend.
    jvm()

    // Consumed by the Android app (only when the SDK is available to build it).
    if (androidSdkAvailable) {
        androidTarget()
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
// separate script that is compiled and applied only when the SDK (and therefore the
// plugin) is present. Referencing those types from this always-compiled file would
// break the build on SDK-less machines.
if (androidSdkAvailable) {
    apply(from = rootProject.file("gradle/android-shared-library.gradle.kts"))
}
