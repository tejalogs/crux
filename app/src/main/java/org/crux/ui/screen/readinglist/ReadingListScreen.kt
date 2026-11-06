package org.crux.ui.screen.readinglist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.BookOpen
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.CloudArrowDown
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Ellipsis
import compose.icons.fontawesomeicons.solid.Rss
import compose.icons.fontawesomeicons.solid.TrashCan
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import kotlinx.coroutines.launch
import org.crux.common.OfflineMediaPrefetcher
import org.crux.data.readinglist.ReadingListItem
import org.crux.data.readinglist.ReadingListRepository
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.CruxTopAppBar
import org.crux.ui.component.FAIcon
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingListScreen(
    onBack: () -> Unit,
    toStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository by koinInject<ReadingListRepository>()
    val prefetcher = remember { OfflineMediaPrefetcher(context) }
    val scope = rememberCoroutineScope()

    val items by repository.items.collectAsState()
    val prefetchStatus by prefetcher.status.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ReadingListFilter.ALL) }
    var selectedItemForDetail by remember { mutableStateOf<ReadingListItem?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val filteredItems =
        remember(items, searchQuery, selectedFilter) {
            items.filter { item ->
                val matchesQuery =
                    searchQuery.isBlank() ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.authorName.contains(searchQuery, ignoreCase = true) ||
                        item.authorHandle.contains(searchQuery, ignoreCase = true) ||
                        item.contentText.contains(searchQuery, ignoreCase = true)

                val matchesFilter =
                    when (selectedFilter) {
                        ReadingListFilter.ALL -> true
                        ReadingListFilter.UNREAD -> !item.isRead
                        ReadingListFilter.READ -> item.isRead
                        ReadingListFilter.HAS_MEDIA -> item.mediaUrls.isNotEmpty()
                        ReadingListFilter.RSS -> item.platformId == "rss"
                        ReadingListFilter.INSTAGRAM -> item.platformId == "instagram"
                    }
                matchesQuery && matchesFilter
            }
        }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(text = "Clear Reading List?") },
            text = {
                Text(
                    text =
                        "This will remove all ${items.size} saved offline items. " +
                            "Cached images can still be managed in Storage settings.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        scope.launch { repository.clearAll() }
                    },
                ) {
                    Text(text = "Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    selectedItemForDetail?.let { detailItem ->
        ReadingListDetailSheet(
            item = detailItem,
            onDismiss = { selectedItemForDetail = null },
            onToggleRead = {
                scope.launch { repository.markAsRead(detailItem.id, !detailItem.isRead) }
                selectedItemForDetail = detailItem.copy(isRead = !detailItem.isRead)
            },
            onDelete = {
                scope.launch { repository.remove(detailItem.id) }
                selectedItemForDetail = null
            },
        )
    }

    CruxScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                CruxTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Reading List",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${items.size} saved for offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        if (prefetchStatus.isPrefetching) {
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { prefetcher.prefetchAll(items) },
                                enabled = items.isNotEmpty(),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.CloudArrowDown,
                                    contentDescription = "Prefetch Media for Offline",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = toStorageSettings) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Database,
                                contentDescription = "Cache & Storage Settings",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (items.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirmation = true }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.TrashCan,
                                    contentDescription = "Clear All",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    },
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
                )
                if (prefetchStatus.isPrefetching) {
                    LinearProgressIndicator(
                        progress = { prefetchStatus.progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("Search offline bookmarks...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ReadingListFilter.entries) { filter ->
                    val count =
                        when (filter) {
                            ReadingListFilter.ALL -> items.size
                            ReadingListFilter.UNREAD -> items.count { !it.isRead }
                            ReadingListFilter.READ -> items.count { it.isRead }
                            ReadingListFilter.HAS_MEDIA -> items.count { it.mediaUrls.isNotEmpty() }
                            ReadingListFilter.RSS -> items.count { it.platformId == "rss" }
                            ReadingListFilter.INSTAGRAM -> items.count { it.platformId == "instagram" }
                        }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text("${filter.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.BookOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching saved items" else "No saved items for offline reading",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Save any post, article, or photo bookmark to access it anytime offline without network.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        ReadingListItemCard(
                            item = item,
                            onClick = { selectedItemForDetail = item },
                            onToggleRead = { scope.launch { repository.markAsRead(item.id, !item.isRead) } },
                            onDelete = { scope.launch { repository.remove(item.id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingListItemCard(
    item: ReadingListItem,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header Row: Platform badge, author info, read status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlatformBadge(platformId = item.platformId)

                if (!item.authorAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.authorAvatarUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.authorName.takeIf { it.isNotBlank() } ?: item.authorHandle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.authorHandle.isNotBlank() && item.authorHandle != item.authorName) {
                        Text(
                            text = item.authorHandle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Read indicator
                if (item.isRead) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "Read",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Date
                val formattedDate =
                    remember(item.savedAtTimestamp) {
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(item.savedAtTimestamp))
                    }
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Title if present
            if (item.title.isNotBlank() && item.title != item.contentText) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Content snippet
            Text(
                text = item.contentText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            // Media thumbnails row
            if (item.mediaUrls.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(item.mediaUrls) { mediaUrl ->
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = "Saved media",
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            // Actions row: Read/Unread, Delete, Open
            val uriHandler = LocalUriHandler.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        val uri =
                            DeeplinkRoute.Status
                                .OmniScribe(
                                    statusKey = MicroBlogKey(item.id.hashCode().toString(), item.platformId),
                                    accountType = AccountType.Guest,
                                    initialText = item.contentText,
                                    authorName = item.authorName.takeIf { it.isNotBlank() } ?: item.authorHandle,
                                    authorHandle = item.authorHandle,
                                    articleTitle = item.title,
                                    sourceUrl = item.sourceUrl,
                                ).toUri()
                        uriHandler.openUri(uri)
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Synthesize",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                TextButton(onClick = onToggleRead) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Check,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (item.isRead) "Mark Unread" else "Mark Read", style = MaterialTheme.typography.labelSmall)
                }

                IconButton(onClick = onDelete) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.TrashCan,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformBadge(platformId: String) {
    val (icon, brush) =
        when (platformId.lowercase()) {
            "instagram" -> {
                FontAwesomeIcons.Brands.Instagram to
                    Brush.linearGradient(listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045)))
            }

            "rss" -> {
                FontAwesomeIcons.Solid.Rss to
                    Brush.linearGradient(listOf(Color(0xFFF26522), Color(0xFFFF9900)))
            }

            else -> {
                FontAwesomeIcons.Solid.Bookmark to
                    Brush.linearGradient(listOf(Color(0xFF4A6572), Color(0xFF344955)))
            }
        }

    Box(
        modifier =
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        FAIcon(
            imageVector = icon,
            contentDescription = platformId,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("DEPRECATION")
@Composable
private fun ReadingListDetailSheet(
    item: ReadingListItem,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlatformBadge(platformId = item.platformId)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.authorName.takeIf { it.isNotBlank() } ?: item.authorHandle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = item.authorHandle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = {
                        uriHandler.openUri(item.sourceUrl)
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                        contentDescription = "Open Source Link",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Title
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Body Content
            Text(
                text = item.contentText,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 22.sp,
            )

            // Media Photos in Grid / Flow
            if (item.mediaUrls.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.mediaUrls.forEach { mediaUrl ->
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = "Full photo",
                            modifier =
                                Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val uri =
                        DeeplinkRoute.Status
                            .OmniScribe(
                                statusKey = MicroBlogKey(item.id.hashCode().toString(), item.platformId),
                                accountType = AccountType.Guest,
                                initialText = item.contentText,
                                authorName = item.authorName.takeIf { it.isNotBlank() } ?: item.authorHandle,
                                authorHandle = item.authorHandle,
                                articleTitle = item.title,
                                sourceUrl = item.sourceUrl,
                            ).toUri()
                    uriHandler.openUri(uri)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Synthesize Post & Carousel", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDelete) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.TrashCan,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }

                TextButton(onClick = onToggleRead) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (item.isRead) "Mark as Unread" else "Mark as Read")
                }
            }
        }
    }
}

private enum class ReadingListFilter(
    val label: String,
) {
    ALL("All"),
    UNREAD("Unread"),
    READ("Read"),
    HAS_MEDIA("Media"),
    RSS("RSS"),
    INSTAGRAM("Instagram"),
}
