package org.crux.data.database.cache

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.withWriteTransaction

internal const val CACHE_DATABASE_VERSION = 48

@Database(
    entities = [
        org.crux.data.database.cache.model.DbEmoji::class,
        org.crux.data.database.cache.model.DbStatusReference::class,
        org.crux.data.database.cache.model.DbStatus::class,
        org.crux.data.database.cache.model.DbUser::class,
        org.crux.data.database.cache.model.DbPagingTimeline::class,
        org.crux.data.database.cache.model.DbTimelineItemPresentationReference::class,
        org.crux.data.database.cache.model.DbMessageRoom::class,
        org.crux.data.database.cache.model.DbMessageItem::class,
        org.crux.data.database.cache.model.DbDirectMessageTimeline::class,
        org.crux.data.database.cache.model.DbMessageRoomReference::class,
        org.crux.data.database.cache.model.DbUserHistory::class,
        org.crux.data.database.cache.model.DbEmojiHistory::class,
        org.crux.data.database.cache.model.DbPagingKey::class,
        org.crux.data.database.cache.model.DbList::class,
        org.crux.data.database.cache.model.DbListPaging::class,
        org.crux.data.database.cache.model.DbListMember::class,
        org.crux.data.database.cache.model.DbUserRelation::class,
        org.crux.data.database.cache.model.DbTranslation::class,
    ],
    version = CACHE_DATABASE_VERSION,
    exportSchema = false,
)
@ColumnTypeConverters(
    org.crux.data.database.adapter.MicroBlogKeyConverter::class,
    org.crux.data.database.adapter.AccountTypeConverter::class,
    org.crux.data.database.cache.model.EmojiContentConverter::class,
    org.crux.data.database.cache.model.StatusConverter::class,
    org.crux.data.database.cache.model.DbStatusContentConverter::class,
    org.crux.data.database.cache.model.ListContentConverters::class,
    org.crux.data.database.cache.model.TranslationConverters::class,
)
@ConstructedBy(CacheDatabaseConstructor::class)
internal abstract class CacheDatabase : RoomDatabase() {
    abstract fun emojiDao(): org.crux.data.database.cache.dao.EmojiDao

    abstract fun statusReferenceDao(): org.crux.data.database.cache.dao.StatusReferenceDao

    abstract fun statusDao(): org.crux.data.database.cache.dao.StatusDao

    abstract fun userDao(): org.crux.data.database.cache.dao.UserDao

    abstract fun pagingTimelineDao(): org.crux.data.database.cache.dao.PagingTimelineDao

    abstract fun messageDao(): org.crux.data.database.cache.dao.MessageDao

    abstract fun listDao(): org.crux.data.database.cache.dao.ListDao

    abstract fun translationDao(): org.crux.data.database.cache.dao.TranslationDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}

internal suspend fun <R> RoomDatabase.connect(block: suspend () -> R): R = withWriteTransaction { block() }
