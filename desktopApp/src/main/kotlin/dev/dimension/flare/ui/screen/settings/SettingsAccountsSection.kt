package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.Res
import dev.dimension.flare.add_account
import dev.dimension.flare.cancel
import dev.dimension.flare.delete
import dev.dimension.flare.home_login
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.remove_account
import dev.dimension.flare.settings_accounts_remove_confirm
import dev.dimension.flare.settings_accounts_title
import dev.dimension.flare.settings_nostr_relays_manage
import dev.dimension.flare.tab_settings_drag
import dev.dimension.flare.ui.component.AccountItem
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.login.ReloginTarget
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.MenuFlyoutContainer
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.RadioButton
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableColumn

internal interface DesktopAccountState : AccountManagementPresenter.State, UserState {
    val expanded: Boolean
    fun setExpanded(value: Boolean)
    fun moveItem(fromIndex: Int, toIndex: Int)
    fun deleteItem(accountKey: MicroBlogKey)
}

@Composable
internal fun accountsPresenter(): DesktopAccountState = run {
    var expanded by remember { mutableStateOf(false) }
    val state = remember { AccountManagementPresenter() }.invoke()
    val activeAccountState = remember { ActiveAccountPresenter() }.invoke()

    object : DesktopAccountState, AccountManagementPresenter.State by state, UserState by activeAccountState {
        override val expanded = expanded

        override fun setExpanded(value: Boolean) {
            expanded = value
        }

        override fun moveItem(fromIndex: Int, toIndex: Int) {
            state.accounts.onSuccess { accounts ->
                accounts
                    .map { it.account.accountKey }
                    .toMutableList()
                    .apply {
                        add(toIndex, removeAt(fromIndex))
                    }.let {
                        state.setOrder(it)
                        state.updateOrder(it)
                    }
            }
        }

        override fun deleteItem(accountKey: MicroBlogKey) {
            state.logout(accountKey)
        }
    }
}

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsAccountsSection(
    accountState: DesktopAccountState,
    toLogin: () -> Unit,
    toRelogin: (ReloginTarget) -> Unit,
    toNostrRelays: (MicroBlogKey) -> Unit,
) {
    var pendingDeleteAccountKey by remember { mutableStateOf<MicroBlogKey?>(null) }
    var pendingDeleteAccountLabel by remember { mutableStateOf<String?>(null) }

    val activeAccount = accountState.user.takeSuccess()
    accountState.accounts
        .onSuccess { accounts ->
            Header(stringResource(Res.string.settings_accounts_title))
            Expander(
                expanded = accountState.expanded,
                onExpandedChanged = {
                    accountState.setExpanded(it)
                },
                heading = {
                    if (activeAccount == null) {
                        Text(stringResource(Res.string.settings_accounts_title))
                    } else {
                        RichText(activeAccount.name)
                    }
                },
                icon = {
                    activeAccount?.let {
                        AvatarComponent(data = it.avatar, size = 24.dp)
                    }
                },
                caption = {
                    Text(
                        text =
                            activeAccount?.handle?.canonical
                                ?: stringResource(Res.string.add_account),
                    )
                },
            ) {
                ReorderableColumn(
                    list = accounts,
                    onSettle = { fromIndex, toIndex ->
                        accountState.moveItem(fromIndex, toIndex)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) { _, account, _ ->
                    key(account.account.accountKey.toString()) {
                        ReorderableItem {
                            AccountItem(
                                account.profile,
                                onClick = {
                                    accountState.setActiveAccount(it)
                                },
                                toLogin = toLogin,
                                toRelogin = toRelogin,
                                trailingContent = { user ->
                                    Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (user == null) {
                                            SubtleButton(
                                                onClick = {
                                                    pendingDeleteAccountKey = account.account.accountKey
                                                    pendingDeleteAccountLabel = account.account.accountKey.toString()
                                                },
                                            ) {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Trash,
                                                    contentDescription = stringResource(Res.string.remove_account),
                                                    tint = FluentTheme.colors.system.critical,
                                                )
                                            }
                                        } else {
                                            RadioButton(
                                                selected = activeAccount?.key == user.key,
                                                onClick = {
                                                    accountState.setActiveAccount(user.key)
                                                },
                                            )
                                            SubtleButton(
                                                modifier = Modifier.draggableHandle(),
                                                onClick = {},
                                                iconOnly = true,
                                            ) {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Bars,
                                                    contentDescription = stringResource(Res.string.tab_settings_drag),
                                                )
                                            }
                                            var isFlyoutVisible by remember { mutableStateOf(false) }
                                            MenuFlyoutContainer(
                                                flyout = {
                                                    if (account.account.supportsRelayManagement) {
                                                        MenuFlyoutItem(
                                                            text = {
                                                                Text(
                                                                    stringResource(Res.string.settings_nostr_relays_manage),
                                                                )
                                                            },
                                                            onClick = {
                                                                toNostrRelays(user.key)
                                                            },
                                                            icon = {
                                                                FAIcon(
                                                                    FontAwesomeIcons.Solid.List,
                                                                    contentDescription =
                                                                        stringResource(
                                                                            Res.string.settings_nostr_relays_manage,
                                                                        ),
                                                                )
                                                            },
                                                        )
                                                    }
                                                    MenuFlyoutItem(
                                                        text = {
                                                            Text(
                                                                stringResource(Res.string.remove_account),
                                                                color = FluentTheme.colors.system.critical,
                                                            )
                                                        },
                                                        onClick = {
                                                            pendingDeleteAccountKey = user.key
                                                            pendingDeleteAccountLabel = user.handle.canonical
                                                        },
                                                        icon = {
                                                            FAIcon(
                                                                FontAwesomeIcons.Solid.Trash,
                                                                contentDescription =
                                                                    stringResource(
                                                                        Res.string.remove_account,
                                                                    ),
                                                                tint = FluentTheme.colors.system.critical,
                                                            )
                                                        },
                                                    )
                                                },
                                            ) {
                                                SubtleButton(
                                                    onClick = {
                                                        isFlyoutVisible = !isFlyoutVisible
                                                    },
                                                    iconOnly = true,
                                                ) {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.EllipsisVertical,
                                                        contentDescription = stringResource(Res.string.remove_account),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                ExpanderItem(
                    heading = {
                        Text(stringResource(Res.string.add_account))
                    },
                    modifier =
                        Modifier.clickable {
                            toLogin.invoke()
                        },
                    icon = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(Res.string.add_account),
                        )
                    },
                )
            }
        }.onError {
            CardExpanderItem(
                heading = {
                    Text(stringResource(Res.string.home_login))
                },
                onClick = {
                    toLogin.invoke()
                },
            )
        }

    ContentDialog(
        title = stringResource(Res.string.remove_account),
        visible = pendingDeleteAccountKey != null,
        content = {
            Text(
                text =
                    stringResource(
                        Res.string.settings_accounts_remove_confirm,
                        pendingDeleteAccountLabel ?: pendingDeleteAccountKey.toString(),
                    ),
                modifier = Modifier.padding(16.dp),
            )
        },
        primaryButtonText = stringResource(Res.string.delete),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    pendingDeleteAccountKey?.let(accountState::deleteItem)
                    pendingDeleteAccountKey = null
                    pendingDeleteAccountLabel = null
                }

                else -> {
                    pendingDeleteAccountKey = null
                    pendingDeleteAccountLabel = null
                }
            }
        },
    )
}
