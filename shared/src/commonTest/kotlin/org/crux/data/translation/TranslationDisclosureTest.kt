package org.crux.data.translation

import org.crux.data.datastore.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationDisclosureTest {
    private fun settings(
        provider: AppSettings.TranslateConfig.Provider,
        acknowledged: String? = null,
        ai: AppSettings.AiConfig = AppSettings.AiConfig(),
    ) = AppSettings(
        version = "",
        aiConfig = ai,
        translateConfig = AppSettings.TranslateConfig(provider = provider, disclosureAcknowledged = acknowledged),
    )

    @Test
    fun defaultGoogleProviderRequiresDisclosureUntilAccepted() {
        val fresh = AppSettings(version = "")
        assertTrue(TranslationDisclosure.isRequired(fresh))
        val destination = requireNotNull(TranslationDisclosure.destination(fresh))
        assertEquals(TranslationDisclosure.Kind.GoogleWeb, destination.kind)

        val accepted = fresh.copy(translateConfig = TranslationDisclosure.acknowledge(fresh.translateConfig, destination))
        assertFalse(TranslationDisclosure.isRequired(accepted))
    }

    @Test
    fun changingProviderAsksAgain() {
        val google = settings(AppSettings.TranslateConfig.Provider.GoogleWeb)
        val googleKey = requireNotNull(TranslationDisclosure.destination(google)).key
        val deepl = settings(AppSettings.TranslateConfig.Provider.DeepL(apiKey = "k"), acknowledged = googleKey)
        assertTrue(TranslationDisclosure.isRequired(deepl))
    }

    @Test
    fun libreTranslateHostIsPartOfTheKey() {
        val a = settings(AppSettings.TranslateConfig.Provider.LibreTranslate(baseUrl = "https://one.example/api"))
        val b = settings(AppSettings.TranslateConfig.Provider.LibreTranslate(baseUrl = "https://two.example"))
        assertEquals("LibreTranslate:one.example", TranslationDisclosure.destination(a)?.key)
        val acceptedA = b.copy(translateConfig = b.translateConfig.copy(disclosureAcknowledged = "LibreTranslate:one.example"))
        assertTrue(TranslationDisclosure.isRequired(acceptedA))
    }

    @Test
    fun onDeviceAiNeverRequiresDisclosure() {
        val ai = AppSettings.AiConfig(type = AppSettings.AiConfig.Type.OnDevice())
        val s = settings(AppSettings.TranslateConfig.Provider.AI, ai = ai)
        assertNull(TranslationDisclosure.destination(s))
        assertFalse(TranslationDisclosure.isRequired(s))
    }

    @Test
    fun localServerDoesNotRequireButRemoteServerDoes() {
        fun withServer(url: String) =
            settings(
                AppSettings.TranslateConfig.Provider.AI,
                ai = AppSettings.AiConfig(type = AppSettings.AiConfig.Type.OpenAI(serverUrl = url, apiKey = "")),
            )
        assertFalse(TranslationDisclosure.isRequired(withServer("http://localhost:11434/v1")))
        assertFalse(TranslationDisclosure.isRequired(withServer("http://127.0.0.1:8080")))
        assertTrue(TranslationDisclosure.isRequired(withServer("https://api.example.com/v1")))
    }

    @Test
    fun hostParsing() {
        assertEquals("example.com", TranslationDisclosure.hostOf("https://user:pw@Example.com:8443/path?q=1"))
        assertEquals("[::1]", TranslationDisclosure.hostOf("http://[::1]:80/"))
        assertNull(TranslationDisclosure.hostOf(""))
    }
}
