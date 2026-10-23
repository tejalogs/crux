package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.datastore.model.TimelineAutoRefreshInterval
import dev.dimension.flare.deeplink_account_selection_browser
import dev.dimension.flare.edit
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.settings_auto_refresh_disabled
import dev.dimension.flare.settings_auto_refresh_fifteen_minutes
import dev.dimension.flare.settings_auto_refresh_five_minutes
import dev.dimension.flare.settings_auto_refresh_one_hour
import dev.dimension.flare.settings_auto_refresh_one_minute
import dev.dimension.flare.settings_auto_refresh_thirty_minutes
import dev.dimension.flare.settings_behavior_title
import dev.dimension.flare.settings_home_timeline_auto_refresh_interval
import dev.dimension.flare.settings_home_timeline_auto_refresh_interval_description
import dev.dimension.flare.settings_link_open_default_ask_every_time
import dev.dimension.flare.settings_link_open_defaults_description
import dev.dimension.flare.settings_link_open_defaults_title
import dev.dimension.flare.settings_mxga_filter_description
import dev.dimension.flare.settings_mxga_filter_title
import dev.dimension.flare.settings_mxga_last_refreshed
import dev.dimension.flare.settings_mxga_learn_more
import dev.dimension.flare.settings_mxga_never_refreshed
import dev.dimension.flare.settings_mxga_refresh_title
import dev.dimension.flare.settings_mxga_refreshing
import dev.dimension.flare.settings_refresh_home_timeline_on_launch
import dev.dimension.flare.settings_refresh_home_timeline_on_launch_description
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.settings.LinkOpenDefaultsPresenter
import dev.dimension.flare.ui.presenter.settings.MxgaSettingsPresenter
import dev.dimension.flare.ui.presenter.settings.MxgaSettingsState
import dev.dimension.flare.ui.render.toUi
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.HyperlinkButton
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsBehaviorSection(
    appSettings: AppSettings,
    updateAppSettings: (AppSettings.() -> AppSettings) -> Unit,
    linkOpenTargets: ImmutableList<LinkOpenDefaultsPresenter.Target>,
    linkOpenDefaultsState: LinkOpenDefaultsPresenter.State,
    mxgaState: MxgaSettingsState,
) {
    Header(stringResource(Res.string.settings_behavior_title))
    CardExpanderItem(
        icon = null,
        heading = {
            Text(stringResource(Res.string.settings_refresh_home_timeline_on_launch))
        },
        caption = {
            Text(stringResource(Res.string.settings_refresh_home_timeline_on_launch_description))
        },
        trailing = {
            Switcher(
                checked = appSettings.refreshHomeTimelineOnLaunch,
                onCheckStateChange = { value ->
                    updateAppSettings { copy(refreshHomeTimelineOnLaunch = value) }
                },
                textBefore = true,
            )
        },
    )
    CardExpanderItem(
        icon = null,
        heading = {
            Text(stringResource(Res.string.settings_home_timeline_auto_refresh_interval))
        },
        caption = {
            Text(stringResource(Res.string.settings_home_timeline_auto_refresh_interval_description))
        },
        trailing = {
            val items =
                persistentMapOf(
                    TimelineAutoRefreshInterval.DISABLED to Res.string.settings_auto_refresh_disabled,
                    TimelineAutoRefreshInterval.ONE_MINUTE to Res.string.settings_auto_refresh_one_minute,
                    TimelineAutoRefreshInterval.FIVE_MINUTES to Res.string.settings_auto_refresh_five_minutes,
                    TimelineAutoRefreshInterval.FIFTEEN_MINUTES to Res.string.settings_auto_refresh_fifteen_minutes,
                    TimelineAutoRefreshInterval.THIRTY_MINUTES to Res.string.settings_auto_refresh_thirty_minutes,
                    TimelineAutoRefreshInterval.ONE_HOUR to Res.string.settings_auto_refresh_one_hour,
                )
            var isIntervalFlyoutVisible by remember { mutableStateOf(false) }
            MenuFlyoutContainer(
                flyout = {
                    items.forEach { (interval, label) ->
                        MenuFlyoutItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                updateAppSettings { copy(homeTimelineAutoRefreshInterval = interval) }
                                isIntervalFlyoutVisible = false
                            },
                        )
                    }
                },
                content = {
                    DropDownButton(
                        onClick = { isIntervalFlyoutVisible = !isIntervalFlyoutVisible },
                        content = {
                            items[appSettings.homeTimelineAutoRefreshInterval]?.let {
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
    var behaviorExpanded by remember { mutableStateOf(false) }
    Expander(
        icon = null,
        expanded = behaviorExpanded,
        onExpandedChanged = { behaviorExpanded = it },
        heading = {
            Text(stringResource(Res.string.settings_link_open_defaults_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_link_open_defaults_description))
        },
    ) {
        linkOpenTargets.forEachIndexed { index, target ->
            if (index > 0) {
                ExpanderItemSeparator()
            }
            LinkOpenDefaultSettingsItem(
                target = target,
                state = linkOpenDefaultsState,
            )
        }
    }

    if (mxgaState.hasXQtAccount) {
        Header("MXGA")
        CardExpanderItem(
            onClick = { mxgaState.setEnabled(!mxgaState.isEnabled) },
            heading = {
                Text(stringResource(Res.string.settings_mxga_filter_title))
            },
            caption = {
                Text(stringResource(Res.string.settings_mxga_filter_description))
            },
            trailing = {
                Switcher(
                    checked = mxgaState.isEnabled,
                    onCheckStateChange = mxgaState::setEnabled,
                    textBefore = true,
                )
            },
        )
        CardExpanderItem(
            onClick = mxgaState::refresh,
            heading = {
                Text(stringResource(Res.string.settings_mxga_refresh_title))
            },
            caption = {
                Text(
                    when {
                        mxgaState.isRefreshing -> {
                            stringResource(Res.string.settings_mxga_refreshing)
                        }

                        mxgaState.lastCheckedAt <= 0L -> {
                            stringResource(Res.string.settings_mxga_never_refreshed)
                        }

                        else -> {
                            stringResource(
                                Res.string.settings_mxga_last_refreshed,
                                Instant
                                    .fromEpochMilliseconds(mxgaState.lastCheckedAt)
                                    .toUi()
                                    .relative,
                            )
                        }
                    },
                )
            },
        )
        HyperlinkButton("https://github.com/foru17/make-x-great-again") {
            Text(stringResource(Res.string.settings_mxga_learn_more))
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun LinkOpenDefaultSettingsItem(
    target: LinkOpenDefaultsPresenter.Target,
    state: LinkOpenDefaultsPresenter.State,
) {
    val askLabel = stringResource(Res.string.settings_link_open_default_ask_every_time)
    val browserLabel = stringResource(Res.string.deeplink_account_selection_browser)
    var isFlyoutVisible by remember { mutableStateOf(false) }
    ExpanderItem(
        heading = {
            Text(target.title)
        },
        caption = {
            LinkOpenDefaultOptionContent(
                option = target.selectedOption,
                askLabel = askLabel,
                browserLabel = browserLabel,
            )
        },
        trailing = {
            MenuFlyoutContainer(
                flyout = {
                    target.options.forEach { option ->
                        MenuFlyoutItem(
                            onClick = {
                                state.select(target, option)
                                isFlyoutVisible = false
                            },
                            text = {
                                LinkOpenDefaultOptionContent(
                                    option = option,
                                    askLabel = askLabel,
                                    browserLabel = browserLabel,
                                )
                            },
                        )
                    }
                },
                content = {
                    DropDownButton(
                        onClick = { isFlyoutVisible = !isFlyoutVisible },
                        content = {
                            Text(stringResource(Res.string.edit))
                        },
                    )
                },
            )
        },
    )
}

@Composable
internal fun LinkOpenDefaultOptionContent(
    option: LinkOpenDefaultsPresenter.Option,
    askLabel: String,
    browserLabel: String,
) {
    when {
        option.isAsk -> Text(askLabel)
        option.isBrowser -> Text(browserLabel)
        option.account != null -> LinkOpenDefaultAccountOption(option.account!!)
    }
}

@Composable
internal fun LinkOpenDefaultAccountOption(account: LinkOpenDefaultsPresenter.Account) {
    account.profile
        .onSuccess { user ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarComponent(data = user.avatar, size = 24.dp)
                Column {
                    RichText(text = user.name, maxLines = 1)
                    Text(text = user.handle.canonical, maxLines = 1)
                }
            }
        }.onLoading {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarComponent(data = null, size = 24.dp)
                Text(account.accountKey.toString())
            }
        }.onError {
            Text(account.accountKey.toString())
        }
}
