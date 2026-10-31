package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareDropdownMenu
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.LocalBottomBarShowing
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.SearchBar
import dev.dimension.flare.ui.component.SearchBarState
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.searchBarPresenter
import dev.dimension.flare.ui.component.searchContent
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.SearchPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.home.ExpressiveAvatarFrame
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchScreen(
    initialQuery: String,
    accountType: AccountType,
    onUserClick: (AccountType, MicroBlogKey) -> Unit,
    onAskAiClick: (String?) -> Unit,
) {
    val state by producePresenter("search_${accountType}_$initialQuery") {
        presenter(
            accountType,
            initialQuery,
        )
    }
    val lazyListState = rememberLazyStaggeredGridState()
    val isBigScreen = isBigScreen()
    val showAskAi = LocalTimelineAppearance.current.aiConfig.agent
    RegisterTabCallback(
        lazyListState = lazyListState,
        onRefresh = {
            state.refresh()
        },
    )
    FlareScaffold(
        topBar = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                SearchBar(
                    state = state,
                    onSearch = {
                        state.commitSearch(it)
                    },
                    trailingIcon =
                        state.searchState.selectedAccount?.let { account ->
                            if (!isBigScreen) {
                                {
                                    state.searchState.accounts.onSuccess { accounts ->
                                        var showMenu by remember { mutableStateOf(false) }
                                        IconButton(
                                            onClick = {
                                                if (accounts.size > 1) {
                                                    showMenu = true
                                                }
                                            },
                                        ) {
                                            ExpressiveAvatarFrame(size = 32.dp) {
                                                AvatarComponent(
                                                    data = account.avatar,
                                                    contentDescription =
                                                        stringResource(
                                                            R.string.select_account_current,
                                                            account.handle.canonical,
                                                        ),
                                                )
                                            }
                                        }
                                        FlareDropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false },
                                        ) {
                                            accounts.forEach { profile ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(profile.handle.canonical)
                                                    },
                                                    leadingIcon = {
                                                        AvatarComponent(
                                                            data = profile.avatar,
                                                            size = 18.dp,
                                                        )
                                                    },
                                                    onClick = {
                                                        state.searchState.setAccount(profile)
                                                        showMenu = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                null
                            }
                        },
                    historyFloatingActionButton =
                        if (showAskAi) {
                            {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        onAskAiClick(
                                            state.queryTextState
                                                .text
                                                .toString()
                                                .trim()
                                                .takeIf { it.isNotEmpty() },
                                        )
                                    },
                                    icon = {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Robot,
                                            contentDescription = null,
                                        )
                                    },
                                    text = {
                                        Text(text = stringResource(id = R.string.ask_ai))
                                    },
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        },
    ) { contentPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val extraBottom = if (LocalBottomBarShowing.current) 32.dp else 0.dp
        val combinedContentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + extraBottom,
            )
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.refreshing,
            onRefresh = state::refresh,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    state = lazyListState,
                    contentPadding = combinedContentPadding,
                ) {
                    if (isBigScreen) {
                        state.searchState.accounts.onSuccess { accounts ->
                            if (accounts.size > 1) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                ) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    ) {
                                        items(accounts.size) { index ->
                                            val profile = accounts[index]
                                            val isSelected = state.searchState.selectedAccount?.key == profile.key
                                            ToggleButton(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    state.searchState.setAccount(profile)
                                                },
                                                colors =
                                                    ToggleButtonDefaults.colors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                                    ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            ) {
                                                AvatarComponent(
                                                    data = profile.avatar,
                                                    size = 18.dp,
                                                )
                                                Text(
                                                    text = profile.handle.canonical,
                                                    style =
                                                        MaterialTheme.typography.labelLarge.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        ),
                                                    modifier = Modifier.padding(start = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    searchContent(
                        searchUsers = state.searchState.users,
                        searchStatus = state.searchState.status,
                        toUser = { key ->
                            state.searchState.selectedAccount?.let { account ->
                                onUserClick(AccountType.Specific(account.key), key)
                            }
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    initialQuery: String,
) = run {
    val searchBarState = searchBarPresenter(initialQuery)
    val searchState =
        remember(initialQuery, accountType) {
            SearchPresenter(accountType = accountType, initialQuery)
        }.invoke()

    object : SearchBarState by searchBarState {
        val searchState = searchState

        val refreshing =
            searchState.users.isRefreshing ||
                searchState.status.isRefreshing

        fun refresh() {
            searchState.search(queryTextState.text.toString())
        }

        fun commitSearch(new: String) {
            searchBarState.setQuery(new)
            searchBarState.addSearchHistory(new)
            searchState.search(new)
        }
    }
}
