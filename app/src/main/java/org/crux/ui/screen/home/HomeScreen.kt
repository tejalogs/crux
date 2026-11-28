package org.crux.ui.screen.home

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.CaretDown
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.PenToSquare
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.ShieldHalved
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.UserGroup
import compose.icons.fontawesomeicons.solid.Users
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.repository.SettingsRepository
import org.crux.ui.common.InternalDeeplinkToken
import org.crux.ui.common.OnNewIntent
import org.crux.ui.common.isLoginCallbackDeepLink
import org.crux.ui.component.BottomBarAccessory
import org.crux.ui.component.FAIcon
import org.crux.ui.component.InAppNotificationComponent
import org.crux.ui.component.LocalBottomBarAccessory
import org.crux.ui.component.NavigationSuiteScaffold2
import org.crux.ui.component.TopLevelBackStack
import org.crux.ui.lock.AppLock
import org.crux.ui.lock.DeferredWhileLocked
import org.crux.ui.model.map
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.model.takeSuccess
import org.crux.ui.presenter.HomeTabsPresenter
import org.crux.ui.presenter.home.DeepLinkPresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.route.Route
import org.crux.ui.route.Router
import org.crux.ui.route.reloginRoute
import org.crux.ui.screen.crux.CruxDrawerHeader
import org.crux.ui.screen.crux.CruxOnboardingScreen
import org.crux.ui.screen.settings.AutoAppUpdatePrompt
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.LocalCruxConnections
import org.crux.ui.screen.crux.rememberCruxConnectionsState
import org.crux.ui.screen.splash.SplashScreen
import org.koin.compose.koinInject

@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
@Composable
internal fun HomeScreen(afterInit: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val state by producePresenter { presenter(uriHandler = uriHandler) }
    // The presenter is retained by a ViewModel, so keep its UI-owned handler current.
    SideEffect {
        state.updateUriHandler(uriHandler)
    }
    val hapticFeedback = LocalHapticFeedback.current
    // The home timeline fills this with its feed tabs so the Home dock item expands into them.
    val homeBottomBarAccessory = remember { BottomBarAccessory() }
    val settingsRepository = koinInject<SettingsRepository>()
    LaunchedEffect(settingsRepository) {
        settingsRepository.ensureRedditTabAdded()
        settingsRepository.ensureLiveWebTabsMigrated()
    }
    val connections = rememberCruxConnectionsState()
    val liveWebPages = remember { LiveWebPages() }
    val latestConnections by rememberUpdatedState(connections)
    DisposableEffect(liveWebPages) {
        // A sign-in or sign-out inside a tab shows up in the side panel without waiting for a
        // resume. The pages call this only when a site's signed-in state changes.
        liveWebPages.onPageFinished = { latestConnections.refresh() }
        onDispose {
            liveWebPages.onPageFinished = null
            liveWebPages.destroy()
        }
    }
    LiveWebTimersWhileBackgrounded(liveWebPages)
    val onHomeLongClick =
        remember(hapticFeedback, state) {
            {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                state.navigate(Route.Settings.Accounts)
            }
        }
    LaunchedEffect(onHomeLongClick) {
        homeBottomBarAccessory.onLongClick = onHomeLongClick
    }
    if (!connections.loaded) {
        // Nothing to show until we know whether onboarding is done, so tabs never flash before it.
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (!connections.onboardingCompleted) {
        LaunchedEffect(Unit) { afterInit.invoke() }
        CompositionLocalProvider(
            LocalLiveWebPages provides liveWebPages,
            LocalCruxConnections provides connections,
        ) {
            CruxOnboardingScreen(connections = connections)
        }
        return
    }
    state.tabs
        .onSuccess { tabs ->
            AutoAppUpdatePrompt()
            val currentRoute =
                remember(
                    tabs,
                    state.topLevelBackStack.takeSuccess()?.topLevelKey,
                ) {
                    state.topLevelBackStack.takeSuccess()?.topLevelKey ?: tabs.first().route
                }
            // While App Lock is covering the app, hold the newest link (share, tile, shortcut,
            // crux://) and open it only after unlock, so nothing opens under or over the cover.
            // The relock decision is made in onStart, and on API 26-27 a stopped activity gets
            // onNewIntent before onStart, so a link only counts as unlocked once the activity is
            // started again.
            val deferredDeeplink = remember { DeferredWhileLocked<String>() }
            val homeLifecycle = LocalLifecycleOwner.current.lifecycle
            val homeLifecycleState by homeLifecycle.currentStateAsState()
            val linksCanOpen = AppLock.isUnlocked && homeLifecycleState.isAtLeast(Lifecycle.State.STARTED)
            OnNewIntent(
                withOnCreateIntent = true,
            ) { incomingIntent ->
                val canOpenNow =
                    AppLock.isUnlocked && homeLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                incomingIntent.dataString
                    ?.takeUnless { url -> url.isLoginCallbackDeepLink() }
                    ?.takeIf { url -> InternalDeeplinkToken.isAllowedForIntent(url, incomingIntent) }
                    ?.let { url -> deferredDeeplink.offer(url, unlocked = canOpenNow) }
                    ?.let { url -> state.deeplinkPresenter.handle(url) }
            }
            LaunchedEffect(linksCanOpen) {
                deferredDeeplink.release(unlocked = linksCanOpen)?.let { url ->
                    state.deeplinkPresenter.handle(url)
                }
            }
            LaunchedEffect(Unit) {
                afterInit.invoke()
            }
            val layoutType =
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfoV2(),
                )
            // Home (its feed pills) is the only dock destination.
            val currentTopKey = state.topLevelBackStack.takeSuccess()?.currentKey
            val isImeVisible = WindowInsets.isImeVisible
            val feedsAndSocialHeader = stringResource(id = R.string.drawer_section_feeds_and_social)
            val personalLibraryHeader = stringResource(id = R.string.drawer_section_personal_library)
            val connectedAccountsHeader = stringResource(id = R.string.drawer_section_connected_accounts)
            val sovereigntyAndSystemHeader = stringResource(id = R.string.drawer_section_sovereignty_and_system)
            Box {
                NavigationSuiteScaffold2(
                    wideNavigationRailState = state.wideNavigationRailState,
                    modifier = Modifier.fillMaxSize(),
                    layoutType = layoutType,
                    // Live web tabs have the site's own compose button, so Crux's would cover it.
                    showFab =
                        state.topLevelBackStack.takeSuccess()?.currentKey is Route.Home &&
                            !homeBottomBarAccessory.keepVisible,
                    bottomBarVisible =
                        tabs.any { it.route == currentTopKey } &&
                            !isImeVisible &&
                            (!homeBottomBarAccessory.keepVisible || homeBottomBarAccessory.webDockExpanded),
                    keepBottomBarVisible = homeBottomBarAccessory.keepVisible,
                    onFabClicked = {
                        state.navigate(Route.CruxCompose())
                    },
                    navigationSuiteColors =
                        NavigationSuiteDefaults.colors(
                            navigationBarContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    railHeader = {
                        if (layoutType == NavigationSuiteType.NavigationRail) {
                            IconButton(
                                onClick = {
                                    state.openDrawer()
                                },
                                modifier =
                                    Modifier
                                        .padding(
                                            horizontal = 24.dp,
                                        ).padding(top = 12.dp, bottom = 4.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Bars,
                                    contentDescription = stringResource(R.string.open_navigation_menu),
                                )
                            }

                            if (layoutType == NavigationSuiteType.NavigationRail) {
                                SharedTransitionLayout {
                                    AnimatedContent(
                                        state.wideNavigationRailState.currentValue,
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                    ) { railState ->
                                        when (railState) {
                                            WideNavigationRailValue.Collapsed -> {
                                                FloatingActionButton(
                                                    onClick = {
                                                        state.navigate(Route.CruxCompose())
                                                    },
                                                    elevation =
                                                        FloatingActionButtonDefaults.elevation(
                                                            defaultElevation = 0.dp,
                                                        ),
                                                    modifier =
                                                        Modifier
                                                            .sharedElement(
                                                                rememberSharedContentState(key = "compose"),
                                                                animatedVisibilityScope = this@AnimatedContent,
                                                            ),
                                                ) {
                                                    FAIcon(
                                                        imageVector = FontAwesomeIcons.Solid.Pen,
                                                        contentDescription = stringResource(id = R.string.compose_title),
                                                    )
                                                }
                                            }

                                            WideNavigationRailValue.Expanded -> {
                                                ExtendedFloatingActionButton(
                                                    onClick = {
                                                        state.navigate(Route.CruxCompose())
                                                    },
                                                    icon = {
                                                        FAIcon(
                                                            imageVector = FontAwesomeIcons.Solid.Pen,
                                                            contentDescription =
                                                                stringResource(
                                                                    id = R.string.compose_title,
                                                                ),
                                                        )
                                                    },
                                                    text = {
                                                        Text(text = stringResource(id = R.string.compose_title))
                                                    },
                                                    elevation =
                                                        FloatingActionButtonDefaults.elevation(
                                                            defaultElevation = 0.dp,
                                                        ),
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .sharedElement(
                                                                rememberSharedContentState(key = "compose"),
                                                                animatedVisibilityScope = this@AnimatedContent,
                                                            ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (layoutType == NavigationSuiteType.NavigationBar) {
                            CruxDrawerHeader(
                                connections = connections,
                                onManage = {
                                    state.navigate(Route.Settings.Accounts)
                                },
                            )
                        }
                    },
                    navigationSuiteItems = {
                        val currentKey = state.topLevelBackStack.takeSuccess()?.currentKey
                        val isTopLevel = tabs.any { it.route == currentKey }
                        tabs.forEach { tab ->
                            item(
                                selected = isTopLevel && (currentKey == tab.route),
                                onClick = {
                                    if (currentRoute == tab.route && isTopLevel) {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.GestureThresholdActivate,
                                        )
                                        state.scrollToTopRegistry.scrollToTop()
                                    } else {
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.SegmentTick,
                                        )
                                        state.navigate(tab.route)
                                    }
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(id = tab.title),
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(id = tab.title),
                                    )
                                },
                                onLongClick =
                                    if (tab == HomeTabsPresenter.State.HomeTabs.Home) {
                                        onHomeLongClick
                                    } else {
                                        null
                                    },
                                expandedContent =
                                    if (tab == HomeTabsPresenter.State.HomeTabs.Home) {
                                        homeBottomBarAccessory.content
                                    } else {
                                        null
                                    },
                            )
                        }
                    },
                    secondaryItems = {
                        header(feedsAndSocialHeader)
                        // One entry per platform the person chose, in a fixed order. Platforms that
                        // still need a sign-in say so instead of silently showing a login wall.
                        connections.enabled.forEach { platform ->
                            val needsSignIn = platform.site != null && !platform.worksSignedOut && !connections.isSignedIn(platform)
                            item(
                                selected = currentRoute == platform.route,
                                onClick = {
                                    state.navigate(platform.route)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = platform.icon,
                                        contentDescription = platform.displayName,
                                    )
                                },
                                label = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(text = platform.displayName)
                                        if (needsSignIn) {
                                            androidx.compose.material3.Surface(
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                            ) {
                                                Text(
                                                    text = stringResource(id = R.string.crux_action_sign_in),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        item(
                            selected = currentRoute is Route.Settings.Accounts,
                            onClick = {
                                state.navigate(Route.Settings.Accounts)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Plus,
                                    contentDescription = stringResource(id = R.string.crux_manage_platforms),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.crux_manage_platforms))
                            },
                        )

                        header(personalLibraryHeader)
                        item(
                            selected = currentRoute is Route.ReadingList,
                            onClick = {
                                state.navigate(Route.ReadingList)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Bookmark,
                                    contentDescription = stringResource(id = R.string.drawer_reading_list),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.drawer_reading_list))
                            },
                        )
                    },
                    footerItems = {
                        header(sovereigntyAndSystemHeader)
                        item(
                            selected = currentRoute is Route.Settings.PrivacyTweaks,
                            onClick = {
                                state.navigate(Route.Settings.PrivacyTweaks)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ShieldHalved,
                                    contentDescription = stringResource(id = R.string.web_tweaks_title),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.web_tweaks_title))
                            },
                        )
                        item(
                            selected = currentRoute is Route.Settings.Main,
                            onClick = {
                                state.navigate(Route.Settings.Main)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Gear,
                                    contentDescription = stringResource(id = R.string.settings_title),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.settings_title))
                            },
                        )
                    },
                ) {
                    CompositionLocalProvider(
                        LocalUriHandler provides
                            remember {
                                object : UriHandler {
                                    override fun openUri(uri: String) {
                                        state.deeplinkPresenter.handle(uri)
                                    }
                                }
                            },
                        LocalScrollToTopRegistry provides state.scrollToTopRegistry,
                        LocalBottomBarAccessory provides homeBottomBarAccessory,
                        LocalLiveWebPages provides liveWebPages,
                        LocalCruxConnections provides connections,
                    ) {
                        Router(
                            backStack =
                                state.topLevelBackStack.takeSuccess()?.backStack
                                    ?: remember {
                                        androidx.compose.runtime.mutableStateListOf(
                                            currentRoute,
                                        )
                                    },
                            openDrawer = {
                                state.openDrawer()
                            },
                            navigate = state::navigate,
                            onBack = {
                                state.goBack()
                                // A sign-in page may just have closed.
                                connections.refresh()
                            },
                        )
                    }
                }
                InAppNotificationComponent(
                    modifier = Modifier.align(Alignment.TopCenter),
                    onRelogin = {
                        state.navigate(reloginRoute(it.platformId))
                    },
                )
            }
        }.onLoading {
            SplashScreen()
        }
}

private val HomeTabsPresenter.State.HomeTabs.route: Route
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> Route.Home
        }
private val HomeTabsPresenter.State.HomeTabs.title: Int
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> R.string.home_tab_home_title
        }

private val HomeTabsPresenter.State.HomeTabs.icon: ImageVector
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> FontAwesomeIcons.Solid.House
        }

@Composable
private fun presenter(uriHandler: UriHandler) =
    run {
        val currentUriHandler = remember { mutableStateOf(uriHandler) }
        val wideNavigationRailState = rememberWideNavigationRailState()
        val tabs =
            remember {
                HomeTabsPresenter()
            }.invoke()
        val scrollToTopRegistry =
            remember {
                ScrollToTopRegistry()
            }
        val firstDirection =
            remember(tabs.tabs) {
                tabs.tabs.map {
                    it.first().route
                }
            }
        val topLevelBackStack =
            remember(firstDirection) {
                firstDirection.map {
                    TopLevelBackStack(it)
                }
            }
        val topLevelRoutes =
            remember(tabs.tabs) {
                tabs.tabs.map { state ->
                    state.map { it.route }.toSet() +
                        buildSet {
                            addAll(
                                setOf(
                                    Route.Settings.Main,
                                    Route.Rss.Sources,
                                    Route.ReadingList,
                                    Route.Settings.Accounts,
                                    Route.Settings.PrivacyTweaks,
                                ),
                            )
                            // Opened from the side panel without a URL; links with a URL still push.
                            addAll(CruxPlatform.entries.map { it.route })
                        }
                }
            }
        val scope = rememberCoroutineScope()
        val deeplinkPresenter =
            remember(topLevelBackStack, topLevelRoutes) {
                DeepLinkPresenter(
                    onRoute = {
                        val route = Route.from(it)
                        if (route != null) {
                            navigate(
                                route = route,
                                topLevelBackStack = topLevelBackStack.takeSuccess(),
                                topLevelRoutes = topLevelRoutes.takeSuccess(),
                                wideNavigationRailState = wideNavigationRailState,
                                scope = scope,
                            )
                        }
                    },
                    onLink = { linkUrl ->
                        when (val target = resolveLinkTarget(linkUrl)) {
                            is LinkTarget.LiveWebScreen -> {
                                navigate(
                                    route = target.route,
                                    topLevelBackStack = topLevelBackStack.takeSuccess(),
                                    topLevelRoutes = topLevelRoutes.takeSuccess(),
                                    wideNavigationRailState = wideNavigationRailState,
                                    scope = scope,
                                )
                            }

                            // Already cleaned; the app-wide handler opens it in the default browser.
                            is LinkTarget.SystemBrowser -> {
                                currentUriHandler.value.openUri(target.url)
                            }

                            is LinkTarget.InternalDeepLink, LinkTarget.Invalid -> {
                                currentUriHandler.value.openUri(linkUrl)
                            }
                        }
                    },
                )
            }.invoke()
        object {
            val tabs = tabs.tabs
            val scrollToTopRegistry = scrollToTopRegistry
            val deeplinkPresenter = deeplinkPresenter
            val topLevelBackStack = topLevelBackStack
            val wideNavigationRailState = wideNavigationRailState

            fun updateUriHandler(uriHandler: UriHandler) {
                currentUriHandler.value = uriHandler
            }

            fun navigate(route: Route) {
                navigate(
                    route = route,
                    topLevelBackStack = topLevelBackStack.takeSuccess(),
                    topLevelRoutes = topLevelRoutes.takeSuccess(),
                    wideNavigationRailState = wideNavigationRailState,
                    scope = scope,
                )
            }

            fun goBack() {
                topLevelBackStack.takeSuccess()?.removeLast()
            }

            fun openDrawer() {
                scope.launch {
                    wideNavigationRailState.toggle()
                }
            }
        }
    }

private fun navigate(
    route: Route,
    topLevelBackStack: TopLevelBackStack<Route>?,
    topLevelRoutes: Set<Route>?,
    wideNavigationRailState: WideNavigationRailState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (topLevelBackStack == null) return
    if (topLevelRoutes?.contains(route) == true) {
        topLevelBackStack.addTopLevel(route)
    } else {
        topLevelBackStack.add(route)
    }
    scope.launch {
        wideNavigationRailState.collapse()
    }
}

internal class ScrollToTopRegistry {
    private val callbacks = mutableSetOf<() -> Unit>()

    fun registerCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun unregisterCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }

    fun scrollToTop() {
        callbacks.forEach { it.invoke() }
    }
}

internal val LocalScrollToTopRegistry =
    androidx.compose.runtime.staticCompositionLocalOf<ScrollToTopRegistry?> {
        null
    }

@Composable
internal fun RegisterTabCallback(
    lazyListState: LazyStaggeredGridState,
    onRefresh: () -> Unit,
) {
    val onRefreshState by rememberUpdatedState(onRefresh)
    val tabState = LocalScrollToTopRegistry.current
    if (tabState != null) {
        val scope = rememberCoroutineScope()
        val callback: () -> Unit =
            remember(lazyListState, scope) {
                {
                    if (lazyListState.firstVisibleItemIndex == 0 &&
                        lazyListState.firstVisibleItemScrollOffset == 0
                    ) {
                        onRefreshState.invoke()
                    } else {
                        scope.launch {
                            if (lazyListState.firstVisibleItemIndex > 20) {
                                lazyListState.scrollToItem(0)
                            } else {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                    }
                }
            }
        DisposableEffect(tabState, callback, lazyListState) {
            tabState.registerCallback(callback)
            onDispose {
                tabState.unregisterCallback(callback)
            }
        }
    }
}

/**
 * Stops the live sites' JavaScript (timers, polling, Crux's own page scans) while Crux is in the
 * background: WebView.onPause alone does not. The pause is process-wide, so it lives here once
 * for the whole activity rather than in each tab. Timers come back when Crux is started again,
 * but never while App Lock is covering it; the unlock itself resumes them.
 */
@Composable
private fun LiveWebTimersWhileBackgrounded(liveWebPages: LiveWebPages) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, liveWebPages) {
        val mainHandler = Handler(Looper.getMainLooper())
        val resumeIfAllowed =
            Runnable {
                if (liveWebTimersMayRun(AppLock.isUnlocked, lifecycle.currentState)) liveWebPages.resumeAllTimers()
            }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        mainHandler.removeCallbacksAndMessages(null)
                        liveWebPages.pauseAllTimers()
                    }

                    // Posted so App Lock has decided whether to re-lock (it does so in onStart).
                    Lifecycle.Event.ON_START -> {
                        mainHandler.post(resumeIfAllowed)
                    }

                    else -> {}
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mainHandler.removeCallbacksAndMessages(null)
            // Never leave the process-wide pause behind for the next screen's WebViews.
            liveWebPages.resumeAllTimers()
        }
    }
    val unlocked = AppLock.isUnlocked
    LaunchedEffect(unlocked, liveWebPages) {
        when {
            liveWebTimersMayRun(unlocked, lifecycle.currentState) -> liveWebPages.resumeAllTimers()
            // Locking while started (the re-lock in onStart) pauses too, not only ON_STOP.
            !unlocked -> liveWebPages.pauseAllTimers()
        }
    }
}

/** Live pages' JavaScript timers run only while Crux is started and App Lock is not covering it. */
internal fun liveWebTimersMayRun(
    isUnlocked: Boolean,
    state: Lifecycle.State,
): Boolean = isUnlocked && state.isAtLeast(Lifecycle.State.STARTED)
