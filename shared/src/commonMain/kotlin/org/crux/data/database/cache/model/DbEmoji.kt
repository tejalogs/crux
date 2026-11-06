package org.crux.data.database.cache.model

import androidx.room3.ColumnInfo
import androidx.room3.ColumnTypeConverter
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import org.crux.common.decodeProtobuf
import org.crux.common.encodeProtobuf
import org.crux.model.DbAccountType

@Entity
internal data class DbEmoji(
    @PrimaryKey
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "content", typeAffinity = ColumnInfo.BLOB)
    val content: EmojiContent,
)

internal class EmojiContentConverter {
    @ColumnTypeConverter
    fun fromEmojiContent(emojiContent: EmojiContent): ByteArray = emojiContent.encodeProtobuf()

    @ColumnTypeConverter
    fun toEmojiContent(data: ByteArray): EmojiContent =
        if (data.isEmpty()) {
            EmojiContent()
        } else {
            data.decodeProtobuf()
        }
}

@Entity
internal data class DbEmojiHistory(
    val accountType: DbAccountType,
    val shortCode: String,
    val lastUse: Long,
    @PrimaryKey
    val _id: String = "$accountType-$shortCode",
)
