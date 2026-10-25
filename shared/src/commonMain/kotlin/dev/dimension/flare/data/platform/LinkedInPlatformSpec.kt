package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.linkedin.LinkedInDataSource
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

public const val LINKEDIN_PLATFORM_ID: String = "linkedin"
public const val LINKEDIN_HOST: String = "linkedin.com"

@Serializable
internal data class LinkedInPostDeepLink(
    val id: String,
)

@Serializable
internal data class LinkedInProfileDeepLink(
    val handle: String,
)

@HiddenFromObjC
public data object LinkedInPlatformSpec : PlatformSpec {
    public override val platformId: String = LINKEDIN_PLATFORM_ID
    public override val metadata: PlatformMetadata =
        PlatformMetadata(
            displayName = "LinkedIn",
            icon = UiIcon.LinkedIn,
            agentAliases = listOf("linkedin", "in", "linkedin.com"),
        )
    public override val order: Int = 75

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> {
        val postPatterns = listOf(
            "https://www.linkedin.com/posts/{id}/",
            "https://www.linkedin.com/posts/{id}",
            "https://linkedin.com/posts/{id}/",
            "https://linkedin.com/posts/{id}",
            "https://www.linkedin.com/feed/update/{id}/",
            "https://www.linkedin.com/feed/update/{id}",
            "https://linkedin.com/feed/update/{id}/",
            "https://linkedin.com/feed/update/{id}",
        )
        val profilePatterns = listOf(
            "https://www.linkedin.com/in/{handle}/",
            "https://www.linkedin.com/in/{handle}",
            "https://linkedin.com/in/{handle}/",
            "https://linkedin.com/in/{handle}",
        )

        val postLinks = postPatterns.map { pattern ->
            PlatformDeepLink(
                uriPattern = pattern,
                serializer = LinkedInPostDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.OpenLinkDirectly("https://www.linkedin.com/feed/update/${data.id}/")
                },
            )
        }

        val profileLinks = profilePatterns.map { pattern ->
            PlatformDeepLink(
                uriPattern = pattern,
                serializer = LinkedInProfileDeepLink.serializer(),
                callback = { data ->
                    DeeplinkRoute.OpenLinkDirectly("https://www.linkedin.com/in/${data.handle}/")
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
        LinkedInDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(LinkedInCredential.serializer()),
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        throw UnsupportedOperationException("$platformId guest data source is not supported")
}
