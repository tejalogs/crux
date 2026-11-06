package org.crux.ui.screen.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.crux.ui.component.BottomSheetSceneStrategy
import org.crux.ui.component.platform.LocalWindowSizeClass
import org.crux.ui.component.platform.WindowSizeClass
import org.crux.ui.route.Route
import org.crux.ui.screen.agent.AgentChatScreen
import org.crux.ui.screen.agent.LocalHistoryAgentScreen
import org.crux.ui.screen.instagram.InstagramWebScreen
import org.crux.ui.screen.linkedin.LinkedInWebScreen
import org.crux.ui.screen.readinglist.ReadingListScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.homeEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    openDrawer: () -> Unit,
    uriHandler: UriHandler,
) {
    entry<Route.Home>(
        metadata = ListDetailSceneStrategy.detailPane(
            "home",
        )
    ) { args ->
        HomeTimelineScreen(
            toQuickMenu = {
                openDrawer.invoke()
            },
            toLogin = {
                navigate(Route.Settings.Accounts)
            },
            toTabSettings = {
                navigate(Route.TabSettings)
            },
            uriHandler = uriHandler,
        )
    }
    entry<Route.Timeline> { args ->
        TimelineScreen(
            tabItem = args.tabItem,
            onBack = onBack,
        )
    }
    entry<Route.DeckTimeline> { args ->
        DeckTimelineScreen(
            id = args.id,
            toQuickMenu = {
                openDrawer.invoke()
            },
            toLogin = {
                navigate(Route.Settings.Accounts)
            },
            toTabSettings = {
                navigate(Route.TabSettings)
            },
        )
    }
    entry<Route.AgentChat> {
        AgentChatScreen(
            conversationId = it.conversationId,
            initialMessage = it.initialMessage,
            onBack = onBack,
            navigate = navigate,
        )
    }
    entry<Route.LocalHistoryAgent> {
        LocalHistoryAgentScreen(
            conversationId = it.conversationId,
            query = it.query,
            target = it.target,
            onBack = onBack,
            navigate = navigate,
        )
    }
    entry<Route.TabSettings>(
        metadata = ListDetailSceneStrategy.extraPane(
            "home",
        ),
    ) { args ->
        CompositionLocalProvider(
            LocalWindowSizeClass provides WindowSizeClass.Compact
        ) {
            TabSettingScreen(
                onBack = onBack,
                toAddRssSource = {
                    navigate(Route.Rss.Create())
                },
                toGroupConfig = {
                    navigate(Route.TabGroupConfig(it?.id))
                },
            )
        }
    }
    entry<Route.TabGroupConfig>(
        metadata = ListDetailSceneStrategy.extraPane(
            "home",
        ),
    ) { args ->
        CompositionLocalProvider(
            LocalWindowSizeClass provides WindowSizeClass.Compact
        ) {
            GroupConfigScreen(
                groupId = args.groupId,
                onBack = onBack,
                toAddRssSource = {
                    navigate(Route.Rss.Create())
                },
            )
        }
    }
    entry<Route.InstagramWeb>(
        metadata = ListDetailSceneStrategy.detailPane("instagram_web"),
    ) { args ->
        InstagramWebScreen(
            onBack = onBack,
            initialUrl = args.initialUrl,
            onNavigateToRss = { rssUrl ->
                navigate(Route.Rss.Create(initialUrl = rssUrl))
            },
        )
    }

    entry<Route.LinkedInWeb>(
        metadata = ListDetailSceneStrategy.detailPane("linkedin_web"),
    ) { args ->
        LinkedInWebScreen(
            onBack = onBack,
            initialUrl = args.initialUrl,
        )
    }

    entry<Route.LiveWeb>(
        metadata = ListDetailSceneStrategy.detailPane("live_web"),
    ) { args ->
        LiveWebScreen(
            siteName = args.site,
            onBack = onBack,
            initialUrl = args.initialUrl,
        )
    }

    entry<Route.CruxCompose>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        org.crux.ui.screen.crux.CruxComposeSheet(
            onBack = onBack,
            navigate = navigate,
            initialText = args.initialText,
        )
    }

    entry<Route.ReadingList>(
        metadata = ListDetailSceneStrategy.detailPane("reading_list"),
    ) {
        ReadingListScreen(
            onBack = onBack,
            toStorageSettings = {
                navigate(Route.Settings.Storage)
            },
        )
    }

    entry<Route.AccountSelection>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        AccountSelectionModal(
            onBack = onBack,
            navigate = navigate,
        )
    }
    entry<Route.DeepLinkAccountPicker>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) {
        CompositionLocalProvider(
            LocalUriHandler provides uriHandler
        ) {
            DeepLinkAccountPickerModal(
                originalUrl = it.originalUrl,
                data = it.data,
                onNavigate = navigate,
                onDismissRequest = onBack,
            )
        }
    }
}
