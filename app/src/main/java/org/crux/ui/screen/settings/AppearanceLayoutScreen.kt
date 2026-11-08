package org.crux.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentMapOf
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.BottomBarStyle
import org.crux.data.model.PostActionStyle
import org.crux.data.model.TimelineDisplayMode
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.status.StatusItem
import org.crux.ui.model.isSuccess
import org.crux.ui.model.onSuccess
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceLayoutScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_layout_group_title))
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
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceLayoutContent(
                state = state,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceLayoutContent(state: AppearanceSettingsUpdater) {
    val globalAppearance = LocalGlobalAppearance.current
    val timelineAppearance = LocalTimelineAppearance.current
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            SingleChoiceSettingsItem(
                headline = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_style)) },
                supporting = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_style_description)) },
                items =
                    persistentMapOf(
                        BottomBarStyle.Floating to stringResource(id = R.string.settings_appearance_bottombar_style_floating),
                        BottomBarStyle.Classic to stringResource(id = R.string.settings_appearance_bottombar_style_classic),
                    ),
                selected = globalAppearance.bottomBarStyle,
                onSelected = {
                    state.update(AppearanceKeys.BottomBarStyle, it)
                },
                shapes = ListItemDefaults.first(),
            )
            SingleChoiceSettingsItem(
                headline = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_behavior)) },
                supporting = { Text(text = stringResource(id = R.string.settings_appearance_bottombar_behavior_description)) },
                items =
                    persistentMapOf(
                        BottomBarBehavior.AlwaysShow to stringResource(id = R.string.settings_appearance_bottombar_behavior_fixed),
                        BottomBarBehavior.MinimizeOnScroll to
                            stringResource(id = R.string.settings_appearance_bottombar_behavior_minimize_on_scroll),
                        BottomBarBehavior.HideOnScroll to
                            stringResource(id = R.string.settings_appearance_bottombar_behavior_hide_on_scroll),
                    ),
                selected = globalAppearance.bottomBarBehavior,
                onSelected = {
                    state.update(AppearanceKeys.BottomBarBehavior, it)
                },
                shapes = ListItemDefaults.item(),
            )
            SegmentedListItem(
                onClick = {
                    state.update(AppearanceKeys.ShowBottomBarLabels, !globalAppearance.showBottomBarLabels)
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_bottom_bar_labels))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_bottom_bar_labels_description))
                },
                trailingContent = {
                    Switch(
                        checked = globalAppearance.showBottomBarLabels,
                        onCheckedChange = {
                            state.update(AppearanceKeys.ShowBottomBarLabels, it)
                        },
                    )
                },
            )
            SegmentedListItem(
                onClick = {
                    state.update(AppearanceKeys.HapticsEnabled, !globalAppearance.hapticsEnabled)
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_haptics))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_haptics_description))
                },
                trailingContent = {
                    Switch(
                        checked = globalAppearance.hapticsEnabled,
                        onCheckedChange = {
                            state.update(AppearanceKeys.HapticsEnabled, it)
                        },
                    )
                },
            )
            SegmentedListItem(
                onClick = {
                    state.update(AppearanceKeys.DeckMode, !globalAppearance.deckMode)
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_deck_mode))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_deck_mode_description))
                },
                trailingContent = {
                    Switch(
                        checked = globalAppearance.deckMode,
                        onCheckedChange = {
                            state.update(AppearanceKeys.DeckMode, it)
                        },
                    )
                },
            )
            SingleChoiceSettingsItem(
                headline = { Text(text = stringResource(id = R.string.settings_appearance_timeline_display_mode)) },
                supporting = { Text(text = stringResource(id = R.string.settings_appearance_timeline_display_mode_description)) },
                items =
                    persistentMapOf(
                        TimelineDisplayMode.Card to stringResource(id = R.string.settings_appearance_timeline_display_mode_card),
                        TimelineDisplayMode.Plain to stringResource(id = R.string.settings_appearance_timeline_display_mode_plain),
                        TimelineDisplayMode.Gallery to stringResource(id = R.string.settings_appearance_timeline_display_mode_gallery),
                    ),
                selected = timelineAppearance.timelineDisplayMode,
                onSelected = {
                    state.update(AppearanceKeys.TimelineDisplayMode, it)
                },
                shapes = ListItemDefaults.last(),
            )
        }
    }
}
