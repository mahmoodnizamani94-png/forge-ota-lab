package dev.forgeotalab.data.repository.impl

import dev.forgeotalab.contracts.model.VerificationStatus
import dev.forgeotalab.data.dao.ArtifactDao
import dev.forgeotalab.data.entity.ArtifactEntity
import dev.forgeotalab.data.repository.ArtifactRepository
import dev.forgeotalab.data.repository.DataResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ArtifactRepository].
 */
@Singleton
class ArtifactRepositoryImpl @Inject constructor(
    private val artifactDao: ArtifactDao,
) : ArtifactRepository {

    override fun observeArtifactsByJob(jobId: String): Flow<List<ArtifactEntity>> {
        return artifactDao.observeByJobId(jobId)
    }

    override fun observeArtifactsByJobSorted(jobId: String): Flow<List<ArtifactEntity>> {
        return artifactDao.observeByJobIdSorted(jobId)
    }

    override fun observeVerifiedArtifacts(jobId: String): Flow<List<ArtifactEntity>> {
        return artifactDao.observeVerifiedByJobId(jobId)
    }

    override suspend fun getArtifactById(artifactId: String): DataResult<ArtifactEntity?> {
        return try {
            DataResult.Success(artifactDao.getById(artifactId))
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to load artifact $artifactId: ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun recordArtifact(artifact: ArtifactEntity): DataResult<Unit> {
        return try {
            artifactDao.insert(artifact)
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to record artifact '${artifact.partitionName}': ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun updateVerification(
        artifactId: String,
        status: VerificationStatus,
        sha256: String?,
    ): DataResult<Unit> {
        return try {
            artifactDao.updateVerification(
                artifactId = artifactId,
                status = status.name,
                sha256 = sha256,
                verifiedAt = System.currentTimeMillis(),
            )
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to update verification for artifact $artifactId: ${e.message}",
                cause = e,
            )
        }
    }
}
