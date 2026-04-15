plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.forgeotalab.nativebridge"
    compileSdk = 35

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Only build for architectures we target — reduces build time
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// ---------------------------------------------------------------------------
// Rust native library build via cargo-ndk
// ---------------------------------------------------------------------------

/**
 * WHY custom task instead of a plugin: cargo-ndk-android-gradle and
 * mozilla/rust-android-gradle plugins add complexity and version coupling.
 * A direct Exec task gives us full control over error messages, build flags,
 * and platform detection — critical for the "first-try build" goal.
 */

// Detect the cargo-ndk binary path
val cargoNdk: String = findCargoNdk()

fun findCargoNdk(): String {
    // Check if cargo-ndk is available on PATH
    val result = try {
        val process = ProcessBuilder("cargo", "ndk", "--version")
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (_: Exception) {
        false
    }

    if (!result) {
        logger.error("""
            |
            | ╔══════════════════════════════════════════════════════════════╗
            | ║  cargo-ndk is not installed!                                ║
            | ║                                                            ║
            | ║  Install it with:  cargo install cargo-ndk                 ║
            | ║  Then add Android targets:                                 ║
            | ║    rustup target add aarch64-linux-android                  ║
            | ║    rustup target add x86_64-linux-android                   ║
            | ║                                                            ║
            | ║  See README.md for full build prerequisites.               ║
            | ╚══════════════════════════════════════════════════════════════╝
            |
        """.trimMargin())
    }

    return "cargo"
}

val rustProjectDir = project.projectDir

// Target ABI → Rust target triple mapping
val abiTargets = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "x86_64" to "x86_64-linux-android",
)

val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")

tasks.register<Exec>("buildRustDebug") {
    group = "rust"
    description = "Build Rust native library for Android (debug)"
    workingDir = rustProjectDir

    val outputDir = jniLibsDir.asFile.absolutePath

    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", outputDir,
        "build",
        "-p", "forge-jni",
        // WHY: Enable the test-panic feature in debug builds so
        // NativeBridgePanicContainmentTest can verify JNI panic isolation.
        // This feature is NEVER enabled in release builds.
        "--features", "forge-jni/test-panic",
    )

    // Ensure the output directory exists
    doFirst {
        abiTargets.keys.forEach { abi ->
            file("$outputDir/$abi").mkdirs()
        }
    }
}

tasks.register<Exec>("buildRustRelease") {
    group = "rust"
    description = "Build Rust native library for Android (release)"
    workingDir = rustProjectDir

    val outputDir = jniLibsDir.asFile.absolutePath

    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", outputDir,
        "build",
        "-p", "forge-jni",
        "--release",
    )

    doFirst {
        abiTargets.keys.forEach { abi ->
            file("$outputDir/$abi").mkdirs()
        }
    }
}

// Wire correct Rust build profile into Android build lifecycle.
// WHY variant-aware: Debug builds use Rust debug profile (fast compile, test-panic
// feature for JNI containment tests). Release builds use Rust release profile
// (LTO, strip, opt-level=s — see Cargo.toml [profile.release]).
tasks.matching { it.name == "preDebugBuild" }.configureEach {
    dependsOn("buildRustDebug")
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("buildRustRelease")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":shared-contracts"))
}
