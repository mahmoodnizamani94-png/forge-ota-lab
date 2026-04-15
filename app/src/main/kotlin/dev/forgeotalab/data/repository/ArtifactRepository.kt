package dev.forgeotalab.data.repository

import dev.forgeotalab.contracts.model.VerificationStatus
import dev.forgeotalab.data.entity.ArtifactEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for extracted artifact persistence.
 */
interface ArtifactRepository {

    /**
     * Observe all artifacts for a job — powers the export results screen.
     */
    fun observeArtifactsByJob(jobId: String): Flow<List<ArtifactEntity>>

    /**
     * Observe artifacts sorted for result screen: mismatches first.
     * WHY: PRD stress test — problems surface at the top of the list.
     */
    fun observeArtifactsByJobSorted(jobId: String): Flow<List<ArtifactEntity>>

    /**
     * Observe only verified artifacts — for filesystem browser eligibility.
     */
    fun observeVerifiedArtifacts(jobId: String): Flow<List<ArtifactEntity>>

    /**
     * Single artifact lookup — for re-extraction and share flows.
     */
    suspend fun getArtifactById(artifactId: String): DataResult<ArtifactEntity?>

    /**
     * Record a newly extracted artifact.
     */
    suspend fun recordArtifact(artifact: ArtifactEntity): DataResult<Unit>

    /**
     * Update verification result after SHA-256 check.
     * PRD Rule #3: no success state before verification completes.
     */
    suspend fun updateVerification(
        artifactId: String,
        status: VerificationStatus,
        sha256: String?,
    ): DataResult<Unit>
}
