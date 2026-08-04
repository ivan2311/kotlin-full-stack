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

// Whether an Android SDK is available on this machine. The Android target and the
// Android app module are switched on only when it is, so the project still builds
// its JVM, iOS and Wasm parts on machines (and CI) that have no Android SDK
// installed. Computed once here and read by the subprojects via `extra`.
val androidSdkAvailable: Boolean = run {
    fun envDir(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }?.let { file(it) }
    val fromEnv = (envDir("ANDROID_HOME") ?: envDir("ANDROID_SDK_ROOT"))?.isDirectory == true
    val fromLocalProps = file("local.properties").takeIf { it.exists() }?.let { props ->
        java.util.Properties().apply { props.inputStream().use { load(it) } }
            .getProperty("sdk.dir")?.takeIf { it.isNotBlank() }?.let { file(it).isDirectory } == true
    } ?: false
    fromEnv || fromLocalProps
}

extra["androidSdkAvailable"] = androidSdkAvailable
