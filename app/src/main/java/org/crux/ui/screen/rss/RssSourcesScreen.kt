package org.crux.ui.screen.rss

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FileExport
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.RotateRight
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.Trash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.common.ComposeInAppNotification
import org.crux.rss.RssFeed
import org.crux.rss.RssRepository
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.segmentedShapes2
import org.koin.compose.koinInject
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSourcesScreen(
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onClicked: (RssFeed) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(context) { RssRepository.get(context) }
    val inAppNotification = koinInject<ComposeInAppNotification>()
    val scope = rememberCoroutineScope()
    val feeds by repo.feeds.collectAsState()
    val syncStatus by repo.syncStatus.collectAsState()
    var pendingDelete by remember { mutableStateOf<RssFeed?>(null) }

    LaunchedEffect(repo) {
        repo.ensureLoaded()
    }

    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val exportPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/xml"),
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val exported =
                        try {
                            val opmlContent = repo.exportOpml()
                            val stream =
                                context.contentResolver.openOutputStream(uri)
                                    ?: throw IOException("Cannot open the export file")
                            stream.use { writeOpmlToStream(it, opmlContent) }
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            false
                        }
                    if (exported) {
                        inAppNotification.message(R.string.export_completed)
                    } else {
                        inAppNotification.errorMessage(R.string.export_failed)
                    }
                }
            }
        }

    pendingDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove feed?") },
            text = { Text("Unsubscribe from \"${feed.title}\" and delete its cached articles?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { repo.deleteFeed(feed.id) }
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.rss_sources_title))
                },
                actions = {
                    if (feeds.isNotEmpty()) {
                        IconButton(
                            enabled = !syncStatus.syncing,
                            onClick = { scope.launch { repo.syncAll(force = true) } },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.RotateRight,
                                contentDescription = "Sync all feeds",
                            )
                        }
                        IconButton(
                            onClick = { exportPicker.launch("crux_export.opml") },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.FileExport,
                                contentDescription = stringResource(R.string.opml_export),
                            )
                        }
                    }
                    IconButton(onClick = onAdd) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(R.string.add_rss_source),
                        )
                    }
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            contentPadding = contentPadding,
        ) {
            if (syncStatus.syncing) {
                item(key = "sync_bar") {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                    )
                }
            }
            if (feeds.isEmpty()) {
                item(key = "empty_sources") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.SquareRss,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = "No subscriptions yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Add an RSS/Atom URL, browse curated feeds, or import your OPML library.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(onClick = onAdd) {
                                Text(stringResource(R.string.add_rss_source))
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(feeds, key = { _, feed -> feed.id }) { index, feed ->
                    SegmentedListItem(
                        selected = false,
                        onClick = { onClicked(feed) },
                        shapes = ListItemDefaults.segmentedShapes2(index, feeds.size),
                        leadingContent = {
                            RssFeedFavicon(iconUrl = feed.iconUrl, size = 28.dp)
                        },
                        content = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = feed.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (feed.unreadCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = feed.unreadCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        },
                        supportingContent = {
                            val subtitle =
                                buildString {
                                    if (!feed.category.isNullOrBlank()) {
                                        append(feed.category)
                                        append(" · ")
                                    }
                                    append(feed.host)
                                    if (!feed.lastError.isNullOrBlank()) {
                                        append(" · Sync issue")
                                    }
                                }
                            Text(
                                text = subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color =
                                    if (!feed.lastError.isNullOrBlank()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onEdit(feed.id.toInt()) }) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Pen,
                                        contentDescription = stringResource(R.string.edit_rss_source),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(onClick = { pendingDelete = feed }) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Trash,
                                        contentDescription = "Remove feed",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        },
                    )
                }
                item(key = "sources_footer_space") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

internal fun writeOpmlToStream(
    outputStream: OutputStream,
    opmlContent: String,
) {
    outputStream.write(opmlContent.toByteArray(StandardCharsets.UTF_8))
}
