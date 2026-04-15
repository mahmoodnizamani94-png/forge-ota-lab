package dev.forgeotalab.data.repository.impl

import dev.forgeotalab.data.dao.ConsentRecordDao
import dev.forgeotalab.data.dao.SettingsDao
import dev.forgeotalab.data.entity.ConsentRecordEntity
import dev.forgeotalab.data.entity.SettingsEntity
import dev.forgeotalab.data.repository.DataResult
import dev.forgeotalab.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [SettingsRepository].
 *
 * WHY combining SettingsDao and ConsentRecordDao: Both serve the settings
 * feature surface. Exposing a single repository hides the two-table
 * implementation detail from the settings ViewModel.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: SettingsDao,
    private val consentRecordDao: ConsentRecordDao,
) : SettingsRepository {

    override fun observeSetting(key: String): Flow<String?> {
        return settingsDao.observe(key).map { it?.value }
    }

    override suspend fun setSetting(key: String, value: String): DataResult<Unit> {
        return try {
            settingsDao.set(
                SettingsEntity(
                    key = key,
                    value = value,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to save setting '$key': ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun getSetting(key: String): DataResult<String?> {
        return try {
            DataResult.Success(settingsDao.get(key)?.value)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to read setting '$key': ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun isConsentGranted(type: String): DataResult<Boolean> {
        return try {
            val latest = consentRecordDao.getLatest(type)
            DataResult.Success(latest?.granted ?: false)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to check consent for '$type': ${e.message}",
                cause = e,
            )
        }
    }

    override suspend fun recordConsent(
        type: String,
        granted: Boolean,
        appVersion: String,
    ): DataResult<Unit> {
        return try {
            consentRecordDao.insert(
                ConsentRecordEntity(
                    id = UUID.randomUUID().toString(),
                    consentType = type,
                    granted = granted,
                    recordedAt = System.currentTimeMillis(),
                    appVersion = appVersion,
                ),
            )
            DataResult.Success(Unit)
        } catch (e: Exception) {
            DataResult.Error(
                message = "Failed to record consent for '$type': ${e.message}",
                cause = e,
            )
        }
    }
}
