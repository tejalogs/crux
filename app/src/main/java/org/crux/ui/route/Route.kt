package org.crux.ui.route

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable
import org.crux.common.SerializableImmutableList
import org.crux.data.model.tab.UiSourceTimelineTabItem
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.feature.agent.localhistory.LocalHistoryAgentTarget
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiMedia

@Immutable
internal sealed interface Route : NavKey {
    @Serializable
    sealed interface Status : Route {
        @Serializable
        data class Detail(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class AltText(
            val text: String,
        ) : Status

        @Serializable
        data class DeleteConfirm(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status

        @Serializable
        data class ShareSheet(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val shareUrl: String,
            val fxShareUrl: String? = null,
            val fixvxShareUrl: String? = null,
        ) : Status

        @Serializable
        data class Insight(
            val accountType: AccountType,
            val statusKey: MicroBlogKey,
        ) : Status

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
        data object PostActionLayout : Settings

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
        data object LocalFilter : Settings

        @Serializable
        data object LocalHistory : Settings

        @Serializable
        data object AgentHistory : Settings

        @Serializable
        data object AiConfig : Settings

        @Serializable
        data object TranslationConfig : Settings

        @Serializable
        data object ColorPicker : Settings

        @Serializable
        data object AppLogging : Settings

        @Serializable
        data class LocalFilterEdit(
            val keyword: String?,
        ) : Settings

        @Serializable
        data object ColorSpace : Settings

        @Serializable
        data object HardenedBrowser : Settings

        /** Per-site protection level, focus tweaks and data clearing for the live web tabs. */
        @Serializable
        data object PrivacyTweaks : Settings
    }

    /** A Crux platform's real site on its own page (X, Reddit). */
    @Serializable
    data class LiveWeb(
        val site: String,
        val initialUrl: String? = null,
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
    ) : Route

    @Serializable
    data class LinkedInWeb(
        val initialUrl: String? = null,
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
    data class AgentChat(
        val conversationId: String = "generic-chat",
        val initialMessage: String? = null,
    ) : Route

    @Serializable
    data class LocalHistoryAgent(
        val conversationId: String,
        val query: String? = null,
        val target: LocalHistoryAgentTarget = LocalHistoryAgentTarget.All,
    ) : Route

    @Serializable
    sealed interface Profile : Route {
        @Serializable
        data class User(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile

        @Serializable
        data class UserNameWithHost(
            val accountType: AccountType,
            val name: String,
            val host: String,
        ) : Profile

        @Serializable
        data class Following(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile

        @Serializable
        data class Fans(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile

        @Serializable
        data class Insight(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile

        @Serializable
        data class Me(
            val accountType: AccountType,
        ) : Profile
    }

    @Serializable
    sealed interface Gallery : Route {
        @Serializable
        data class Detail(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Gallery

        @Serializable
        data class Comments(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Gallery
    }

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

        @Serializable
        data class StatusMedia(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val index: Int,
            val preview: String?,
        ) : Media
    }

    @Serializable
    data object AccountSelection : Route

    @Serializable
    data class DeepLinkAccountPicker(
        val originalUrl: String,
        val data: ImmutableMap<MicroBlogKey, Route>,
    ) : Route

    @Serializable
    data class Article(
        val accountType: AccountType,
        val articleKey: MicroBlogKey,
    ) : Route

    @Serializable
    data class BlockUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class UnblockUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class MuteUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class UnmuteUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    @Serializable
    public data class ReportUser(
        val accountType: AccountType?,
        val userKey: MicroBlogKey,
    ) : Route

    companion object {
        public fun parse(url: String): Route? {
            val deeplinkRoute = DeeplinkRoute.parse(url) ?: return null
            return from(deeplinkRoute)
        }

        public fun from(deeplinkRoute: DeeplinkRoute): Route? {
            return when (deeplinkRoute) {
                is DeeplinkRoute.Timeline.XQTDeviceFollow -> {
                    null
                }

                is DeeplinkRoute.OpenLinkDirectly -> {
                    null
                }

                is DeeplinkRoute.DeepLinkAccountPicker -> {
                    DeepLinkAccountPicker(
                        originalUrl = deeplinkRoute.originalUrl,
                        data =
                            deeplinkRoute.data
                                .mapNotNull { (key, value) ->
                                    val route = Route.from(value) ?: return@mapNotNull null
                                    key to route
                                }.toMap()
                                .toImmutableMap(),
                    )
                }

                // There are no login providers left: "log in" means choosing Crux platforms.
                is DeeplinkRoute.Login -> {
                    Settings.Accounts
                }

                is DeeplinkRoute.Relogin -> {
                    reloginRoute(deeplinkRoute.platformId)
                }

                is DeeplinkRoute.Compose.New -> {
                    CruxCompose()
                }

                is DeeplinkRoute.Compose.Crux -> {
                    CruxCompose(initialText = deeplinkRoute.initialText)
                }

                // Replying to or quoting a post needs an API account, and none exist any more.
                is DeeplinkRoute.Compose.Quote -> {
                    null
                }

                is DeeplinkRoute.Compose.Reply -> {
                    null
                }

                is DeeplinkRoute.Media.Image -> {
                    Media.Image(
                        uri = deeplinkRoute.uri,
                        previewUrl = deeplinkRoute.previewUrl,
                        customHeaders = deeplinkRoute.customHeaders,
                    )
                }

                is DeeplinkRoute.Media.Podcast -> {
                    null
                }

                is DeeplinkRoute.Media.StatusMedia -> {
                    Media.StatusMedia(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                        index = deeplinkRoute.index,
                        preview = deeplinkRoute.preview,
                    )
                }

                is DeeplinkRoute.Profile.User -> {
                    Profile.User(
                        accountType = deeplinkRoute.accountType,
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.Profile.UserNameWithHost -> {
                    Profile.UserNameWithHost(
                        accountType = deeplinkRoute.accountType,
                        name = deeplinkRoute.userName,
                        host = deeplinkRoute.host,
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

                is DeeplinkRoute.Article -> {
                    Article(
                        accountType = deeplinkRoute.accountType,
                        articleKey = deeplinkRoute.articleKey,
                    )
                }

                is DeeplinkRoute.Search -> {
                    null
                }

                is DeeplinkRoute.Gallery.Detail -> {
                    Gallery.Detail(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.AltText -> {
                    Status.AltText(
                        text = deeplinkRoute.text,
                    )
                }

                is DeeplinkRoute.Status.DeleteConfirm -> {
                    Status.DeleteConfirm(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.Detail -> {
                    Status.Detail(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                    )
                }

                is DeeplinkRoute.Status.Insight -> {
                    Status.Insight(
                        accountType = deeplinkRoute.accountType,
                        statusKey = deeplinkRoute.statusKey,
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

                is DeeplinkRoute.Status.ShareSheet -> {
                    Status.ShareSheet(
                        statusKey = deeplinkRoute.statusKey,
                        accountType = deeplinkRoute.accountType,
                        shareUrl = deeplinkRoute.shareUrl,
                        fxShareUrl = deeplinkRoute.fxShareUrl,
                        fixvxShareUrl = deeplinkRoute.fixvxShareUrl,
                    )
                }

                is DeeplinkRoute.BlockUser -> {
                    Route.BlockUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.UnblockUser -> {
                    Route.UnblockUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                // Direct messages, lists, local follows and search have no data source left.
                is DeeplinkRoute.DirectMessage,
                is DeeplinkRoute.EditLocalFollow,
                is DeeplinkRoute.EditUserList,
                is DeeplinkRoute.AllLists,
                is DeeplinkRoute.AllDirectMessages,
                -> {
                    null
                }

                is DeeplinkRoute.MuteUser -> {
                    Route.MuteUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.UnmuteUser -> {
                    Route.UnmuteUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }

                is DeeplinkRoute.ReportUser -> {
                    Route.ReportUser(
                        accountType = deeplinkRoute.accountKey?.let { AccountType.Specific(it) },
                        userKey = deeplinkRoute.userKey,
                    )
                }
            }
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
