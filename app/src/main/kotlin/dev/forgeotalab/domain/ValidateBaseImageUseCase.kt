package dev.forgeotalab.domain

import dev.forgeotalab.contracts.model.BaseValidationResult
import dev.forgeotalab.data.repository.BaseImageRepository
import dev.forgeotalab.data.repository.DataResult
import javax.inject.Inject

/**
 * Orchestrates single-partition base image validation.
 *
 * WHY a use case: Validation involves side effects (Room update, cache write,
 * event emission) beyond what a pure repository should own. The use case
 * coordinates the repository call and translates the result for the ViewModel.
 *
 * Called when the user imports a base image via SAF in the prerequisite wizard.
 */
class ValidateBaseImageUseCase @Inject constructor(
    private val baseImageRepository: BaseImageRepository,
) {

    /**
     * Validate a base image for a specific partition prerequisite.
     *
     * @param matchId UUID of the BaseMatchEntity to validate against.
     * @param baseUri SAF URI of the user-selected base image.
     * @return DataResult wrapping the validation result with field-level detail.
     */
    suspend fun execute(
        matchId: String,
        baseUri: String,
    ): DataResult<BaseValidationResult> {
        return baseImageRepository.validateBaseImage(matchId, baseUri)
    }
}
