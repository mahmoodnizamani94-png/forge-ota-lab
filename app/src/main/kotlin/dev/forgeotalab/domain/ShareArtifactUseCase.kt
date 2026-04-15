package dev.forgeotalab.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.forgeotalab.data.repository.ArtifactRepository
import dev.forgeotalab.data.repository.DataResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a share intent for an extracted artifact.
 *
 * WHY include SHA-256 in share text: A security researcher receiving a boot.img
 * via share needs to verify provenance. Including the checksum in the share text
 * lets the recipient independently verify the artifact against the manifest hash.
 *
 * PRD FR-7: "Share intent for extracted images includes checksum in the share text."
 */
@Singleton
class ShareArtifactUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val artifactRepository: ArtifactRepository,
) {

    /**
     * Build a share Intent for the given artifact.
     *
     * @return The Intent to launch, or null if the artifact cannot be found.
     */
    suspend fun buildShareIntent(artifactId: String): ShareResult {
        val result = artifactRepository.getArtifactById(artifactId)
        val artifact = when (result) {
            is DataResult.Success -> result.data
            is DataResult.Error -> return ShareResult.Error(result.message)
        } ?: return ShareResult.Error("Artifact not found")

        val shareText = buildString {
            appendLine("${artifact.partitionName}.img")
            appendLine("Size: ${formatFileSize(artifact.sizeBytes)}")
            appendLine("Derivation: ${formatDerivationType(artifact.derivationType)}")

            when (artifact.verificationStatus) {
                "VERIFIED" -> {
                    appendLine("Status: ✓ SHA-256 Verified")
                    artifact.sha256?.let { appendLine("SHA-256: $it") }
                }
                "MISMATCH" -> {
                    appendLine("Status: ⚠ Hash Mismatch — USE WITH CAUTION")
                    appendLine("Expected: ${artifact.expectedHash ?: "unknown"}")
                    appendLine("Actual:   ${artifact.sha256 ?: "unknown"}")
                }
                "UNVERIFIABLE" -> {
                    appendLine("Status: — Unverifiable (no target hash in manifest)")
                    artifact.sha256?.let { appendLine("SHA-256: $it") }
                }
                else -> {
                    appendLine("Status: Verification pending")
                }
            }

            appendLine()
            appendLine("Extracted by Forge OTA Lab")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(artifact.outputUri))
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "${artifact.partitionName}.img — Forge OTA Lab",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return ShareResult.Ready(intent)
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1_048_576 -> "${bytes / 1024} KB"
        bytes < 1_073_741_824 -> String.format("%.1f MB", bytes / 1_048_576.0)
        else -> String.format("%.2f GB", bytes / 1_073_741_824.0)
    }

    private fun formatDerivationType(type: String): String = when (type) {
        "DIRECT" -> "Direct extract"
        "RECONSTRUCTED" -> "Reconstructed from base"
        "PARTIAL" -> "Partial output"
        "RAW_UNVERIFIED" -> "Raw — unverified"
        else -> type
    }
}

/** Result of building a share intent. */
sealed class ShareResult {
    data class Ready(val intent: Intent) : ShareResult()
    data class Error(val message: String) : ShareResult()
}
