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
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.status.StatusItem
import org.crux.ui.model.isSuccess
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceDisplayScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { appearancePresenter() }
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_display_group_title))
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
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            AppearanceDisplayContent(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceDisplayContent(state: AppearanceSettingsUpdater) {
    val timelineAppearance = LocalTimelineAppearance.current
    Column(
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        state.sampleStatus.onSuccess { sample ->
            SegmentedListItem(
                onClick = {},
                shapes = ListItemDefaults.first(),
                contentPadding = PaddingValues(0.dp),
            ) {
                StatusItem(
                    sample,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.AbsoluteTimestamp, !timelineAppearance.absoluteTimestamp)
            },
            shapes =
                if (state.sampleStatus.isSuccess) {
                    ListItemDefaults.item()
                } else {
                    ListItemDefaults.first()
                },
            content = {
                Text(text = stringResource(id = R.string.settings_appearance_absolute_timestamp))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_appearance_absolute_timestamp_description))
            },
            trailingContent = {
                Switch(
                    checked = timelineAppearance.absoluteTimestamp,
                    onCheckedChange = {
                        state.update(AppearanceKeys.AbsoluteTimestamp, it)
                    },
                )
            },
        )
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.ShowPlatformLogo, !timelineAppearance.showPlatformLogo)
            },
            shapes = ListItemDefaults.item(),
            content = {
                Text(text = stringResource(id = R.string.settings_appearance_show_platform_logo))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_appearance_show_platform_logo_description))
            },
            trailingContent = {
                Switch(
                    checked = timelineAppearance.showPlatformLogo,
                    onCheckedChange = {
                        state.update(AppearanceKeys.ShowPlatformLogo, it)
                    },
                )
            },
        )
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.ShowEmojiReactions, !timelineAppearance.showEmojiReactions)
            },
            shapes = ListItemDefaults.item(),
            content = {
                Text(text = stringResource(id = R.string.settings_appearance_show_reactions))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_appearance_show_reactions_description))
            },
            trailingContent = {
                Switch(
                    checked = timelineAppearance.showEmojiReactions,
                    onCheckedChange = {
                        state.update(AppearanceKeys.ShowEmojiReactions, it)
                    },
                )
            },
        )
        SegmentedListItem(
            onClick = {
                state.update(AppearanceKeys.ShowLinkPreview, !timelineAppearance.showLinkPreview)
            },
            shapes =
                if (timelineAppearance.showLinkPreview) {
                    ListItemDefaults.item()
                } else {
                    ListItemDefaults.last()
                },
            content = {
                Text(text = stringResource(id = R.string.settings_appearance_show_link_previews))
            },
            supportingContent = {
                Text(text = stringResource(id = R.string.settings_appearance_show_link_previews_description))
            },
            trailingContent = {
                Switch(
                    checked = timelineAppearance.showLinkPreview,
                    onCheckedChange = {
                        state.update(AppearanceKeys.ShowLinkPreview, it)
                    },
                )
            },
        )
        AnimatedVisibility(visible = timelineAppearance.showLinkPreview) {
            SegmentedListItem(
                onClick = {
                    state.update(AppearanceKeys.CompatLinkPreview, !timelineAppearance.compatLinkPreview)
                },
                shapes = ListItemDefaults.last(),
                content = {
                    Text(text = stringResource(id = R.string.settings_appearance_compat_link_previews))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_compat_link_previews_description))
                },
                trailingContent = {
                    Switch(
                        checked = timelineAppearance.compatLinkPreview,
                        onCheckedChange = {
                            state.update(AppearanceKeys.CompatLinkPreview, it)
                        },
                    )
                },
            )
        }
    }
}
