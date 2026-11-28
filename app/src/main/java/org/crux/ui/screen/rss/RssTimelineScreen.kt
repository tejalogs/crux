package org.crux.ui.screen.rss

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.RotateRight
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.rss.RssRepository
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.route.Route

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssTimelineScreen(
    id: Int,
    url: String,
    title: String?,
    onOpenArticle: (Route.Rss.Detail) -> Unit,
    onEditFeed: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(context) { RssRepository.get(context) }
    val feeds by repo.feeds.collectAsState()
    val syncStatus by repo.syncStatus.collectAsState()
    val scope = rememberCoroutineScope()
    val feed = remember(feeds, id, url) {
        feeds.firstOrNull { it.id == id.toLong() } ?: feeds.firstOrNull { it.url.equals(url, ignoreCase = true) }
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    CruxScaffold(
        topBar = {
            CruxTopAppBar(
                scrollBehavior = topAppBarScrollBehavior,
                title = {
                    Text(
                        text = feed?.title ?: title?.takeIf { it.isNotBlank() } ?: url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (feed != null) {
                        IconButton(
                            enabled = !syncStatus.syncing,
                            onClick = { scope.launch { repo.syncFeed(feed.id) } },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = "Refresh feed",
                            )
                        }
                        IconButton(onClick = { onEditFeed(feed.id.toInt()) }) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Pen,
                                contentDescription = stringResource(R.string.edit_rss_source),
                            )
                        }
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        NativeRssFeedList(
            feedId = feed?.id ?: id.toLong().takeIf { it > 0L },
            feedUrl = url,
            contentPadding = contentPadding,
            onOpenArticle = onOpenArticle,
            onManageFeeds = {},
            onAddFeed = {},
        )
    }
}
