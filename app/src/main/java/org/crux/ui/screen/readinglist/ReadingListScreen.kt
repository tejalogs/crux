package org.crux.ui.screen.readinglist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.CloudArrowDown
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Folder
import compose.icons.fontawesomeicons.solid.FolderPlus
import compose.icons.fontawesomeicons.solid.LayerGroup
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.TrashCan
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import compose.icons.fontawesomeicons.solid.Xmark
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.common.OfflineMediaPrefetcher
import org.crux.data.readinglist.BookmarkFolder
import org.crux.data.readinglist.DEFAULT_BOOKMARK_FOLDER_ID
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
import org.crux.ui.route.Route
import org.crux.ui.route.toUri
import org.crux.ui.screen.home.AuthorWithPlatformAvatar
import org.crux.ui.screen.home.CreateBookmarkFolderDialog
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.SocialPlatformBadge
import org.crux.ui.screen.home.cleanOutgoingLink
import org.crux.ui.screen.home.folderIconForName
import org.crux.ui.screen.home.openLinkOutsideWebView
import org.crux.ui.screen.home.platformDisplayName
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingListScreen(
    onBack: () -> Unit,
    toStorageSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigate: (Route) -> Unit = {},
) {
    val context = LocalContext.current
    val repository by koinInject<ReadingListRepository>()
    val prefetcher = remember { OfflineMediaPrefetcher(context) }
    val scope = rememberCoroutineScope()

    val items by repository.items.collectAsState()
    val folders by repository.folders.collectAsState()
    val prefetchStatus by prefetcher.status.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(BookmarkPlatformFilter.ALL) }
    var selectedItemForDetailId by remember { mutableStateOf<String?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderPendingEdit by remember { mutableStateOf<BookmarkFolder?>(null) }
    var folderPendingDelete by remember { mutableStateOf<BookmarkFolder?>(null) }

    val foldersById = remember(folders) { folders.associateBy { it.id } }
    val selectedItemForDetail = remember(items, selectedItemForDetailId) {
        selectedItemForDetailId?.let { id -> items.firstOrNull { it.id == id } }
    }

    val filteredItems =
        remember(items, searchQuery, selectedFolderId, selectedFilter) {
            items.filter { item ->
                val matchesFolder = selectedFolderId == null || item.folderId == selectedFolderId
                val matchesQuery =
                    searchQuery.isBlank() ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.authorName.contains(searchQuery, ignoreCase = true) ||
                        item.authorHandle.contains(searchQuery, ignoreCase = true) ||
                        item.contentText.contains(searchQuery, ignoreCase = true) ||
                        item.note.contains(searchQuery, ignoreCase = true)

                val platform = item.platformId.lowercase()
                val matchesFilter =
                    when (selectedFilter) {
                        BookmarkPlatformFilter.ALL -> true
                        BookmarkPlatformFilter.X -> platform == "x" || platform == "twitter"
                        BookmarkPlatformFilter.INSTAGRAM -> platform == "instagram"
                        BookmarkPlatformFilter.LINKEDIN -> platform == "linkedin"
                        BookmarkPlatformFilter.REDDIT -> platform == "reddit"
                        BookmarkPlatformFilter.RSS -> platform == "rss"
                        BookmarkPlatformFilter.HAS_MEDIA -> item.mediaUrls.isNotEmpty()
                        BookmarkPlatformFilter.UNREAD -> !item.isRead
                    }
                matchesFolder && matchesQuery && matchesFilter
            }
        }

    if (showCreateFolderDialog) {
        CreateBookmarkFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name, iconName, colorHex ->
                scope.launch {
                    val folder =
                        repository.createFolder(
                            name = name,
                            iconName = iconName,
                            colorHex = colorHex,
                            createdAtTimestamp = System.currentTimeMillis(),
                        )
                    selectedFolderId = folder.id
                    showCreateFolderDialog = false
                }
            },
        )
    }

    folderPendingEdit?.let { editing ->
        CreateBookmarkFolderDialog(
            initialName = editing.name,
            initialIcon = editing.iconName,
            initialColor = editing.colorHex,
            isEditing = true,
            onDismiss = { folderPendingEdit = null },
            onCreate = { name, iconName, colorHex ->
                val targetId = editing.id
                folderPendingEdit = null
                scope.launch {
                    repository.updateFolder(
                        folderId = targetId,
                        name = name,
                        iconName = iconName,
                        colorHex = colorHex,
                    )
                }
            },
        )
    }

    folderPendingDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            title = { Text("Delete \"${folder.name}\"?") },
            text = {
                Text("Bookmarks inside this folder will be kept and moved to Quick Saves.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idToDelete = folder.id
                        folderPendingDelete = null
                        if (selectedFolderId == idToDelete) selectedFolderId = null
                        scope.launch { repository.deleteFolder(idToDelete) }
                    },
                ) {
                    Text("Delete folder", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(text = stringResource(id = R.string.reading_list_clear_title)) },
            text = {
                Text(
                    text = stringResource(id = R.string.reading_list_clear_message, items.size),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        scope.launch { repository.clearAll() }
                    },
                ) {
                    Text(text = stringResource(id = R.string.reading_list_clear_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(text = stringResource(id = android.R.string.cancel))
                }
            },
        )
    }

    selectedItemForDetail?.let { detailItem ->
        ReadingListDetailSheet(
            item = detailItem,
            folders = folders,
            onDismiss = { selectedItemForDetailId = null },
            onMoveFolder = { targetFolderId ->
                scope.launch { repository.moveToFolder(detailItem.id, targetFolderId) }
            },
            onUpdateNote = { newNote ->
                scope.launch { repository.updateNote(detailItem.id, newNote) }
            },
            onToggleRead = {
                scope.launch { repository.markAsRead(detailItem.id, !detailItem.isRead) }
            },
            onDelete = {
                scope.launch { repository.remove(detailItem.id) }
                selectedItemForDetailId = null
            },
            onOpenSource = {
                selectedItemForDetailId = null
                openBookmarkSource(context, detailItem.sourceUrl, detailItem.platformId, onNavigate)
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
                                text = "Bookmarks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${items.size} saved · ${folders.size} folders",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        BackButton(onBack = onBack)
                    },
                    actions = {
                        IconButton(onClick = { showCreateFolderDialog = true }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.FolderPlus,
                                contentDescription = "New Bookmark Folder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
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
                                    contentDescription = stringResource(id = R.string.reading_list_prefetch),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                        IconButton(onClick = toStorageSettings) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Database,
                                contentDescription = stringResource(id = R.string.reading_list_storage_settings),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        if (items.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirmation = true }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.TrashCan,
                                    contentDescription = stringResource(id = R.string.reading_list_clear_all),
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
            // Bookmark Folders Bento Carousel
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "all_bookmarks_folder") {
                    val isSelected = selectedFolderId == null
                    Surface(
                        onClick = { selectedFolderId = null },
                        shape = RoundedCornerShape(18.dp),
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        border =
                            BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    },
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.LayerGroup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "All Bookmarks",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${items.size} ${if (items.size == 1) "post" else "posts"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                items(folders, key = { it.id }) { folder ->
                    val isSelected = selectedFolderId == folder.id
                    val accent = Color(folder.colorHex)
                    val count = items.count { it.folderId == folder.id }
                    Surface(
                        onClick = {
                            selectedFolderId = if (isSelected) null else folder.id
                        },
                        shape = RoundedCornerShape(18.dp),
                        color =
                            if (isSelected) {
                                accent.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        border =
                            BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(accent.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                FAIcon(
                                    imageVector = folderIconForName(folder.iconName),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "$count ${if (count == 1) "post" else "posts"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected && !folder.isDefault) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    IconButton(
                                        onClick = { folderPendingEdit = folder },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Pen,
                                            contentDescription = "Edit folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { folderPendingDelete = folder },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        FAIcon(
                                            imageVector = FontAwesomeIcons.Solid.Xmark,
                                            contentDescription = "Delete folder",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "add_new_folder_card") {
                    Surface(
                        onClick = { showCreateFolderDialog = true },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Plus,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Text(
                                text = "New Folder",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search saved posts, @handles, or notes…") },
                leadingIcon = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon =
                    if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Xmark,
                                    contentDescription = "Clear search",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )

            // Cross-Platform Filter Chips (show All, active filter, and non-empty categories only)
            val folderScopedItems =
                remember(items, selectedFolderId) {
                    if (selectedFolderId == null) items else items.filter { it.folderId == selectedFolderId }
                }
            val visibleFilterCounts =
                remember(folderScopedItems, selectedFilter) {
                    BookmarkPlatformFilter.entries
                        .map { filter ->
                            val count =
                                when (filter) {
                                    BookmarkPlatformFilter.ALL -> folderScopedItems.size
                                    BookmarkPlatformFilter.X ->
                                        folderScopedItems.count {
                                            val p = it.platformId.lowercase()
                                            p == "x" || p == "twitter"
                                        }
                                    BookmarkPlatformFilter.INSTAGRAM ->
                                        folderScopedItems.count { it.platformId.equals("instagram", ignoreCase = true) }
                                    BookmarkPlatformFilter.LINKEDIN ->
                                        folderScopedItems.count { it.platformId.equals("linkedin", ignoreCase = true) }
                                    BookmarkPlatformFilter.REDDIT ->
                                        folderScopedItems.count { it.platformId.equals("reddit", ignoreCase = true) }
                                    BookmarkPlatformFilter.RSS ->
                                        folderScopedItems.count { it.platformId.equals("rss", ignoreCase = true) }
                                    BookmarkPlatformFilter.HAS_MEDIA ->
                                        folderScopedItems.count { it.mediaUrls.isNotEmpty() }
                                    BookmarkPlatformFilter.UNREAD ->
                                        folderScopedItems.count { !it.isRead }
                                }
                            filter to count
                        }.filter { (filter, count) ->
                            filter == BookmarkPlatformFilter.ALL || filter == selectedFilter || count > 0
                        }
                }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleFilterCounts, key = { it.first.name }) { (filter, count) ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text("${filter.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }

            if (filteredItems.isEmpty()) {
                val activeFolder = selectedFolderId?.let { foldersById[it] }
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            FAIcon(
                                imageVector =
                                    if (activeFolder != null) {
                                        folderIconForName(activeFolder.iconName)
                                    } else {
                                        FontAwesomeIcons.Solid.Bookmark
                                    },
                                contentDescription = null,
                                tint =
                                    if (activeFolder != null) {
                                        Color(activeFolder.colorHex)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Text(
                            text =
                                when {
                                    searchQuery.isNotBlank() -> stringResource(id = R.string.reading_list_empty_no_match)
                                    activeFolder != null -> "No bookmarks in \"${activeFolder.name}\" yet"
                                    else -> "Your Universal Bookmarks folder is ready"
                                },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text =
                                "Tap the Bookmark icon in the bottom dock while browsing X, Instagram, LinkedIn, or Reddit to save any post into your folders.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val folder = foldersById[item.folderId] ?: foldersById[DEFAULT_BOOKMARK_FOLDER_ID]
                        ReadingListItemCard(
                            item = item,
                            folder = folder,
                            allFolders = folders,
                            onClick = { selectedItemForDetailId = item.id },
                            onOpenSource = { openBookmarkSource(context, item.sourceUrl, item.platformId, onNavigate) },
                            onMoveFolder = { targetFolderId ->
                                scope.launch { repository.moveToFolder(item.id, targetFolderId) }
                            },
                            onToggleRead = { scope.launch { repository.markAsRead(item.id, !item.isRead) } },
                            onDelete = { scope.launch { repository.remove(item.id) } },
                        )
                    }
                }
            }
        }
    }
}

private fun openBookmarkSource(
    context: android.content.Context,
    url: String,
    platformId: String,
    onNavigate: (Route) -> Unit,
) {
    if (url.isBlank()) return
    val cleaned = cleanOutgoingLink(url.trim())
    val host = runCatching { URI(cleaned).host?.lowercase() }.getOrNull()
    val normPlatform = platformId.lowercase().trim()
    when {
        LiveWebSite.Instagram.allows(host) || normPlatform == "instagram" -> {
            onNavigate(Route.InstagramWeb(initialUrl = cleaned))
        }

        LiveWebSite.LinkedIn.allows(host) || normPlatform == "linkedin" -> {
            onNavigate(Route.LinkedInWeb(initialUrl = cleaned))
        }

        LiveWebSite.X.allows(host) || normPlatform == "x" || normPlatform == "twitter" -> {
            onNavigate(Route.LiveWeb(site = LiveWebSite.X.name, initialUrl = cleaned))
        }

        LiveWebSite.Reddit.allows(host) || normPlatform == "reddit" -> {
            onNavigate(Route.LiveWeb(site = LiveWebSite.Reddit.name, initialUrl = cleaned))
        }

        LiveWebSite.Mastodon.allows(host) || normPlatform == "mastodon" -> {
            onNavigate(Route.LiveWeb(site = LiveWebSite.Mastodon.name, initialUrl = cleaned))
        }

        else -> {
            openLinkOutsideWebView(
                context = context,
                url = cleaned,
                clean = true,
                openInReader = normPlatform == "rss" || org.crux.ui.screen.home.LiveWebPrivacyStore.get(context).settings.value.openLinksInReader,
            )
        }
    }
}

@Composable
private fun ReadingListItemCard(
    item: ReadingListItem,
    folder: BookmarkFolder?,
    allFolders: List<BookmarkFolder>,
    onClick: () -> Unit,
    onOpenSource: () -> Unit,
    onMoveFolder: (String) -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFolderMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header Row: Platform badge, author info, folder chip, date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AuthorWithPlatformAvatar(
                    avatarUrl = item.authorAvatarUrl,
                    platformId = item.platformId,
                    size = 32.dp,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            item.authorName.takeIf { it.isNotBlank() }
                                ?: item.authorHandle.takeIf { it.isNotBlank() }
                                ?: platformDisplayName(item.platformId),
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

                // Interactive Folder Chip
                if (folder != null) {
                    val folderColor = Color(folder.colorHex)
                    Box {
                        Surface(
                            onClick = { showFolderMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            color = folderColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, folderColor.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                FAIcon(
                                    imageVector = folderIconForName(folder.iconName),
                                    contentDescription = null,
                                    tint = folderColor,
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = folderColor,
                                    maxLines = 1,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showFolderMenu,
                            onDismissRequest = { showFolderMenu = false },
                        ) {
                            allFolders.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.name) },
                                    leadingIcon = {
                                        FAIcon(
                                            imageVector = folderIconForName(candidate.iconName),
                                            contentDescription = null,
                                            tint = Color(candidate.colorHex),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                    trailingIcon =
                                        if (candidate.id == item.folderId) {
                                            {
                                                FAIcon(
                                                    imageVector = FontAwesomeIcons.Solid.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                    onClick = {
                                        showFolderMenu = false
                                        onMoveFolder(candidate.id)
                                    },
                                )
                            }
                        }
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

            // Title if distinct from body
            if (item.title.isNotBlank() && item.title != item.contentText && !item.contentText.startsWith(item.title.removeSuffix("…"))) {
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
                color =
                    if (item.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            // Personal note if present
            if (item.note.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Note: ${item.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Media thumbnails row
            if (item.mediaUrls.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(item.mediaUrls) { mediaUrl ->
                        AsyncImage(
                            model = mediaUrl,
                            contentDescription = stringResource(id = R.string.reading_list_saved_media),
                            modifier =
                                Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            // Actions row: Open in Platform, AI Remix, Mark Read, Delete
            val uriHandler = LocalUriHandler.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenSource) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Open in ${platformDisplayName(item.platformId)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = stringResource(id = R.string.reading_list_create_post),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    IconButton(onClick = onToggleRead, modifier = Modifier.size(34.dp)) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Check,
                            contentDescription = stringResource(id = if (item.isRead) R.string.reading_list_mark_unread else R.string.reading_list_mark_read),
                            tint = if (item.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.TrashCan,
                            contentDescription = stringResource(id = R.string.reading_list_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("DEPRECATION")
@Composable
private fun ReadingListDetailSheet(
    item: ReadingListItem,
    folders: List<BookmarkFolder>,
    onDismiss: () -> Unit,
    onMoveFolder: (String) -> Unit,
    onUpdateNote: (String) -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val noteRequester = remember { BringIntoViewRequester() }
    var isNoteFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    var noteDraft by remember(item.id, item.note) { mutableStateOf(item.note) }

    LaunchedEffect(isNoteFocused, isImeVisible, scrollState.maxValue, noteDraft.length) {
        if (isNoteFocused && isImeVisible) {
            noteRequester.bringIntoView()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (noteDraft.trim() != item.note) {
                onUpdateNote(noteDraft.trim())
            }
            onDismiss()
        },
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.modalWindowInsets.union(WindowInsets.ime) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SocialPlatformBadge(platformId = item.platformId, size = 32.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.authorName.takeIf { it.isNotBlank() } ?: platformDisplayName(item.platformId),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.authorHandle.isNotBlank()) {
                        Text(
                            text = item.authorHandle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                TextButton(
                    onClick = {
                        onOpenSource()
                        onDismiss()
                    },
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                        contentDescription = stringResource(id = R.string.reading_list_open_source),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open in ${platformDisplayName(item.platformId)}")
                }
            }

            // Folder selector strip
            Text(
                text = "Folder",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders, key = { it.id }) { folder ->
                    val isSelected = item.folderId == folder.id
                    val accent = Color(folder.colorHex)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onMoveFolder(folder.id) },
                        label = { Text(folder.name) },
                        leadingIcon = {
                            FAIcon(
                                imageVector = folderIconForName(folder.iconName),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(12.dp),
                            )
                        },
                    )
                }
            }

            // Title
            if (item.title.isNotBlank() && item.title != item.contentText) {
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
                            contentDescription = stringResource(id = R.string.reading_list_full_photo),
                            modifier =
                                Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            // Personal Note Editor
            OutlinedTextField(
                value = noteDraft,
                onValueChange = {
                    noteDraft = it
                    onUpdateNote(it)
                },
                label = { Text("Personal note") },
                placeholder = { Text("Add your thoughts or why you saved this post…") },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(noteRequester)
                        .onFocusChanged { focusState ->
                            isNoteFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                scope.launch { noteRequester.bringIntoView() }
                            }
                        },
                shape = RoundedCornerShape(12.dp),
            )

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
                Text(stringResource(id = R.string.reading_list_create_post_long), fontWeight = FontWeight.Bold)
            }

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
                    Text(stringResource(id = R.string.reading_list_delete), color = MaterialTheme.colorScheme.error)
                }

                TextButton(onClick = onToggleRead) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = if (item.isRead) R.string.reading_list_mark_as_unread else R.string.reading_list_mark_as_read))
                }
            }
        }
    }
}

private enum class BookmarkPlatformFilter(
    val label: String,
) {
    ALL("All"),
    X("X"),
    INSTAGRAM("Instagram"),
    LINKEDIN("LinkedIn"),
    REDDIT("Reddit"),
    RSS("RSS"),
    HAS_MEDIA("Media"),
    UNREAD("Unread"),
}
