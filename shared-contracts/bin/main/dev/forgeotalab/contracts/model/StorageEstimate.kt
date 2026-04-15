package dev.forgeotalab.contracts.model

/**
 * Storage budget estimate for an extraction operation.
 *
 * WHY a separate model: FR-6 requires instant recalculation when partition
 * selection changes. The UI binds directly to this model — recalculating
 * in ≤ 16 ms for any selection change. Used by both the analysis screen
 * (pre-extraction estimate) and the extraction preflight check.
 *
 * @property requiredBytes Total bytes needed: selected outputs + overhead.
 * @property availableBytes Free space on the target storage volume.
 * @property overheadFactor Multiplier applied: 1.25 for full OTA, 1.40 for incremental.
 * @property filesystemOverheadFactor Filesystem overhead multiplier (1.02 per PRD).
 * @property isSufficient True if availableBytes >= requiredBytes.
 * @property deficitBytes Positive value if insufficient, 0 otherwise.
 */
data class StorageEstimate(
    val requiredBytes: Long,
    val availableBytes: Long,
    val overheadFactor: Double,
    val filesystemOverheadFactor: Double = 1.02,
    val isSufficient: Boolean = availableBytes >= requiredBytes,
    val deficitBytes: Long = if (availableBytes >= requiredBytes) 0L else requiredBytes - availableBytes,
)
