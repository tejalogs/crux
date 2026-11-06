package org.crux.data.database.adapter

import org.crux.model.MicroBlogKey

internal class MicroBlogKeyConverter {
    @androidx.room3.ColumnTypeConverter
    fun fromString(value: String): MicroBlogKey = MicroBlogKey.valueOf(value)

    @androidx.room3.ColumnTypeConverter
    fun fromEnum(value: MicroBlogKey): String = value.toString()
}
