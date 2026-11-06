package org.crux.ui.screen.settings

import androidx.compose.animation.core.AnimationConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemElevation
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.FaceSadTear
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import kotlinx.coroutines.delay
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.data.repository.LoginExpiredException
import org.crux.model.MicroBlogKey
import org.crux.model.UnsupportedPlatformException
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.AvatarComponentDefaults
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.component.RichText
import org.crux.ui.component.ThemeIconData
import org.crux.ui.component.ThemedIcon
import org.crux.ui.component.placeholder
import org.crux.ui.component.toImageVector
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.model.isError
import org.crux.ui.model.isSuccess
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.login.ReloginTarget
import org.crux.ui.presenter.settings.AccountsState.AccountItem
import org.crux.ui.route.Route
import org.crux.ui.screen.crux.CruxPlatformList
import org.crux.ui.screen.crux.CruxSectionHeader
import org.crux.ui.screen.crux.rememberCruxConnections
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.segmentedShapes2
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsScreen(
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
    toRelogin: (ReloginTarget) -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter {
        accountsPresenter()
    }
    val connections = rememberCruxConnections()
    var pendingDeleteAccountKey by remember { mutableStateOf<MicroBlogKey?>(null) }
    var pendingDeleteAccountLabel by remember { mutableStateOf<String?>(null) }
    val lazyListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val reorderableLazyColumnState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            state.moveItem(from.key, to.key)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_accounts_title))
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
        LazyColumn(
            contentPadding = it,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            state = lazyListState,
        ) {
            item(key = "crux_platforms_header") {
                CruxSectionHeader(
                    title = stringResource(R.string.crux_accounts_platforms_title),
                    subtitle = stringResource(R.string.crux_accounts_platforms_subtitle),
                )
            }
            item(key = "crux_platforms") {
                CruxPlatformList(connections = connections, navigate = navigate)
            }
            if (state.currentItems.isNotEmpty()) {
                item(key = "crux_other_header") {
                    CruxSectionHeader(
                        title = stringResource(R.string.crux_accounts_other_title),
                        subtitle = stringResource(R.string.crux_accounts_other_subtitle),
                    )
                }
            }
            itemsIndexed(state.currentItems, key = { _, item -> item.account.accountKey.toString() }) { index, (account, data) ->
                val swipeState =
                    rememberSwipeToDismissBoxState()
                val shape = ListItemDefaults.segmentedShapes2(index, state.currentItems.size)
                var showMenu by remember { mutableStateOf(false) }
                val isSwiping =
                    swipeState.dismissDirection != SwipeToDismissBoxValue.Settled
                LaunchedEffect(swipeState.settledValue) {
                    if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
                        delay(AnimationConstants.DefaultDurationMillis.toLong())
                        pendingDeleteAccountKey = account.accountKey
                        pendingDeleteAccountLabel =
                            when (val profile = data) {
                                is UiState.Success -> profile.data.handle.canonical
                                is UiState.Error, is UiState.Loading -> account.accountKey.toString()
                            }
                        swipeState.snapTo(SwipeToDismissBoxValue.Settled)
                    }
                }
                ReorderableItem(
                    reorderableLazyColumnState,
                    key = account.accountKey.toString(),
                ) { isDragging ->
                    SwipeToDismissBox(
                        state = swipeState,
                        backgroundContent = {
                            if (swipeState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                color = MaterialTheme.colorScheme.error,
                                                shape = shape.draggedShape,
                                            ).padding(16.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.settings_accounts_remove),
                                        color = MaterialTheme.colorScheme.onError,
                                    )
                                }
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = data.isSuccess || data.isError,
                    ) {
                        AccountItem(
                            selected = isSwiping || isDragging,
                            userState = data,
                            shapes = shape,
                            onClick = {
                                state.setActiveAccount(it)
                            },
                            onLongClick = {
                                showMenu = true
                            },
                            toLogin = {},
                            toRelogin = toRelogin,
                            trailingContent = { user ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.activeAccount.onSuccess {
                                        RadioButton(
                                            selected = it.accountKey == user.key,
                                            onClick = {
                                                state.setActiveAccount(user.key)
                                            },
                                        )
                                    }

                                    IconButton(
                                        modifier =
                                            Modifier.draggableHandle(
                                                onDragStarted = {
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.Confirm,
                                                    )
                                                },
                                                onDragStopped = {
                                                    haptics.performHapticFeedback(
                                                        HapticFeedbackType.Confirm,
                                                    )
                                                },
                                            ),
                                        onClick = {},
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Bars,
                                            contentDescription = stringResource(id = R.string.tab_settings_drag),
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            showMenu = true
                                        },
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.EllipsisVertical,
                                            contentDescription = stringResource(id = R.string.more),
                                        )
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = {
                                                showMenu = false
                                            },
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = stringResource(id = R.string.settings_accounts_remove),
                                                        color = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    pendingDeleteAccountKey = account.accountKey
                                                    pendingDeleteAccountLabel =
                                                        when (val profile = data) {
                                                            is UiState.Success -> profile.data.handle.canonical
                                                            is UiState.Error, is UiState.Loading -> account.accountKey.toString()
                                                        }
                                                },
                                                leadingIcon = {
                                                    FAIcon(
                                                        imageVector = FontAwesomeIcons.Solid.Trash,
                                                        contentDescription =
                                                            stringResource(
                                                                id = R.string.settings_accounts_remove,
                                                            ),
                                                        tint = MaterialTheme.colorScheme.error,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    pendingDeleteAccountKey?.let { accountKey ->
        AlertDialog(
            onDismissRequest = {
                pendingDeleteAccountKey = null
                pendingDeleteAccountLabel = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.deleteItem(accountKey)
                        pendingDeleteAccountKey = null
                        pendingDeleteAccountLabel = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDeleteAccountKey = null
                        pendingDeleteAccountLabel = null
                    },
                ) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
            title = {
                Text(text = stringResource(id = R.string.settings_accounts_remove))
            },
            text = {
                Text(
                    text =
                        stringResource(
                            id = R.string.settings_accounts_remove_confirm,
                            pendingDeleteAccountLabel ?: accountKey.toString(),
                        ),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountItem(
    userState: UiState<UiProfile>,
    onClick: (MicroBlogKey) -> Unit,
    toLogin: () -> Unit,
    modifier: Modifier = Modifier,
    toRelogin: (ReloginTarget) -> Unit = { toLogin() },
    trailingContent: @Composable (UiProfile) -> Unit = { },
    headlineContent: @Composable (UiProfile) -> Unit = {
        RichText(text = it.name, maxLines = 1)
    },
    supportingContent: @Composable (UiProfile) -> Unit = {
        Text(text = it.handle.canonical, maxLines = 1)
    },
    avatarSize: Dp = AvatarComponentDefaults.size,
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    Box(modifier = modifier) {
        userState
            .onSuccess { data ->
                SegmentedListItem(
                    selected = selected,
                    elevation = elevation,
                    onClick = {
                        onClick.invoke(data.key)
                    },
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    shapes = shapes,
                    content = {
                        headlineContent.invoke(data)
                    },
//                    modifier =
//                        modifier
//                            .clickable {
//                                onClick.invoke(data.key)
//                            },
                    leadingContent = {
                        AvatarComponent(
                            data = data.avatar,
                            size = avatarSize,
                            platformId = data.platformId,
                            host = data.key.host,
                            platformIcon = data.platformIcon,
                        )
                    },
                    trailingContent = {
                        trailingContent.invoke(data)
                    },
                    supportingContent = {
                        supportingContent.invoke(data)
                    },
                    colors = colors,
                )
            }.onLoading {
                SegmentedListItem(
                    selected = selected,
                    onClick = {},
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    elevation = elevation,
                    shapes = shapes,
                    content = {
                        Text(text = "Loading...", modifier = Modifier.placeholder(true))
                    },
                    leadingContent = {
                        AvatarComponent(
                            data = null,
                            modifier = Modifier.placeholder(true, shape = CircleShape),
                            size = avatarSize,
                        )
                    },
                    supportingContent = {
                        Text(text = "Loading...", modifier = Modifier.placeholder(true))
                    },
                    colors = colors,
                )
            }.onError { throwable ->
                SegmentedListItem(
                    selected = selected,
                    onClick = {},
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    elevation = elevation,
                    shapes = shapes,
                    content = {
                        if (throwable is UnsupportedPlatformException) {
                            Text(text = throwable.platformId)
                        } else if (throwable is LoginExpiredException) {
                            Text(
                                text =
                                    stringResource(
                                        id = R.string.login_expired,
                                        throwable.accountKey.toString(),
                                    ),
                            )
                        } else {
                            Text(text = stringResource(id = R.string.account_item_error_title))
                        }
                    },
                    leadingContent = {
                        if (throwable is UnsupportedPlatformException) {
                            ThemedIcon(
                                UiIcon.World.toImageVector(),
                                contentDescription = throwable.platformId,
                                color = ThemeIconData.Color.ImperialMagenta,
                                size = avatarSize,
                            )
                        } else {
                            ThemedIcon(
                                FontAwesomeIcons.Solid.FaceSadTear,
                                contentDescription = stringResource(id = R.string.account_item_error_title),
                                color = ThemeIconData.Color.ImperialMagenta,
                                size = avatarSize,
                            )
                        }
                    },
                    supportingContent = {
                        if (throwable is UnsupportedPlatformException) {
                            Text(text = throwable.accountKey?.toString() ?: throwable.platformId)
                        } else if (throwable is LoginExpiredException) {
                            Text(text = throwable.accountKey.toString())
                        } else {
                            Text(text = stringResource(id = R.string.account_item_error_message))
                        }
                    },
                    trailingContent =
                        if (throwable is LoginExpiredException) {
                            {
                                TextButton(
                                    onClick = {
                                        toRelogin(
                                            ReloginTarget(
                                                accountKey = throwable.accountKey,
                                                platformId = throwable.platformId,
                                            ),
                                        )
                                    },
                                ) {
                                    Text(text = stringResource(id = R.string.login_expired_relogin))
                                }
                            }
                        } else {
                            null
                        },
                    colors = colors,
                )
            }
    }
}

@Composable
private fun accountsPresenter() =
    run {
        val cacheItems =
            remember {
                mutableStateListOf<AccountItem>()
            }

        val state =
            remember {
                AccountManagementPresenter()
            }.invoke()

        val webSessionKeys = remember { mutableStateListOf<MicroBlogKey>() }
        state.accounts.onSuccess {
            LaunchedEffect(it) {
                webSessionKeys.clear()
                webSessionKeys.addAll(
                    it
                        .filter { item ->
                            item.account.platformId == INSTAGRAM_PLATFORM_ID || item.account.platformId == LINKEDIN_PLATFORM_ID
                        }.map { item -> item.account.accountKey },
                )
                cacheItems.clear()
                // Instagram and LinkedIn are shown once, as Crux platform rows, not again here.
                cacheItems.addAll(
                    it.filter { item ->
                        item.account.platformId != INSTAGRAM_PLATFORM_ID && item.account.platformId != LINKEDIN_PLATFORM_ID
                    },
                )
            }
        }

        LaunchedEffect(Unit) {
            snapshotFlow { cacheItems.toList() }
                .collect {
                    state.setOrder(cacheItems.map { it.account.accountKey } + webSessionKeys)
                }
        }

        object : AccountManagementPresenter.State by state {
            val currentItems = cacheItems

            fun moveItem(
                from: Any,
                to: Any,
            ) {
                val fromKey = MicroBlogKey.valueOf(from.toString())
                val toKey = MicroBlogKey.valueOf(to.toString())
                val fromIndex = cacheItems.indexOfFirst { it.account.accountKey == fromKey }
                val toIndex = cacheItems.indexOfFirst { it.account.accountKey == toKey }
                if (fromIndex == -1 || toIndex == -1) return
                cacheItems.add(toIndex, cacheItems.removeAt(fromIndex))
            }

            fun deleteItem(accountKey: MicroBlogKey) {
                cacheItems.removeIf { it.account.accountKey == accountKey }
//                state.updateOrder(cacheItems.map { it.account.accountKey })
                state.logout(accountKey)
            }
        }
    }
