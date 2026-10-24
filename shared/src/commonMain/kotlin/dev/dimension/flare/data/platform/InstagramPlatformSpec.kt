package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.instagram.InstagramDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

public const val INSTAGRAM_PLATFORM_ID: String = "instagram"
public const val INSTAGRAM_HOST: String = "instagram.com"

@Serializable
internal data class InstagramPostDeepLink(
    val code: String,
)

@Serializable
internal data class InstagramProfileDeepLink(
    val handle: String,
)

@HiddenFromObjC
public data object InstagramPlatformSpec : PlatformSpec {
    public override val platformId: String = INSTAGRAM_PLATFORM_ID
    public override val metadata: PlatformMetadata =
        PlatformMetadata(
            displayName = "Instagram",
            icon = UiIcon.Instagram,
            agentAliases = listOf("instagram", "ig", "insta", "instagram.com"),
        )
    public override val order: Int = 70

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> {
        val postPatterns = listOf(
            "https://www.instagram.com/p/{code}/",
            "https://www.instagram.com/p/{code}",
            "https://instagram.com/p/{code}/",
            "https://instagram.com/p/{code}",
            "https://www.instagram.com/reel/{code}/",
            "https://www.instagram.com/reel/{code}",
        )
        val profilePatterns = listOf(
            "https://www.instagram.com/{handle}/",
            "https://www.instagram.com/{handle}",
            "https://instagram.com/{handle}/",
            "https://instagram.com/{handle}",
        )

        val postLinks = postPatterns.map { pattern ->
            PlatformDeepLink(
                uriPattern = pattern,
                serializer = InstagramPostDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.OpenLinkDirectly("https://www.instagram.com/p/${data.code}/")
                },
            )
        }

        val profileLinks = profilePatterns.map { pattern ->
            PlatformDeepLink(
                uriPattern = pattern,
                serializer = InstagramProfileDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.OpenLinkDirectly("https://www.instagram.com/${data.handle}/")
                },
            )
        }

        return (postLinks + profileLinks).toImmutableList()
    }

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        InstagramDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(InstagramCredential.serializer()),
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        throw UnsupportedOperationException("$platformId guest data source is not supported")
}
