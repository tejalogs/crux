package org.crux.ui.screen.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.CaretDown
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Sliders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.TimelineDisplayMode
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.resolveTimelineAppearance
import org.crux.ui.component.AvatarComponent
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalBottomBarAccessory
import org.crux.ui.component.LocalBottomBarShowing
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.TabIcon
import org.crux.ui.component.platform.LocalWindowSizeClass
import org.crux.ui.component.platform.WindowSizeClass
import org.crux.ui.component.platform.isBigScreen
import org.crux.ui.component.platform.isCompatScreen
import org.crux.ui.model.map
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.model.takeSuccess
import org.crux.ui.presenter.HomeTimelineWithTabsPresenter
import org.crux.ui.presenter.home.DeepLinkPresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.route.Route
import org.crux.ui.route.Router
import org.crux.ui.theme.isLightTheme
import org.crux.ui.component.Text as CruxText

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
internal fun HomeTimelineScreen(
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
    uriHandler: UriHandler,
    toReadingList: () -> Unit = {},
    onNavigate: (Route) -> Unit = {},
) {
    val state by producePresenter(key = "home_timeline") {
        timelinePresenter()
    }
    val scope = rememberCoroutineScope()

    if (LocalGlobalAppearance.current.deckMode && isBigScreen()) {
        LazyRow(
            modifier =
                Modifier
                    .consumeWindowInsets(
                        WindowInsets.systemBars
                            .union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal),
                    ),
            contentPadding =
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
        ) {
            state.tabState.onSuccess { tabs ->
                items(tabs) { tab ->
                    val tabItemState by producePresenter("tabItemState_${tab.id}") {
                        val backStack =
                            remember {
                                mutableStateListOf<Route>(Route.DeckTimeline(tab.id))
                            }
                        val state =
                            DeepLinkPresenter(
                                onRoute = {
                                    Route.from(it)?.let { route ->
                                        backStack.add(route)
                                    }
                                },
                                onLink = {
                                    uriHandler.openUri(it)
                                },
                            ).invoke()
                        object : DeepLinkPresenter.State by state {
                            val backStack = backStack
                        }
                    }
                    CompositionLocalProvider(
                        LocalUriHandler provides
                            remember(tabItemState) {
                                object : UriHandler {
                                    override fun openUri(uri: String) {
                                        tabItemState.handle(uri)
                                    }
                                }
                            },
                        LocalWindowSizeClass provides WindowSizeClass.Compact,
                    ) {
                        Row {
                            Router(
                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .width(360.dp),
                                backStack = tabItemState.backStack,
                                openDrawer = {
                                    toQuickMenu.invoke()
                                },
                                navigate = {
                                    when (it) {
                                        is Route.TabSettings -> {
                                            toTabSettings.invoke()
                                        }

                                        is Route.Settings.Accounts -> {
                                            toLogin.invoke()
                                        }

                                        else -> {
                                            tabItemState.backStack.add(it)
                                        }
                                    }
                                },
                                onBack = {
                                    if (tabItemState.backStack.size > 1) {
                                        tabItemState.backStack.removeAt(tabItemState.backStack.lastIndex)
                                    }
                                },
                            )
                            if (tab != tabs.last()) {
                                VerticalDivider(
                                    modifier =
                                        Modifier
                                            .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        val topAppBarScrollBehavior =
            if (LocalGlobalAppearance.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
                TopAppBarDefaults.pinnedScrollBehavior()
            } else {
                TopAppBarDefaults.enterAlwaysScrollBehavior()
            }
        // Phones (bottom dock visible): the Home dock item expands into the feed tabs and the top
        // bar goes away. Tablets/rail layouts keep the top tab strip.
        val bottomBarAccessory = LocalBottomBarAccessory.current
        val useBottomTabs = LocalBottomBarShowing.current && bottomBarAccessory != null
        val drawerButton: @Composable () -> Unit = {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                toQuickMenu.invoke()
                            },
                            onLongClick = bottomBarAccessory?.onLongClick,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Bars,
                    contentDescription = stringResource(R.string.open_navigation_menu),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        CruxScaffold(
            topBar = topBar@{
                if (useBottomTabs) return@topBar
                val appearance = LocalTimelineAppearance.current
                val backgroundColor = MaterialTheme.colorScheme.background
                val plainColor =
                    if (isLightTheme() && isCompatScreen()) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        backgroundColor
                    }
                val tabs = state.tabState.takeSuccess()
                val tabBackgroundColors =
                    remember(tabs, appearance, plainColor, backgroundColor) {
                        tabs
                            ?.map { tab ->
                                when (tab.resolveTimelineAppearance(appearance).timelineDisplayMode) {
                                    TimelineDisplayMode.Plain -> plainColor
                                    else -> backgroundColor
                                }
                            }.orEmpty()
                    }
                val pagerState = state.pagerState.takeSuccess()
                val color by remember(pagerState, tabBackgroundColors, backgroundColor) {
                    derivedStateOf {
                        if (pagerState == null || tabBackgroundColors.isEmpty()) {
                            backgroundColor
                        } else {
                            val pagePosition =
                                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                    .coerceIn(0f, tabBackgroundColors.lastIndex.toFloat())
                            val startIndex = pagePosition.toInt().coerceIn(0, tabBackgroundColors.lastIndex)
                            val endIndex = (startIndex + 1).coerceAtMost(tabBackgroundColors.lastIndex)
                            val fraction = (pagePosition - startIndex).coerceIn(0f, 1f)
                            lerp(
                                tabBackgroundColors[startIndex],
                                tabBackgroundColors[endIndex],
                                fraction,
                            )
                        }
                    }
                }
                CruxTopAppBar(
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = color,
                            scrolledContainerColor = color,
                            actionIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    title = {
                        state.pagerState.onSuccess { pagerState ->
                            state.tabState.onSuccess { tabs ->
                                if (tabs.any()) {
                                    if (isCompatScreen() && tabs.size > MAX_SCROLLABLE_HOME_TABS) {
                                        HomeTabDropdown(
                                            tabs = tabs,
                                            selectedTabIndex =
                                                minOf(
                                                    pagerState.currentPage,
                                                    tabs.lastIndex,
                                                ),
                                            onTabSelected = { index ->
                                                scope.launch {
                                                    pagerState.scrollToPage(index)
                                                }
                                            },
                                        )
                                    } else {
                                        ExpressiveHomeTabStrip(
                                            tabs = tabs,
                                            selectedTabIndex =
                                                minOf(
                                                    pagerState.currentPage,
                                                    tabs.lastIndex,
                                                ),
                                            onTabSelected = { index ->
                                                scope.launch {
                                                    pagerState.switchToTab(tabs, index)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    actions = {
                        FilledTonalIconButton(
                            onClick = {
                                toTabSettings.invoke()
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Sliders,
                                contentDescription = stringResource(R.string.edit_tab_title),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            },
            modifier = if (useBottomTabs) Modifier else Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        ) { contentPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                state.pagerState.onSuccess { pagerState ->
                    state.tabState.onSuccess { tabState ->
                        if (tabState.isEmpty()) {
                            HomeEmptyState(
                                onManagePlatforms = toLogin,
                                modifier = Modifier.fillMaxSize().padding(contentPadding),
                                topStart = if (useBottomTabs) drawerButton else null,
                            )
                        }
                        LaunchedEffect(pagerState.currentPage >= tabState.size) {
                            if (pagerState.currentPage >= tabState.size) {
                                scope.launch {
                                    pagerState.scrollToPage(0)
                                }
                            }
                        }

                        val currentIsLiveWeb = tabState.getOrNull(pagerState.currentPage)?.liveWebSite() != null
                        // The dock steps aside on a live tab's immersive page (Reels, Stories, messages).
                        val dockOnImmersivePage =
                            useBottomTabs && currentIsLiveWeb && bottomBarAccessory?.onImmersiveWebPage == true
                        val dockHiddenForImmersive = dockOnImmersivePage && bottomBarAccessory?.immersiveDockHidden == true
                        val dockHiddenByScroll =
                            useBottomTabs && currentIsLiveWeb && !dockOnImmersivePage && bottomBarAccessory?.webDockExpanded == false
                        val immersiveKind =
                            if (dockOnImmersivePage) dockImmersiveKind(bottomBarAccessory?.immersivePageUrl) else null
                        // Registered before the pager, so the live tab's own Back (page history) still
                        // goes first; once the page cannot go back, Back brings the hidden dock back.
                        BackHandler(enabled = dockHiddenForImmersive) {
                            bottomBarAccessory?.peekDock()
                        }
                        BackHandler(enabled = dockHiddenByScroll) {
                            bottomBarAccessory?.webDockExpanded = true
                        }
                        val layoutDirection = LocalLayoutDirection.current
                        val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        // Not while typing: the handle would sit on the message field above the keyboard.
                        val imeShown = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                        val showImmersiveHandle = dockHiddenForImmersive && !imeShown
                        val immersiveBaseBottom =
                            immersiveBaseBottomPadding(
                                scaffoldBottom = contentPadding.calculateBottomPadding(),
                                navigationBarBottom = navigationBarBottom,
                            )
                        HorizontalPager(
                            state = pagerState,
                            // Live sites have their own horizontal gestures; dock tabs handle switching.
                            userScrollEnabled = !useBottomTabs && tabState.getOrNull(pagerState.currentPage)?.liveWebSite() == null,
                            key = { index ->
                                tabState.getOrNull(index)?.id ?: "timeline_$index"
                            },
                        ) { index ->
                            val item = tabState.getOrNull(index)
                            val liveWebSite = item?.liveWebSite()
                            if (liveWebSite != null) {
                                // On an immersive page the hidden dock leaves a strip for its handle below the
                                // page. A dock brought back floats over Reels and Stories (one WebView size) and
                                // pushes a message thread up, so the reply field is never under it.
                                val liveTabPadding =
                                    if (immersiveKind != null && pagerState.currentPage == index) {
                                        PaddingValues(
                                            start = contentPadding.calculateStartPadding(layoutDirection),
                                            top = contentPadding.calculateTopPadding(),
                                            end = contentPadding.calculateEndPadding(layoutDirection),
                                            bottom =
                                                liveTabBottomPadding(
                                                    scaffoldBottom = contentPadding.calculateBottomPadding(),
                                                    navigationBarBottom = navigationBarBottom,
                                                    kind = immersiveKind,
                                                    dockPeeked = bottomBarAccessory?.immersiveDockPeeked == true,
                                                    handleReserve = if (imeShown) 0.dp else IMMERSIVE_HANDLE_TOUCH_HEIGHT,
                                                ),
                                        )
                                    } else if (useBottomTabs) {
                                        // Keep WebView height constant while the floating dock hides/shows on scroll,
                                        // so scrolling never resizes the WebView or triggers SPA timeline relayouts.
                                        PaddingValues(
                                            start = contentPadding.calculateStartPadding(layoutDirection),
                                            top = contentPadding.calculateTopPadding(),
                                            end = contentPadding.calculateEndPadding(layoutDirection),
                                            bottom = immersiveBaseBottom,
                                        )
                                    } else {
                                        contentPadding
                                    }
                                LiveWebTab(
                                    site = liveWebSite,
                                    isCurrentlyVisible = pagerState.currentPage == index,
                                    contentPadding = liveTabPadding,
                                )
                            } else if (item != null) {
                                val rssTarget = org.crux.ui.screen.rss.nativeRssTabTargetForId(item.id)
                                val feedUrl = (rssTarget as? org.crux.ui.screen.rss.NativeRssTabTarget.SingleFeed)?.feedUrl
                                org.crux.ui.screen.rss.NativeRssFeedList(
                                    feedUrl = feedUrl,
                                    contentPadding = contentPadding,
                                    isCurrentlyVisible = pagerState.currentPage == index,
                                    onOpenArticle = { detail -> onNavigate(detail) },
                                    onManageFeeds = { onNavigate(Route.Rss.Sources) },
                                    onAddFeed = { onNavigate(Route.Rss.Create()) },
                                )
                            }
                        }

                        if (useBottomTabs && bottomBarAccessory != null && tabState.any()) {
                            val dockTabs: @Composable (() -> Unit) -> Unit = { onReselect ->
                                ExpressiveHomeDockTabs(
                                    tabs = tabState,
                                    selectedTabIndex = minOf(pagerState.currentPage, tabState.lastIndex),
                                    onTabSelected = { index ->
                                        scope.launch {
                                            pagerState.switchToTab(tabState, index)
                                        }
                                    },
                                    onReselect = onReselect,
                                    onEditTabs = toTabSettings,
                                    drawerButton = drawerButton,
                                    // Platforms are connected in onboarding / the side panel, not via a login pill.
                                    loginButton = null,
                                    smartReplyAction = bottomBarAccessory.smartReply,
                                    bookmarkAction = bottomBarAccessory.bookmarkPost,
                                    onOpenBookmarks = toReadingList,
                                    onCollapseDock =
                                        if (currentIsLiveWeb) {
                                            { bottomBarAccessory.webDockExpanded = false }
                                        } else {
                                            null
                                        },
                                )
                            }
                            val latestDockTabs by rememberUpdatedState(dockTabs)
                            DisposableEffect(bottomBarAccessory) {
                                val content: @Composable (() -> Unit) -> Unit = { latestDockTabs(it) }
                                bottomBarAccessory.content = content
                                onDispose {
                                    if (bottomBarAccessory.content === content) {
                                        bottomBarAccessory.content = null
                                    }
                                }
                            }
                        }
                        if (showImmersiveHandle && bottomBarAccessory != null) {
                            // In the strip liveTabBottomPadding reserves, just above the navigation bar.
                            ImmersiveDockHandle(
                                onReveal = bottomBarAccessory::peekDock,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = immersiveBaseBottom),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTabDropdown(
    tabs: List<UiTimelineTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTab = tabs[selectedTabIndex]

    Box {
        AnimatedContent(selectedTab) { selectedTab ->
            Row(
                modifier =
                    Modifier
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabIcon(selectedTab)
                CruxText(selectedTab.title, style = MaterialTheme.typography.bodyLarge)
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.CaretDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        CruxDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 200.dp, max = 320.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                DropdownMenuItem(
                    text = {
                        CruxText(tab.title)
                    },
                    leadingIcon = {
                        TabIcon(tab)
                    },
                    trailingIcon =
                        if (index == selectedTabIndex) {
                            {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Check,
                                    contentDescription = stringResource(R.string.selected),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    onClick = {
                        expanded = false
                        onTabSelected(index)
                    },
                )
            }
        }
    }
}

private const val MAX_SCROLLABLE_HOME_TABS = 10

@Composable
private fun timelinePresenter() =
    run {
        val state = remember { HomeTimelineWithTabsPresenter() }.invoke()

        val pagerState =
            state.tabState.map {
                rememberPagerState { it.size }
            }

        val changeLogState = changeLogPresenter()

        object :
            HomeTimelineWithTabsPresenter.State by state,
            ChangeLogState by changeLogState {
            val pagerState = pagerState
        }
    }

/** Shown when the home screen has no tabs at all, instead of a blank page. */
@Composable
private fun HomeEmptyState(
    onManagePlatforms: () -> Unit,
    modifier: Modifier = Modifier,
    topStart: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier) {
        if (topStart != null) {
            Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) { topStart() }
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.crux_home_empty_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.crux_home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onManagePlatforms) {
                Text(text = stringResource(R.string.crux_manage_platforms))
            }
        }
    }
}

/**
 * Switches the home pager to [index]. The slide animation composes every page it passes, which
 * would briefly wake a live site's WebView (its renderer and network) on the way, so it jumps
 * whenever a live web tab is at either end or in between, and slides only between other feeds.
 */
internal suspend fun PagerState.switchToTab(
    tabs: List<UiTimelineTabItem>,
    index: Int,
) {
    if (shouldJumpBetweenTabs(currentPage, index) { tabs.getOrNull(it)?.liveWebSite() != null }) {
        scrollToPage(index)
    } else {
        animateScrollToPage(index)
    }
}

/** True when moving from page [from] to [to] would show or pass a live web tab. */
internal fun shouldJumpBetweenTabs(
    from: Int,
    to: Int,
    isLiveWebTab: (Int) -> Boolean,
): Boolean = (minOf(from, to)..maxOf(from, to)).any(isLiveWebTab)
