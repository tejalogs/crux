package org.crux.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import okio.BufferedSink
import okio.BufferedSource
import org.crux.common.protobufSerializer
import org.crux.data.datastore.model.AppSettings
import org.crux.data.datastore.model.ComposeConfigData
import org.crux.data.datastore.model.CruxConfig
import org.crux.data.datastore.model.PlatformOAuthPendingData
import org.crux.data.datastore.model.PrivacySettingsData
import org.crux.data.io.FileStorage
import org.crux.data.security.CredentialCipher
import org.koin.core.annotation.Single

@Single
internal class AppDataStore(
    internal val fileStorage: FileStorage,
) {
    private val credentialCipher: CredentialCipher by lazy {
        CredentialCipher(fileStorage)
    }

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
            serializer =
                EncryptedAppSettingsSerializer(
                    delegate = protobufSerializer(AppSettings(version = "")),
                    cipher = credentialCipher,
                ),
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

internal class EncryptedAppSettingsSerializer(
    private val delegate: OkioSerializer<AppSettings>,
    private val cipher: CredentialCipher,
) : OkioSerializer<AppSettings> {
    override val defaultValue: AppSettings
        get() = delegate.defaultValue

    override suspend fun readFrom(source: BufferedSource): AppSettings =
        delegate.readFrom(source).transformApiKeys { value ->
            if (value.isBlank()) value else runCatching { cipher.decrypt(value) }.getOrDefault("")
        }

    override suspend fun writeTo(
        t: AppSettings,
        sink: BufferedSink,
    ) {
        val protected =
            t.transformApiKeys { value ->
                if (value.isBlank()) value else cipher.encrypt(value)
            }
        delegate.writeTo(protected, sink)
    }

    private fun AppSettings.transformApiKeys(transform: (String) -> String): AppSettings {
        val updatedAiType =
            when (val type = aiConfig.type) {
                is AppSettings.AiConfig.Type.OpenAI -> type.copy(apiKey = transform(type.apiKey))
                is AppSettings.AiConfig.Type.OnDevice -> type
            }
        // Unused since translation was removed; kept for stored-settings compatibility.
        val updatedTranslateProvider =
            when (val provider = translateConfig.provider) {
                is AppSettings.TranslateConfig.Provider.DeepL -> provider.copy(apiKey = transform(provider.apiKey))
                is AppSettings.TranslateConfig.Provider.GoogleCloud -> provider.copy(apiKey = transform(provider.apiKey))
                is AppSettings.TranslateConfig.Provider.LibreTranslate -> provider.copy(apiKey = transform(provider.apiKey))
                AppSettings.TranslateConfig.Provider.AI,
                AppSettings.TranslateConfig.Provider.GoogleWeb,
                -> provider
            }
        return copy(
            aiConfig = aiConfig.copy(type = updatedAiType),
            translateConfig = translateConfig.copy(provider = updatedTranslateProvider),
        )
    }
}
