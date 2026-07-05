import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// `google-services.json` is gitignored, so the plugin can't run in CI without
// secret restoration. Apply only when the file is present — local developers
// keep Firebase working, CI without the file still builds a distributable APK
// (Firestore community-reports dependency stays inert in that case).
if (rootProject.file("app/google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.sysadmindoc.nimbus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sysadmindoc.nimbus"
        minSdk = 26
        targetSdk = 36
        versionCode = 105
        versionName = "1.25.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Emit Room schema JSON to `app/schemas/` so migrations can be diffed
        // against committed baselines in code review and regression-tested.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    // Read signing credentials from local.properties (or local env in CI).
    // If the keystore file is missing (CI run without restored secrets) we
    // skip `signingConfigs` entirely so `validateSigningStandardRelease`
    // doesn't abort the build — the resulting APK is unsigned and users
    // or the release workflow must sign it before distribution.
    val releaseProps = Properties().apply {
        val propsFile = rootProject.file("local.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { load(it) }
        }
    }
    val releaseKeystore = file(releaseProps.getProperty("RELEASE_STORE_FILE", "../zeuswatch.jks"))
    val hasReleaseKeystore = releaseKeystore.exists()

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseProps.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = releaseProps.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = releaseProps.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            // Includes Google Play Services for location
        }
        create("freenet") {
            dimension = "distribution"
            // F-Droid compatible: no proprietary deps, uses Android LocationManager
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    // Adaptive Layout
    implementation(libs.compose.material3.windowsize)
    implementation(libs.window)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Hilt WorkManager
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Storage
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.tink.android)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Location (standard flavor only)
    "standardImplementation"(libs.play.services.location)

    // Wearable DataLayer sync (standard flavor only)
    "standardImplementation"(libs.play.services.wearable)

    // Gemini Nano on-device AI (standard flavor only — requires Google AI Core)
    "standardImplementation"("com.google.ai.edge.aicore:aicore:0.0.1-exp02")

    // Animations
    implementation(libs.lottie.compose)

    // Widgets
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Background Work
    implementation(libs.work.runtime)
    implementation(libs.profileinstaller)

    // Coroutines
    implementation(libs.coroutines.android)

    // Immutable Collections
    implementation(libs.kotlinx.collections.immutable)

    // Charts
    implementation(libs.vico.compose)

    // MapLibre (Phase 3)
    implementation(libs.maplibre)

    // Firebase (Phase 3.8 — Community Reports) — standard flavor only
    // NOTE: Requires google-services.json in app/ directory.
    // Configure at https://console.firebase.google.com and download the config file.
    "standardImplementation"(platform("com.google.firebase:firebase-bom:34.12.0"))
    "standardImplementation"("com.google.firebase:firebase-firestore")
    // Anonymous Authentication — binds community reports to an anonymous account
    // so Firestore rules can enforce ownerUid + server-side write-rate limiting
    // without any personal sign-in. Requires Anonymous sign-in enabled in console.
    "standardImplementation"("com.google.firebase:firebase-auth")
    // App Check attestation for the only user-writable backend surface.
    "standardImplementation"("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug:18.0.0")

    // ACRA — crash reporting that works in both standard and freenet flavors
    // (no Google Play Services dependency). Core + mail sender so F-Droid
    // users can opt-in to emailing logs without any remote backend.
    implementation(libs.acra.core)
    implementation(libs.acra.mail)
    implementation(libs.acra.dialog)

    // Desugaring
    coreLibraryDesugaring(libs.desugar)

    // Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.core.testing)

    // Instrumented / Compose UI Tests
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.hilt.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.coroutines.test)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
    baselineProfile(project(":benchmark"))
}
