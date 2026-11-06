package org.crux.data.database

import androidx.sqlite.SQLiteDriver
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public expect fun createDatabaseDriver(): SQLiteDriver
