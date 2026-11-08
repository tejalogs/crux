package org.crux.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.common.PagingState
import org.crux.common.isRefreshing
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.common.itemsIndexed
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.RefreshContainer
import org.crux.ui.component.listCard
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiState
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.profile.FansPresenter
import org.crux.ui.presenter.profile.FollowingPresenter
import org.crux.ui.presenter.profile.UserListPresenter
import org.crux.ui.screen.settings.AccountItem
import org.crux.ui.theme.screenHorizontalPadding

@Composable
internal fun FollowingScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter(
        key = "FollowingScreen$userKey",
    ) {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType, userKey) {
                FollowingPresenter(accountType, userKey)
            }.invoke()
        object : UserListPresenter.State by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }

    UserListScreen(
        data = state.listState,
        title = {
            Text(text = stringResource(R.string.following_title))
        },
        onBack = onBack,
        isRefreshing = state.listState.isRefreshing,
        refresh = state::refresh,
        onUserClick = onUserClick,
    )
}

@Composable
internal fun FansScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter(
        key = "FansScreen$userKey",
    ) {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType, userKey) {
                FansPresenter(accountType, userKey)
            }.invoke()
        object : UserListPresenter.State by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }

    UserListScreen(
        data = state.listState,
        title = {
            Text(text = stringResource(R.string.fans_title))
        },
        onBack = onBack,
        isRefreshing = state.listState.isRefreshing,
        refresh = state::refresh,
        onUserClick = onUserClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserListScreen(
    data: PagingState<UiProfile>,
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    isRefreshing: Boolean,
    refresh: () -> Unit,
    onUserClick: (MicroBlogKey) -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CruxScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = title,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = isRefreshing,
            onRefresh = refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        data,
                        loadingContent = { index, itemCount ->
                            AccountItem(
                                modifier =
                                    Modifier
                                        .listCard(
                                            index = index,
                                            totalCount = itemCount,
                                        ),
                                userState = UiState.Loading(),
                                onClick = { onUserClick(it) },
                            )
                        },
                    ) { index, itemCount, it ->
                        AccountItem(
                            modifier =
                                Modifier
                                    .listCard(
                                        index = index,
                                        totalCount = itemCount,
                                    ),
                            userState = UiState.Success(it),
                            onClick = { onUserClick(it) },
                        )
                    }
                }
            },
        )
    }
}
