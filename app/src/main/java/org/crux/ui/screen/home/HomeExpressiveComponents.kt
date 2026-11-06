package org.crux.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Sliders
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import org.crux.R
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.component.FAIcon
import org.crux.ui.component.TabIcon
import org.crux.ui.component.Text as CruxText

/**
 * Material 3 Expressive tab strip for the home timeline: each tab is a ToggleButton whose
 * shape morphs from a pill to a rounded square when selected. Purely presentational —
 * selection state and navigation stay with the caller's pager.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveHomeTabStrip(
    tabs: List<UiTimelineTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedLabelOnly: Boolean = false,
    uncheckedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    trailing: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTabIndex) {
        listState.animateScrollToItem((selectedTabIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
            val isSelected = index == selectedTabIndex
            val showLabel = isSelected || (!selectedLabelOnly && tabs.size <= COMPACT_LABEL_THRESHOLD)
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onTabSelected(index) },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = uncheckedContainerColor,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = PaddingValues(horizontal = if (showLabel) 12.dp else 8.dp),
            ) {
                TabIcon(
                    tabItem = tab,
                    size = 18.dp,
                    color = LocalContentColor.current,
                )
                if (showLabel) {
                    CruxText(
                        text = tab.title,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (trailing != null) {
            item(key = "trailing") {
                trailing()
            }
        }
    }
}

/**
 * Material 3 Expressive filter chip strip for topic strings (e.g. in the Daily Brief).
 * Sibling to [ExpressiveHomeTabStrip], using ToggleButton for shape morphing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveTopicFilterChipStrip(
    topics: List<String>,
    selectedTopic: String?,
    onTopicSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "All",
) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all_topics") {
            val isSelected = selectedTopic == null
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onTopicSelected(null) },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(
                    text = allLabel,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                )
            }
        }
        items(topics.size, key = { topics[it] }) { index ->
            val topic = topics[index]
            val isSelected = selectedTopic.equals(topic, ignoreCase = true)
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onTopicSelected(if (isSelected) null else topic) },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Text(
                    text = topic,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                )
            }
        }
    }
}

/**
 * Avatar framed in an expressive "cookie" shape, used as the home top bar's drawer button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveAvatarFrame(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(size - 8.dp).clip(shape)) {
                content()
            }
        }
    }
}

/**
 * Content of the expanded Home item in the bottom dock: [drawer avatar] [feed pills … edit].
 * Tapping the already-selected pill calls [onReselect] (scroll to top / refresh), matching the
 * old "tap Home again" behaviour. Purely presentational — every action is a caller callback.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveHomeDockTabs(
    tabs: List<UiTimelineTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onReselect: () -> Unit,
    onEditTabs: () -> Unit,
    drawerButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    loginButton: (@Composable () -> Unit)? = null,
    smartReplyAction: (() -> Unit)? = null,
) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        drawerButton()
        ExpressiveHomeTabStrip(
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { index ->
                if (index == selectedTabIndex) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    onReselect()
                } else {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onTabSelected(index)
                }
            },
            selectedLabelOnly = true,
            uncheckedContainerColor = Color.Transparent,
            modifier = Modifier.weight(1f),
        )
        // Outside the scrolling strip so it stays reachable however many feeds there are.
        loginButton?.invoke()
        if (smartReplyAction != null) {
            IconButton(
                onClick = smartReplyAction,
                shapes = IconButtonDefaults.shapes(),
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                    contentDescription = stringResource(R.string.live_web_smart_reply),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        IconButton(
            onClick = onEditTabs,
            shapes = IconButtonDefaults.shapes(),
        ) {
            FAIcon(
                imageVector = FontAwesomeIcons.Solid.Sliders,
                contentDescription = stringResource(R.string.edit_tab_title),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// Above this many tabs, unselected tabs collapse to icon-only so the strip stays scannable.
private const val COMPACT_LABEL_THRESHOLD = 4
