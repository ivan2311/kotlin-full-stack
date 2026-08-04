rootProject.name = "kotlin-full-stack-predictor"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.jetbrains.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    // Versions for the Android Gradle plugin. These are only *resolved* when a module
    // actually applies the plugin, which the modules below do solely when an Android
    // SDK is present (see `androidSdkAvailable` in the module build files). On a
    // machine without the SDK — including CI here — the plugin is never applied, so
    // its artifacts are never fetched and the JVM/Wasm build is unaffected.
    plugins {
        id("com.android.application") version "8.5.2"
        id("com.android.library") version "8.5.2"
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.jetbrains.*")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Shared, multiplatform domain + business logic (JVM + Wasm/JS)
include(":shared")

// Ktor backend (JVM) — serves the REST API and the compiled web frontend
include(":server")

// Compose Multiplatform frontend, compiled to WebAssembly
include(":web")
