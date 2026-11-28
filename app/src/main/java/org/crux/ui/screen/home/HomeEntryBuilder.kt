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
            toReadingList = {
                navigate(Route.ReadingList)
            },
            onNavigate = navigate,
        )
    }
    entry<Route.Timeline> { args ->
        TimelineScreen(
            tabItem = args.tabItem,
            onBack = onBack,
            onNavigate = navigate,
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
            onNavigate = navigate,
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
            forSignIn = args.forSignIn,
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
            forSignIn = args.forSignIn,
        )
    }

    entry<Route.LiveWeb>(
        metadata = ListDetailSceneStrategy.detailPane("live_web"),
    ) { args ->
        LiveWebScreen(
            siteName = args.site,
            onBack = onBack,
            initialUrl = args.initialUrl,
            forSignIn = args.forSignIn,
        )
    }

    // Full-screen on purpose: never give this bottom-sheet or dialog metadata, so it can never
    // open in its own window above the App Lock cover.
    entry<Route.LiveShare>(
        metadata = ListDetailSceneStrategy.detailPane("live_share"),
    ) { args ->
        LiveShareScreen(
            siteNames = args.sites,
            text = args.text,
            url = args.url,
            slidesSaved = args.slidesSaved,
            onBack = onBack,
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
            onNavigate = navigate,
        )
    }
}
