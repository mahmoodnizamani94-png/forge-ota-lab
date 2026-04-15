package dev.forgeotalab.contracts.model

import kotlinx.serialization.Serializable

/**
 * Domain model carrying everything needed to start an extraction job.
 *
 * WHY a dedicated model: Separates the extraction intent from the Room entity.
 * The ViewModel creates this; the StartExtractionUseCase validates and persists
 * it. The model is immutable — once created, the extraction parameters are frozen.
 *
 * @property packageId UUID of the analyzed package in Room DB.
 * @property selectedPartitionIds UUIDs of partitions the user selected for extraction.
 * @property outputDirectoryUri SAF URI of the user-selected output directory.
 * @property supportTier Frozen snapshot of the support tier at extraction start.
 * @property isIncremental Whether this is an incremental package — affects overhead factor.
 */
@Serializable
data class ExtractionRequest(
    val packageId: String,
    val selectedPartitionIds: List<String>,
    val outputDirectoryUri: String,
    val supportTier: String,
    val isIncremental: Boolean = false,
)
