// Plugins are declared with `apply false` at the root so subprojects share a
// single version per plugin. Without this, applying kotlin.jvm in one module
// and kotlin.android in another fails with "plugin already on the classpath
// with an unknown version" because both transitively bring the Kotlin Gradle
// Plugin in via the per-subproject plugins {} block.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
}
