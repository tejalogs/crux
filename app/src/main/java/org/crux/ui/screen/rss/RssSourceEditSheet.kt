package org.crux.ui.screen.rss

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleXmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.network.PublicUrlValidator
import org.crux.rss.CuratedRssDirectory
import org.crux.rss.DiscoveredFeed
import org.crux.rss.RssFeedDisplayMode
import org.crux.rss.RssNetworkClient
import org.crux.rss.RssRepository
import org.crux.ui.component.FAIcon
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.segmentedShapes2
import org.crux.ui.theme.single

private sealed interface FeedDiscoveryState {
    data object Idle : FeedDiscoveryState

    data object Checking : FeedDiscoveryState

    data class Found(
        val feeds: List<DiscoveredFeed>,
    ) : FeedDiscoveryState

    data class Failed(
        val message: String,
    ) : FeedDiscoveryState
}

@Composable
internal fun RssSourceEditSheet(
    onDismissRequest: () -> Unit,
    onImportOPML: (String) -> Unit,
    id: Int?,
    initialUrl: String? = null,
) {
    val context = LocalContext.current
    val repo = remember(context) { RssRepository.get(context) }
    val feeds by repo.feeds.collectAsState()
    val existingFeed = remember(feeds, id) { id?.let { target -> feeds.firstOrNull { it.id == target.toLong() } } }
    val scope = rememberCoroutineScope()

    var urlText by remember(id, initialUrl) { mutableStateOf(existingFeed?.url ?: initialUrl.orEmpty()) }
    var titleText by remember(id) { mutableStateOf(existingFeed?.title.orEmpty()) }
    var categoryText by remember(id) { mutableStateOf(existingFeed?.category.orEmpty()) }
    var displayMode by remember(id) { mutableStateOf(existingFeed?.displayMode ?: RssFeedDisplayMode.FULL_CONTENT) }
    var discoveryState by remember { mutableStateOf<FeedDiscoveryState>(FeedDiscoveryState.Idle) }
    val selectedDiscovered = remember { mutableStateListOf<DiscoveredFeed>() }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(existingFeed) {
        if (existingFeed != null && urlText.isBlank()) {
            urlText = existingFeed.url
            titleText = existingFeed.title
            categoryText = existingFeed.category.orEmpty()
            displayMode = existingFeed.displayMode
        }
    }

    // Live auto-discovery when typing a URL in Create mode (or after changing URL).
    LaunchedEffect(urlText, id) {
        val raw = urlText.trim()
        if (raw.isEmpty() || (existingFeed != null && raw == existingFeed.url)) {
            discoveryState = FeedDiscoveryState.Idle
            return@LaunchedEffect
        }
        val normalized = RssNetworkClient.normalizeWebUrl(raw)
        if (!PublicUrlValidator.isSafePublicHttpUrl(normalized)) {
            discoveryState = FeedDiscoveryState.Idle
            return@LaunchedEffect
        }
        delay(450L)
        discoveryState = FeedDiscoveryState.Checking
        selectedDiscovered.clear()
        discoveryState =
            try {
                val found = repo.discoverFeeds(normalized)
                if (found.isEmpty()) {
                    FeedDiscoveryState.Failed("No RSS or Atom feed found at this URL")
                } else {
                    if (found.size == 1 && titleText.isBlank()) {
                        titleText = found.first().title
                    } else if (found.size > 1) {
                        selectedDiscovered.add(found.first())
                    }
                    FeedDiscoveryState.Found(found)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedDiscoveryState.Failed(e.message ?: "Could not reach feed")
            }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (id == null && initialUrl.isNullOrBlank()) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    val opmlPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                onImportOPML(it.toString())
                onDismissRequest()
            }
        }

    val normalizedUrl = remember(urlText) { RssNetworkClient.normalizeWebUrl(urlText) }
    val urlLooksValid = remember(normalizedUrl) { PublicUrlValidator.isSafePublicHttpUrl(normalizedUrl) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenHorizontalPadding)
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(id = if (id == null) R.string.add_rss_source else R.string.edit_rss_source),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            label = { Text(stringResource(R.string.rss_sources_url_label)) },
            singleLine = true,
            supportingText = {
                val hint =
                    when (val ds = discoveryState) {
                        is FeedDiscoveryState.Failed -> ds.message
                        else -> stringResource(R.string.subscription_url_hint)
                    }
                Text(text = hint)
            },
            isError = discoveryState is FeedDiscoveryState.Failed,
            trailingIcon = {
                val checkingDesc = stringResource(R.string.rss_source_checking)
                when (discoveryState) {
                    FeedDiscoveryState.Checking -> {
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .size(22.dp)
                                    .semantics { contentDescription = checkingDesc },
                            strokeWidth = 2.dp,
                        )
                    }

                    is FeedDiscoveryState.Found -> {
                        FAIcon(
                            FontAwesomeIcons.Solid.CircleCheck,
                            contentDescription = stringResource(R.string.rss_source_valid),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    is FeedDiscoveryState.Failed -> {
                        FAIcon(
                            FontAwesomeIcons.Solid.CircleXmark,
                            contentDescription = stringResource(R.string.rss_source_invalid),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    FeedDiscoveryState.Idle -> {}
                }
            },
        )

        val discoveredFeeds = (discoveryState as? FeedDiscoveryState.Found)?.feeds.orEmpty()
        if (discoveredFeeds.size > 1 && id == null) {
            Text(
                text = stringResource(R.string.rss_sources_discovered_rss_sources),
                style = MaterialTheme.typography.labelLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                discoveredFeeds.forEachIndexed { index, candidate ->
                    val checked = candidate in selectedDiscovered
                    SegmentedListItem(
                        selected = checked,
                        onClick = {
                            if (checked) selectedDiscovered.remove(candidate) else selectedDiscovered.add(candidate)
                        },
                        shapes = ListItemDefaults.segmentedShapes2(index, discoveredFeeds.size),
                        leadingContent = {
                            RssFeedFavicon(iconUrl = candidate.iconUrl, size = 24.dp)
                        },
                        content = { Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(candidate.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    if (on && candidate !in selectedDiscovered) {
                                        selectedDiscovered.add(candidate)
                                    } else if (!on) {
                                        selectedDiscovered.remove(candidate)
                                    }
                                },
                            )
                        },
                    )
                }
            }
        } else if (urlText.isNotBlank() || id != null) {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text(stringResource(R.string.rss_sources_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = categoryText,
                onValueChange = { categoryText = it },
                label = { Text("Folder / Category (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RssDisplayModePicker(
                selected = displayMode,
                onSelect = { displayMode = it },
            )
        }

        AnimatedVisibility(visible = id == null && urlText.isBlank() && initialUrl.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { opmlPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.opml_import))
                }
                RssCuratedSourcesSection(
                    existingUrls = feeds.map { it.url }.toSet(),
                    onQuickAdd = { curated ->
                        scope.launch {
                            repo.addFeed(
                                url = curated.url,
                                title = curated.title,
                                category = curated.category,
                                displayMode = curated.displayMode,
                            )
                        }
                    },
                    onFillUrl = { curated ->
                        urlText = curated.url
                        titleText = curated.title
                        categoryText = curated.category
                    },
                )
            }
        }

        val canSave =
            !saving &&
                when {
                    id != null -> urlLooksValid && titleText.isNotBlank()
                    discoveredFeeds.size > 1 -> selectedDiscovered.isNotEmpty()
                    else -> urlLooksValid
                }

        if (id != null || urlText.isNotBlank()) {
            Button(
                enabled = canSave,
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            if (id != null) {
                                repo.updateFeed(
                                    id = id.toLong(),
                                    url = normalizedUrl,
                                    title = titleText.trim().ifEmpty { normalizedUrl },
                                    category = categoryText.trim().ifEmpty { null },
                                    displayMode = displayMode,
                                )
                            } else if (discoveredFeeds.size > 1) {
                                selectedDiscovered.forEach { chosen ->
                                    repo.addFeed(
                                        url = chosen.url,
                                        title = chosen.title,
                                        siteUrl = chosen.siteUrl,
                                        iconUrl = chosen.iconUrl,
                                        category = categoryText.trim().ifEmpty { null },
                                        displayMode = displayMode,
                                    )
                                }
                            } else {
                                val single = discoveredFeeds.firstOrNull()
                                repo.addFeed(
                                    url = single?.url ?: normalizedUrl,
                                    title = titleText.trim().ifEmpty { single?.title ?: normalizedUrl },
                                    siteUrl = single?.siteUrl,
                                    iconUrl = single?.iconUrl,
                                    category = categoryText.trim().ifEmpty { null },
                                    displayMode = displayMode,
                                )
                            }
                            onDismissRequest()
                        } finally {
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }

        if (id != null) {
            TextButton(
                onClick = {
                    scope.launch {
                        repo.deleteFeed(id.toLong())
                        onDismissRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remove subscription", color = MaterialTheme.colorScheme.error)
            }
        }

        TextButton(
            onClick = onDismissRequest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(android.R.string.cancel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RssDisplayModePicker(
    selected: RssFeedDisplayMode,
    onSelect: (RssFeedDisplayMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    SegmentedListItem(
        selected = expanded,
        onClick = { expanded = !expanded },
        shapes = ListItemDefaults.single(),
        content = { Text(stringResource(R.string.rss_sources_display_mode)) },
        trailingContent = {
            Text(rssDisplayModeLabel(selected))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RssFeedDisplayMode.entries.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(rssDisplayModeLabel(value)) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun rssDisplayModeLabel(mode: RssFeedDisplayMode): String =
    stringResource(
        when (mode) {
            RssFeedDisplayMode.FULL_CONTENT -> R.string.rss_sources_full_content
            RssFeedDisplayMode.DESCRIPTION_ONLY -> R.string.rss_sources_description_only
            RssFeedDisplayMode.OPEN_IN_BROWSER -> R.string.rss_sources_open_in_browser
        },
    )

@Composable
private fun RssCuratedSourcesSection(
    existingUrls: Set<String>,
    onQuickAdd: (CuratedRssDirectory.CuratedFeed) -> Unit,
    onFillUrl: (CuratedRssDirectory.CuratedFeed) -> Unit,
) {
    var selectedCategoryIndex by remember { mutableStateOf<Int?>(null) }
    val categories = remember { CuratedRssDirectory.categories }
    val displayedFeeds =
        remember(selectedCategoryIndex) {
            if (selectedCategoryIndex == null) {
                CuratedRssDirectory.allFeeds
            } else {
                categories[selectedCategoryIndex!!].feeds
            }
        }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.rss_curated_sources_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.rss_curated_sources_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategoryIndex == null,
                onClick = { selectedCategoryIndex = null },
                label = { Text(stringResource(R.string.rss_curated_category_all)) },
            )
            categories.forEachIndexed { index, cat ->
                FilterChip(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    label = { Text(cat.name) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            displayedFeeds.forEach { feed ->
                val alreadySubscribed = feed.url in existingUrls
                val domain =
                    feed.url
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore("/")
                Surface(
                    onClick = { onFillUrl(feed) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = feed.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = feed.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        FilledTonalButton(
                            enabled = !alreadySubscribed,
                            onClick = { onQuickAdd(feed) },
                        ) {
                            Text(if (alreadySubscribed) "Added" else "Add")
                        }
                    }
                }
            }
        }
    }
}
