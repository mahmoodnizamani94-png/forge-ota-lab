package dev.forgeotalab.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.forgeotalab.data.repository.AdapterManifestRepository
import dev.forgeotalab.data.repository.AnalysisRepository
import dev.forgeotalab.data.repository.ArtifactRepository
import dev.forgeotalab.data.repository.BaseImageRepository
import dev.forgeotalab.data.repository.JobRepository
import dev.forgeotalab.data.repository.PackageRepository
import dev.forgeotalab.data.repository.SettingsRepository
import dev.forgeotalab.data.repository.impl.AdapterManifestRepositoryImpl
import dev.forgeotalab.data.repository.impl.AnalysisRepositoryImpl
import dev.forgeotalab.data.repository.impl.ArtifactRepositoryImpl
import dev.forgeotalab.data.repository.impl.BaseImageRepositoryImpl
import dev.forgeotalab.data.repository.impl.JobRepositoryImpl
import dev.forgeotalab.data.repository.impl.PackageRepositoryImpl
import dev.forgeotalab.data.repository.impl.SettingsRepositoryImpl
import javax.inject.Singleton

/**
 * Hilt module binding repository interfaces to their implementations.
 *
 * WHY @Binds instead of @Provides: @Binds generates no code — it tells
 * Hilt to use the implementation's constructor injection directly. More
 * efficient than @Provides which creates a factory method. This only
 * works because the implementations use @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPackageRepository(impl: PackageRepositoryImpl): PackageRepository

    @Binds
    @Singleton
    abstract fun bindJobRepository(impl: JobRepositoryImpl): JobRepository

    @Binds
    @Singleton
    abstract fun bindArtifactRepository(impl: ArtifactRepositoryImpl): ArtifactRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAnalysisRepository(impl: AnalysisRepositoryImpl): AnalysisRepository

    @Binds
    @Singleton
    abstract fun bindBaseImageRepository(impl: BaseImageRepositoryImpl): BaseImageRepository

    @Binds
    @Singleton
    abstract fun bindAdapterManifestRepository(impl: AdapterManifestRepositoryImpl): AdapterManifestRepository
}

