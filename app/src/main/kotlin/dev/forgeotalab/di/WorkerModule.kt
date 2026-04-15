package dev.forgeotalab.di

import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkerFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for WorkManager worker injection.
 *
 * WHY: @HiltWorker on ExtractionWorker requires Hilt to provide a
 * WorkerFactory that knows how to inject dependencies into workers.
 * This module binds HiltWorkerFactory as the app's WorkerFactory.
 *
 * The actual worker configuration (WorkManager initialization) happens
 * in ForgeApplication — Hilt just provides the factory.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    @Singleton
    abstract fun bindWorkerFactory(factory: HiltWorkerFactory): WorkerFactory
}
