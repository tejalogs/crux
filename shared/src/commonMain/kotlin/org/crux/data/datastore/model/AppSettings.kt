package org.crux.data.datastore.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.crux.model.MicroBlogKey

@Serializable
public data class AppSettings(
    val version: String,
    val aiConfig: AiConfig = AiConfig(),
    val language: String = "",
    val translateConfig: TranslateConfig = TranslateConfig(),
    val linkOpenDefaults: LinkOpenDefaults = LinkOpenDefaults(),
    val mxgaEnabled: Boolean = false,
    val refreshHomeTimelineOnLaunch: Boolean = true,
    val homeTimelineAutoRefreshInterval: TimelineAutoRefreshInterval = TimelineAutoRefreshInterval.DISABLED,
    val redditTabAdded: Boolean = false,
    val xTabAdded: Boolean = false,
    // Crux: first-run welcome flow has been finished (or skipped).
    val onboardingCompleted: Boolean = false,
    // Crux: platforms the person chose to use (CruxPlatform names). Defaults match what earlier builds showed.
    val cruxPlatforms: List<String> = DEFAULT_CRUX_PLATFORMS,
    // Crux: Mastodon server used for the Mastodon web tab, e.g. "mastodon.social".
    val mastodonInstance: String = "",
    // Crux: Instagram/LinkedIn account-based home pills were moved to static live-web pills.
    val liveWebTabsMigrated: Boolean = false,
    // The starter RSS feeds were offered once; deleting every feed later must not bring them back.
    val rssDefaultsSeeded: Boolean = false,
    // Unused: a test build wrote this slot with the wrong meaning. Kept so later fields stay in place.
    val retiredHomeTabsFlag: Boolean = false,
    // Crux: the empty combined "Home" tab was dropped and every enabled platform got its pill.
    // Fields are stored by position, so new ones always go at the end.
    val homeTabsNormalized: Boolean = false,
) {
    public companion object {
        // Must be initialised before [default], which reads it through the constructor default.
        public val DEFAULT_CRUX_PLATFORMS: List<String> = listOf("Instagram", "LinkedIn", "X", "Reddit", "Rss")

        // for SwiftUI environment defaults
        public val default: AppSettings = AppSettings(version = "")
    }

    @Serializable
    public data class LinkOpenDefaults(
        val rules: List<Rule> = emptyList(),
    ) {
        @Serializable
        public data class Rule(
            val host: String,
            val method: Method,
        )

        @Serializable
        public sealed interface Method {
            @Serializable
            @SerialName("Browser")
            public data object Browser : Method

            @Serializable
            @SerialName("Account")
            public data class Account(
                val accountKey: MicroBlogKey,
            ) : Method
        }
    }

    @Serializable
    public data class TranslateConfig(
        val preTranslate: Boolean = false,
        val provider: Provider = Provider.GoogleWeb,
        val autoTranslateExcludedLanguages: List<String> = emptyList(),
        val preferPlatformTranslation: Boolean = false,
        val showOriginalWithTranslation: Boolean = false,
        // Key of the translation destination the person accepted in the one-time disclosure (new fields go last).
        val disclosureAcknowledged: String? = null,
    ) {
        @Serializable
        public sealed interface Provider {
            @Serializable
            @SerialName("AI")
            public data object AI : Provider

            @Serializable
            @SerialName("Google")
            public data object GoogleWeb : Provider

            @Serializable
            public data class DeepL(
                val apiKey: String = "",
                val usePro: Boolean = false,
            ) : Provider

            @Serializable
            public data class GoogleCloud(
                val apiKey: String = "",
            ) : Provider

            @Serializable
            public data class LibreTranslate(
                val baseUrl: String = "",
                val apiKey: String = "",
            ) : Provider
        }
    }

    @Serializable
    public data class AiConfig(
        @Deprecated(
            message = "Translation is always enabled.",
            level = DeprecationLevel.ERROR,
        )
        val translation: Boolean = false,
        val tldr: Boolean = false,
        val type: Type = Type.OnDevice(),
        val translatePrompt: String = AiPromptDefaults.TRANSLATE_PROMPT,
        val tldrPrompt: String = AiPromptDefaults.TLDR_PROMPT,
        @Deprecated(
            message = "Use AppSettings.translateConfig.preTranslate instead.",
            level = DeprecationLevel.ERROR,
        )
        val preTranslation: Boolean = false,
        val agent: Boolean = false,
    ) {
        public companion object {
            // for iOS
            public val default: AiConfig = AiConfig()
        }

        @Serializable
        public sealed interface Type {
            @Serializable
            @SerialName("org.crux.data.datastore.model.AppSettings.AiConfig.Type.OnDevice")
            public data class OnDevice(
                val model: String = DEFAULT_MODEL,
                val hardwareBackend: String = DEFAULT_HARDWARE_BACKEND,
                val contextLength: Long = DEFAULT_CONTEXT_LENGTH,
                val enableMtp: Boolean = true,
                val localhostEndpoint: String = DEFAULT_LOCALHOST_ENDPOINT,
            ) : Type {
                public companion object {
                    public const val DEFAULT_MODEL: String = "gemma-4-e2b"
                    public const val DEFAULT_HARDWARE_BACKEND: String = "auto"
                    public const val DEFAULT_CONTEXT_LENGTH: Long = 8192L
                    public const val DEFAULT_LOCALHOST_ENDPOINT: String = "http://localhost:11434"
                }
            }

            @Serializable
            public data class OpenAI(
                val serverUrl: String,
                val apiKey: String,
                val model: String = "",
                val reasoningEffort: String = "",
                val extraBody: String = "",
            ) : Type
        }
    }
}

@Serializable
public enum class TimelineAutoRefreshInterval(
    public val minutes: Int,
) {
    DISABLED(0),
    FIVE_MINUTES(5),
    FIFTEEN_MINUTES(15),
    THIRTY_MINUTES(30),
    ONE_HOUR(60),
    ONE_MINUTE(1),
}
