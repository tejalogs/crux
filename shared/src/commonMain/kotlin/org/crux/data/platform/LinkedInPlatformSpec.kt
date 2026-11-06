package org.crux.data.platform

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.crux.data.datasource.linkedin.LinkedInDataSource
import org.crux.data.datasource.microblog.MicroblogDataSource
import org.crux.data.model.tab.TimelineSpec
import org.crux.model.MicroBlogKey
import org.crux.model.PlatformDataSourceContext
import org.crux.model.PlatformDeepLink
import org.crux.model.PlatformMetadata
import org.crux.model.PlatformSpec
import org.crux.ui.model.UiIcon
import kotlin.native.HiddenFromObjC

public const val LINKEDIN_PLATFORM_ID: String = "linkedin"
public const val LINKEDIN_HOST: String = "linkedin.com"

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

    /**
     * LinkedIn opens in its live web screen, not in native Status/Profile screens (there is no data
     * source behind them), so no link is claimed here and instagram.com/linkedin.com links fall
     * through to the live web route.
     */
    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

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
    ): MicroblogDataSource = throw UnsupportedOperationException("$platformId guest data source is not supported")
}
