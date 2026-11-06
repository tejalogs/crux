package org.crux.data.model.appearance

import kotlinx.serialization.ExperimentalSerializationApi
import org.crux.data.datasource.microblog.PostActionFamily
import org.crux.data.datasource.microblog.PostActionLayoutConfig
import org.crux.data.model.AppearanceSettings
import org.crux.data.model.AvatarShape
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.BottomBarStyle
import org.crux.data.model.IconType
import org.crux.data.model.PostActionStyle
import org.crux.data.model.Theme
import org.crux.data.model.TimelineDisplayMode
import org.crux.data.model.TimelineMediaLayout
import org.crux.data.model.VideoAutoplay
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.model.tab.resolveTimelineAppearance
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.platform.CommonTimelineSpecs
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppearancePatchTest {
    @Test
    fun mediaViewerPostPreferenceSurvivesStorageAndCanReturnToDefault() {
        val stored = AppearancePatch.EMPTY.set(AppearanceKeys.ShowPostInMediaViewer, false).toBag()
        val restored = stored.toPatch()

        assertFalse(restored.toGlobalAppearance().showPostInMediaViewer)
        assertEquals(true, restored.clear(AppearanceKeys.ShowPostInMediaViewer).toGlobalAppearance().showPostInMediaViewer)
        assertEquals(true, AppearanceBag().toPatch().toGlobalAppearance().showPostInMediaViewer)
    }

    @Test
    fun emptyPatchSynthesizesDefaultAppearanceSettings() {
        assertEquals(AppearanceSettings.Default, AppearancePatch.EMPTY.toAppearanceSettings())
        assertFalse(TimelineAppearance.Default.expandContentWarning)
        assertEquals(TimelineMediaLayout.Grid, TimelineAppearance.Default.mediaLayout)
        assertEquals(true, TimelineAppearance.Default.limitMediaGridToNine)
        assertEquals(true, TimelineAppearance.Default.showEmojiReactions)
        assertEquals(true, AppearancePatch.EMPTY.toGlobalAppearance().showPostInMediaViewer)
    }

    @Test
    fun getSetAndClearUseDefaults() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.DARK)

        assertEquals(Theme.DARK, patch[AppearanceKeys.Theme])
        assertEquals(AppearanceKeys.ShowMedia.default, patch[AppearanceKeys.ShowMedia])

        val cleared = patch.clear(AppearanceKeys.Theme)
        assertEquals(AppearanceKeys.Theme.default, cleared[AppearanceKeys.Theme])
        assertFalse(cleared.contains(AppearanceKeys.Theme))
    }

    @Test
    fun patchSynthesizesGlobalAndTimelineAppearanceModels() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.DARK)
                .set(AppearanceKeys.ShowBottomBarLabels, false)
                .set(AppearanceKeys.DeckMode, true)
                .set(AppearanceKeys.ShowPostInMediaViewer, false)
                .set(AppearanceKeys.AvatarShape, AvatarShape.SQUARE)
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.ExpandContentWarning, true)
                .set(AppearanceKeys.MediaLayout, TimelineMediaLayout.Carousel)
                .set(AppearanceKeys.LimitMediaGridToNine, false)
                .set(AppearanceKeys.ShowEmojiReactions, false)
                .set(AppearanceKeys.PostActionFixedWidth, false)
                .set(AppearanceKeys.TimelineDisplayMode, TimelineDisplayMode.Gallery)

        assertEquals(
            GlobalAppearance(
                theme = Theme.DARK,
                showBottomBarLabels = false,
                deckMode = true,
                showPostInMediaViewer = false,
            ),
            patch.toGlobalAppearance(),
        )
        assertEquals(
            TimelineAppearance(
                avatarShape = AvatarShape.SQUARE,
                showMedia = false,
                expandContentWarning = true,
                mediaLayout = TimelineMediaLayout.Carousel,
                limitMediaGridToNine = false,
                showEmojiReactions = false,
                postActionFixedWidth = false,
                timelineDisplayMode = TimelineDisplayMode.Gallery,
            ),
            patch.toTimelineAppearance(),
        )
    }

    @Test
    fun timelineOverrideFallsBackToGlobalTimelineDefaults() {
        val globalPatch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.ShowEmojiReactions, false)
                .set(AppearanceKeys.ShowNumbers, false)
                .set(AppearanceKeys.PostActionFixedWidth, false)
                .set(AppearanceKeys.ExpandContentWarning, false)
        val timelinePatch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.ShowMedia, true)
                .set(AppearanceKeys.ShowEmojiReactions, true)
                .set(AppearanceKeys.ExpandContentWarning, true)

        assertEquals(
            TimelineAppearance(
                showMedia = true,
                expandContentWarning = true,
                showEmojiReactions = true,
                showNumbers = false,
                postActionFixedWidth = false,
            ),
            globalPatch.toTimelineAppearance(timelinePatch),
        )
    }

    @Test
    fun timelineAppearanceWithPatchPreservesNonPatchFields() {
        val base =
            TimelineAppearance(
                showMedia = false,
                showNumbers = false,
                aiConfig = TimelineAppearance.AiConfig(translation = true, tldr = true),
                lineLimit = 9,
                showTranslateButton = false,
            )
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.ShowMedia, true)
                .set(AppearanceKeys.ExpandContentWarning, true)
                .set(AppearanceKeys.MediaLayout, TimelineMediaLayout.Carousel)
                .set(AppearanceKeys.LimitMediaGridToNine, false)
                .set(AppearanceKeys.PostActionFixedWidth, false)
                .set(AppearanceKeys.TimelineDisplayMode, TimelineDisplayMode.Gallery)

        assertEquals(
            TimelineAppearance(
                showMedia = true,
                expandContentWarning = true,
                mediaLayout = TimelineMediaLayout.Carousel,
                limitMediaGridToNine = false,
                showNumbers = false,
                postActionFixedWidth = false,
                timelineDisplayMode = TimelineDisplayMode.Gallery,
                aiConfig = TimelineAppearance.AiConfig(translation = true, tldr = true),
                lineLimit = 9,
                showTranslateButton = false,
            ),
            base.withPatch(patch),
        )
    }

    @Test
    fun timelineTabItemV2ResolvesTimelineAppearanceFromItemPatch() {
        val item =
            CommonTimelineSpecs.home
                .candidate(
                    data = TimelineSpec.AccountBasedData(MicroBlogKey("test", "example.test")),
                    title = UiText.Raw("Test"),
                    icon = IconType.Material(UiIcon.List),
                ).copy(
                    appearancePatch =
                        AppearancePatch.EMPTY
                            .set(AppearanceKeys.ShowNumbers, false)
                            .set(AppearanceKeys.ShowEmojiReactions, false)
                            .set(AppearanceKeys.ExpandContentWarning, true)
                            .set(AppearanceKeys.AbsoluteTimestamp, true),
                ).toUiTimelineTabItem()
        val base =
            TimelineAppearance(
                showNumbers = true,
                absoluteTimestamp = false,
                aiConfig = TimelineAppearance.AiConfig(translation = true),
                lineLimit = 7,
            )

        assertEquals(
            TimelineAppearance(
                showNumbers = false,
                showEmojiReactions = false,
                expandContentWarning = true,
                absoluteTimestamp = true,
                aiConfig = TimelineAppearance.AiConfig(translation = true),
                lineLimit = 7,
            ),
            item.resolveTimelineAppearance(base),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun activeAppearanceSettingsFieldsAreCoveredByCatalog() {
        val deprecatedFields = setOf("showActions")
        val bagOnlyFields =
            setOf(
                AppearanceKeys.ShowBottomBarLabels,
                AppearanceKeys.DeckMode,
                AppearanceKeys.ShowPostInMediaViewer,
                AppearanceKeys.ExpandContentWarning,
                AppearanceKeys.MediaLayout,
                AppearanceKeys.LimitMediaGridToNine,
                AppearanceKeys.ShowEmojiReactions,
                AppearanceKeys.PostActionLayout,
                AppearanceKeys.PostActionFixedWidth,
                AppearanceKeys.HapticsEnabled,
                AppearanceKeys.SwipeToActEnabled,
                AppearanceKeys.LiteMode,
                AppearanceKeys.Grayscale,
                AppearanceKeys.PaperTint,
            )
        val activeFields =
            AppearanceSettings
                .serializer()
                .descriptor
                .let { descriptor ->
                    (0 until descriptor.elementsCount)
                        .map { descriptor.getElementName(it) }
                        .filterNot { it in deprecatedFields }
                }

        assertEquals(activeFields.size + bagOnlyFields.size, AppearanceKeys.all.size)
    }

    @Test
    fun appearanceSettingsRoundTripPreservesActiveFields() {
        val settings =
            AppearanceSettings(
                theme = Theme.DARK,
                dynamicTheme = false,
                colorSeed = 123456u,
                avatarShape = AvatarShape.SQUARE,
                pureColorMode = false,
                showNumbers = false,
                showLinkPreview = false,
                showMedia = false,
                showSensitiveContent = true,
                videoAutoplay = VideoAutoplay.NEVER,
                expandMediaSize = false,
                compatLinkPreview = true,
                fontSizeDiff = 2f,
                lineHeightDiff = 4f,
                showComposeInHomeTimeline = false,
                bottomBarStyle = BottomBarStyle.Classic,
                bottomBarBehavior = BottomBarBehavior.AlwaysShow,
                inAppBrowser = false,
                fullWidthPost = true,
                postActionStyle = PostActionStyle.Hidden,
                absoluteTimestamp = true,
                showPlatformLogo = false,
                timelineDisplayMode = TimelineDisplayMode.Gallery,
            )

        assertEquals(settings, settings.toPatch().toAppearanceSettings())
    }

    @Test
    fun bagRoundTripPreservesExplicitValues() {
        val patch =
            AppearancePatch.EMPTY
                .set(AppearanceKeys.Theme, Theme.LIGHT)
                .set(AppearanceKeys.ShowBottomBarLabels, false)
                .set(AppearanceKeys.DeckMode, true)
                .set(AppearanceKeys.ShowMedia, false)
                .set(AppearanceKeys.ExpandContentWarning, true)
                .set(AppearanceKeys.LimitMediaGridToNine, false)
                .set(AppearanceKeys.ShowEmojiReactions, false)
                .set(AppearanceKeys.VideoAutoplay, VideoAutoplay.ALWAYS)
                .set(AppearanceKeys.PostActionStyle, PostActionStyle.Stretch)
                .set(AppearanceKeys.PostActionFixedWidth, false)
                .set(
                    AppearanceKeys.PostActionLayout,
                    PostActionLayoutConfig(
                        enabled = true,
                        primary = kotlinx.collections.immutable.persistentListOf(PostActionFamily.Like),
                        overflow = kotlinx.collections.immutable.persistentListOf(PostActionFamily.Share),
                        hidden = kotlinx.collections.immutable.persistentListOf(PostActionFamily.Report),
                    ),
                )

        assertEquals(patch, patch.toBag().toPatch())
    }

    @Test
    fun unknownEntriesAreIgnored() {
        val bag =
            AppearanceBag(
                entries = mapOf("future.key" to byteArrayOf(1, 2, 3).toHexString()),
            )

        assertEquals(AppearancePatch.EMPTY, bag.toPatch())
    }
}
