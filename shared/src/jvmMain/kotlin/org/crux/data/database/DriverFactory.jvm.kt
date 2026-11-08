package org.crux.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import org.crux.common.FileSystemUtilsExt
import org.koin.core.annotation.Single
import java.io.File

@Single
public actual class DriverFactory {
    public actual inline fun <reified T : RoomDatabase> createBuilder(
        name: String,
        isCache: Boolean,
    ): RoomDatabase.Builder<T> {
        val dbFolder =
            if (isCache) {
                FileSystemUtilsExt.cruxCacheDirectory()
            } else {
                FileSystemUtilsExt.cruxDirectory()
            }
        val dbFile = File(dbFolder, name)
        return Room.databaseBuilder<T>(
            name = dbFile.absolutePath,
        )
    }

    public actual fun deleteDatabase(
        name: String,
        isCache: Boolean,
    ) {
        val dbFolder =
            if (isCache) {
                FileSystemUtilsExt.cruxCacheDirectory()
            } else {
                FileSystemUtilsExt.cruxDirectory()
            }
        val dbFile = File(dbFolder, name)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }
}
