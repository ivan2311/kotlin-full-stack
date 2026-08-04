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
