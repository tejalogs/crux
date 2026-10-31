package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.brands.LinkedinIn
import compose.icons.fontawesomeicons.solid.Bars
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.CaretDown
import compose.icons.fontawesomeicons.solid.ClockRotateLeft
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.PenToSquare
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.UserGroup
import compose.icons.fontawesomeicons.solid.Users
import dev.dimension.flare.R
import dev.dimension.flare.data.repository.NativeXRemoval
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.common.OnNewIntent
import dev.dimension.flare.ui.common.isLoginCallbackDeepLink
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.BottomBarAccessory
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.InAppNotificationComponent
import dev.dimension.flare.ui.component.LocalBottomBarAccessory
import dev.dimension.flare.ui.component.NavigationSuiteScaffold2
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.component.TopLevelBackStack
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.asText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.HomeTabsPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.AllNotificationBadgePresenter
import dev.dimension.flare.ui.presenter.home.CanComposePresenter
import dev.dimension.flare.ui.presenter.home.DeepLinkPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInPresenter
import dev.dimension.flare.ui.presenter.home.SecondaryTabsPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.presenter.settings.AiAgentEnabledPresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.route.Router
import dev.dimension.flare.ui.screen.splash.SplashScreen
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(
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
    val activeAccountState = remember { ActiveAccountPresenter() }.invoke()
    val accountsPresenter = remember { AccountsPresenter() }.invoke()
    // The home timeline fills this with its feed tabs so the Home dock item expands into them.
    val homeBottomBarAccessory = remember { BottomBarAccessory() }
    val settingsRepository = koinInject<SettingsRepository>()
    val nativeXRemoval = koinInject<NativeXRemoval>()
    LaunchedEffect(settingsRepository) {
        settingsRepository.ensureRedditTabAdded()
        nativeXRemoval.run()
    }
    val liveWebPages = remember { LiveWebPages() }
    DisposableEffect(liveWebPages) {
        onDispose { liveWebPages.destroy() }
    }
    val onHomeLongClick =
        remember(hapticFeedback, state) {
            {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                state.navigate(Route.AccountSelection)
            }
        }
    LaunchedEffect(onHomeLongClick) {
        homeBottomBarAccessory.onLongClick = onHomeLongClick
    }
    state.tabs
        .onSuccess { tabs ->
            val currentRoute =
                remember(
                    tabs,
                    state.topLevelBackStack.takeSuccess()?.topLevelKey,
                ) {
                    state.topLevelBackStack.takeSuccess()?.topLevelKey ?: tabs.first().route
                }
            OnNewIntent(
                withOnCreateIntent = true,
            ) {
                it.dataString
                    ?.takeUnless { url -> url.isLoginCallbackDeepLink() }
                    ?.let { url -> state.deeplinkPresenter.handle(url) }
            }
            LaunchedEffect(Unit) {
                afterInit.invoke()
            }
            val layoutType =
                NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(
                    currentWindowAdaptiveInfoV2(),
                )
            // Phones: the dock is only Home (its feed pills); Notifications and Discover move to the
            // side panel, and the dock stays visible on them as a way back to the feeds.
            val isPhoneLayout = layoutType == NavigationSuiteType.NavigationBar
            val currentTopKey = state.topLevelBackStack.takeSuccess()?.currentKey
            val dockTabs =
                if (isPhoneLayout) {
                    tabs.filter { it == HomeTabsPresenter.State.HomeTabs.Home }
                } else {
                    tabs
                }
            val notificationBadge: @Composable () -> Unit = {
                if (state.notificationState.count > 0) {
                    Badge {
                        Text(text = state.notificationState.count.toString())
                    }
                }
            }
            LaunchedEffect(isPhoneLayout, state.notificationState.count) {
                homeBottomBarAccessory.badgeCount = if (isPhoneLayout) state.notificationState.count else 0
            }
            val feedsAndSocialHeader = stringResource(id = R.string.drawer_section_feeds_and_social)
            val personalLibraryHeader = stringResource(id = R.string.drawer_section_personal_library)
            val connectedAccountsHeader = stringResource(id = R.string.drawer_section_connected_accounts)
            val sovereigntyAndSystemHeader = stringResource(id = R.string.drawer_section_sovereignty_and_system)
            Box {
                NavigationSuiteScaffold2(
                    wideNavigationRailState = state.wideNavigationRailState,
                    modifier = Modifier.fillMaxSize(),
                    layoutType = layoutType,
                    showFab =
                        state.canComposeState.takeSuccess() == true &&
                            state.topLevelBackStack.takeSuccess()?.currentKey is Route.Home,
                    bottomBarVisible = tabs.any { it.route == currentTopKey },
                    keepBottomBarVisible = homeBottomBarAccessory.keepVisible,
                    onFabClicked = {
                        state.navigate(Route.Compose.New)
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

                            if (layoutType == NavigationSuiteType.NavigationRail &&
                                state.canComposeState.takeSuccess() == true
                            ) {
                                SharedTransitionLayout {
                                    AnimatedContent(
                                        state.wideNavigationRailState.currentValue,
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                    ) { railState ->
                                        when (railState) {
                                            WideNavigationRailValue.Collapsed -> {
                                                FloatingActionButton(
                                                    onClick = {
                                                        state.navigate(Route.Compose.New)
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
                                                        state.navigate(Route.Compose.New)
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
                            val heroProfile =
                                activeAccountState.user.takeSuccess()
                                    ?: accountsPresenter.accounts
                                        .takeSuccess()
                                        ?.firstOrNull { it.profile.takeSuccess() != null }
                                        ?.profile
                                        ?.takeSuccess()
                            FlareDrawerHeader(
                                primaryProfile = heroProfile,
                                accountCount = accountsPresenter.accounts.takeSuccess()?.size ?: 1,
                                onManageAccounts = {
                                    state.navigate(Route.AccountSelection)
                                },
                            )
                        }
                    },
                    navigationSuiteItems = {
                        val currentKey = state.topLevelBackStack.takeSuccess()?.currentKey
                        val isTopLevel = tabs.any { it.route == currentKey }
                        dockTabs.forEach { tab ->
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
                                badge =
                                    if (tab == HomeTabsPresenter.State.HomeTabs.Notifications) {
                                        notificationBadge
                                    } else {
                                        null
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
                        if (isPhoneLayout) {
                            tabs
                                .filter { it != HomeTabsPresenter.State.HomeTabs.Home }
                                .forEach { tab ->
                                    item(
                                        selected = currentRoute == tab.route,
                                        onClick = {
                                            state.navigate(tab.route)
                                        },
                                        icon = {
                                            FAIcon(
                                                imageVector = tab.icon,
                                                contentDescription = stringResource(id = tab.title),
                                            )
                                        },
                                        label = {
                                            Text(text = stringResource(id = tab.title))
                                        },
                                        badge =
                                            if (tab == HomeTabsPresenter.State.HomeTabs.Notifications) {
                                                notificationBadge
                                            } else {
                                                null
                                            },
                                    )
                                }
                        }
                        header(feedsAndSocialHeader)
                        item(
                            selected = currentRoute is Route.InstagramWeb,
                            onClick = {
                                state.navigate(Route.InstagramWeb())
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Brands.Instagram,
                                    contentDescription = stringResource(id = R.string.drawer_instagram),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.drawer_instagram))
                            },
                        )
                        item(
                            selected = currentRoute is Route.LinkedInWeb,
                            onClick = {
                                state.navigate(Route.LinkedInWeb())
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Brands.LinkedinIn,
                                    contentDescription = stringResource(id = R.string.drawer_linkedin),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.drawer_linkedin))
                            },
                        )
                        item(
                            selected = currentRoute is Route.Rss.Sources,
                            onClick = {
                                state.navigate(Route.Rss.Sources)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.SquareRss,
                                    contentDescription = stringResource(id = R.string.settings_rss_management_title),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.settings_rss_management_title))
                            },
                        )

                        item(
                            selected = currentRoute is Route.LocalFollows,
                            onClick = {
                                state.navigate(Route.LocalFollows.Manage)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.UserGroup,
                                    contentDescription = stringResource(id = R.string.local_follows_title),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.local_follows_title))
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
                        item(
                            selected = currentRoute is Route.DraftBox,
                            onClick = {
                                state.navigate(Route.DraftBox)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.PenToSquare,
                                    contentDescription = stringResource(id = R.string.draft_box_title),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.draft_box_title))
                            },
                        )
                        item(
                            selected = currentRoute is Route.Settings.LocalHistory,
                            onClick = {
                                state.navigate(Route.Settings.LocalHistory)
                            },
                            icon = {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.ClockRotateLeft,
                                    contentDescription = stringResource(id = R.string.settings_local_history_title),
                                )
                            },
                            label = {
                                Text(text = stringResource(id = R.string.settings_local_history_title))
                            },
                        )

                        state.secondaryTabsState.onSuccess { secondaryTabs ->
                            if (secondaryTabs.isNotEmpty()) {
                                header(connectedAccountsHeader)
                                secondaryTabs.forEach { item ->
                                    expandableItem(
                                        icon = {
                                            item.user.onSuccess {
                                                AvatarComponent(
                                                    data = it.avatar,
                                                    platformId = it.platformId,
                                                    host = it.handle.host,
                                                    platformIcon = it.platformIcon,
                                                    showNetworkBadge = true,
                                                )
                                            }
                                        },
                                        label = {
                                            Column {
                                                item.user.onSuccess {
                                                    RichText(it.name)
                                                    Text(
                                                        it.handle.canonical,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                        },
                                        children = {
                                            item.tabs.forEach {
                                                val direction = getDirection(it)
                                                if (direction != null) {
                                                    item(
                                                        selected = currentRoute == direction,
                                                        onClick = {
                                                            if (currentRoute == direction) {
                                                                state.scrollToTopRegistry.scrollToTop()
                                                            } else {
                                                                state.navigate(direction)
                                                            }
                                                        },
                                                        icon = {
                                                            TabIcon(
                                                                icon = it.icon.asType(),
                                                                title = it.title.asText(),
                                                                iconOnly = true,
                                                            )
                                                        },
                                                        label = {
                                                            dev.dimension.flare.ui.component.Text(
                                                                text = it.title.asText(),
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    },
                    footerItems = {
                        header(sovereigntyAndSystemHeader)
                        if (state.aiAgentEnabled) {
                            item(
                                selected = currentRoute is Route.Settings.AgentHistory,
                                onClick = {
                                    state.navigate(Route.Settings.AgentHistory)
                                },
                                icon = {
                                    FAIcon(
                                        imageVector = FontAwesomeIcons.Solid.Robot,
                                        contentDescription = stringResource(id = R.string.agent_history_title),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(id = R.string.agent_history_title))
                                },
                            )
                        }
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
                            onBack = state::goBack,
                        )
                    }
                }
                InAppNotificationComponent(
                    modifier = Modifier.align(Alignment.TopCenter),
                    onRelogin = {
                        state.navigate(Route.ServiceSelect.Relogin(it))
                    },
                )
            }
        }.onLoading {
            SplashScreen()
        }
}

private fun getDirection(data: SecondaryTabsPresenter.Tab): Route? =
    when (val target = data.destination) {
        is SecondaryTabsPresenter.Destination.Route -> {
            Route.from(target.route)
        }

        is SecondaryTabsPresenter.Destination.Timeline -> {
            Route.Timeline(target.tabItem)
        }
    }

private val HomeTabsPresenter.State.HomeTabs.route: Route
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> Route.Home
            HomeTabsPresenter.State.HomeTabs.Notifications -> Route.Notification
            HomeTabsPresenter.State.HomeTabs.Discover -> Route.Discover
        }
private val HomeTabsPresenter.State.HomeTabs.title: Int
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> R.string.home_tab_home_title
            HomeTabsPresenter.State.HomeTabs.Notifications -> R.string.home_tab_notifications_title
            HomeTabsPresenter.State.HomeTabs.Discover -> R.string.home_tab_discover_title
        }

private val HomeTabsPresenter.State.HomeTabs.icon: ImageVector
    get() =
        when (this) {
            HomeTabsPresenter.State.HomeTabs.Home -> FontAwesomeIcons.Solid.House
            HomeTabsPresenter.State.HomeTabs.Notifications -> FontAwesomeIcons.Solid.Bell
            HomeTabsPresenter.State.HomeTabs.Discover -> FontAwesomeIcons.Solid.MagnifyingGlass
        }

@Composable
private fun presenter(uriHandler: UriHandler) =
    run {
        val currentUriHandler = remember { mutableStateOf(uriHandler) }
        val secondaryTabsPresenter = remember { SecondaryTabsPresenter() }.invoke()
        val loggedInState = remember { LoggedInPresenter() }.invoke()
        val canComposeState = remember { CanComposePresenter() }.invoke()
        val wideNavigationRailState = rememberWideNavigationRailState()
        val tabs =
            remember {
                HomeTabsPresenter()
            }.invoke()
        val scrollToTopRegistry =
            remember {
                ScrollToTopRegistry()
            }
        val notificationState =
            remember {
                AllNotificationBadgePresenter()
            }.invoke()
        val aiAgentEnabledState =
            remember {
                AiAgentEnabledPresenter()
            }.invoke()
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
            remember(tabs.tabs, aiAgentEnabledState.enabled) {
                tabs.tabs.map { state ->
                    state.map { it.route }.toSet() +
                        buildSet {
                            addAll(
                                setOf(
                                    Route.Settings.Main,
                                    Route.DraftBox,
                                    Route.Rss.Sources,
                                    Route.Settings.LocalHistory,
                                    Route.ReadingList,
                                    Route.LocalFollows.Manage,
                                    // Opened from the side panel without a URL; links with a URL still push.
                                    Route.InstagramWeb(),
                                    Route.LinkedInWeb(),
                                ),
                            )
                            if (aiAgentEnabledState.enabled) {
                                add(Route.Settings.AgentHistory)
                            }
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
                        if (isInstagramUrl(linkUrl)) {
                            navigate(
                                route = Route.InstagramWeb(initialUrl = linkUrl),
                                topLevelBackStack = topLevelBackStack.takeSuccess(),
                                topLevelRoutes = topLevelRoutes.takeSuccess(),
                                wideNavigationRailState = wideNavigationRailState,
                                scope = scope,
                            )
                        } else if (isLinkedInUrl(linkUrl)) {
                            navigate(
                                route = Route.LinkedInWeb(initialUrl = linkUrl),
                                topLevelBackStack = topLevelBackStack.takeSuccess(),
                                topLevelRoutes = topLevelRoutes.takeSuccess(),
                                wideNavigationRailState = wideNavigationRailState,
                                scope = scope,
                            )
                        } else {
                            currentUriHandler.value.openUri(linkUrl)
                        }
                    },
                )
            }.invoke()
        object {
            val secondaryTabsState = secondaryTabsPresenter.items
            val notificationState = notificationState
            val tabs = tabs.tabs
            val scrollToTopRegistry = scrollToTopRegistry
            val deeplinkPresenter = deeplinkPresenter
            val topLevelBackStack = topLevelBackStack
            val wideNavigationRailState = wideNavigationRailState
            val loggedInState = loggedInState.isLoggedIn
            val canComposeState = canComposeState.canCompose
            val aiAgentEnabled = aiAgentEnabledState.enabled

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

@Composable
private fun userPresenter(accountType: AccountType) =
    run {
        remember(accountType) { UserPresenter(accountType, null) }.invoke().user
    }

@Composable
private fun FlareDrawerHeader(
    primaryProfile: UiProfile?,
    accountCount: Int,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpressiveAvatarFrame(size = 52.dp) {
                    AvatarComponent(
                        data = primaryProfile?.avatar,
                        size = 44.dp,
                        platformId = primaryProfile?.platformId,
                        host = primaryProfile?.handle?.host,
                        platformIcon = primaryProfile?.platformIcon,
                        showNetworkBadge = true,
                    )
                }

                Surface(
                    onClick = onManageAccounts,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Users,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        val accountsText =
                            if (accountCount > 1) {
                                LocalContext.current.resources.getQuantityString(
                                    R.plurals.drawer_account_count,
                                    accountCount,
                                    accountCount,
                                )
                            } else {
                                stringResource(id = R.string.drawer_accounts_single)
                            }
                        Text(
                            text = accountsText,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.CaretDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (primaryProfile != null) {
                    RichText(
                        text = primaryProfile.name,
                        textStyle = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = primaryProfile.handle.canonical,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.drawer_flare_client_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = stringResource(id = R.string.drawer_flare_client_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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

private fun isInstagramUrl(url: String): Boolean {
    val clean = url.trim().lowercase()
    return clean.startsWith("https://instagram.com") ||
        clean.startsWith("https://www.instagram.com") ||
        clean.startsWith("http://instagram.com") ||
        clean.startsWith("http://www.instagram.com")
}

private fun isLinkedInUrl(url: String): Boolean {
    val clean = url.trim().lowercase()
    return clean.startsWith("https://linkedin.com") ||
        clean.startsWith("https://www.linkedin.com") ||
        clean.startsWith("http://linkedin.com") ||
        clean.startsWith("http://www.linkedin.com")
}
