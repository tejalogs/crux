package org.crux.data.database.app

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import org.crux.data.database.app.dao.AccountDao
import org.crux.data.database.app.dao.DraftDao
import org.crux.data.database.app.dao.KeywordFilterDao
import org.crux.data.database.app.dao.RssSourceDao
import org.crux.data.database.app.dao.SearchHistoryDao

@Database(
    entities = [
        org.crux.data.database.app.model.DbAccount::class,
        org.crux.data.database.app.model.DbDraftGroup::class,
        org.crux.data.database.app.model.DbDraftTarget::class,
        org.crux.data.database.app.model.DbDraftMedia::class,
        org.crux.data.database.app.model.DbKeywordFilter::class,
        org.crux.data.database.app.model.DbSearchHistory::class,
        org.crux.data.database.app.model.DbRssSources::class,
        org.crux.data.database.app.model.DbLocalFollow::class,
        org.crux.data.database.app.model.DbLocalFollowGroup::class,
    ],
    version = 13,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
        ),
        AutoMigration(
            from = 4,
            to = 5,
        ),
        AutoMigration(
            from = 5,
            to = 6,
        ),
        AutoMigration(
            from = 6,
            to = 7,
        ),
        AutoMigration(
            from = 7,
            to = 8,
        ),
    ],
    exportSchema = true,
)
@ColumnTypeConverters(
    org.crux.data.database.adapter.MicroBlogKeyConverter::class,
    org.crux.data.database.app.model.DraftConverters::class,
    org.crux.data.database.adapter.SubscriptionTypeConverter::class,
    org.crux.data.database.adapter.RssDisplayModeConverter::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun draftDao(): DraftDao

    abstract fun keywordFilterDao(): KeywordFilterDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun rssSourceDao(): RssSourceDao

    abstract fun localFollowDao(): org.crux.data.database.app.dao.LocalFollowDao

    companion object {
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.executeSQL(
                        "ALTER TABLE DbRssSources ADD COLUMN displayMode TEXT NOT NULL DEFAULT 'FULL_CONTENT'",
                    )
                    connection.executeSQL(
                        "UPDATE DbRssSources SET displayMode = 'OPEN_IN_BROWSER' WHERE openInBrowser = 1",
                    )
                    connection.executeSQL(
                        "ALTER TABLE DbRssSources DROP COLUMN openInBrowser",
                    )
                }
            }
        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.executeSQL(
                        "ALTER TABLE DbKeywordFilter ADD COLUMN is_regex INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }
        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.executeSQL("DROP TABLE IF EXISTS DbApplication")
                }
            }
        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    // Kotlin now models platform_type as a String. The physical schema is unchanged.
                }
            }

        val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.executeSQL(
                        "CREATE TABLE IF NOT EXISTS `DbLocalFollow` (`accountKey` TEXT NOT NULL, `userKey` TEXT NOT NULL, " +
                            "`platformId` TEXT NOT NULL, `handle` TEXT NOT NULL, `displayName` TEXT NOT NULL, `avatar` TEXT, " +
                            "`groupId` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`accountKey`, `userKey`))",
                    )
                    connection.executeSQL(
                        "CREATE TABLE IF NOT EXISTS `DbLocalFollowGroup` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                }
            }
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
