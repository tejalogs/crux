package org.crux.data.datastore

import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.crux.common.protobufSerializer
import org.crux.data.datastore.model.AppSettings
import org.crux.data.io.InMemoryFileStorage
import org.crux.data.security.CredentialCipher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppSettingsLocalAiMigrationTest {
    private val cipher = CredentialCipher(InMemoryFileStorage())
    private val delegate = protobufSerializer(AppSettings(version = ""))
    private val serializer = EncryptedAppSettingsSerializer(delegate, cipher)

    private fun legacySettings(apiKey: String): AppSettings =
        AppSettings(
            version = "1.0.8",
            aiConfig =
                AppSettings.AiConfig(
                    tldr = true,
                    agent = true,
                    tldrPrompt = "custom tldr prompt",
                    type =
                        AppSettings.AiConfig.Type.OpenAI(
                            serverUrl = "https://api.example.com/v1",
                            apiKey = apiKey,
                            model = "gpt-legacy",
                        ),
                ),
        )

    @Test
    fun storedLegacyCloudTypeReadsBackAsOnDevice() =
        runTest {
            // As an older build stored it: the key encrypted, the cloud type as-is.
            val raw = Buffer()
            delegate.writeTo(legacySettings(apiKey = cipher.encrypt("sk-legacy-secret")), raw)

            val read = serializer.readFrom(raw)

            assertEquals(AppSettings.AiConfig.Type.OnDevice(), read.aiConfig.type)
            // Only the type value changes; the other AI fields are kept.
            assertEquals(true, read.aiConfig.tldr)
            assertEquals(true, read.aiConfig.agent)
            assertEquals("custom tldr prompt", read.aiConfig.tldrPrompt)
            assertEquals("1.0.8", read.version)
        }

    @Test
    fun legacyCloudServerAndKeyAreNeverWritten() =
        runTest {
            val raw = Buffer()
            serializer.writeTo(legacySettings(apiKey = "sk-legacy-secret"), raw)
            val bytes = raw.readByteArray()

            val text = bytes.decodeToString()
            assertFalse(text.contains("api.example.com"))
            assertFalse(text.contains("sk-legacy-secret"))
            assertFalse(text.contains("gpt-legacy"))
            assertEquals(AppSettings.AiConfig.Type.OnDevice(), delegate.readFrom(Buffer().write(bytes)).aiConfig.type)
        }

    @Test
    fun onDeviceConfigRoundTripsUnchanged() =
        runTest {
            val original =
                AppSettings(
                    version = "1.0.13",
                    aiConfig =
                        AppSettings.AiConfig(
                            agent = true,
                            type = AppSettings.AiConfig.Type.OnDevice(model = "gemma-custom", contextLength = 4096L),
                        ),
                )
            val raw = Buffer()
            serializer.writeTo(original, raw)

            assertEquals(original.aiConfig, serializer.readFrom(raw).aiConfig)
        }
}
