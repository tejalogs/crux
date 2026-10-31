package dev.dimension.flare.ui.screen.localfollow

import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.data.model.tab.LocalFollowTimelineSpecs
import dev.dimension.flare.data.model.tab.LocalFollowsTimelineData
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.home.TimelineScreen

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.localFollowEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.LocalFollows.Manage> {
        LocalFollowsScreen(
            onOpenFeed = { groupId, title ->
                navigate(Route.LocalFollows.Timeline(groupId = groupId, title = title))
            },
            onBack = onBack,
        )
    }
    entry<Route.LocalFollows.Timeline> { args ->
        TimelineScreen(
            tabItem =
                remember(args) {
                    LocalFollowTimelineSpecs.follows
                        .candidate(
                            data = LocalFollowsTimelineData(args.groupId),
                            title = UiText.Raw(args.title),
                        ).toUiTimelineTabItem()
                },
            onBack = onBack,
        )
    }
    entry<Route.LocalFollows.Edit>(
        metadata = BottomSheetSceneStrategy.bottomSheet(),
    ) { args ->
        EditLocalFollowSheet(
            accountKey = args.accountKey,
            userKey = args.userKey,
            onDismiss = onBack,
        )
    }
}
