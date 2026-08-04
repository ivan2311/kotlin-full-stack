// Android configuration for the :shared library. Applied from shared/build.gradle.kts
// only when an Android SDK is available, so the Android Gradle plugin types used here
// are guaranteed to be on the classpath. On SDK-less machines this file is never
// applied (and therefore never compiled), keeping the JVM/iOS/Wasm build green.
import com.android.build.gradle.LibraryExtension

extensions.configure<LibraryExtension>("android") {
    namespace = "com.predictor.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
