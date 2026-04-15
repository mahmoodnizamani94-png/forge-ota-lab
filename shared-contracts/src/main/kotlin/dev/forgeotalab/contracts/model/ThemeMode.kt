package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * User preference for app theme.
 *
 * WHY an enum in shared-contracts: Theme mode is referenced by both the DataStore
 * persistence layer and the Compose UI. Placing it in shared-contracts avoids
 * circular dependencies between data and UI layers.
 *
 * PRD: "Dark mode as default. Light mode fully implemented as secondary option."
 */
@Serializable
enum class ThemeMode {
    /** Forces dark theme regardless of system setting. */
    DARK,

    /** Forces light theme regardless of system setting. */
    LIGHT,

    /** Follows the device system theme preference. */
    SYSTEM,
}
