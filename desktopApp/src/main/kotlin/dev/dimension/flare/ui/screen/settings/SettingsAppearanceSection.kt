package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AngleRight
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.Res
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.LiteMode
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.TimelineMediaLayout
import dev.dimension.flare.data.model.VideoAutoplay
import dev.dimension.flare.data.model.appearance.AppearanceKey
import dev.dimension.flare.data.model.appearance.AppearanceKeys
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.settings_appearance_absolute_timestamp
import dev.dimension.flare.settings_appearance_absolute_timestamp_description
import dev.dimension.flare.settings_appearance_avatar_shape
import dev.dimension.flare.settings_appearance_avatar_shape_description
import dev.dimension.flare.settings_appearance_avatar_shape_round
import dev.dimension.flare.settings_appearance_avatar_shape_square
import dev.dimension.flare.settings_appearance_compat_link_previews
import dev.dimension.flare.settings_appearance_compat_link_previews_description
import dev.dimension.flare.settings_appearance_deck_mode
import dev.dimension.flare.settings_appearance_deck_mode_description
import dev.dimension.flare.settings_appearance_display_group_subtitle
import dev.dimension.flare.settings_appearance_display_group_title
import dev.dimension.flare.settings_appearance_expand_content_warning
import dev.dimension.flare.settings_appearance_expand_content_warning_description
import dev.dimension.flare.settings_appearance_expand_media
import dev.dimension.flare.settings_appearance_expand_media_description
import dev.dimension.flare.settings_appearance_full_width_post
import dev.dimension.flare.settings_appearance_full_width_post_description
import dev.dimension.flare.settings_appearance_layout_group_subtitle
import dev.dimension.flare.settings_appearance_layout_group_title
import dev.dimension.flare.settings_appearance_limit_media_grid_to_nine
import dev.dimension.flare.settings_appearance_limit_media_grid_to_nine_description
import dev.dimension.flare.settings_appearance_media_group_subtitle
import dev.dimension.flare.settings_appearance_media_group_title
import dev.dimension.flare.settings_appearance_media_layout
import dev.dimension.flare.settings_appearance_media_layout_carousel
import dev.dimension.flare.settings_appearance_media_layout_description
import dev.dimension.flare.settings_appearance_media_layout_grid
import dev.dimension.flare.settings_appearance_post_action_layout
import dev.dimension.flare.settings_appearance_post_action_layout_description
import dev.dimension.flare.settings_appearance_post_action_style
import dev.dimension.flare.settings_appearance_post_action_style_description
import dev.dimension.flare.settings_appearance_post_action_style_hidden
import dev.dimension.flare.settings_appearance_post_action_style_left_aligned
import dev.dimension.flare.settings_appearance_post_action_style_right_aligned
import dev.dimension.flare.settings_appearance_post_action_style_stretch
import dev.dimension.flare.settings_appearance_show_bottom_bar_labels
import dev.dimension.flare.settings_appearance_show_bottom_bar_labels_description
import dev.dimension.flare.settings_appearance_show_compose_in_home_timeline
import dev.dimension.flare.settings_appearance_show_compose_in_home_timeline_description
import dev.dimension.flare.settings_appearance_show_cw_img
import dev.dimension.flare.settings_appearance_show_cw_img_description
import dev.dimension.flare.settings_appearance_show_link_previews
import dev.dimension.flare.settings_appearance_show_link_previews_description
import dev.dimension.flare.settings_appearance_show_media
import dev.dimension.flare.settings_appearance_show_media_description
import dev.dimension.flare.settings_appearance_show_numbers
import dev.dimension.flare.settings_appearance_show_numbers_description
import dev.dimension.flare.settings_appearance_show_platform_logo
import dev.dimension.flare.settings_appearance_show_platform_logo_description
import dev.dimension.flare.settings_appearance_show_reactions
import dev.dimension.flare.settings_appearance_show_reactions_description
import dev.dimension.flare.settings_appearance_swipe_to_act
import dev.dimension.flare.settings_appearance_swipe_to_act_description
import dev.dimension.flare.settings_appearance_theme
import dev.dimension.flare.settings_appearance_theme_auto
import dev.dimension.flare.settings_appearance_theme_dark
import dev.dimension.flare.settings_appearance_theme_description
import dev.dimension.flare.settings_appearance_theme_group_subtitle
import dev.dimension.flare.settings_appearance_theme_group_title
import dev.dimension.flare.settings_appearance_theme_light
import dev.dimension.flare.settings_appearance_timeline_display_mode
import dev.dimension.flare.settings_appearance_timeline_display_mode_card
import dev.dimension.flare.settings_appearance_timeline_display_mode_description
import dev.dimension.flare.settings_appearance_timeline_display_mode_gallery
import dev.dimension.flare.settings_appearance_timeline_display_mode_plain
import dev.dimension.flare.settings_appearance_title
import dev.dimension.flare.settings_appearance_video_autoplay
import dev.dimension.flare.settings_appearance_video_autoplay_description
import dev.dimension.flare.settings_language_description
import dev.dimension.flare.settings_language_title
import dev.dimension.flare.settings_lite_mode_balanced
import dev.dimension.flare.settings_lite_mode_description
import dev.dimension.flare.settings_lite_mode_off
import dev.dimension.flare.settings_lite_mode_text_only
import dev.dimension.flare.settings_lite_mode_title
import dev.dimension.flare.settings_post_action_fixed_width
import dev.dimension.flare.settings_post_action_fixed_width_description
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.util.Locale

internal interface DesktopAppearanceState {
    fun <T : Any> update(key: AppearanceKey<T>, value: T)
}

@Composable
internal fun appearancePresenter(): DesktopAppearanceState {
    val scope = rememberCoroutineScope()
    val settingsRepository = koinInject<SettingsRepository>()
    return remember(scope, settingsRepository) {
        object : DesktopAppearanceState {
            override fun <T : Any> update(
                key: AppearanceKey<T>,
                value: T,
            ) {
                scope.launch {
                    settingsRepository.updateAppearance(key, value)
                }
            }
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsAppearanceSection(
    appearanceState: DesktopAppearanceState,
    updateAppSettings: (AppSettings.() -> AppSettings) -> Unit,
    toPostActionLayout: () -> Unit,
) {
    Header(stringResource(Res.string.settings_appearance_title))

    CardExpanderItem(
        icon = null,
        heading = {
            Text(stringResource(Res.string.settings_language_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_language_description))
        },
        trailing = {
            var isFlyoutVisible by remember { mutableStateOf(false) }
            DropDownButton(
                onClick = { isFlyoutVisible = !isFlyoutVisible },
                content = {
                    Text(Locale.getDefault().displayName)
                },
            )
            MenuFlyout(
                visible = isFlyoutVisible,
                onDismissRequest = { isFlyoutVisible = false },
                modifier = Modifier.heightIn(max = 200.dp),
                placement = FlyoutPlacement.BottomAlignedEnd,
            ) {
                BuildConfig.supportedLocaleTags.forEach {
                    MenuFlyoutItem(
                        text = {
                            Text(
                                Locale.forLanguageTag(it).let {
                                    it.getDisplayName(it)
                                },
                            )
                        },
                        onClick = {
                            updateAppSettings { copy(language = it) }
                            isFlyoutVisible = false
                        },
                    )
                }
            }
        },
    )

    var themeExpanded by remember { mutableStateOf(false) }
    Expander(
        icon = null,
        expanded = themeExpanded,
        onExpandedChanged = { themeExpanded = it },
        heading = {
            Text(stringResource(Res.string.settings_appearance_theme_group_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_appearance_theme_group_subtitle))
        },
    ) {
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_theme))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_theme_description))
            },
            trailing = {
                MenuFlyoutContainer(
                    flyout = {
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.settings_appearance_theme_auto)) },
                            onClick = {
                                appearanceState.update(AppearanceKeys.Theme, Theme.SYSTEM)
                                isFlyoutVisible = false
                            },
                        )
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.settings_appearance_theme_dark)) },
                            onClick = {
                                isFlyoutVisible = false
                                appearanceState.update(AppearanceKeys.Theme, Theme.DARK)
                            },
                        )
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.settings_appearance_theme_light)) },
                            onClick = {
                                isFlyoutVisible = false
                                appearanceState.update(AppearanceKeys.Theme, Theme.LIGHT)
                            },
                        )
                    },
                    content = {
                        DropDownButton(
                            onClick = { isFlyoutVisible = !isFlyoutVisible },
                            content = {
                                Text(
                                    when (LocalGlobalAppearance.current.theme) {
                                        Theme.SYSTEM -> stringResource(Res.string.settings_appearance_theme_auto)
                                        Theme.DARK -> stringResource(Res.string.settings_appearance_theme_dark)
                                        Theme.LIGHT -> stringResource(Res.string.settings_appearance_theme_light)
                                    },
                                )
                            },
                        )
                    },
                    adaptivePlacement = true,
                    placement = FlyoutPlacement.BottomAlignedEnd,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_avatar_shape))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_avatar_shape_description))
            },
            trailing = {
                MenuFlyoutContainer(
                    flyout = {
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.settings_appearance_avatar_shape_round)) },
                            onClick = {
                                appearanceState.update(AppearanceKeys.AvatarShape, AvatarShape.CIRCLE)
                                isFlyoutVisible = false
                            },
                        )
                        MenuFlyoutItem(
                            text = { Text(stringResource(Res.string.settings_appearance_avatar_shape_square)) },
                            onClick = {
                                appearanceState.update(AppearanceKeys.AvatarShape, AvatarShape.SQUARE)
                                isFlyoutVisible = false
                            },
                        )
                    },
                    content = {
                        DropDownButton(
                            onClick = { isFlyoutVisible = !isFlyoutVisible },
                            content = {
                                Text(
                                    when (LocalTimelineAppearance.current.avatarShape) {
                                        AvatarShape.CIRCLE -> {
                                            stringResource(
                                                Res.string.settings_appearance_avatar_shape_round,
                                            )
                                        }

                                        AvatarShape.SQUARE -> {
                                            stringResource(
                                                Res.string.settings_appearance_avatar_shape_square,
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    },
                    adaptivePlacement = true,
                    placement = FlyoutPlacement.BottomAlignedEnd,
                )
            },
        )
    }

    var layoutExpanded by remember { mutableStateOf(false) }
    Expander(
        icon = null,
        expanded = layoutExpanded,
        onExpandedChanged = { layoutExpanded = it },
        heading = {
            Text(stringResource(Res.string.settings_appearance_layout_group_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_appearance_layout_group_subtitle))
        },
    ) {
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_show_compose_in_home_timeline))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_show_compose_in_home_timeline_description))
            },
            trailing = {
                Switcher(
                    checked = LocalGlobalAppearance.current.showComposeInHomeTimeline,
                    {
                        appearanceState.update(AppearanceKeys.ShowComposeInHomeTimeline, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_show_bottom_bar_labels))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_show_bottom_bar_labels_description))
            },
            trailing = {
                Switcher(
                    checked = LocalGlobalAppearance.current.showBottomBarLabels,
                    {
                        appearanceState.update(AppearanceKeys.ShowBottomBarLabels, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_deck_mode))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_deck_mode_description))
            },
            trailing = {
                Switcher(
                    checked = LocalGlobalAppearance.current.deckMode,
                    {
                        appearanceState.update(AppearanceKeys.DeckMode, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_swipe_to_act))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_swipe_to_act_description))
            },
            trailing = {
                Switcher(
                    checked = LocalGlobalAppearance.current.swipeToActEnabled,
                    onCheckStateChange = {
                        appearanceState.update(AppearanceKeys.SwipeToActEnabled, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_timeline_display_mode))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_timeline_display_mode_description))
            },
            trailing = {
                val items =
                    remember {
                        persistentMapOf(
                            TimelineDisplayMode.Card to Res.string.settings_appearance_timeline_display_mode_card,
                            TimelineDisplayMode.Plain to Res.string.settings_appearance_timeline_display_mode_plain,
                            TimelineDisplayMode.Gallery to Res.string.settings_appearance_timeline_display_mode_gallery,
                        )
                    }
                MenuFlyoutContainer(
                    flyout = {
                        items.forEach { (key, value) ->
                            MenuFlyoutItem(
                                onClick = {
                                    appearanceState.update(AppearanceKeys.TimelineDisplayMode, key)
                                    isFlyoutVisible = false
                                },
                                text = { Text(stringResource(value)) },
                            )
                        }
                    },
                    content = {
                        DropDownButton(
                            onClick = { isFlyoutVisible = !isFlyoutVisible },
                            content = {
                                items[LocalTimelineAppearance.current.timelineDisplayMode]?.let {
                                    Text(stringResource(it))
                                }
                            },
                        )
                    },
                    adaptivePlacement = true,
                    placement = FlyoutPlacement.BottomAlignedEnd,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_full_width_post))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_full_width_post_description))
            },
            trailing = {
                Switcher(
                    checked = LocalTimelineAppearance.current.fullWidthPost,
                    {
                        appearanceState.update(AppearanceKeys.FullWidthPost, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_post_action_style))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_post_action_style_description))
            },
            trailing = {
                val items =
                    remember {
                        persistentMapOf(
                            PostActionStyle.Hidden to Res.string.settings_appearance_post_action_style_hidden,
                            PostActionStyle.LeftAligned to Res.string.settings_appearance_post_action_style_left_aligned,
                            PostActionStyle.RightAligned to Res.string.settings_appearance_post_action_style_right_aligned,
                            PostActionStyle.Stretch to Res.string.settings_appearance_post_action_style_stretch,
                        )
                    }
                MenuFlyoutContainer(
                    flyout = {
                        items.forEach { (key, value) ->
                            MenuFlyoutItem(
                                onClick = {
                                    appearanceState.update(AppearanceKeys.PostActionStyle, key)
                                    isFlyoutVisible = false
                                },
                                text = { Text(stringResource(value)) },
                            )
                        }
                    },
                    content = {
                        DropDownButton(
                            onClick = { isFlyoutVisible = !isFlyoutVisible },
                            content = {
                                items[LocalTimelineAppearance.current.postActionStyle]?.let {
                                    Text(stringResource(it))
                                }
                            },
                        )
                    },
                    adaptivePlacement = true,
                    placement = FlyoutPlacement.BottomAlignedEnd,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_post_action_layout))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_post_action_layout_description))
            },
            trailing = {
                FAIcon(
                    FontAwesomeIcons.Solid.AngleRight,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable(onClick = toPostActionLayout),
        )
        AnimatedVisibility(LocalTimelineAppearance.current.postActionStyle != PostActionStyle.Hidden) {
            Column {
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_show_numbers))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_show_numbers_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalTimelineAppearance.current.showNumbers,
                            {
                                appearanceState.update(AppearanceKeys.ShowNumbers, it)
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_post_action_fixed_width))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_post_action_fixed_width_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalTimelineAppearance.current.postActionFixedWidth,
                            {
                                appearanceState.update(AppearanceKeys.PostActionFixedWidth, it)
                            },
                            textBefore = true,
                        )
                    },
                )
            }
        }
    }

    var displayExpanded by remember { mutableStateOf(false) }
    Expander(
        icon = null,
        expanded = displayExpanded,
        onExpandedChanged = { displayExpanded = it },
        heading = {
            Text(stringResource(Res.string.settings_appearance_display_group_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_appearance_display_group_subtitle))
        },
    ) {
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_absolute_timestamp))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_absolute_timestamp_description))
            },
            trailing = {
                Switcher(
                    checked = LocalTimelineAppearance.current.absoluteTimestamp,
                    {
                        appearanceState.update(AppearanceKeys.AbsoluteTimestamp, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_show_platform_logo))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_show_platform_logo_description))
            },
            trailing = {
                Switcher(
                    checked = LocalTimelineAppearance.current.showPlatformLogo,
                    {
                        appearanceState.update(AppearanceKeys.ShowPlatformLogo, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_show_reactions))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_show_reactions_description))
            },
            trailing = {
                Switcher(
                    checked = LocalTimelineAppearance.current.showEmojiReactions,
                    {
                        appearanceState.update(AppearanceKeys.ShowEmojiReactions, it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_show_link_previews))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_show_link_previews_description))
            },
            trailing = {
                Switcher(
                    checked = LocalTimelineAppearance.current.showLinkPreview,
                    {
                        appearanceState.update(AppearanceKeys.ShowLinkPreview, it)
                    },
                    textBefore = true,
                )
            },
        )
        AnimatedVisibility(LocalTimelineAppearance.current.showLinkPreview) {
            Column {
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_compat_link_previews))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_compat_link_previews_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalTimelineAppearance.current.compatLinkPreview,
                            {
                                appearanceState.update(AppearanceKeys.CompatLinkPreview, it)
                            },
                            textBefore = true,
                        )
                    },
                )
            }
        }
    }

    var mediaExpanded by remember { mutableStateOf(false) }
    Expander(
        icon = null,
        expanded = mediaExpanded,
        onExpandedChanged = { mediaExpanded = it },
        heading = {
            Text(stringResource(Res.string.settings_appearance_media_group_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_appearance_media_group_subtitle))
        },
    ) {
        CardExpanderItem(
            icon = null,
            heading = {
                Text(stringResource(Res.string.settings_lite_mode_title))
            },
            caption = {
                Text(stringResource(Res.string.settings_lite_mode_description))
            },
            trailing = {
                var isFlyoutVisible by remember { mutableStateOf(false) }
                val currentLiteMode = LocalGlobalAppearance.current.liteMode
                val currentLabel =
                    when (currentLiteMode) {
                        LiteMode.OFF -> stringResource(Res.string.settings_lite_mode_off)
                        LiteMode.BALANCED -> stringResource(Res.string.settings_lite_mode_balanced)
                        LiteMode.TEXT_ONLY -> stringResource(Res.string.settings_lite_mode_text_only)
                    }
                DropDownButton(
                    onClick = { isFlyoutVisible = !isFlyoutVisible },
                    content = {
                        Text(currentLabel)
                    },
                )
                MenuFlyout(
                    visible = isFlyoutVisible,
                    onDismissRequest = { isFlyoutVisible = false },
                    placement = FlyoutPlacement.BottomAlignedEnd,
                ) {
                    MenuFlyoutItem(
                        text = { Text(stringResource(Res.string.settings_lite_mode_off)) },
                        onClick = {
                            appearanceState.update(AppearanceKeys.LiteMode, LiteMode.OFF)
                            isFlyoutVisible = false
                        },
                    )
                    MenuFlyoutItem(
                        text = { Text(stringResource(Res.string.settings_lite_mode_balanced)) },
                        onClick = {
                            appearanceState.update(AppearanceKeys.LiteMode, LiteMode.BALANCED)
                            isFlyoutVisible = false
                        },
                    )
                    MenuFlyoutItem(
                        text = { Text(stringResource(Res.string.settings_lite_mode_text_only)) },
                        onClick = {
                            appearanceState.update(AppearanceKeys.LiteMode, LiteMode.TEXT_ONLY)
                            isFlyoutVisible = false
                        },
                    )
                }
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_show_media))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_show_media_description))
            },
            trailing = {
                Switcher(
                    checked = LocalTimelineAppearance.current.showMedia,
                    {
                        appearanceState.update(AppearanceKeys.ShowMedia, it)
                    },
                    textBefore = true,
                )
            },
        )
        AnimatedVisibility(LocalTimelineAppearance.current.showMedia) {
            Column {
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_show_cw_img))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_show_cw_img_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalTimelineAppearance.current.showSensitiveContent,
                            {
                                appearanceState.update(AppearanceKeys.ShowSensitiveContent, it)
                            },
                            textBefore = true,
                        )
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_expand_content_warning))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_expand_content_warning_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalTimelineAppearance.current.expandContentWarning,
                            {
                                appearanceState.update(AppearanceKeys.ExpandContentWarning, it)
                            },
                            textBefore = true,
                        )
                    },
                )
            }
        }
        AnimatedVisibility(LocalTimelineAppearance.current.showMedia) {
            Column {
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_media_layout))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_media_layout_description))
                    },
                    trailing = {
                        val items =
                            remember {
                                persistentMapOf(
                                    TimelineMediaLayout.Grid to Res.string.settings_appearance_media_layout_grid,
                                    TimelineMediaLayout.Carousel to Res.string.settings_appearance_media_layout_carousel,
                                )
                            }
                        MenuFlyoutContainer(
                            flyout = {
                                items.forEach { (key, value) ->
                                    MenuFlyoutItem(
                                        onClick = {
                                            appearanceState.update(AppearanceKeys.MediaLayout, key)
                                            isFlyoutVisible = false
                                        },
                                        text = { Text(stringResource(value)) },
                                    )
                                }
                            },
                            content = {
                                DropDownButton(
                                    onClick = { isFlyoutVisible = !isFlyoutVisible },
                                    content = {
                                        items[LocalTimelineAppearance.current.mediaLayout]?.let {
                                            Text(stringResource(it))
                                        }
                                    },
                                )
                            },
                            adaptivePlacement = true,
                            placement = FlyoutPlacement.BottomAlignedEnd,
                        )
                    },
                )
                AnimatedVisibility(LocalTimelineAppearance.current.mediaLayout == TimelineMediaLayout.Grid) {
                    Column {
                        ExpanderItemSeparator()
                        ExpanderItem(
                            heading = {
                                Text(stringResource(Res.string.settings_appearance_limit_media_grid_to_nine))
                            },
                            caption = {
                                Text(stringResource(Res.string.settings_appearance_limit_media_grid_to_nine_description))
                            },
                            trailing = {
                                Switcher(
                                    checked = LocalTimelineAppearance.current.limitMediaGridToNine,
                                    {
                                        appearanceState.update(AppearanceKeys.LimitMediaGridToNine, it)
                                    },
                                    textBefore = true,
                                )
                            },
                        )
                    }
                }
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.settings_appearance_expand_media))
                    },
                    caption = {
                        Text(stringResource(Res.string.settings_appearance_expand_media_description))
                    },
                    trailing = {
                        Switcher(
                            checked = LocalTimelineAppearance.current.expandMediaSize,
                            {
                                appearanceState.update(AppearanceKeys.ExpandMediaSize, it)
                            },
                            textBefore = true,
                        )
                    },
                )
            }
        }
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_appearance_video_autoplay))
            },
            caption = {
                Text(stringResource(Res.string.settings_appearance_video_autoplay_description))
            },
            trailing = {
                Switcher(
                    checked = LocalTimelineAppearance.current.videoAutoplay == VideoAutoplay.ALWAYS,
                    {
                        appearanceState.update(
                            AppearanceKeys.VideoAutoplay,
                            if (it) VideoAutoplay.ALWAYS else VideoAutoplay.NEVER,
                        )
                    },
                    textBefore = true,
                )
            },
        )
    }
}
