package org.crux.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import org.crux.model.DbAccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiRelation

@Entity(
    indices = [
        Index(value = ["canonicalHandle", "host"], unique = true),
    ],
)
internal data class DbUser(
    @PrimaryKey
    val userKey: MicroBlogKey,
    val name: String,
    val canonicalHandle: String,
    val host: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val content: UiProfile,
)

@Entity(
    primaryKeys = ["accountType", "userKey"],
    indices = [
        Index(value = ["accountType", "userKey"], unique = true),
    ],
)
internal data class DbUserRelation(
    val accountType: DbAccountType,
    val userKey: MicroBlogKey,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val relation: UiRelation,
)
