package org.crux.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.TableCells
import compose.icons.fontawesomeicons.solid.TableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import moe.tlaster.nestedscrollview.VerticalNestedScrollView
import moe.tlaster.nestedscrollview.rememberNestedScrollViewState
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.common.PagingState
import org.crux.common.refreshSuspend
import org.crux.data.model.TimelineDisplayMode
import org.crux.data.model.VideoAutoplay
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.common.items
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.ErrorContent
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.ProfileHeader
import org.crux.ui.component.ProfileHeaderLoading
import org.crux.ui.component.ProfileMenu
import org.crux.ui.component.ProfileTabsLoadingPlaceholder
import org.crux.ui.component.RefreshContainer
import org.crux.ui.component.RichText
import org.crux.ui.component.TabRowIndicator
import org.crux.ui.component.placeholder
import org.crux.ui.component.platform.adaptiveSideContentSize
import org.crux.ui.component.platform.isBigScreen
import org.crux.ui.component.status.AdaptiveCard
import org.crux.ui.component.status.LazyStatusVerticalStaggeredGrid
import org.crux.ui.component.status.MediaItem
import org.crux.ui.component.status.StatusPlaceholder
import org.crux.ui.component.status.status
import org.crux.ui.component.status.statusLoadingPlaceholders
import org.crux.ui.haptic.CruxHapticEvent
import org.crux.ui.haptic.rememberFlareHapticFeedback
import org.crux.ui.model.ClickContext
import org.crux.ui.model.UiMedia
import org.crux.ui.model.UiRelation
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.asText
import org.crux.ui.model.contentPostOrNull
import org.crux.ui.model.map
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.model.takeSuccess
import org.crux.ui.model.takeSuccessOr
import org.crux.ui.presenter.home.TimelinePresenter
import org.crux.ui.presenter.home.TimelineState
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.profile.ProfileMedia
import org.crux.ui.presenter.profile.ProfileMediaPresenter
import org.crux.ui.presenter.profile.ProfileMediaState
import org.crux.ui.presenter.profile.ProfilePresenter
import org.crux.ui.presenter.profile.ProfileState
import org.crux.ui.presenter.profile.ProfileWithUserNameAndHostPresenter
import org.crux.ui.screen.home.RegisterTabCallback
import org.crux.ui.theme.screenHorizontalPadding
import kotlin.math.max

@Composable
internal fun ProfileWithUserNameAndHostDeeplinkRoute(
    userName: String,
    host: String,
    accountType: AccountType,
    toEditAccountList: (userKey: MicroBlogKey) -> Unit,
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit,
    toStartMessage: (MicroBlogKey) -> Unit,
    onFollowListClick: (userKey: MicroBlogKey) -> Unit,
    onFansListClick: (userKey: MicroBlogKey) -> Unit,
    onProfileInsightClick: (userKey: MicroBlogKey) -> Unit,
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    onMediaClick: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
) {
    val state by producePresenter(key = "acct_${accountType}_$userName@$host") {
        profileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountType = accountType,
        )
    }
    state
        .onSuccess {
            ProfileScreen(
                accountType = accountType,
                toEditAccountList = {
                    toEditAccountList(it.key)
                },
                toSearchUserUsingAccount = toSearchUserUsingAccount,
                toStartMessage = toStartMessage,
                onFollowListClick = onFollowListClick,
                onFansListClick = onFansListClick,
                onProfileInsightClick = onProfileInsightClick,
                userKey = it.key,
                onBack = onBack,
                showBackButton = showBackButton,
                onMediaClick = onMediaClick,
            )
        }.onLoading {
            ProfileLoadingScreen(
                onBack = onBack,
            )
        }.onError {
            ProfileErrorScreen(
                onBack = onBack,
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileErrorScreen(onBack: () -> Unit) {
    CruxScaffold(
        topBar = {
            CruxTopAppBar(
                title = {
                    Text(text = "Error")
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Error")
        }
    }
}

@Composable
private fun ProfileLoadingScreen(onBack: () -> Unit) {
    CruxScaffold {
        LazyColumn(
            contentPadding = it,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ProfileHeaderLoading(withStatusBarHeight = false)
            }
            items(5) {
                StatusPlaceholder(
                    modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                )
            }
        }
    }
}

@Composable
private fun profileWithUserNameAndHostPresenter(
    userName: String,
    host: String,
    accountType: AccountType,
) = run {
    remember(
        userName,
        host,
    ) {
        ProfileWithUserNameAndHostPresenter(
            userName = userName,
            host = host,
            accountType = accountType,
        )
    }.invoke().user
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
internal fun ProfileScreen(
    accountType: AccountType,
    toEditAccountList: () -> Unit,
    toSearchUserUsingAccount: (String, MicroBlogKey) -> Unit,
    toStartMessage: (MicroBlogKey) -> Unit,
    onFollowListClick: (userKey: MicroBlogKey) -> Unit,
    onFansListClick: (userKey: MicroBlogKey) -> Unit,
    onProfileInsightClick: (userKey: MicroBlogKey) -> Unit,
    userKey: MicroBlogKey? = null,
    onBack: () -> Unit = {},
    showBackButton: Boolean = true,
    onMediaClick: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
) {
    val haptics = rememberFlareHapticFeedback(LocalGlobalAppearance.current.hapticsEnabled)
    val state by producePresenter(key = "${accountType}_$userKey") {
        profilePresenter(userKey = userKey, accountType = accountType)
    }
    val nestedScrollState = rememberNestedScrollViewState()
    var showBlockedProfileContent by remember(accountType, userKey) { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var activeTabRefresh by remember { mutableStateOf<suspend () -> Unit>({}) }
    val isBlockedProfile =
        state.state
            .relationState
            .takeSuccessOr(UiRelation())
            .blocking
    LaunchedEffect(isBlockedProfile) {
        if (!isBlockedProfile) {
            showBlockedProfileContent = false
        }
    }
    val windowSize =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize
                .toSize()
                .toDpSize()
        }
    val profileTabHeight =
        with(LocalDensity.current) {
            MaterialTheme.typography.bodyLarge.lineHeight
                .toDp()
        } + 16.dp
    val bigScreen = isBigScreen()
    val scrollBehavior =
        if (bigScreen) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        } else {
            TopAppBarDefaults.pinnedScrollBehavior()
        }
    val scope = rememberCoroutineScope()
    val currentAppearance = LocalTimelineAppearance.current
    val userProfile = state.state.userState.takeSuccess()
    val isPhotoCentricProfile =
        remember(userProfile?.key) {
            val host = userProfile?.key?.host.orEmpty()
            val handleHost = userProfile?.handle?.host.orEmpty()
            val canonical = userProfile?.handle?.canonical.orEmpty()
            host.contains("pixelfed", ignoreCase = true) ||
                handleHost.contains("pixelfed", ignoreCase = true) ||
                canonical.contains("pixelfed", ignoreCase = true) ||
                host.contains("instagram", ignoreCase = true)
        }
    var galleryOverride by remember(userProfile?.key, isPhotoCentricProfile) {
        mutableStateOf<Boolean?>(if (isPhotoCentricProfile) true else null)
    }
    val effectiveGallery = galleryOverride ?: (currentAppearance.timelineDisplayMode == TimelineDisplayMode.Gallery)
    val effectiveDisplayMode =
        if (effectiveGallery) {
            TimelineDisplayMode.Gallery
        } else {
            if (currentAppearance.timelineDisplayMode == TimelineDisplayMode.Gallery) {
                TimelineDisplayMode.Card
            } else {
                currentAppearance.timelineDisplayMode
            }
        }
    CruxScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets =
            ScaffoldDefaults
                .contentWindowInsets
                .exclude(WindowInsets.statusBars),
        topBar = {
            val titleAlpha by remember {
                derivedStateOf {
                    if (nestedScrollState.offset == nestedScrollState.maxOffset ||
                        bigScreen
                    ) {
                        1f
                    } else {
                        max(
                            0f,
                            nestedScrollState.offset / nestedScrollState.maxOffset,
                        )
                    }
                }
            }
            Box {
                if (!bigScreen) {
                    Column(
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    alpha = titleAlpha
                                },
                    ) {
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .windowInsetsTopHeight(WindowInsets.statusBars)
                                    .background(MaterialTheme.colorScheme.background),
                        )
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(MaterialTheme.colorScheme.background),
                        )
                    }
                }
                CruxTopAppBar(
                    title = {
                        state.state.userState.onSuccess {
                            RichText(
                                text = it.name,
                                modifier =
                                    Modifier.graphicsLayer {
                                        alpha = titleAlpha
                                    },
                                maxLines = 1,
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
//                    windowInsets = if (bigScreen) {
//                        TopAppBarDefaults.windowInsets
//                    } else {
//                        WindowInsets()
//                    },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (showBackButton) {
                            BackButton(onBack = onBack)
                        }
                    },
                    actions = {
                        Row(
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.shapes.medium,
                                    ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    galleryOverride = !effectiveGallery
                                },
                            ) {
                                FAIcon(
                                    imageVector =
                                        if (effectiveGallery) {
                                            FontAwesomeIcons.Solid.TableList
                                        } else {
                                            FontAwesomeIcons.Solid.TableCells
                                        },
                                    contentDescription =
                                        if (effectiveGallery) {
                                            "Switch to list view"
                                        } else {
                                            "Switch to photo grid"
                                        },
                                )
                            }
                            if (!bigScreen) {
                                ProfileInsightAction(
                                    profileState = state.state,
                                    onClick = onProfileInsightClick,
                                )
                                ProfileMenu(
                                    profileState = state.state,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) {
        Row {
            if (bigScreen) {
                val width by adaptiveSideContentSize()
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .width(width)
                            .padding(horizontal = 16.dp)
                            .padding(it),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptiveCard {
                        ProfileHeader(
                            state = state.state,
                            menu = {
                                ProfileInsightAction(
                                    profileState = state.state,
                                    onClick = onProfileInsightClick,
                                )
                                ProfileMenu(
                                    profileState = state.state,
                                    modifier =
                                        Modifier
                                            .padding(8.dp)
                                            .padding(top = 4.dp),
                                )
                            },
                            onAvatarClick = {
                                state.state.userState.onSuccess {
//                                    onMediaClick(it.avatar)
                                }
                            },
                            onBannerClick = {
                                state.state.userState.onSuccess {
//                                    it.banner?.let { it1 -> onMediaClick(it1) }
                                }
                            },
                            isBigScreen = true,
                            onFollowListClick = onFollowListClick,
                            onFansListClick = onFansListClick,
                        )
                    }
                }
            }
            RefreshContainer(
                modifier = Modifier.fillMaxSize(),
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        try {
                            activeTabRefresh()
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                isRefreshing = isRefreshing,
                indicatorPadding = it,
                content = {
                    val content = @Composable {
                        if (isBlockedProfile && !showBlockedProfileContent) {
                            BlockedProfileGate(
                                onShow = {
                                    showBlockedProfileContent = true
                                },
                            )
                        } else {
                            state.tabs.onLoading {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ProfileTabsLoadingPlaceholder(
                                        tabHeight = profileTabHeight,
                                        verticalPadding = 0.dp,
                                    )
                                    CompositionLocalProvider(
                                        LocalTimelineAppearance provides
                                            currentAppearance.copy(
                                                timelineDisplayMode = effectiveDisplayMode,
                                            ),
                                    ) {
                                        LazyStatusVerticalStaggeredGrid(
                                            contentPadding =
                                                PaddingValues(
                                                    top = 8.dp,
                                                    bottom = 8.dp + it.calculateBottomPadding(),
                                                ),
                                            allowGalleryMode = effectiveGallery,
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            statusLoadingPlaceholders()
                                        }
                                    }
                                }
                            }
                            state.tabs.onError { error ->
                                ErrorContent(
                                    error = error,
                                    onRetry = state.state::retryTabs,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            state.pagerState.onSuccess { pagerState ->
                                state.tabs.onSuccess { tabs ->
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (tabs.size > 1) {
                                            LazyRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                itemsIndexed(tabs) { index, profileTab ->
                                                    val isSelected = pagerState.currentPage == index
                                                    ToggleButton(
                                                        checked = isSelected,
                                                        onCheckedChange = {
                                                            haptics.perform(CruxHapticEvent.SelectionChange)
                                                            scope.launch {
                                                                pagerState.animateScrollToPage(index)
                                                            }
                                                        },
                                                        colors =
                                                            ToggleButtonDefaults.colors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                                                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                                            ),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    ) {
                                                        org.crux.ui.component.Text(
                                                            text = profileTab.name.asText(),
                                                            style =
                                                                MaterialTheme.typography.labelLarge.copy(
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        HorizontalPager(
                                            state = pagerState,
                                            key = { index ->
                                                tabs.getOrNull(index)?.id ?: "profile_tab_$index"
                                            },
                                        ) { index ->
                                            val tab = tabs[index]
                                            when (tab) {
                                                is ProfileTabItem.Media -> {
                                                    val tabState = rememberProfileMediaTabState(tab)
                                                    val data = tabState.mediaState
                                                    val latestData = rememberUpdatedState(data)
                                                    if (index == pagerState.currentPage) {
                                                        LaunchedEffect(tab.id, index) {
                                                            activeTabRefresh = {
                                                                latestData.value.refreshSuspend()
                                                            }
                                                        }
                                                    }
                                                    val gridState = rememberLazyStaggeredGridState()
                                                    ProfileMediaTab(
                                                        mediaState = data,
                                                        state = gridState,
                                                        onItemClicked = { statusKey, index, preview ->
                                                            onMediaClick(statusKey, index, preview)
                                                        },
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                }

                                                is ProfileTabItem.Timeline -> {
                                                    val tabState = rememberProfileTimelineTabState(tab)
                                                    val data = tabState.listState
                                                    val latestData = rememberUpdatedState(data)
                                                    if (index == pagerState.currentPage) {
                                                        LaunchedEffect(tab.id, index) {
                                                            activeTabRefresh = {
                                                                latestData.value.refreshSuspend()
                                                            }
                                                        }
                                                    }
                                                    val listState = rememberLazyStaggeredGridState()
                                                    if (index == pagerState.currentPage) {
                                                        RegisterTabCallback(
                                                            lazyListState = listState,
                                                            onRefresh = {
                                                                scope.launch {
                                                                    activeTabRefresh()
                                                                }
                                                            },
                                                        )
                                                    }
                                                    CompositionLocalProvider(
                                                        LocalTimelineAppearance provides
                                                            currentAppearance.copy(
                                                                timelineDisplayMode = effectiveDisplayMode,
                                                            ),
                                                    ) {
                                                        LazyStatusVerticalStaggeredGrid(
                                                            state = listState,
                                                            allowGalleryMode = effectiveGallery,
                                                            contentPadding =
                                                                PaddingValues(
                                                                    top = 8.dp,
                                                                    bottom = 8.dp + it.calculateBottomPadding(),
                                                                ),
                                                            modifier = Modifier.fillMaxSize(),
                                                        ) {
                                                            status(data)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (bigScreen) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(
                                        top = it.calculateTopPadding(),
                                    ),
                        ) {
                            content.invoke()
                        }
                    } else {
                        VerticalNestedScrollView(
                            state = nestedScrollState,
                            contentTopPadding = it.calculateTopPadding(),
                            header = {
                                Column {
                                    ProfileHeader(
                                        state = state.state,
                                        menu = {
                                            Spacer(modifier = Modifier.width(screenHorizontalPadding))
                                        },
                                        onAvatarClick = {
                                            state.state.userState.onSuccess {
//                                                    onMediaClick(it.avatar)
                                            }
                                        },
                                        onBannerClick = {
                                            state.state.userState.onSuccess {
//                                                    it.banner?.let { it1 -> onMediaClick(it1) }
                                            }
                                        },
                                        isBigScreen = false,
                                        onFollowListClick = onFollowListClick,
                                        onFansListClick = onFansListClick,
                                    )
                                }
                            },
                            content = {
                                content.invoke()
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BlockedProfileGate(
    onShow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = screenHorizontalPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(id = R.string.profile_blocked_gate_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(id = R.string.profile_blocked_gate_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onShow) {
            Text(text = stringResource(id = R.string.profile_blocked_gate_show))
        }
    }
}

@Composable
private fun ProfileInsightAction(
    profileState: ProfileState,
    onClick: (MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalTimelineAppearance.current.aiConfig.agent) {
        return
    }
    profileState.userState.onSuccess { profile ->
        IconButton(
            onClick = {
                onClick(profile.key)
            },
            modifier = modifier,
        ) {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Robot,
                contentDescription = stringResource(id = R.string.profile_insight_title),
            )
        }
    }
}

@Composable
private fun ProfileMediaTab(
    mediaState: PagingState<ProfileMedia>,
    state: LazyStaggeredGridState,
    onItemClicked: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBigScreen = isBigScreen()
    val uriHandler = LocalUriHandler.current
    CompositionLocalProvider(
        LocalTimelineAppearance provides
            LocalTimelineAppearance.current.copy(
                videoAutoplay = VideoAutoplay.NEVER,
            ),
    ) {
        val size =
            if (isBigScreen) {
                240.dp
            } else {
                120.dp
            }
        LazyVerticalStaggeredGrid(
            modifier = modifier,
            state = state,
            columns = StaggeredGridCells.Adaptive(size),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = screenHorizontalPadding),
        ) {
            items(
                mediaState,
                key = {
                    it.key
                },
                loadingContent = {
                    Box(
                        modifier =
                            Modifier
                                .size(120.dp)
                                .placeholder(true),
                    )
                },
            ) { item ->
                val media = item.media
                BoxWithConstraints {
                    MediaItem(
                        media = media,
                        showCountdown = false,
                        modifier =
                            Modifier
                                .heightIn(max = maxWidth * 21 / 9)
                                .clip(MaterialTheme.shapes.medium)
                                .clipToBounds()
                                .clickable {
                                    item.status.contentPostOrNull()?.let { content ->
                                        when (content.mediaClickPolicy) {
                                            UiTimelineV2.Post.MediaClickPolicy.OpenStatusMedia -> {
                                                onItemClicked(
                                                    item.statusKey,
                                                    item.index,
                                                    when (media) {
                                                        is UiMedia.Image -> media.previewUrl
                                                        is UiMedia.Video -> media.thumbnailUrl
                                                        is UiMedia.Gif -> media.previewUrl
                                                        else -> null
                                                    },
                                                )
                                            }

                                            UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent -> {
                                                content.onClicked.invoke(
                                                    ClickContext(launcher = uriHandler::openUri),
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

@Composable
private fun rememberProfileTimelineTabState(tab: ProfileTabItem.Timeline): TimelineState {
    val state by producePresenter(key = "profile_timeline_${tab.id}") {
        remember(tab.id) { tab.presenter }.invoke()
    }
    return state
}

@Composable
private fun rememberProfileMediaTabState(tab: ProfileTabItem.Media): ProfileMediaState {
    val state by producePresenter(key = "profile_media_${tab.id}") {
        remember(tab.id) { tab.presenter }.invoke()
    }
    return state
}

@Composable
private fun profilePresenter(
    userKey: MicroBlogKey?,
    accountType: AccountType,
) = run {
    val state =
        remember(userKey) {
            ProfilePresenter(
                userKey = userKey,
                accountType = accountType,
            )
        }.invoke()

    val tabs =
        state.tabs.map {
            it.map {
                when (it) {
                    is ProfileState.Tab.Timeline -> {
                        ProfileTabItem.Timeline(
                            id = it.id,
                            name = it.name,
                            presenter = it.presenter,
                        )
                    }

                    is ProfileState.Tab.Media -> {
                        ProfileTabItem.Media(
                            id = it.id,
                            name = it.name,
                            presenter = it.presenter,
                        )
                    }
                }
            }
        }

    val pagerState =
        state.tabs.map { tabs ->
            rememberPagerState { tabs.size }
        }

    object {
        val state = state
        val tabs = tabs
        val pagerState = pagerState
    }
}

private sealed interface ProfileTabItem {
    val id: String
    val name: UiStrings

    data class Timeline(
        override val id: String,
        override val name: UiStrings,
        val presenter: TimelinePresenter,
    ) : ProfileTabItem

    data class Media(
        override val id: String,
        override val name: UiStrings,
        val presenter: ProfileMediaPresenter,
    ) : ProfileTabItem
}
