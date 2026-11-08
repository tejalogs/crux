package org.crux.data.translation

import org.crux.data.datastore.model.AppSettings

/**
 * Decides whether the person must be told, once, where translated text is sent before the first
 * translation goes out. Nothing is sent while [isRequired] is true.
 */
public object TranslationDisclosure {
    public enum class Kind {
        GoogleWeb,
        DeepL,
        GoogleCloud,
        LibreTranslate,
        AiServer,
    }

    /** Where text would be sent: a provider [kind] and, when it is configurable, its [host]. */
    public data class Destination(
        val kind: Kind,
        val host: String? = null,
    ) {
        /** Stable identity of the destination; a change of provider or host asks again. */
        val key: String get() = if (host.isNullOrEmpty()) kind.name else "${kind.name}:$host"
    }

    /** The remote destination the current settings would send text to, or null when nothing leaves the device. */
    public fun destination(settings: AppSettings): Destination? =
        when (val provider = settings.translateConfig.provider) {
            AppSettings.TranslateConfig.Provider.GoogleWeb -> {
                Destination(Kind.GoogleWeb, "translate.google.com")
            }

            is AppSettings.TranslateConfig.Provider.DeepL -> {
                Destination(Kind.DeepL, if (provider.usePro) "api.deepl.com" else "api-free.deepl.com")
            }

            is AppSettings.TranslateConfig.Provider.GoogleCloud -> {
                Destination(Kind.GoogleCloud, "translation.googleapis.com")
            }

            is AppSettings.TranslateConfig.Provider.LibreTranslate -> {
                Destination(Kind.LibreTranslate, hostOf(provider.baseUrl).orEmpty())
            }

            AppSettings.TranslateConfig.Provider.AI -> {
                when (val type = settings.aiConfig.type) {
                    is AppSettings.AiConfig.Type.OnDevice -> {
                        null
                    }

                    is AppSettings.AiConfig.Type.OpenAI -> {
                        hostOf(type.serverUrl)
                            ?.takeUnless { isLoopback(it) }
                            ?.let { Destination(Kind.AiServer, it) }
                    }
                }
            }
        }

    /** True when text would leave the device and the person has not yet accepted this destination. */
    public fun isRequired(settings: AppSettings): Boolean =
        destination(settings)?.let { it.key != settings.translateConfig.disclosureAcknowledged } ?: false

    /** The translate settings with [destination] recorded as accepted. */
    public fun acknowledge(
        config: AppSettings.TranslateConfig,
        destination: Destination,
    ): AppSettings.TranslateConfig = config.copy(disclosureAcknowledged = destination.key)

    internal fun hostOf(url: String): String? {
        val withoutScheme = url.trim().substringAfter("://", url.trim())
        val authority =
            withoutScheme
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringAfterLast('@')
        val host =
            if (authority.startsWith("[")) {
                authority.substringBefore(']') + "]"
            } else {
                authority.substringBefore(':')
            }
        return host.lowercase().ifEmpty { null }
    }

    private fun isLoopback(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host.endsWith(".localhost")
}
