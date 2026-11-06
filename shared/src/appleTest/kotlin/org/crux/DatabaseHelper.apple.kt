package org.crux

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.coroutines.test.TestScope
import org.crux.data.database.app.AppDatabase
import org.crux.data.database.cache.CacheDatabase
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
internal actual fun <T : RoomDatabase> Room.memoryDatabaseBuilder(databaseClass: KClass<T>): RoomDatabase.Builder<T> =
    when (databaseClass) {
        AppDatabase::class -> Room.inMemoryDatabaseBuilder<AppDatabase>()
        CacheDatabase::class -> Room.inMemoryDatabaseBuilder<CacheDatabase>()
        else -> error("Unsupported test database: ${databaseClass.qualifiedName}")
    } as RoomDatabase.Builder<T>

actual open class RobolectricTest actual constructor()

internal actual fun TestScope.startPlatformEventLoop() = Unit
