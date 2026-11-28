package org.crux.ui.screen.rss

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.CheckDouble
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.Sliders
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.Xmark
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.model.tab.TimelineSpecIds
import org.crux.rss.RssArticle
import org.crux.rss.RssFeed
import org.crux.rss.RssFeedDisplayMode
import org.crux.rss.RssRepository
import org.crux.ui.component.FAIcon
import org.crux.ui.route.Route
import org.crux.ui.screen.home.ExtractedSocialPost
import org.crux.ui.screen.home.LocalScrollToTopRegistry
import org.crux.ui.screen.home.SaveToBookmarkFolderSheet
import org.crux.ui.screen.home.openInSystemBrowser
import org.crux.ui.theme.screenHorizontalPadding
import java.text.DateFormat
import java.util.Date

internal sealed interface NativeRssTabTarget {
    data object AllFeeds : NativeRssTabTarget

    data class SingleFeed(
        val feedUrl: String,
    ) : NativeRssTabTarget
}

/**
 * Matches a home timeline tab ID to our native RSS reader (either All Feeds or a pinned single feed).
 * Returns null for non-RSS tabs (e.g. live web tabs).
 */
internal fun nativeRssTabTargetForId(id: String): NativeRssTabTarget? =
    when {
        id == TimelineSpecIds.RSS_ALL || id.startsWith("${TimelineSpecIds.RSS_ALL}:") -> {
            NativeRssTabTarget.AllFeeds
        }

        id.startsWith("${TimelineSpecIds.RSS_FEED}:") -> {
            val url = id.removePrefix("${TimelineSpecIds.RSS_FEED}:").trim()
            if (url.isNotEmpty()) NativeRssTabTarget.SingleFeed(url) else NativeRssTabTarget.AllFeeds
        }

        id.startsWith("${TimelineSpecIds.RSS_SUBSCRIPTION}:") -> {
            val rest = id.removePrefix("${TimelineSpecIds.RSS_SUBSCRIPTION}:")
            val url = rest.substringAfter(":", rest).trim()
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                NativeRssTabTarget.SingleFeed(url)
            } else {
                NativeRssTabTarget.AllFeeds
            }
        }

        else -> {
            null
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeRssFeedList(
    onOpenArticle: (Route.Rss.Detail) -> Unit,
    onManageFeeds: () -> Unit,
    modifier: Modifier = Modifier,
    feedId: Long? = null,
    feedUrl: String? = null,
    contentPadding: PaddingValues = PaddingValues(),
    isCurrentlyVisible: Boolean = true,
    showHeaderControls: Boolean = true,
    onAddFeed: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(context) { RssRepository.get(context) }
    val scope = rememberCoroutineScope()
    val feeds by repo.feeds.collectAsState()
    val allArticles by repo.articles.collectAsState()
    val syncStatus by repo.syncStatus.collectAsState()

    val resolvedFeed: RssFeed? =
        remember(feeds, feedId, feedUrl) {
            when {
                feedId != null -> feeds.firstOrNull { it.id == feedId }
                !feedUrl.isNullOrBlank() -> feeds.firstOrNull { it.url.equals(feedUrl, ignoreCase = true) }
                else -> null
            }
        }
    val singleFeedMode = feedId != null || !feedUrl.isNullOrBlank()

    var selectedFilterFeedId by rememberSaveable(feedId, feedUrl) { mutableStateOf<Long?>(null) }
    var unreadOnly by rememberSaveable(feedId, feedUrl) { mutableStateOf(false) }
    var searchOpen by rememberSaveable(feedId, feedUrl) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(feedId, feedUrl) { mutableStateOf("") }
    var pendingBookmark by remember { mutableStateOf<ExtractedSocialPost?>(null) }

    LaunchedEffect(repo) {
        repo.ensureLoaded()
    }

    val effectiveFeedId = if (singleFeedMode) resolvedFeed?.id ?: feedId else selectedFilterFeedId
    val baseArticles =
        remember(allArticles, effectiveFeedId, singleFeedMode, feedUrl) {
            when {
                effectiveFeedId != null -> allArticles.filter { it.feedId == effectiveFeedId }
                singleFeedMode && !feedUrl.isNullOrBlank() -> emptyList()
                else -> allArticles
            }
        }
    val unreadCount = remember(baseArticles) { baseArticles.count { !it.isRead } }
    val displayedArticles =
        remember(baseArticles, unreadOnly, searchQuery) {
            val q = searchQuery.trim()
            baseArticles.filter { article ->
                val passesRead = !unreadOnly || !article.isRead
                val passesSearch =
                    q.isEmpty() ||
                        article.title.contains(q, ignoreCase = true) ||
                        article.snippet.contains(q, ignoreCase = true) ||
                        article.feedTitle.contains(q, ignoreCase = true) ||
                        (article.author?.contains(q, ignoreCase = true) == true)
                passesRead && passesSearch
            }
        }

    val listState = rememberLazyListState()
    val onRefresh: () -> Unit = {
        scope.launch {
            if (effectiveFeedId != null) {
                repo.syncFeed(effectiveFeedId)
            } else {
                repo.syncAll(force = true)
            }
        }
    }
    val latestRefresh by rememberUpdatedState(onRefresh)

    val scrollToTopRegistry = LocalScrollToTopRegistry.current
    if (isCurrentlyVisible && scrollToTopRegistry != null) {
        val callback: () -> Unit =
            remember(listState, scope) {
                {
                    if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                        latestRefresh()
                    } else {
                        scope.launch {
                            if (listState.firstVisibleItemIndex > 20) {
                                listState.scrollToItem(0)
                            } else {
                                listState.animateScrollToItem(0)
                            }
                        }
                    }
                }
            }
        DisposableEffect(scrollToTopRegistry, callback, listState) {
            scrollToTopRegistry.registerCallback(callback)
            onDispose { scrollToTopRegistry.unregisterCallback(callback) }
        }
    }

    pendingBookmark?.let { post ->
        SaveToBookmarkFolderSheet(
            post = post,
            onDismiss = { pendingBookmark = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = syncStatus.syncing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showHeaderControls) {
                item(key = "rss_header_controls") {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenHorizontalPadding)
                                .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilterChip(
                                    selected = !unreadOnly,
                                    onClick = { unreadOnly = false },
                                    label = { Text(stringResource(R.string.rss_curated_category_all)) },
                                )
                                FilterChip(
                                    selected = unreadOnly,
                                    onClick = { unreadOnly = true },
                                    label = {
                                        Text(
                                            if (unreadCount > 0) "Unread ($unreadCount)" else "Unread",
                                        )
                                    },
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        searchOpen = !searchOpen
                                        if (!searchOpen) searchQuery = ""
                                    },
                                ) {
                                    FAIcon(
                                        imageVector =
                                            if (searchOpen) FontAwesomeIcons.Solid.Xmark else FontAwesomeIcons.Solid.MagnifyingGlass,
                                        contentDescription = "Search articles",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                if (unreadCount > 0) {
                                    IconButton(
                                        onClick = {
                                            scope.launch { repo.markAllRead(effectiveFeedId) }
                                        },
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.CheckDouble,
                                            contentDescription = "Mark all read",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                if (!singleFeedMode) {
                                    IconButton(onClick = onManageFeeds) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Sliders,
                                            contentDescription = stringResource(R.string.rss_sources_title),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    IconButton(onClick = onAddFeed) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Plus,
                                            contentDescription = stringResource(R.string.add_rss_source),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = searchOpen) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search headlines & feeds") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon =
                                    if (searchQuery.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                FAIcon(
                                                    FontAwesomeIcons.Solid.Xmark,
                                                    contentDescription = "Clear search",
                                                    modifier = Modifier.size(14.dp),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (!singleFeedMode && feeds.size > 1) {
                item(key = "rss_feed_filter_bar") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = screenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "filter_all_feeds") {
                            FilterChip(
                                selected = selectedFilterFeedId == null,
                                onClick = { selectedFilterFeedId = null },
                                label = { Text("All Feeds (${feeds.size})") },
                            )
                        }
                        items(feeds, key = { "filter_${it.id}" }) { feed ->
                            FilterChip(
                                selected = selectedFilterFeedId == feed.id,
                                onClick = {
                                    selectedFilterFeedId = if (selectedFilterFeedId == feed.id) null else feed.id
                                },
                                leadingIcon = {
                                    RssFeedFavicon(iconUrl = feed.iconUrl, size = 16.dp)
                                },
                                label = {
                                    val label =
                                        if (feed.unreadCount > 0) {
                                            "${feed.title} (${feed.unreadCount})"
                                        } else {
                                            feed.title
                                        }
                                    Text(
                                        text = label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            if (syncStatus.syncing) {
                item(key = "rss_sync_progress") {
                    val progress =
                        if (syncStatus.totalFeeds > 0) {
                            syncStatus.syncedFeeds.toFloat() / syncStatus.totalFeeds.toFloat()
                        } else {
                            0f
                        }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (syncStatus.totalFeeds > 1) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            when {
                feeds.isEmpty() -> {
                    item(key = "rss_empty_no_feeds") {
                        RssEmptyStateCard(
                            title = "No feeds yet",
                            body = "Subscribe to RSS, Atom, or JSON feeds, import an OPML file, or pick from curated sources.",
                            primaryLabel = stringResource(R.string.add_rss_source),
                            onPrimary = onAddFeed,
                        )
                    }
                }

                displayedArticles.isEmpty() && syncStatus.syncing -> {
                    item(key = "rss_syncing_initial") {
                        RssEmptyStateCard(
                            title = "Fetching your feeds…",
                            body = "Checking ${feeds.size} subscriptions for new articles.",
                            primaryLabel = null,
                            onPrimary = null,
                        )
                    }
                }

                displayedArticles.isEmpty() && unreadOnly -> {
                    item(key = "rss_all_caught_up") {
                        RssEmptyStateCard(
                            title = "All caught up",
                            body = "Every article in this view has been read.",
                            primaryLabel = "Show all articles",
                            onPrimary = { unreadOnly = false },
                        )
                    }
                }

                displayedArticles.isEmpty() -> {
                    item(key = "rss_empty_articles") {
                        RssEmptyStateCard(
                            title = if (searchQuery.isNotBlank()) "No matching articles" else "No articles loaded yet",
                            body =
                                if (searchQuery.isNotBlank()) {
                                    "Try a different keyword or clear the search filter."
                                } else {
                                    "Pull down or tap refresh to sync the latest stories."
                                },
                            primaryLabel = if (searchQuery.isNotBlank()) "Clear search" else "Refresh feeds",
                            onPrimary = {
                                if (searchQuery.isNotBlank()) searchQuery = "" else onRefresh()
                            },
                        )
                    }
                }

                else -> {
                    items(
                        items = displayedArticles,
                        key = { it.id },
                    ) { article ->
                        RssArticleCard(
                            article = article,
                            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                            onClick = {
                                scope.launch { repo.markRead(article.id, true) }
                                when (article.feedDisplayMode) {
                                    RssFeedDisplayMode.OPEN_IN_BROWSER -> {
                                        openInSystemBrowser(context, article.link)
                                    }

                                    RssFeedDisplayMode.DESCRIPTION_ONLY -> {
                                        onOpenArticle(
                                            Route.Rss.Detail(
                                                url = article.link,
                                                descriptionHtml = article.summaryHtml,
                                                title = article.title,
                                                fallbackHtml = article.summaryHtml,
                                            ),
                                        )
                                    }

                                    RssFeedDisplayMode.FULL_CONTENT -> {
                                        onOpenArticle(
                                            Route.Rss.Detail(
                                                url = article.link,
                                                title = article.title,
                                                fallbackHtml = article.summaryHtml,
                                            ),
                                        )
                                    }
                                }
                            },
                            onToggleRead = {
                                scope.launch { repo.markRead(article.id, !article.isRead) }
                            },
                            onBookmark = {
                                pendingBookmark =
                                    ExtractedSocialPost(
                                        id = article.link,
                                        title = article.title,
                                        authorName = article.author?.takeIf { it.isNotBlank() } ?: article.feedTitle,
                                        authorHandle = "rss",
                                        authorAvatarUrl = article.feedIconUrl,
                                        contentText = article.snippet.ifBlank { article.title },
                                        sourceUrl = article.link,
                                        platformId = "rss",
                                        mediaUrls = listOfNotNull(article.imageUrl),
                                    )
                            },
                        )
                    }
                    item(key = "rss_bottom_spacer") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RssArticleCard(
    article: RssArticle,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (article.isRead) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!article.isRead) {
                    Box(
                        modifier =
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
                RssFeedFavicon(iconUrl = article.feedIconUrl, size = 16.dp)
                Text(
                    text = article.feedTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val timeLabel = remember(article.publishedAt) { formatRelativeTime(article.publishedAt) }
                if (timeLabel.isNotEmpty()) {
                    Text(
                        text = "· $timeLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!article.cachedFullHtml.isNullOrBlank()) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Download,
                        contentDescription = "Available offline",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onToggleRead)
                            .padding(6.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Check,
                        contentDescription = if (article.isRead) "Mark unread" else "Mark read",
                        tint =
                            if (article.isRead) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            },
                        modifier = Modifier.size(12.dp),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onBookmark)
                            .padding(6.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Bookmark,
                        contentDescription = "Save to bookmarks",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (article.isRead) FontWeight.Medium else FontWeight.SemiBold,
                        color =
                            if (article.isRead) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (article.snippet.isNotBlank()) {
                        Text(
                            text = article.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!article.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RssFeedFavicon(
    iconUrl: String?,
    size: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.SquareRss,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(size * 0.65f),
        )
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RssEmptyStateCard(
    title: String,
    body: String,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = screenHorizontalPadding, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.SquareRss,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (primaryLabel != null && onPrimary != null) {
                Spacer(modifier = Modifier.height(4.dp))
                FilledTonalButton(onClick = onPrimary) {
                    Text(primaryLabel)
                }
            }
        }
    }
}

internal fun formatRelativeTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val now = System.currentTimeMillis()
    return runCatching {
        DateUtils
            .getRelativeTimeSpanString(
                epochMs,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
    }.getOrDefault("")
}

internal fun formatFullDateTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return runCatching {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
    }.getOrDefault("")
}
