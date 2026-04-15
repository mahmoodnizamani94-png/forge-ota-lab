package dev.forgeotalab.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.forgeotalab.data.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for key-value application settings.
 */
@Dao
interface SettingsDao {

    /**
     * Insert or replace a setting — upsert semantics on the key PK.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingsEntity)

    /**
     * One-shot read of a setting value.
     */
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingsEntity?

    /**
     * Observable setting — for reactive UI updates when settings change
     * (e.g., theme toggle, privileged mode toggle).
     */
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<SettingsEntity?>

    /**
     * All settings — for diagnostics export.
     */
    @Query("SELECT * FROM settings ORDER BY `key` ASC")
    suspend fun getAll(): List<SettingsEntity>
}
