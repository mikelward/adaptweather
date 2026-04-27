plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

/**
 * Runs `git` and returns its trimmed stdout. Throws — loudly — when git is
 * unavailable or the working tree isn't a repo, rather than silently shipping
 * a bogus version. Configure-time failures are the right outcome: a release
 * APK with versionCode = 0 is worse than a build that won't start.
 */
fun git(vararg args: String): String {
    val process = ProcessBuilder("git", *args)
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exit = process.waitFor()
    check(exit == 0) { "git ${args.joinToString(" ")} failed (exit $exit): $output" }
    return output
}

// versionCode: total commit count on the current branch. Monotonically increases,
// reproducible (same commit -> same number), required by Firebase App Distribution
// and the Play Store. CI must use `actions/checkout@v4` with `fetch-depth: 0` —
// shallow clones make rev-list --count return 1 and break monotonicity across builds.
val gitCommitCount: Int = git("rev-list", "--count", "HEAD").toInt()

// versionName base. Bumped manually for marketing-meaningful releases. The short
// SHA is appended at build time so any APK in the wild can be traced to the
// commit that produced it (visible in Settings -> About at runtime).
val versionNameBase = "0.1.0"
val gitShortSha: String = git("rev-parse", "--short", "HEAD")

android {
    // Pinned to app.clothescast. Renamed from app.adaptweather as part of the
    // product rename; existing FAD testers had to uninstall + reinstall,
    // losing their encrypted API keys (Gemini, OpenAI, ElevenLabs), schedule,
    // location, and clothes rules. Once a sideloaded build with applicationId
    // X is in the wild, switching to Y means installs of Y are a different
    // app — the applicationId is the install identity; do not change it again
    // without a planned migration story.
    namespace = "app.clothescast"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.clothescast"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        // semver build metadata: <base>+<commitCount>.<shortSha>. Some downstream
        // tools (Crashlytics, Bugsnag, screenshots in bug reports) carry only
        // versionName, not versionCode — embedding the count means one string
        // identifies the build everywhere.
        versionName = "$versionNameBase+$gitCommitCount.$gitShortSha"
    }

    signingConfigs {
        // Stable debug-keystore signing. When the env vars below are present
        // (decoded from GitHub Secrets in the workflow), every CI build is
        // signed with the same identity — so the user's installed APK upgrades
        // in place rather than failing with "App not installed (signature
        // mismatch)" on each new build, which would otherwise force a wipe of
        // their encrypted Gemini key + saved settings.
        //
        // Locally, the env vars are absent, so AGP's auto-generated
        // ~/.android/debug.keystore is used (the conventional dev path —
        // unchanged behaviour for anyone running `./gradlew assembleDebug`
        // from their own machine).
        getByName("debug") {
            // All four vars must be present for the override; if only some are
            // set we'd otherwise silently install blank credentials and let
            // signing fail with a confusing message at the end of the build.
            // Treat partial configuration as user error, fail fast.
            val keystorePath = System.getenv("DEBUG_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
            val storePass = System.getenv("DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
            val alias = System.getenv("DEBUG_KEY_ALIAS")?.takeIf { it.isNotBlank() }
            val keyPass = System.getenv("DEBUG_KEY_PASSWORD")?.takeIf { it.isNotBlank() }

            val anySet = keystorePath != null || storePass != null || alias != null || keyPass != null
            val allSet = keystorePath != null && storePass != null && alias != null && keyPass != null

            if (anySet && !allSet) {
                error(
                    "Partial debug-keystore configuration. Set all of DEBUG_KEYSTORE_FILE, " +
                        "DEBUG_KEYSTORE_PASSWORD, DEBUG_KEY_ALIAS, DEBUG_KEY_PASSWORD — or none, " +
                        "to fall back to AGP's default debug keystore.",
                )
            }

            if (allSet) {
                val keystore = file(keystorePath!!)
                check(keystore.exists()) {
                    "DEBUG_KEYSTORE_FILE is set but does not exist: ${keystore.path}"
                }
                storeFile = keystore
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
        // Upload key for Play App Signing. Same env-var-or-fail pattern as the
        // debug block above: in CI the four UPLOAD_* env vars are populated from
        // GitHub Secrets and bundleRelease produces a Play-uploadable AAB; locally
        // the env vars are absent, the signing config has no storeFile, and AGP
        // fails loudly on bundleRelease/assembleRelease — which is the right
        // default (better than silently shipping a debug-signed release).
        create("release") {
            val keystorePath = System.getenv("UPLOAD_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
            val storePass = System.getenv("UPLOAD_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
            val alias = System.getenv("UPLOAD_KEY_ALIAS")?.takeIf { it.isNotBlank() }
            val keyPass = System.getenv("UPLOAD_KEY_PASSWORD")?.takeIf { it.isNotBlank() }

            val anySet = keystorePath != null || storePass != null || alias != null || keyPass != null
            val allSet = keystorePath != null && storePass != null && alias != null && keyPass != null

            if (anySet && !allSet) {
                error(
                    "Partial upload-keystore configuration. Set all of UPLOAD_KEYSTORE_FILE, " +
                        "UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD — or none, " +
                        "to leave the release signing config empty (bundleRelease will then fail).",
                )
            }

            if (allSet) {
                val keystore = file(keystorePath!!)
                check(keystore.exists()) {
                    "UPLOAD_KEYSTORE_FILE is set but does not exist: ${keystore.path}"
                }
                storeFile = keystore
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            // PNGs land *under source control* at app/snapshots/ so GitHub
            // renders image diffs natively in PR "Files changed" view — no
            // artifact download required to see what changed. CI commits any
            // new/updated PNGs back to the PR branch (see ci.yml).
            it.systemProperty(
                "roborazzi.output.dir",
                file("snapshots").absolutePath,
            )
            // Always (re-)record snapshots when tests run. The diff gate is "the
            // PNGs in the repo changed and CI committed an update" — reviewers
            // see the pixel diff directly in the PR. When the catalogue is
            // mature, switch to verifyRoborazzi* on stable surfaces for a
            // hard-fail regression gate.
            it.systemProperty("roborazzi.test.record", "true")
        }
        // Required because kotlinx-coroutines-android is on the unit-test classpath
        // (transitively via androidx.lifecycle). Without this, AndroidDispatcherFactory
        // calls the unmocked Looper.getMainLooper() and throws "not mocked", which
        // poisons MainDispatcherLoader for the rest of the JVM. With defaults enabled,
        // getMainLooper() returns null, AndroidDispatcherFactory fails cleanly, and
        // Dispatchers.setMain installs a TestMainDispatcher as expected.
        unitTests.isReturnDefaultValues = true
        // Roborazzi/Robolectric need real Android resources (drawables, strings,
        // themes) on the unit-test classpath to render Compose previews to PNG.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.vico.compose.m3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.core)

    // Encrypted on-device storage of the user's Gemini API key.
    // Tink wraps an Android-Keystore master key around a Tink AEAD keyset; ciphertext
    // is persisted in DataStore Preferences. EncryptedSharedPreferences is deprecated.
    implementation(libs.tink.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)

    // Home-screen App Widget — Glance speaks Compose at the source level but
    // emits RemoteViews under the hood, so it works back to the API 26 minSdk
    // and there's no separate XML layout to maintain.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Ktor with the OkHttp engine for production HTTP. Tests in :core:data use MockEngine,
    // so the engine choice is :app's concern.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.datastore.preferences.core)
    // SettingsViewModelTest stubs the geocoding client with a Ktor MockEngine so the
    // tests don't need network. Same library that :core:data already uses.
    testImplementation(libs.ktor.client.mock)

    // Snapshot rendering of @Preview composables to PNG. Robolectric provides the
    // Android runtime; Roborazzi captures the rendered Surface; the JUnit 4 +
    // ui-test-junit4 stack is what AndroidJUnit4 needs. Vintage engine bridges
    // the JUnit 4 tests onto the existing JUnit 5 platform so they run alongside
    // the rest of the unit tests in the same `:app:testDebugUnitTest` task.
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

configurations.matching { it.name.startsWith("test") || it.name.startsWith("debugUnitTest") }.all {
    // kotlinx-coroutines-android arrives transitively via androidx.lifecycle. Its
    // AndroidDispatcherFactory is the highest-priority MainDispatcherFactory on the
    // service-loader, and it calls Looper.getMainLooper() at static init time —
    // which throws "not mocked" against the AGP stub Android jar and poisons
    // MainDispatcherLoader for the rest of the JVM, breaking every coroutine test
    // that touches Dispatchers.setMain. Excluding it from the test classpath only
    // leaves kotlinx-coroutines-test, whose TestMainDispatcherFactory then wins the
    // service-loader race cleanly. Production code is unaffected; the runtime
    // classpath still has it (via implementation deps).
    exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
}

