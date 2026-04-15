package dev.forgeotalab.baselineprofile

/**
 * Baseline Profile generator placeholder.
 *
 * WHY this exists: A Baseline Profile improves cold start and reduces
 * JIT compilation by pre-compiling critical code paths during installation.
 * The actual generator requires a Macrobenchmark test running on a connected
 * device or emulator (API 33+).
 *
 * When ready to generate:
 * 1. Add `androidx.benchmark:benchmark-macro-junit4` dependency
 * 2. Apply `com.android.test` plugin
 * 3. Configure the managed device in the module's build.gradle.kts
 * 4. Run: `./gradlew :baselineprofile:connectedBenchmarkAndroidTest`
 *
 * Critical user journey to profile:
 * - Cold start → Home screen populated
 * - Import file → Analysis screen rendered
 * - Start extraction → Extraction progress rendered
 * - Extraction complete → Result screen with verified artifacts
 *
 * The generated baseline-prof.txt should be committed to
 * app/src/main/baseline-prof.txt for inclusion in the release APK.
 *
 * See: https://developer.android.com/topic/performance/baselineprofiles
 */
object BaselineProfilePlaceholder
