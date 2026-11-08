package org.crux.ui.screen.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.SquareRss
import org.crux.data.model.IconType
import org.crux.data.model.tab.RssTimelineData
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.platform.RssTimelineSpecs
import org.crux.ui.component.BottomSheetSceneStrategy
import org.crux.ui.component.FAIcon
import org.crux.ui.component.CruxScaffold
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiText
import org.crux.ui.route.Route
import org.crux.ui.screen.home.TimelineScreen

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
internal fun EntryProviderScope<NavKey>.rssEntryBuilder(
    navigate: (Route) -> Unit,
    onBack: () -> Unit
) {
    entry<Route.Rss.Sources>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "Rss",
            detailPlaceholder = {
                RssPlaceholder()
            },
        )
    ) {
        RssSourcesScreen(
            onAdd = {
                navigate(Route.Rss.Create())
            },
            onEdit = {
                navigate(Route.Rss.Edit(it))
            },
            onClicked = {
                navigate(
                    Route.Rss.Timeline(
                        id = it.id,
                        title = it.title,
                        url = it.url,
                        favIcon = it.favIcon,
                    )
                )
            },
            onBack = onBack,
        )
    }
    entry<Route.Rss.Timeline>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = "Rss",
            detailPlaceholder = {
                RssPlaceholder()
            },
        )
    ) { args ->
        TimelineScreen(
            tabItem = remember(args) {
                RssTimelineSpecs.rss.candidate(
                    data = RssTimelineData(args.url),
                    title = UiText.Raw(args.title ?: args.url),
                    icon =
                        args.favIcon?.let {
                            IconType.Url(args.favIcon)
                        } ?: IconType.Material(UiIcon.Rss),
                ).toUiTimelineTabItem()
            },
            onBack = onBack,
        )
    }
    entry<Route.Rss.Detail>(
        metadata = ListDetailSceneStrategy.detailPane(
            sceneKey = "Rss",
        )
    ) { args ->
        RssDetailScreen(
            url = args.url,
            descriptionHtml = args.descriptionHtml,
            fallbackHtml = args.fallbackHtml,
            descriptionTitle = args.title,
            onBack = onBack,
            toTranslationSettings = { navigate(Route.Settings.TranslationConfig) },
        )
    }
    entry<Route.Rss.Create>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        RssSourceEditSheet(
            onDismissRequest = onBack,
            id = null,
            initialUrl = args.initialUrl,
            onImportOPML = {
                navigate(Route.Rss.OPMLImport(it))
            }
        )
    }
    entry<Route.Rss.Edit>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        RssSourceEditSheet(
            onDismissRequest = onBack,
            id = args.id,
            onImportOPML = {
                navigate(Route.Rss.OPMLImport(it))
            },
        )
    }
    entry<Route.Rss.OPMLImport>(
        metadata = BottomSheetSceneStrategy.bottomSheet()
    ) { args ->
        OPMLImportSheet(
            uri = args.url.toUri(),
            onBack = onBack,
        )
    }
}


@Composable
internal fun RssPlaceholder(
    modifier: Modifier = Modifier,
) {
    CruxScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.SquareRss,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
