plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // TODO(release): pin the final namespace + applicationId BEFORE the first
    // distribution outside this dev team. Once a sideloaded build with applicationId
    // X is in the wild, switching to Y means installs of Y are a different app —
    // users have to uninstall + reinstall and lose their encrypted Gemini key,
    // schedule, location, and wardrobe rules. Convention is reverse-DNS based on
    // a domain you own (e.g. dev.mikelward.adaptweather) and a confirmed product
    // name; "AdaptWeather" itself is also up for grabs.
    namespace = "com.adaptweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adaptweather"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
            // Signing config wired in by CI; assembleRelease in dev builds will fail loudly
            // until then, which is the right default.
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
        }
        // Required because kotlinx-coroutines-android is on the unit-test classpath
        // (transitively via androidx.lifecycle). Without this, AndroidDispatcherFactory
        // calls the unmocked Looper.getMainLooper() and throws "not mocked", which
        // poisons MainDispatcherLoader for the rest of the JVM. With defaults enabled,
        // getMainLooper() returns null, AndroidDispatcherFactory fails cleanly, and
        // Dispatchers.setMain installs a TestMainDispatcher as expected.
        unitTests.isReturnDefaultValues = true
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
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.core)

    // Encrypted on-device storage of the user's Gemini API key.
    // Tink wraps an Android-Keystore master key around a Tink AEAD keyset; ciphertext
    // is persisted in DataStore Preferences. EncryptedSharedPreferences is deprecated.
    implementation(libs.tink.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)

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

