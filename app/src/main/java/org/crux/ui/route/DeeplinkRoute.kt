package org.crux.ui.route

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey

public const val APPSCHEMA: String = "crux"

@Serializable
public sealed class DeeplinkRoute {
    @Serializable
    public data object Login : DeeplinkRoute()

    @Serializable
    public data class Relogin(
        val accountKey: MicroBlogKey,
        @SerialName("platformType")
        val platformId: String,
    ) : DeeplinkRoute()

    @Serializable
    public sealed class Timeline : DeeplinkRoute() {
        @Serializable
        public data class XQTDeviceFollow(
            val accountType: AccountType,
        ) : Timeline()
    }

    @Serializable
    public sealed class Status : DeeplinkRoute() {
        @Serializable
        public data class Detail(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class AltText(
            val text: String,
        ) : Status()

        @Serializable
        public data class DeleteConfirm(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Status()

        @Serializable
        public data class ShareSheet(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val shareUrl: String,
            val fxShareUrl: String? = null,
            val fixvxShareUrl: String? = null,
        ) : Status()

        @Serializable
        public data class OmniScribe(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val initialText: String = "",
            val authorName: String = "",
            val authorHandle: String = "",
            val articleTitle: String = "",
            val sourceUrl: String = "",
        ) : Status()

        @Serializable
        public data class SmartReply(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val initialText: String = "",
            val authorName: String = "",
            val authorHandle: String = "",
            val mediaDescription: String = "",
            val parentContext: String = "",
        ) : Status()
    }

    @Serializable
    public sealed class Gallery : DeeplinkRoute() {
        @Serializable
        public data class Detail(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
        ) : Gallery()
    }

    @Serializable
    public sealed class Rss : DeeplinkRoute() {
        @Serializable
        public data class Detail(
            val url: String,
            val descriptionHtml: String? = null,
            val title: String? = null,
            val fallbackHtml: String? = null,
        ) : Rss()
    }

    @Serializable
    public data class Article(
        val accountType: AccountType,
        val articleKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class Search(
        val accountType: AccountType,
        val query: String,
    ) : DeeplinkRoute()

    @Serializable
    public sealed class Compose : DeeplinkRoute() {
        @Serializable
        public data object New : Compose()

        @Serializable
        public data class Reply(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
            val initialText: String = "",
        ) : Compose()

        /** Crux's write-once compose sheet, optionally prefilled (share sheet, tile, shortcut). */
        @Serializable
        public data class Crux(
            val initialText: String = "",
        ) : Compose()

        @Serializable
        public data class Quote(
            val accountKey: MicroBlogKey,
            val statusKey: MicroBlogKey,
        ) : Compose()
    }

    @Serializable
    public sealed class Media : DeeplinkRoute() {
        @Serializable
        public data class Image(
            val uri: String,
            val previewUrl: String?,
            val customHeaders: Map<String, String>? = null,
        ) : Media()

        @Serializable
        public data class StatusMedia(
            val statusKey: MicroBlogKey,
            val accountType: AccountType,
            val index: Int,
            val preview: String?,
            val aspectRatio: Float = 0f,
            val previewIsImage: Boolean = true,
        ) : Media()

        @Serializable
        public data class Podcast(
            val accountType: AccountType,
            val id: String,
        ) : Media()
    }

    @Serializable
    public sealed class Profile : DeeplinkRoute() {
        @Serializable
        public data class User(
            val accountType: AccountType,
            val userKey: MicroBlogKey,
        ) : Profile()

        @Serializable
        public data class UserNameWithHost(
            val accountType: AccountType,
            val userName: String,
            val host: String,
        ) : Profile()
    }

    @Serializable
    public data class DeepLinkAccountPicker(
        val originalUrl: String,
        val data: Map<MicroBlogKey, DeeplinkRoute>,
    ) : DeeplinkRoute()

    @Serializable
    public data class OpenLinkDirectly(
        val url: String,
    ) : DeeplinkRoute()

    @Serializable
    public data class EditUserList(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class EditLocalFollow(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class DirectMessage(
        val accountKey: MicroBlogKey,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class BlockUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class UnblockUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class MuteUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class UnmuteUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class ReportUser(
        val accountKey: MicroBlogKey?,
        val userKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class AllLists(
        val accountKey: MicroBlogKey,
    ) : DeeplinkRoute()

    @Serializable
    public data class AllDirectMessages(
        val accountKey: MicroBlogKey,
    ) : DeeplinkRoute()

    public companion object Companion {
        @OptIn(ExperimentalSerializationApi::class)
        public fun parse(uri: String): DeeplinkRoute? {
            if (!uri.startsWith("$APPSCHEMA://")) return null
            val route =
                runCatching {
                    ProtoBuf.decodeFromHexString<RoutePackage>(uri.removePrefix("$APPSCHEMA://")).route
                }.getOrNull() ?: return null
            // "Open this link directly" hands its target back to the link opener, which stamps
            // in-app links as trusted. A wrapped in-app link would skip [isSafeFromContent], so the
            // wrapper may only point outside the app.
            if (route is OpenLinkDirectly && isInternalLink(route.url)) return null
            return route
        }

        public fun isDeeplink(uri: String): Boolean = uri.startsWith("$APPSCHEMA://")

        /**
         * Whether [uri] uses one of Crux's own schemes (`crux:` routes or `flare-event:` post
         * actions), in any letter case and with or without the `//`.
         */
        public fun isInternalLink(uri: String): Boolean {
            val scheme = uri.trim().substringBefore(':', missingDelimiterValue = "")
            return scheme.isNotEmpty() && scheme.lowercase() in INTERNAL_SCHEMES
        }

        private val INTERNAL_SCHEMES = setOf(APPSCHEMA, "flare-event")

        /**
         * Whether a link found inside feed, post or article content may be opened. Ordinary web
         * links are fine; an in-app link is allowed only for plain navigation (profiles, search,
         * opening a post, starting a post, login). Prefilled AI sheets, media viewers, article
         * HTML, moderation and account routes, and flare-event:// post actions can only come from
         * Crux's own buttons, so a crafted link in content cannot open them.
         */
        public fun isSafeFromContent(uri: String): Boolean {
            val trimmed = uri.trim()
            if (!isInternalLink(trimmed)) return true
            // flare-event:// actions and odd spellings such as CRUX:// are never content links.
            if (!isDeeplink(trimmed)) return false
            return when (parse(trimmed)) {
                is Profile.User,
                is Profile.UserNameWithHost,
                is Search,
                is Status.Detail,
                is Compose.New,
                is Compose.Crux,
                is Login,
                -> true

                else -> false
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
public fun DeeplinkRoute.toUri(): String {
    val protobuf = ProtoBuf.encodeToHexString(RoutePackage(this))
    return "$APPSCHEMA://$protobuf"
}

@Serializable
private data class RoutePackage(
    val route: DeeplinkRoute,
)
