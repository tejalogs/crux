package org.crux.data.database.cache.model

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey

@Entity(
    indices = [
        Index(value = ["userKey", "accountType"], unique = true),
        Index(value = ["lastVisit"]),
    ],
)
internal data class DbUserHistory(
    val userKey: MicroBlogKey,
    val accountType: DbAccountType,
    val lastVisit: Long,
    @PrimaryKey
    val _id: String = "$accountType-$userKey",
)

internal data class DbUserHistoryWithUser(
    @Embedded
    val data: DbUserHistory,
    @Relation(parentColumns = ["userKey"], entityColumns = ["userKey"])
    val user: DbUser,
)
