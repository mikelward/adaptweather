// Plugins are applied per-subproject. The root build is intentionally empty so
// configuring a pure-Kotlin module (e.g. :core:domain) does not need the Android
// Gradle Plugin to be resolvable.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
