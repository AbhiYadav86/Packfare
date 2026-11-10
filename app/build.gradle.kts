import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Secrets live in local.properties (never committed):
//   travelpayouts.token=YOUR_API_TOKEN
//   travelpayouts.marker=YOUR_PARTNER_ID
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.packfare.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.packfare.app"
        minSdk = 26
        targetSdk = 35
        // Base version: change these two when you start a new version (e.g. 0.17).
        // GitHub builds add the run number: 0.16.<run>; local builds stay 0.16.0.
        val baseVersionCode = 1600
        val baseVersionName = "0.16"
        val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = baseVersionCode + (ciRun ?: 0)
        versionName = if (ciRun != null) "$baseVersionName.$ciRun" else "$baseVersionName.0"

        buildConfigField("String", "TRAVELPAYOUTS_TOKEN", "\"${localProps.getProperty("travelpayouts.token", "")}\"")
        buildConfigField("String", "TRAVELPAYOUTS_MARKER", "\"${localProps.getProperty("travelpayouts.marker", "")}\"")
    }

    // A fixed test key so every test APK (from Android Studio or GitHub Actions) installs over the
    // previous one. Only for testing: make a private release key before publishing on the Play Store.
    signingConfigs {
        getByName("debug") {
            storeFile = file("packfare-test.jks")
            storePassword = "packfare-test"
            keyAlias = "packfare-test"
            keyPassword = "packfare-test"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // On-device OCR for receipts (bundled model, works offline)
    implementation(libs.mlkit.text.recognition)

    // Maps: MapLibre (open source) with free OpenFreeMap/OpenStreetMap tiles, no API key
    implementation(libs.maplibre.android)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
