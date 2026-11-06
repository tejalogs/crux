package org.crux.data.platform

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.crux.data.datasource.instagram.InstagramDataSource
import org.crux.data.datasource.microblog.MicroblogDataSource
import org.crux.data.model.tab.TimelineSpec
import org.crux.model.MicroBlogKey
import org.crux.model.PlatformDataSourceContext
import org.crux.model.PlatformDeepLink
import org.crux.model.PlatformMetadata
import org.crux.model.PlatformSpec
import org.crux.ui.model.UiIcon
import kotlin.native.HiddenFromObjC

public const val INSTAGRAM_PLATFORM_ID: String = "instagram"
public const val INSTAGRAM_HOST: String = "instagram.com"

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

    /**
     * Instagram opens in its live web screen, not in native Status/Profile screens (there is no data
     * source behind them), so no link is claimed here and instagram.com/linkedin.com links fall
     * through to the live web route.
     */
    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

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
    ): MicroblogDataSource = throw UnsupportedOperationException("$platformId guest data source is not supported")
}
