plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "dev.forgeotalab"
    compileSdk = 35

    // Release signing — properties loaded from ~/.gradle/gradle.properties
    // or CI environment. NEVER check keystore into source control.
    signingConfigs {
        create("release") {
            val keystorePath = findProperty("FORGE_KEYSTORE_PATH") as? String
            storeFile = keystorePath?.let { file(it) }
            storePassword = findProperty("FORGE_KEYSTORE_PASSWORD") as? String ?: ""
            keyAlias = findProperty("FORGE_KEY_ALIAS") as? String ?: ""
            keyPassword = findProperty("FORGE_KEY_PASSWORD") as? String ?: ""
        }
    }

    defaultConfig {
        applicationId = "dev.forgeotalab"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ABI filters match what cargo-ndk builds in :core-extractor-rs
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use release signing config when keystore is available
            val keystorePath = findProperty("FORGE_KEYSTORE_PATH") as? String
            if (keystorePath != null && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export for @AutoMigration support.
// WHY: Schema JSON must be versioned so Room can generate migration code
// automatically. Without this, additive-only migration (PRD policy) would
// require writing manual Migration classes for every schema change.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Project modules
    implementation(project(":shared-contracts"))
    implementation(project(":core-extractor-rs"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.navigation)
    implementation(libs.compose.lifecycle.runtime)
    implementation(libs.compose.lifecycle.viewmodel)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.core.ktx)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room persistence
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    // WorkManager — extraction foreground service and job scheduling
    implementation(libs.work.runtime.ktx)

    // Hilt WorkManager integration — @HiltWorker support
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler) // Also needed for hilt-work code generation

    // DocumentFile — SAF tree URI operations for extraction output
    implementation(libs.documentfile)

    // DataStore — reactive preferences for theme, onboarding, consent
    implementation(libs.datastore.preferences)

    // Ed25519 signature verification — adapter manifest security
    implementation(libs.bouncycastle)

    // HTTP client — adapter manifest refresh
    implementation(libs.okhttp)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Firebase — opt-in crash reporting (PRD §Permissions: opt-in only)
    // WHY platform BOM: Single version management for all Firebase libs.
    // Crashlytics collection is disabled by default in AndroidManifest.xml
    // and programmatically in CrashReportingManager — only user opt-in enables it.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // Protobuf — declared for manifest parsing in later slices
    implementation(libs.protobuf.javalite)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.espresso.accessibility)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.truth)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}
