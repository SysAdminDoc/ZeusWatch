import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sysadmindoc.nimbus.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sysadmindoc.nimbus.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = 53
        versionName = "1.14.0"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { stream -> props.load(stream) }
            }
            storeFile = file(props.getProperty("RELEASE_STORE_FILE", "../zeuswatch.jks"))
            storePassword = props.getProperty("RELEASE_STORE_PASSWORD", "")
            keyAlias = props.getProperty("RELEASE_KEY_ALIAS", "")
            keyPassword = props.getProperty("RELEASE_KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose for Wear OS
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation("androidx.wear.compose:compose-material3:1.0.0-alpha27")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.compose:compose-navigation:1.4.1")

    // Wear OS Tiles
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.tiles:tiles-material:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")

    // Complications
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Networking (shared with phone app)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Location
    implementation(libs.play.services.location)

    // Wearable DataLayer sync
    implementation(libs.play.services.wearable)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation("androidx.concurrent:concurrent-futures:1.2.0")

    // Coroutines
    implementation(libs.coroutines.android)

    // Desugaring
    coreLibraryDesugaring(libs.desugar)
}
