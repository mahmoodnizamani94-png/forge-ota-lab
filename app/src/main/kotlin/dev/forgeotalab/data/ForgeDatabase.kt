package dev.forgeotalab.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.forgeotalab.data.converter.ForgeTypeConverters
import dev.forgeotalab.data.dao.AdapterVersionDao
import dev.forgeotalab.data.dao.ArtifactDao
import dev.forgeotalab.data.dao.BaseCacheDao
import dev.forgeotalab.data.dao.BaseMatchDao
import dev.forgeotalab.data.dao.ConsentRecordDao
import dev.forgeotalab.data.dao.JobDao
import dev.forgeotalab.data.dao.JobPhaseDao
import dev.forgeotalab.data.dao.PackageDao
import dev.forgeotalab.data.dao.PartitionDao
import dev.forgeotalab.data.dao.SettingsDao
import dev.forgeotalab.data.entity.AdapterVersionEntity
import dev.forgeotalab.data.entity.ArtifactEntity
import dev.forgeotalab.data.entity.BaseCacheEntity
import dev.forgeotalab.data.entity.BaseMatchEntity
import dev.forgeotalab.data.entity.ConsentRecordEntity
import dev.forgeotalab.data.entity.JobEntity
import dev.forgeotalab.data.entity.JobPhaseEntity
import dev.forgeotalab.data.entity.PackageEntity
import dev.forgeotalab.data.entity.PartitionEntity
import dev.forgeotalab.data.entity.SettingsEntity

/**
 * Forge OTA Lab Room database.
 *
 * WHY exportSchema = true: Enables Room's @AutoMigration annotations for
 * additive migrations during beta. Schema JSON is exported to app/schemas/
 * for migration verification and diff review.
 *
 * WHY version 1: Pre-release — no shipped versions exist to migrate from.
 * Auto-migrations will be added when the schema evolves post-release.
 * Destructive migration is prohibited after public beta.
 */
@Database(
    entities = [
        PackageEntity::class,
        PartitionEntity::class,
        JobEntity::class,
        JobPhaseEntity::class,
        ArtifactEntity::class,
        BaseMatchEntity::class,
        BaseCacheEntity::class,
        AdapterVersionEntity::class,
        SettingsEntity::class,
        ConsentRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ForgeTypeConverters::class)
abstract class ForgeDatabase : RoomDatabase() {

    abstract fun packageDao(): PackageDao
    abstract fun partitionDao(): PartitionDao
    abstract fun jobDao(): JobDao
    abstract fun jobPhaseDao(): JobPhaseDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun baseMatchDao(): BaseMatchDao
    abstract fun baseCacheDao(): BaseCacheDao
    abstract fun adapterVersionDao(): AdapterVersionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun consentRecordDao(): ConsentRecordDao

    companion object {
        /** Database filename — used by DatabaseModule and test builders. */
        const val DATABASE_NAME = "forge_ota_lab.db"
    }
}
