# Project-level R8/Proguard rules. Defaults from proguard-android-optimize.txt apply.

# Ktor ships io.ktor.util.debug.IntellijIdeaDebugDetector which references
# java.lang.management.ManagementFactory / RuntimeMXBean — JVM-only classes
# that don't exist on Android. Suppress the R8 missing-class error so the
# release build succeeds; the debug-detector code path is unreachable on Android.
-dontwarn io.ktor.util.debug.**
-dontwarn java.lang.management.**
