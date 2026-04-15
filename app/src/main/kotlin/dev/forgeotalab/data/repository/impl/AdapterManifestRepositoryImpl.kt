package dev.forgeotalab.data.repository.impl

import dev.forgeotalab.contracts.model.AdapterManifest
import dev.forgeotalab.contracts.model.ManifestFeatureFlags
import dev.forgeotalab.contracts.model.SignedManifestEnvelope
import dev.forgeotalab.data.ForgePreferences
import dev.forgeotalab.data.dao.AdapterVersionDao
import dev.forgeotalab.data.entity.AdapterVersionEntity
import dev.forgeotalab.data.repository.AdapterManifestRepository
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.ManifestRefreshOutcome
import dev.forgeotalab.domain.ManifestSignatureVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [AdapterManifestRepository] with Ed25519 signature
 * verification, timeout pinning, and revocation support.
 *
 * WHY signature before parse: The PRD's Signed Manifest Contract mandates
 * "Signature verification happens BEFORE any manifest field is parsed."
 * This implementation verifies the raw body bytes before deserializing
 * the JSON into AdapterManifest.
 *
 * WHY OkHttp: Lightweight HTTP client with configurable timeouts.
 * No Retrofit needed for a single GET endpoint.
 */
@Singleton
class AdapterManifestRepositoryImpl @Inject constructor(
    private val adapterVersionDao: AdapterVersionDao,
    private val forgePreferences: ForgePreferences,
    private val signatureVerifier: ManifestSignatureVerifier,
    private val okHttpClient: OkHttpClient,
) : AdapterManifestRepository {

    companion object {
        /**
         * Manifest endpoint URL.
         * TODO(P01): Replace with production URL before closed beta.
         */
        const val MANIFEST_URL =
            "https://api.forgeotalab.dev/adapter-manifest/v1/index.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun refreshManifest(): DataResult<ManifestRefreshOutcome> {
        return try {
            val envelopeResult = fetchManifestEnvelope()

            when {
                envelopeResult == null -> {
                    // Network failure — pin last known-good
                    DataResult.Success(
                        ManifestRefreshOutcome.PinnedLastKnownGood(
                            reason = "Network request failed or timed out",
                        ),
                    )
                }

                else -> {
                    verifyAndApplyManifest(envelopeResult)
                }
            }
        } catch (e: Exception) {
            DataResult.Success(
                ManifestRefreshOutcome.PinnedLastKnownGood(
                    reason = "Unexpected error: ${e.message}",
                ),
            )
        }
    }

    override fun observeFeatureFlags(): Flow<ManifestFeatureFlags> {
        return forgePreferences.lastKnownGoodManifest.map { manifestJson ->
            if (manifestJson != null) {
                try {
                    json.decodeFromString<AdapterManifest>(manifestJson).featureFlags
                } catch (_: Exception) {
                    ManifestFeatureFlags()
                }
            } else {
                ManifestFeatureFlags()
            }
        }
    }

    override suspend fun getFeatureFlags(): ManifestFeatureFlags {
        return observeFeatureFlags().first()
    }

    override suspend fun getCurrentManifest(): AdapterManifest? {
        val manifestJson = forgePreferences.lastKnownGoodManifest.first() ?: return null
        return try {
            json.decodeFromString<AdapterManifest>(manifestJson)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetch the signed manifest envelope from the remote endpoint.
     * Returns null on network failure (timeout, connectivity issues).
     */
    private suspend fun fetchManifestEnvelope(): SignedManifestEnvelope? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(MANIFEST_URL)
                    .get()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext null
                }

                val responseBody = response.body?.string() ?: return@withContext null
                json.decodeFromString<SignedManifestEnvelope>(responseBody)
            } catch (_: IOException) {
                null
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Verify Ed25519 signature and apply the manifest if valid.
     *
     * Sequence (per PRD):
     * 1. Verify signature against raw body bytes — BEFORE any parsing
     * 2. Parse manifest body only after signature passes
     * 3. Upsert adapter versions to Room
     * 4. Apply revocations immediately
     * 5. Persist as last-known-good
     */
    private suspend fun verifyAndApplyManifest(
        envelope: SignedManifestEnvelope,
    ): DataResult<ManifestRefreshOutcome> {
        // Step 1: Verify signature BEFORE parsing body
        val bodyBytes = envelope.body.toByteArray(Charsets.UTF_8)
        val signatureValid = signatureVerifier.verify(bodyBytes, envelope.signature)

        if (!signatureValid) {
            return DataResult.Success(
                ManifestRefreshOutcome.SignatureFailed(
                    reason = "Ed25519 signature verification failed",
                ),
            )
        }

        // Step 2: Parse manifest body — only after signature verification passes
        val manifest = try {
            json.decodeFromString<AdapterManifest>(envelope.body)
        } catch (e: Exception) {
            return DataResult.Success(
                ManifestRefreshOutcome.PinnedLastKnownGood(
                    reason = "Manifest JSON parse failed after signature verification: ${e.message}",
                ),
            )
        }

        // Step 3: Upsert adapters
        val now = System.currentTimeMillis()
        val adapterEntities = manifest.adapters.map { adapter ->
            AdapterVersionEntity(
                id = adapter.id,
                family = adapter.family,
                version = adapter.version,
                supportTier = adapter.supportTier,
                isRevoked = false,
                minimumAppVersion = adapter.minimumAppVersion,
                compatibilityNotes = adapter.compatibilityNotes,
                installedAt = now,
                lastRefreshedAt = now,
            )
        }
        adapterVersionDao.insertOrReplaceAll(adapterEntities)

        // Step 4: Apply revocations immediately
        var revocationsApplied = 0
        for (revokedId in manifest.revocations) {
            val existing = adapterVersionDao.getById(revokedId)
            if (existing != null && !existing.isRevoked) {
                adapterVersionDao.markRevoked(revokedId)
                revocationsApplied++
            }
        }

        // Step 5: Persist as last-known-good
        forgePreferences.setLastKnownGoodManifest(envelope.body)

        return DataResult.Success(
            ManifestRefreshOutcome.Applied(
                manifest = manifest,
                adaptersUpdated = adapterEntities.size,
                revocationsApplied = revocationsApplied,
            ),
        )
    }
}
