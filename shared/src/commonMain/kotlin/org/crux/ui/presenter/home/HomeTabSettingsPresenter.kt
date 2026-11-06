package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.crux.data.model.AvatarShape
import org.crux.data.model.IconType
import org.crux.data.model.PostActionStyle
import org.crux.data.model.TimelineDisplayMode
import org.crux.data.model.VideoAutoplay
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.data.model.appearance.AppearancePatch
import org.crux.data.model.appearance.TimelineAppearance
import org.crux.data.model.tab.TimelineFilterConfig
import org.crux.data.model.tab.TimelineMergePolicy
import org.crux.data.model.tab.UiGroupTimelineTabItem
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.isSystemHomeMixedTimeline
import org.crux.data.model.tab.resolveTimelineAppearance
import org.crux.data.model.tab.withSystemHomeMixedTimelineEnabled
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.TabPickerUiIcons
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter

@WebPresenter("homeTabSettings")
public class HomeTabSettingsPresenter : PresenterBase<HomeTabSettingsPresenter.State>() {
    private val settingsRepository: SettingsRepository by koinInject()
    private val appScope: CoroutineScope by koinInject()

    private val homeTimelineTabs by lazy {
        settingsRepository.homeTimelineTabs
            .map { it.toImmutableList() }
    }

    @Composable
    override fun body(): State {
        val tabs by homeTimelineTabs.collectAsUiState()
        val groupConfigState = remember { GroupConfigPresenter() }.body()
        val availableIcons =
            remember {
                TabPickerUiIcons.map { IconType.Material(it) }.toImmutableList()
            }

        return object : State {
            override val homeTimelineTabs: UiState<ImmutableList<UiTimelineTabItem>> = tabs
            override val availableIcons: ImmutableList<IconType> = availableIcons

            override fun replaceHomeTimelineTabs(tabs: List<UiTimelineTabItem>) {
                appScope.launch {
                    settingsRepository.replaceHomeTimelineTabs(tabs)
                }
            }

            override fun replaceHomeTimelineTabsWithSystemHomeMixedTimeline(
                tabs: List<UiTimelineTabItem>,
                enabled: Boolean,
                mergePolicy: TimelineMergePolicy,
            ) {
                appScope.launch {
                    settingsRepository.replaceHomeTimelineTabs(
                        tabs.withSystemHomeMixedTimelineEnabled(
                            enabled = enabled,
                            mergePolicy = mergePolicy,
                        ),
                    )
                }
            }

            override fun homeTimelineTabsWithSystemHomeMixedTimeline(
                tabs: List<UiTimelineTabItem>,
                enabled: Boolean,
                mergePolicy: TimelineMergePolicy,
            ): ImmutableList<UiTimelineTabItem> =
                tabs
                    .withSystemHomeMixedTimelineEnabled(
                        enabled = enabled,
                        mergePolicy = mergePolicy,
                    ).toImmutableList()

            override fun updateTabPresentation(
                tab: UiTimelineTabItem,
                title: String,
                icon: IconType,
                enabled: Boolean,
                excludedKinds: String,
                excludedContents: String,
                layoutEnabled: Boolean,
                timelineDisplayMode: TimelineDisplayMode,
                fullWidthPost: Boolean,
                postActionStyle: PostActionStyle,
                showNumbers: Boolean,
                displayEnabled: Boolean,
                absoluteTimestamp: Boolean,
                showPlatformLogo: Boolean,
                showLinkPreview: Boolean,
                compatLinkPreview: Boolean,
                mediaEnabled: Boolean,
                showMedia: Boolean,
                showSensitiveContent: Boolean,
                expandContentWarning: Boolean,
                expandMediaSize: Boolean,
                videoAutoplay: VideoAutoplay,
                themeEnabled: Boolean,
                avatarShape: AvatarShape,
            ): UiTimelineTabItem =
                tab.withPresentationOverrides(
                    title = title,
                    icon = icon,
                    appearancePatch =
                        buildAppearancePatch(
                            layoutEnabled = layoutEnabled,
                            timelineDisplayMode = timelineDisplayMode,
                            fullWidthPost = fullWidthPost,
                            postActionStyle = postActionStyle,
                            showNumbers = showNumbers,
                            displayEnabled = displayEnabled,
                            absoluteTimestamp = absoluteTimestamp,
                            showPlatformLogo = showPlatformLogo,
                            showLinkPreview = showLinkPreview,
                            compatLinkPreview = compatLinkPreview,
                            mediaEnabled = mediaEnabled,
                            showMedia = showMedia,
                            showSensitiveContent = showSensitiveContent,
                            expandContentWarning = expandContentWarning,
                            expandMediaSize = expandMediaSize,
                            videoAutoplay = videoAutoplay,
                            themeEnabled = themeEnabled,
                            avatarShape = avatarShape,
                        ),
                    enabled = enabled,
                    filterConfig =
                        TimelineFilterConfig(
                            excludedKinds = excludedKinds.parseEnumList(),
                            excludedContents = excludedContents.parseEnumList(),
                        ),
                )

            override fun buildGroupItem(
                initialItem: UiTimelineTabItem?,
                name: String,
                icon: IconType,
                enabled: Boolean,
                tabs: List<UiTimelineTabItem>,
                mergePolicy: TimelineMergePolicy,
                excludedKinds: String,
                excludedContents: String,
                layoutEnabled: Boolean,
                timelineDisplayMode: TimelineDisplayMode,
                fullWidthPost: Boolean,
                postActionStyle: PostActionStyle,
                showNumbers: Boolean,
                displayEnabled: Boolean,
                absoluteTimestamp: Boolean,
                showPlatformLogo: Boolean,
                showLinkPreview: Boolean,
                compatLinkPreview: Boolean,
                mediaEnabled: Boolean,
                showMedia: Boolean,
                showSensitiveContent: Boolean,
                expandContentWarning: Boolean,
                expandMediaSize: Boolean,
                videoAutoplay: VideoAutoplay,
                themeEnabled: Boolean,
                avatarShape: AvatarShape,
                defaultGroupName: String,
            ): UiTimelineTabItem? =
                groupConfigState.buildGroupItem(
                    initialItem = initialItem as? UiGroupTimelineTabItem,
                    name = name,
                    icon = icon,
                    appearancePatch =
                        buildAppearancePatch(
                            layoutEnabled = layoutEnabled,
                            timelineDisplayMode = timelineDisplayMode,
                            fullWidthPost = fullWidthPost,
                            postActionStyle = postActionStyle,
                            showNumbers = showNumbers,
                            displayEnabled = displayEnabled,
                            absoluteTimestamp = absoluteTimestamp,
                            showPlatformLogo = showPlatformLogo,
                            showLinkPreview = showLinkPreview,
                            compatLinkPreview = compatLinkPreview,
                            mediaEnabled = mediaEnabled,
                            showMedia = showMedia,
                            showSensitiveContent = showSensitiveContent,
                            expandContentWarning = expandContentWarning,
                            expandMediaSize = expandMediaSize,
                            videoAutoplay = videoAutoplay,
                            themeEnabled = themeEnabled,
                            avatarShape = avatarShape,
                        ),
                    enabled = enabled,
                    tabs = tabs,
                    mergePolicy = mergePolicy,
                    filterConfig =
                        TimelineFilterConfig(
                            excludedKinds = excludedKinds.parseEnumList(),
                            excludedContents = excludedContents.parseEnumList(),
                        ),
                    defaultGroupName = defaultGroupName,
                )

            override fun isSystemHomeMixedTimeline(tab: UiTimelineTabItem): Boolean = tab.isSystemHomeMixedTimeline

            override fun isGroup(tab: UiTimelineTabItem): Boolean = tab is UiGroupTimelineTabItem

            override fun groupChildren(tab: UiTimelineTabItem): ImmutableList<UiTimelineTabItem> =
                ((tab as? UiGroupTimelineTabItem)?.children ?: emptyList()).toImmutableList()

            override fun groupMergePolicy(tab: UiTimelineTabItem): TimelineMergePolicy =
                (tab as? UiGroupTimelineTabItem)?.mergePolicy ?: TimelineMergePolicy.Time

            override fun resolveAppearance(
                tab: UiTimelineTabItem,
                base: TimelineAppearance,
            ): TimelineAppearance = tab.resolveTimelineAppearance(base)

            override fun hasLayoutAppearanceOverride(tab: UiTimelineTabItem): Boolean =
                tab.appearancePatch.hasAny(
                    AppearanceKeys.TimelineDisplayMode,
                    AppearanceKeys.FullWidthPost,
                    AppearanceKeys.PostActionStyle,
                    AppearanceKeys.ShowNumbers,
                )

            override fun hasDisplayAppearanceOverride(tab: UiTimelineTabItem): Boolean =
                tab.appearancePatch.hasAny(
                    AppearanceKeys.AbsoluteTimestamp,
                    AppearanceKeys.ShowPlatformLogo,
                    AppearanceKeys.ShowLinkPreview,
                    AppearanceKeys.CompatLinkPreview,
                )

            override fun hasMediaAppearanceOverride(tab: UiTimelineTabItem): Boolean =
                tab.appearancePatch.hasAny(
                    AppearanceKeys.ShowMedia,
                    AppearanceKeys.ShowSensitiveContent,
                    AppearanceKeys.ExpandContentWarning,
                    AppearanceKeys.ExpandMediaSize,
                    AppearanceKeys.VideoAutoplay,
                )

            override fun hasThemeAppearanceOverride(tab: UiTimelineTabItem): Boolean =
                tab.appearancePatch?.contains(AppearanceKeys.AvatarShape) == true
        }
    }

    public interface State {
        public val homeTimelineTabs: UiState<ImmutableList<UiTimelineTabItem>>
        public val availableIcons: ImmutableList<IconType>

        public fun replaceHomeTimelineTabs(tabs: List<UiTimelineTabItem>)

        public fun replaceHomeTimelineTabsWithSystemHomeMixedTimeline(
            tabs: List<UiTimelineTabItem>,
            enabled: Boolean,
            mergePolicy: TimelineMergePolicy,
        )

        public fun homeTimelineTabsWithSystemHomeMixedTimeline(
            tabs: List<UiTimelineTabItem>,
            enabled: Boolean,
            mergePolicy: TimelineMergePolicy,
        ): ImmutableList<UiTimelineTabItem>

        public fun updateTabPresentation(
            tab: UiTimelineTabItem,
            title: String,
            icon: IconType,
            enabled: Boolean,
            excludedKinds: String,
            excludedContents: String,
            layoutEnabled: Boolean,
            timelineDisplayMode: TimelineDisplayMode,
            fullWidthPost: Boolean,
            postActionStyle: PostActionStyle,
            showNumbers: Boolean,
            displayEnabled: Boolean,
            absoluteTimestamp: Boolean,
            showPlatformLogo: Boolean,
            showLinkPreview: Boolean,
            compatLinkPreview: Boolean,
            mediaEnabled: Boolean,
            showMedia: Boolean,
            showSensitiveContent: Boolean,
            expandContentWarning: Boolean,
            expandMediaSize: Boolean,
            videoAutoplay: VideoAutoplay,
            themeEnabled: Boolean,
            avatarShape: AvatarShape,
        ): UiTimelineTabItem

        public fun buildGroupItem(
            initialItem: UiTimelineTabItem?,
            name: String,
            icon: IconType,
            enabled: Boolean,
            tabs: List<UiTimelineTabItem>,
            mergePolicy: TimelineMergePolicy,
            excludedKinds: String,
            excludedContents: String,
            layoutEnabled: Boolean,
            timelineDisplayMode: TimelineDisplayMode,
            fullWidthPost: Boolean,
            postActionStyle: PostActionStyle,
            showNumbers: Boolean,
            displayEnabled: Boolean,
            absoluteTimestamp: Boolean,
            showPlatformLogo: Boolean,
            showLinkPreview: Boolean,
            compatLinkPreview: Boolean,
            mediaEnabled: Boolean,
            showMedia: Boolean,
            showSensitiveContent: Boolean,
            expandContentWarning: Boolean,
            expandMediaSize: Boolean,
            videoAutoplay: VideoAutoplay,
            themeEnabled: Boolean,
            avatarShape: AvatarShape,
            defaultGroupName: String,
        ): UiTimelineTabItem?

        public fun isSystemHomeMixedTimeline(tab: UiTimelineTabItem): Boolean

        public fun isGroup(tab: UiTimelineTabItem): Boolean

        public fun groupChildren(tab: UiTimelineTabItem): ImmutableList<UiTimelineTabItem>

        public fun groupMergePolicy(tab: UiTimelineTabItem): TimelineMergePolicy

        public fun resolveAppearance(
            tab: UiTimelineTabItem,
            base: TimelineAppearance,
        ): TimelineAppearance

        public fun hasLayoutAppearanceOverride(tab: UiTimelineTabItem): Boolean

        public fun hasDisplayAppearanceOverride(tab: UiTimelineTabItem): Boolean

        public fun hasMediaAppearanceOverride(tab: UiTimelineTabItem): Boolean

        public fun hasThemeAppearanceOverride(tab: UiTimelineTabItem): Boolean
    }
}

private fun AppearancePatch?.hasAny(vararg keys: org.crux.data.model.appearance.AppearanceKey<*>): Boolean =
    this != null && keys.any { contains(it) }

private fun buildAppearancePatch(
    layoutEnabled: Boolean,
    timelineDisplayMode: TimelineDisplayMode,
    fullWidthPost: Boolean,
    postActionStyle: PostActionStyle,
    showNumbers: Boolean,
    displayEnabled: Boolean,
    absoluteTimestamp: Boolean,
    showPlatformLogo: Boolean,
    showLinkPreview: Boolean,
    compatLinkPreview: Boolean,
    mediaEnabled: Boolean,
    showMedia: Boolean,
    showSensitiveContent: Boolean,
    expandContentWarning: Boolean,
    expandMediaSize: Boolean,
    videoAutoplay: VideoAutoplay,
    themeEnabled: Boolean,
    avatarShape: AvatarShape,
): AppearancePatch? {
    var patch = AppearancePatch.EMPTY
    if (layoutEnabled) {
        patch =
            patch
                .set(AppearanceKeys.TimelineDisplayMode, timelineDisplayMode)
                .set(AppearanceKeys.FullWidthPost, fullWidthPost)
                .set(AppearanceKeys.PostActionStyle, postActionStyle)
                .set(AppearanceKeys.ShowNumbers, showNumbers)
    }
    if (displayEnabled) {
        patch =
            patch
                .set(AppearanceKeys.AbsoluteTimestamp, absoluteTimestamp)
                .set(AppearanceKeys.ShowPlatformLogo, showPlatformLogo)
                .set(AppearanceKeys.ShowLinkPreview, showLinkPreview)
                .set(AppearanceKeys.CompatLinkPreview, compatLinkPreview)
    }
    if (mediaEnabled) {
        patch =
            patch
                .set(AppearanceKeys.ShowMedia, showMedia)
                .set(AppearanceKeys.ShowSensitiveContent, showSensitiveContent)
                .set(AppearanceKeys.ExpandContentWarning, expandContentWarning)
                .set(AppearanceKeys.ExpandMediaSize, expandMediaSize)
                .set(AppearanceKeys.VideoAutoplay, videoAutoplay)
    }
    if (themeEnabled) {
        patch = patch.set(AppearanceKeys.AvatarShape, avatarShape)
    }
    return patch.takeUnless { it == AppearancePatch.EMPTY }
}

private inline fun <reified T : Enum<T>> String.parseEnumList(): List<T> =
    split(",")
        .mapNotNull { value ->
            value
                .takeIf { it.isNotBlank() }
                ?.let { enumValueOf<T>(it) }
        }
