package dev.forgeotalab.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.forgeotalab.data.ForgeDatabase
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
import javax.inject.Singleton

/**
 * Hilt module providing the Room database singleton and all DAO instances.
 *
 * WHY @Singleton on the database: Room.databaseBuilder creates the database
 * on first access. Multiple instances would create separate SQLite connections
 * competing for the same file — leading to SQLITE_BUSY errors and data
 * corruption. A single instance with Room's internal connection pool is
 * the correct pattern.
 *
 * WHY individual DAO providers: Hilt needs explicit provision for each DAO
 * type injected into repositories. Abstract function extraction from the
 * database class is the canonical Room + Hilt pattern.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ForgeDatabase {
        return Room.databaseBuilder(
            context,
            ForgeDatabase::class.java,
            ForgeDatabase.DATABASE_NAME,
        ).build()
    }

    @Provides
    fun providePackageDao(database: ForgeDatabase): PackageDao = database.packageDao()

    @Provides
    fun providePartitionDao(database: ForgeDatabase): PartitionDao = database.partitionDao()

    @Provides
    fun provideJobDao(database: ForgeDatabase): JobDao = database.jobDao()

    @Provides
    fun provideJobPhaseDao(database: ForgeDatabase): JobPhaseDao = database.jobPhaseDao()

    @Provides
    fun provideArtifactDao(database: ForgeDatabase): ArtifactDao = database.artifactDao()

    @Provides
    fun provideBaseMatchDao(database: ForgeDatabase): BaseMatchDao = database.baseMatchDao()

    @Provides
    fun provideBaseCacheDao(database: ForgeDatabase): BaseCacheDao = database.baseCacheDao()

    @Provides
    fun provideAdapterVersionDao(database: ForgeDatabase): AdapterVersionDao = database.adapterVersionDao()

    @Provides
    fun provideSettingsDao(database: ForgeDatabase): SettingsDao = database.settingsDao()

    @Provides
    fun provideConsentRecordDao(database: ForgeDatabase): ConsentRecordDao = database.consentRecordDao()
}
