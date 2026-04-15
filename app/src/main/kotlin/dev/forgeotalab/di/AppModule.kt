package dev.forgeotalab.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.forgeotalab.data.ForgePreferences
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * App-level Hilt module providing non-repository singletons.
 *
 * Provides:
 * - appVersion string for diagnostics bundles and about screen
 * - ForgePreferences DataStore instance
 * - OkHttpClient for manifest refresh
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @Named("appVersion")
    fun provideAppVersion(): String = "0.1.0"

    /**
     * ForgePreferences backed by DataStore.
     *
     * WHY @Singleton: DataStore must be a single instance per process
     * to avoid file corruption from concurrent access.
     */
    @Provides
    @Singleton
    fun provideForgePreferences(
        @ApplicationContext context: Context,
    ): ForgePreferences = ForgePreferences(context)

    /**
     * OkHttpClient for adapter manifest refresh.
     *
     * WHY custom timeouts: The PRD requires graceful timeout handling
     * (pin last-known-good). A 15-second timeout prevents manifest
     * refresh from blocking app startup on poor connections.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
}
