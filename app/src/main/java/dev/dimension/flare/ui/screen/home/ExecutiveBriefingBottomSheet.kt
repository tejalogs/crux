package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.data.ai.FeedDigest
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.status.DigestAudioPlayerBar
import dev.dimension.flare.ui.component.status.DigestDetailHighlightCard
import dev.dimension.flare.ui.component.status.buildDigestAudioScript
import dev.dimension.flare.ui.component.status.formatDigestTime
import dev.dimension.flare.ui.component.status.rememberAudioBriefingState
import dev.dimension.flare.ui.model.ClickContext

/**
 * Full-screen Daily Brief ("Your catch-up") modal bottom sheet.
 * Structured like a calm news briefing with topic filters, highlight cards,
 * top-bar carousel export, and a slim docked audio player bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ExecutiveBriefingBottomSheet(
    digest: FeedDigest,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onSynthesizeCarousel: ((FeedDigest) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val audioState = rememberAudioBriefingState()
    val audioScript = remember(digest) { buildDigestAudioScript(digest) }
    val formattedTime = remember(digest.generatedAt) { formatDigestTime(digest.generatedAt) }

    var selectedTopic by remember { mutableStateOf<String?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val countSubtitle =
        remember(digest.postCount, formattedTime) {
            context.resources.getQuantityString(
                R.plurals.catch_up_post_count,
                digest.postCount,
                digest.postCount,
                formattedTime,
            )
        }

    val displayedHighlights =
        remember(digest.highlights, selectedTopic) {
            val filter = selectedTopic
            if (filter == null) {
                digest.highlights
            } else {
                digest.highlights.filter {
                    it.topic.equals(filter, ignoreCase = true) ||
                        it.summary.contains(filter.removePrefix("#"), ignoreCase = true)
                }
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
        ) {
            // Header Row: Title + Time Window / Count + Actions (Overflow & Close)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.catch_up_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = countSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Overflow menu: "Make a carousel"
                    if (onSynthesizeCarousel != null) {
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.size(48.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.EllipsisVertical,
                                    contentDescription = stringResource(id = R.string.catch_up_overflow_menu),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(id = R.string.catch_up_make_carousel)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onSynthesizeCarousel(digest)
                                    },
                                )
                            }
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(48.dp),
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Xmark,
                            contentDescription = stringResource(id = R.string.catch_up_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Topic Filter Chips (Expressive shape-morphing ToggleButtons)
            if (digest.topTopics.isNotEmpty()) {
                ExpressiveTopicFilterChipStrip(
                    topics = digest.topTopics,
                    selectedTopic = selectedTopic,
                    onTopicSelected = { selectedTopic = it },
                    allLabel = stringResource(id = R.string.catch_up_all_topics),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Highlight List
            if (displayedHighlights.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.catch_up_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(displayedHighlights, key = { it.topic + (it.authorHandle ?: it.authorName) }) { highlight ->
                        val highlightOnClick = highlight.onClick
                        DigestDetailHighlightCard(
                            highlight = highlight,
                            onReadPost =
                                if (highlightOnClick != null) {
                                    { highlightOnClick.invoke(ClickContext(uriHandler::openUri)) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }

            // Slim Docked Audio Player Bar
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
            ) {
                DigestAudioPlayerBar(
                    audioState = audioState,
                    audioScript = audioScript,
                )
            }
        }
    }
}
