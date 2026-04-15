package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.forgeotalab.data.entity.ConsentRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for consent records.
 *
 * WHY insert-only (no update/delete): Consent records are append-only for
 * auditability. The latest record for each consent type represents the
 * current state — older records form the consent history.
 */
@Dao
interface ConsentRecordDao {

    @Insert
    suspend fun insert(record: ConsentRecordEntity)

    /**
     * Latest consent decision for a specific type — determines current consent state.
     */
    @Query(
        """
        SELECT * FROM consent_records 
        WHERE consentType = :consentType 
        ORDER BY recordedAt DESC 
        LIMIT 1
        """
    )
    suspend fun getLatest(consentType: String): ConsentRecordEntity?

    /**
     * Full consent history — for settings display and diagnostics.
     */
    @Query("SELECT * FROM consent_records ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<ConsentRecordEntity>>

    /**
     * Observable latest consent for reactive UI (e.g., settings toggle).
     */
    @Query(
        """
        SELECT * FROM consent_records 
        WHERE consentType = :consentType 
        ORDER BY recordedAt DESC 
        LIMIT 1
        """
    )
    fun observeLatest(consentType: String): Flow<ConsentRecordEntity?>
}
