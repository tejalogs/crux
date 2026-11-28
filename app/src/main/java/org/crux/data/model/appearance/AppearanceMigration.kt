package org.crux.data.model.appearance

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.crux.common.PlatformDispatchers
import org.crux.data.io.FileStorage
import org.crux.data.model.AppearanceSettings

@OptIn(ExperimentalSerializationApi::class)
internal suspend fun migrateAppearanceV1ToV2(
    fileStorage: FileStorage,
    bagStore: DataStore<AppearanceBag>,
) {
    withContext(PlatformDispatchers.IO) {
        val v1Path = fileStorage.dataStoreFile("appearance_settings.pb")
        if (!fileStorage.exists(v1Path)) return@withContext
        if (bagStore.data
                .first()
                .entries
                .isNotEmpty()
        ) {
            runCatching { fileStorage.delete(v1Path) }
            return@withContext
        }

        val v1 =
            runCatching {
                val v1Bytes = fileStorage.read(v1Path)
                ProtoBuf.decodeFromByteArray<AppearanceSettings>(v1Bytes)
            }.getOrNull()
        if (v1 != null) {
            bagStore.updateData { v1.toPatch().toBag() }
        }
        runCatching { fileStorage.delete(v1Path) }
    }
}
