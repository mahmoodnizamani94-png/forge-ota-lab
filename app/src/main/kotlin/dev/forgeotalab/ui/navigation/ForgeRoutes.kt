package dev.forgeotalab.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for all PRD surfaces.
 *
 * WHY @Serializable: Compose Navigation 2.9+ supports type-safe routes via
 * kotlinx.serialization. Each route is a data class or object that carries
 * its arguments as typed properties — no more string-based argument parsing.
 *
 * WHY all routes declared now: The PRD specifies 9 major surfaces. Declaring
 * all routes upfront ensures consistent navigation patterns and prevents
 * route name collisions as features land in later slices.
 *
 * Accessibility: Screen titles are derived from route types for TalkBack
 * announcements on navigation transitions.
 */

/** Home / job history — the default landing screen. */
@Serializable
object HomeRoute

/**
 * Package analysis screen — displays analysis result with partition inventory.
 *
 * @param packageId UUID of the analyzed package in Room DB.
 */
@Serializable
data class AnalysisRoute(val packageId: String)

/**
 * Extraction configuration and progress — partition selection confirmed.
 * Stub destination until P06.
 *
 * @param packageId UUID of the package to extract from.
 */
@Serializable
data class ExtractionRoute(val packageId: String)

/**
 * Export results screen — shows verified artifacts after extraction completes.
 * Stub destination until P06.
 *
 * @param jobId UUID of the completed extraction job.
 */
@Serializable
data class ResultRoute(val jobId: String)

/**
 * Incremental prerequisite wizard — validates base inputs for delta OTAs.
 * Stub destination until later slice.
 *
 * @param packageId UUID of the incremental package.
 */
@Serializable
data class IncrementalWizardRoute(val packageId: String)

/**
 * Filesystem browser — read-only browsing of verified extracted images.
 * Stub destination until later slice.
 *
 * @param artifactId UUID of the verified artifact to browse.
 */
@Serializable
data class FilesystemBrowserRoute(val artifactId: String)

/** Settings — theme switching, diagnostics, adapter management, advanced options. */
@Serializable
object SettingsRoute

/**
 * First-launch onboarding — welcome, transparency, privacy consent.
 * Shown once, never re-shown. Persisted via DataStore.
 */
@Serializable
object OnboardingRoute
