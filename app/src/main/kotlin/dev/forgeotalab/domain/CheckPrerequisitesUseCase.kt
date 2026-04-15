package dev.forgeotalab.domain

import dev.forgeotalab.data.repository.BaseImageRepository
import dev.forgeotalab.data.repository.DataResult
import javax.inject.Inject

/**
 * Checks whether all selected partitions have validated bases for extraction.
 *
 * WHY a separate use case: This check is consumed by two callers:
 * 1. The wizard ViewModel — for CTA enable/disable rendering
 * 2. PreflightValidator — for domain-level gate enforcement (Rule #2)
 *
 * Extracting the check into a use case prevents duplicated logic and ensures
 * the gate is enforced identically in both the UI and the domain layer.
 */
class CheckPrerequisitesUseCase @Inject constructor(
    private val baseImageRepository: BaseImageRepository,
) {

    /**
     * Check prerequisite status for an incremental package.
     *
     * @param packageId The incremental package ID.
     * @param selectedPartitionNames Partitions currently selected for extraction.
     * @return Sealed result indicating whether extraction can proceed.
     */
    suspend fun execute(
        packageId: String,
        selectedPartitionNames: List<String>,
    ): PrerequisiteCheckResult {
        val result = baseImageRepository.arePrerequisitesMet(
            packageId = packageId,
            selectedPartitionNames = selectedPartitionNames,
        )

        return when (result) {
            is DataResult.Success -> {
                if (result.data) {
                    PrerequisiteCheckResult.AllSatisfied
                } else {
                    PrerequisiteCheckResult.Unmet(
                        reason = "Not all selected partitions have validated base images.",
                    )
                }
            }
            is DataResult.Error -> {
                PrerequisiteCheckResult.CheckFailed(
                    message = result.message,
                )
            }
        }
    }
}

/**
 * Sealed result of prerequisite checking.
 *
 * WHY sealed: enables exhaustive `when` handling at all consumption sites.
 * Adding a new variant causes compile errors until handled everywhere.
 */
sealed class PrerequisiteCheckResult {

    /** All selected partitions have validated bases (or raw export allowed). */
    data object AllSatisfied : PrerequisiteCheckResult()

    /** One or more partitions still need validated bases. */
    data class Unmet(val reason: String) : PrerequisiteCheckResult()

    /** The check itself failed (DB error). */
    data class CheckFailed(val message: String) : PrerequisiteCheckResult()
}
