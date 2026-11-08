package org.crux.ui.screen.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Minus
import compose.icons.fontawesomeicons.solid.Plus
import kotlinx.collections.immutable.persistentMapOf
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.model.AvatarShape
import org.crux.data.model.Theme
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.screen.rss.ReaderFont
import org.crux.ui.screen.rss.ReaderMode
import org.crux.ui.screen.rss.ReaderSpacing
import org.crux.ui.screen.rss.ReaderTheme
import org.crux.ui.screen.rss.rememberReaderSettings
import org.crux.ui.theme.first
import org.crux.ui.theme.isLightTheme
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding
import kotlin.math.roundToInt

internal enum class AppearanceStudioTab(
    @StringRes val labelRes: Int,
) {
    Theme(R.string.appearance_tab_theme),
    Layout(R.string.appearance_tab_layout),
    Display(R.string.appearance_tab_feeds),
    Media(R.string.appearance_tab_media),
    Reader(R.string.appearance_tab_reader),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceThemeScreen(
    onBack: () -> Unit,
    toColorPicker: () -> Unit,
    initialTab: AppearanceStudioTab = AppearanceStudioTab.Theme,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.appearance_studio_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = screenHorizontalPadding)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppearanceStudioTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = stringResource(id = tab.labelRes),
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                AppearanceStudioTab.Theme -> {
                    AppearanceThemeContent(
                        state = state,
                        toColorPicker = toColorPicker,
                    )
                }

                AppearanceStudioTab.Layout -> {
                    AppearanceLayoutContent(state = state)
                }

                AppearanceStudioTab.Display -> {
                    AppearanceDisplayContent(state = state)
                }

                AppearanceStudioTab.Media -> {
                    AppearanceMediaContent(state = state)
                }

                AppearanceStudioTab.Reader -> {
                    AppearanceReaderContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceThemeContent(
    state: AppearanceSettingsUpdater,
    toColorPicker: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val globalAppearance = LocalGlobalAppearance.current
    val timelineAppearance = LocalTimelineAppearance.current

    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        SingleChoiceSettingsItem(
            headline = { Text(text = stringResource(id = R.string.settings_appearance_theme)) },
            supporting = { Text(text = stringResource(id = R.string.settings_appearance_theme_description)) },
            items =
                persistentMapOf(
                    Theme.LIGHT to stringResource(id = R.string.settings_appearance_theme_light),
                    Theme.SYSTEM to stringResource(id = R.string.settings_appearance_theme_auto),
                    Theme.DARK to stringResource(id = R.string.settings_appearance_theme_dark),
                ),
            selected = globalAppearance.theme,
            onSelected = {
                state.update(AppearanceKeys.Theme, it)
            },
            shapes = ListItemDefaults.first(),
        )
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.Grayscale, !globalAppearance.grayscale)
            },
            shapes = ListItemDefaults.item(),
            content = {
                Text(text = stringResource(id = R.string.crux_grayscale_title))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.crux_grayscale_description))
            },
            trailingContent = {
                Switch(
                    checked = globalAppearance.grayscale,
                    onCheckedChange = {
                        state.update(AppearanceKeys.Grayscale, it)
                    },
                )
            },
        )
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.PaperTint, !globalAppearance.paperTint)
            },
            shapes = ListItemDefaults.item(),
            content = {
                Text(text = stringResource(id = R.string.crux_paper_tint_title))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.crux_paper_tint_description))
            },
            trailingContent = {
                Switch(
                    checked = globalAppearance.paperTint,
                    onCheckedChange = {
                        state.update(AppearanceKeys.PaperTint, it)
                    },
                )
            },
        )
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.PureColorMode, !globalAppearance.pureColorMode)
            },
            shapes = ListItemDefaults.item(),
            content = {
                Text(text = stringResource(id = R.string.settings_appearance_theme_pure_color))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_appearance_theme_pure_color_description))
            },
            trailingContent = {
                Switch(
                    checked = globalAppearance.pureColorMode,
                    onCheckedChange = {
                        state.update(AppearanceKeys.PureColorMode, it)
                    },
                )
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SegmentedListItem(
                onClick = {
                    state.update(AppearanceKeys.DynamicTheme, !globalAppearance.dynamicTheme)
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_dynamic_theme))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_dynamic_theme_description))
                },
                trailingContent = {
                    Switch(
                        checked = globalAppearance.dynamicTheme,
                        onCheckedChange = {
                            state.update(AppearanceKeys.DynamicTheme, it)
                        },
                    )
                },
            )
        }
        AnimatedVisibility(visible = !globalAppearance.dynamicTheme || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            SegmentedListItem(
                onClick = {
                    toColorPicker.invoke()
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_theme_color))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_theme_color_description))
                },
                trailingContent = {
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ).size(36.dp),
                    )
                },
            )
        }
        SingleChoiceSettingsItem(
            headline = { Text(text = stringResource(id = R.string.settings_appearance_avatar_shape)) },
            supporting = { Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_description)) },
            items =
                persistentMapOf(
                    AvatarShape.CIRCLE to stringResource(id = R.string.settings_appearance_avatar_shape_round),
                    AvatarShape.SQUARE to stringResource(id = R.string.settings_appearance_avatar_shape_square),
                ),
            selected = timelineAppearance.avatarShape,
            onSelected = {
                state.update(AppearanceKeys.AvatarShape, it)
            },
            shapes = ListItemDefaults.item(),
        )

        val fontSizeState =
            rememberSliderState(
                value = globalAppearance.fontSizeDiff,
                steps = 7,
                trackRange = -4f..4f,
            )
        SegmentedListItem(
            onClick = {},
            shapes = ListItemDefaults.last(),
            content = {
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_appearance_font_size_diff),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            supportingContent = {
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_appearance_font_size_diff_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        IconButton(
                            onClick = {
                                if (fontSizeState.value > -4f) {
                                    fontSizeState.value -= 1f
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    state.updateFontScale(fontSizeState.value)
                                }
                            },
                            enabled = fontSizeState.value > -4f,
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Minus,
                                contentDescription = stringResource(R.string.settings_appearance_font_size_diff_decrease),
                            )
                        }
                        Slider(
                            state = fontSizeState,
                            onValueChange = {
                                fontSizeState.value = it
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                            onValueChangeFinished = {
                                state.updateFontScale(fontSizeState.value)
                            },
                            modifier =
                                Modifier
                                    .weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (fontSizeState.value < 4f) {
                                    fontSizeState.value += 1f
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    state.updateFontScale(fontSizeState.value)
                                }
                            },
                            enabled = fontSizeState.value < 4f,
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Plus,
                                contentDescription = stringResource(R.string.settings_appearance_font_size_diff_increase),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
internal fun AppearanceReaderContent() {
    val readerState = rememberReaderSettings()
    val settings = readerState.value
    val paperBg =
        settings.theme.background ?: if (isLightTheme()) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.background
        }
    val paperText = settings.theme.text ?: MaterialTheme.colorScheme.onSurface

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = paperBg,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.appearance_reader_preview_title),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontFamily = settings.font.family,
                            fontWeight = FontWeight.Bold,
                            color = paperText,
                        ),
                )
                Text(
                    text = stringResource(id = R.string.appearance_reader_preview_body),
                    style = settings.bodyStyle(LocalTextStyle.current).copy(color = paperText),
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.reader_text_size, (settings.textScale * 100).roundToInt()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = settings.textScale,
                    onValueChange = { readerState.update(settings.copy(textScale = it)) },
                    valueRange = 0.8f..1.6f,
                    steps = 7,
                )

                ReaderChipSection(
                    title = stringResource(R.string.reader_mode),
                    options = ReaderMode.entries,
                    selected = settings.mode,
                    label = { stringResource(it.labelRes) },
                    onSelect = { readerState.update(settings.copy(mode = it)) },
                )
                ReaderChipSection(
                    title = stringResource(R.string.reader_theme),
                    options = ReaderTheme.entries,
                    selected = settings.theme,
                    label = { stringResource(it.labelRes) },
                    onSelect = { readerState.update(settings.copy(theme = it)) },
                )
                ReaderChipSection(
                    title = stringResource(R.string.reader_font),
                    options = ReaderFont.entries,
                    selected = settings.font,
                    label = { stringResource(it.labelRes) },
                    onSelect = { readerState.update(settings.copy(font = it)) },
                )
                ReaderChipSection(
                    title = stringResource(R.string.reader_spacing),
                    options = ReaderSpacing.entries,
                    selected = settings.spacing,
                    label = { stringResource(it.labelRes) },
                    onSelect = { readerState.update(settings.copy(spacing = it)) },
                )
            }
        }
    }
}

@Composable
private fun <T> ReaderChipSection(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text = label(option)) },
                )
            }
        }
    }
}
