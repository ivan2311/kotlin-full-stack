// Android application config for the :web (Compose Multiplatform) client. Applied from
// web/build.gradle.kts only when an Android SDK is available, so the Android Gradle
// plugin types used here are on the classpath. Never applied — hence never compiled —
// on SDK-less machines, keeping the JVM/iOS/Wasm build green.
import com.android.build.api.dsl.ApplicationExtension

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.predictor.web"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.predictor.web"
        minSdk = 24
        targetSdk = 34
        // Overridable so a CI distribution build can stamp a fresh, increasing version
        // (e.g. VERSION_CODE=${{ github.run_number }}); defaults keep local builds stable.
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: 1
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
