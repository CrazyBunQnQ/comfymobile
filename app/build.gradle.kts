plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "sh.hnet.comfychair"
    compileSdk = 36

    defaultConfig {
        applicationId = "sh.hnet.comfychair"
        minSdk = 33
        targetSdk = 36
        // CI test builds count up so each one installs as an update
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(1000) ?: 40
        versionName = "v0.8.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Fixed key for CI test builds (from GitHub secrets), so updates install over the previous build
        val devKeystore = System.getenv("DEV_KEYSTORE_FILE")
        if (devKeystore != null && file(devKeystore).exists()) {
            create("devRelease") {
                storeFile = file(devKeystore)
                storePassword = System.getenv("DEV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DEV_KEY_ALIAS")
                keyPassword = System.getenv("DEV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        // Small, shrunk test build for sideloading (CI publishes this)
        create("dev") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("devRelease") ?: signingConfigs.getByName("debug")
        }
        // Sideloading build that coexists with an installed release of the app.
        // The release applicationId is kept for the published variants.
        create("refsmoke") {
            initWith(getByName("release"))
            applicationIdSuffix = ".refsmoke"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Disable baseline profile generation for F-Droid reproducible builds
// https://f-droid.org/docs/Reproducible_Builds/
tasks.whenTaskAdded {
    if (name.contains("ArtProfile")) {
        enabled = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    // OkHttp for HTTP/HTTPS requests and WebSocket communication
    implementation(libs.okhttp)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle and ViewModel for Compose
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation Compose
    implementation(libs.navigation.compose)

    // Media3 for video playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)

    // Markdown parsing
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.strikethrough)

    // Security (encrypted SharedPreferences)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}