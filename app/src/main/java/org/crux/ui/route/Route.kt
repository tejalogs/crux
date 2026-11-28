package org.crux.ui.route

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiMedia

@Immutable
internal sealed interface Route : NavKey {
    @Serializable
    sealed interface Status : Route {
        @Serializable
        data class OmniScribe(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val initialText: String = "",
            val authorName: String = "",
            val authorHandle: String = "",
            val articleTitle: String = "",
            val sourceUrl: String = "",
        ) : Status

        @Serializable
        data class SmartReply(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val initialText: String = "",
            val authorName: String = "",
            val authorHandle: String = "",
            val mediaDescription: String = "",
            val parentContext: String = "",
        ) : Status
    }

    @Serializable
    sealed interface Settings : Route {
        @Serializable
        data object Main : Settings

        @Serializable
        data object Accounts : Settings

        @Serializable
        data object AppearanceTheme : Settings

        @Serializable
        data object AppearanceLayout : Settings

        @Serializable
        data object AppearanceDisplay : Settings

        @Serializable
        data object AppearanceMedia : Settings

        @Serializable
        data object Behavior : Settings

        @Serializable
        data object Privacy : Settings

        @Serializable
        data object PrivacyTrafficLog : Settings

        @Serializable
        data object PrivacyBlocklist : Settings

        @Serializable
        data object LinkOpenDefaults : Settings

        @Serializable
        data object Storage : Settings

        @Serializable
        data object About : Settings

        @Serializable
        data object OpenSourceLicenses : Settings

        @Serializable
        data object AiConfig : Settings

        @Serializable
        data object ColorPicker : Settings

        @Serializable
        data object AppLogging : Settings

        @Serializable
        data object ColorSpace : Settings

        @Serializable
        data object HardenedBrowser : Settings

        /** Per-site protection level, focus tweaks and data clearing for the live web tabs. */
        @Serializable
        data object PrivacyTweaks : Settings

        @Serializable
        data object PrivacyCheckup : Settings
    }

    /** A Crux platform's real site on its own page (X, Reddit, Mastodon). */
    @Serializable
    data class LiveWeb(
        val site: String,
        val initialUrl: String? = null,
        val forSignIn: Boolean = false,
    ) : Route

    /** Crux: write once, then pick which connected platforms to post it on. */
    @Serializable
    data class CruxCompose(
        val initialText: String = "",
    ) : Route

    @Serializable
    data class LiveShare(
        val sites: kotlin.collections.List<String>,
        val text: String,
        val url: String,
        val slidesSaved: Boolean,
    ) : Route

    sealed interface Rss : Route {
        @Serializable
        data object Sources : Rss

        @Serializable
        data class Timeline(
            val id: Int,
            val url: String,
            val title: String?,
            val favIcon: String?,
        ) : Rss

        @Serializable
        data class Detail(
            val url: String,
            val descriptionHtml: String? = null,
            val title: String? = null,
            val fallbackHtml: String? = null,
        ) : Rss

        @Serializable
        data class Create(
            val initialUrl: String? = null,
        ) : Rss

        @Serializable
        data class OPMLImport(
            val url: String,
        ) : Rss

        @Serializable
        data class Edit(
            val id: Int,
        ) : Rss
    }

    @Serializable
    data object Home : Route

    data class Timeline(
        val tabItem: UiTimelineTabItem,
    ) : Route

    @Serializable
    data class DeckTimeline(
        val id: String,
    ) : Route

    @Serializable
    data class InstagramWeb(
        val initialUrl: String? = null,
        val forSignIn: Boolean = false,
    ) : Route

    @Serializable
    data class LinkedInWeb(
        val initialUrl: String? = null,
        val forSignIn: Boolean = false,
    ) : Route

    @Serializable
    data object ReadingList : Route

    @Serializable
    data object TabSettings : Route

    @Serializable
    data class TabGroupConfig(
        val groupId: String? = null,
    ) : Route

    @Serializable
    sealed interface Media : Route {
        @Serializable
        data class Image(
            val uri: String,
            val previewUrl: String?,
            val customHeaders: Map<String, String>? = null,
        ) : Media

        @Serializable
        data class RawMedia(
            val medias: SerializableImmutableList<UiMedia>,
            val index: Int = 0,
            val preview: String? = null,
        ) : Media
    }

    companion object {
        public fun parse(url: String): Route? {
            val deeplinkRoute = DeeplinkRoute.parse(url) ?: return null
            return from(deeplinkRoute)
        }

        public fun from(deeplinkRoute: DeeplinkRoute): Route? =
            when (deeplinkRoute) {
                is DeeplinkRoute.Login -> Settings.Accounts

                is DeeplinkRoute.Relogin -> reloginRoute(deeplinkRoute.platformId)

                is DeeplinkRoute.Compose.New -> CruxCompose()

                is DeeplinkRoute.Compose.Crux -> CruxCompose(initialText = deeplinkRoute.initialText)

                is DeeplinkRoute.Media.Image -> {
                    Media.Image(
                        uri = deeplinkRoute.uri,
                        previewUrl = deeplinkRoute.previewUrl,
                        customHeaders = deeplinkRoute.customHeaders,
                    )
                }

                is DeeplinkRoute.Rss.Detail -> {
                    Rss.Detail(
                        url = deeplinkRoute.url,
                        descriptionHtml = deeplinkRoute.descriptionHtml,
                        fallbackHtml = deeplinkRoute.fallbackHtml,
                        title = deeplinkRoute.title,
                    )
                }

                is DeeplinkRoute.Status.OmniScribe -> {
                    Status.OmniScribe(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                        initialText = deeplinkRoute.initialText,
                        authorName = deeplinkRoute.authorName,
                        authorHandle = deeplinkRoute.authorHandle,
                        articleTitle = deeplinkRoute.articleTitle,
                        sourceUrl = deeplinkRoute.sourceUrl,
                    )
                }

                is DeeplinkRoute.Status.SmartReply -> {
                    Status.SmartReply(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                        initialText = deeplinkRoute.initialText,
                        authorName = deeplinkRoute.authorName,
                        authorHandle = deeplinkRoute.authorHandle,
                        mediaDescription = deeplinkRoute.mediaDescription,
                        parentContext = deeplinkRoute.parentContext,
                    )
                }

                // Legacy Flare Fediverse/Podcast/DM/Profile/Gallery routes have been stripped in Crux v2.
                else -> null
            }
    }
}

/** Where "sign in again" leads: Instagram and LinkedIn are live web pages, everything else is the platform list. */
internal fun reloginRoute(platformId: String): Route =
    when (platformId) {
        INSTAGRAM_PLATFORM_ID -> Route.InstagramWeb()
        LINKEDIN_PLATFORM_ID -> Route.LinkedInWeb()
        else -> Route.Settings.Accounts
    }
