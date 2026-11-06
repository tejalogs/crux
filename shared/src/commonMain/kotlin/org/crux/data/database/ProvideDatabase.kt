package org.crux.data.database

import org.crux.common.PlatformDispatchers
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.TimelineRevisionCallback
import org.koin.core.annotation.Single

@Single
internal fun provideAppDatabase(driverFactory: DriverFactory): AppDatabase =
    driverFactory
        .createBuilder<AppDatabase>("app.db")
        .addMigrations(
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12,
            AppDatabase.MIGRATION_12_13,
        ).setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()

internal const val CACHE_DATABASE_NAME = "cache.db"

@Single
internal fun provideCacheDatabase(driverFactory: DriverFactory): CacheDatabase =
    driverFactory
        .createBuilder<CacheDatabase>(CACHE_DATABASE_NAME, isCache = true)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(TimelineRevisionCallback)
        .setDriver(createDatabaseDriver())
        .setQueryCoroutineContext(PlatformDispatchers.IO)
        .build()
