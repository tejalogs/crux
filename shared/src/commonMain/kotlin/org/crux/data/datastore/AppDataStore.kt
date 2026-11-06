package org.crux.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import org.crux.common.protobufSerializer
import org.crux.data.datastore.model.AppSettings
import org.crux.data.datastore.model.ComposeConfigData
import org.crux.data.datastore.model.CruxConfig
import org.crux.data.datastore.model.PlatformOAuthPendingData
import org.crux.data.datastore.model.PrivacySettingsData
import org.crux.data.io.FileStorage
import org.koin.core.annotation.Single

@Single
internal class AppDataStore(
    internal val fileStorage: FileStorage,
) {
    val flareDataStore: DataStore<CruxConfig> by lazy {
        createDataStore(
            name = "crux_config.pb",
            serializer = protobufSerializer(CruxConfig()),
        )
    }

    val composeConfigData: DataStore<ComposeConfigData> by lazy {
        createDataStore(
            name = "compose_config.pb",
            serializer = protobufSerializer(ComposeConfigData()),
        )
    }

    val appSettingsStore: DataStore<AppSettings> by lazy {
        createDataStore(
            name = "app_settings.pb",
            serializer = protobufSerializer(AppSettings(version = "")),
        )
    }

    val platformOAuthPendingStore: DataStore<PlatformOAuthPendingData> by lazy {
        createDataStore(
            name = "platform_oauth_pending_v2.pb",
            serializer = protobufSerializer(PlatformOAuthPendingData()),
        )
    }

    val privacySettingsStore: DataStore<PrivacySettingsData> by lazy {
        createDataStore(
            name = "privacy_settings.pb",
            serializer = protobufSerializer(PrivacySettingsData()),
        )
    }

    private fun <T> createDataStore(
        name: String,
        serializer: OkioSerializer<T>,
    ): DataStore<T> =
        DataStoreFactory.create(
            storage =
                createDataStoreStorage(
                    name = name,
                    serializer = serializer,
                    fileStorage = fileStorage,
                ),
        )
}
