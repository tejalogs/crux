package org.crux.data.database.adapter

import androidx.room3.ColumnTypeConverter
import org.crux.common.decodeJson
import org.crux.common.decodeProtobuf
import org.crux.common.encodeJson
import org.crux.common.encodeProtobuf
import org.crux.model.DbAccountType
import org.crux.ui.model.UiDMItem
import org.crux.ui.model.UiDMRoom
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiRelation
import org.crux.ui.model.UiTimelineV2

internal class AccountTypeConverter {
    @ColumnTypeConverter
    fun fromString(value: String): DbAccountType = value.decodeJson()

    @ColumnTypeConverter
    fun fromEnum(value: DbAccountType): String = value.encodeJson()

    @ColumnTypeConverter
    fun fromUiProfile(value: UiProfile): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiProfile(value: ByteArray): UiProfile = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiTimelineV2(value: UiTimelineV2): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiTimelineV2(value: ByteArray): UiTimelineV2 = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiDMRoom(value: UiDMRoom): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiDMRoom(value: ByteArray): UiDMRoom = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiDMItem(value: UiDMItem): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiDMItem(value: ByteArray): UiDMItem = value.decodeProtobuf()

    @ColumnTypeConverter
    fun fromUiRelation(value: UiRelation): ByteArray = value.encodeProtobuf()

    @ColumnTypeConverter
    fun toUiRelation(value: ByteArray): UiRelation = value.decodeProtobuf()
}
