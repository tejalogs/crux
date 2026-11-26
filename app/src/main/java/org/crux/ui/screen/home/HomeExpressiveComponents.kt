package org.crux.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Sliders
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import org.crux.R
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.ui.component.BottomBarAccessory
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalBottomBarAccessory
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
    iconOnly: Boolean = false,
    uncheckedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (fitsWithoutScrolling(iconOnly, tabs.size) && trailing == null) {
        // Few enough pills to share the width equally, so none can end up behind a neighbouring button.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    HomeTabPill(
                        tab = tab,
                        isSelected = index == selectedTabIndex,
                        showLabel = false,
                        uncheckedContainerColor = uncheckedContainerColor,
                        onClick = { onTabSelected(index) },
                        modifier = Modifier.weight(1f),
                        horizontalPadding = 2.dp,
                        minWidth = 0.dp,
                    )
                }
            }
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTabIndex, iconOnly, tabs.size) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isNotEmpty() && visible.any { it.index == selectedTabIndex }) {
            return@LaunchedEffect
        }
        listState.animateScrollToItem((selectedTabIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = if (iconOnly) 2.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (iconOnly) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
            val isSelected = index == selectedTabIndex
            HomeTabPill(
                tab = tab,
                isSelected = isSelected,
                showLabel = !iconOnly && (isSelected || (!selectedLabelOnly && tabs.size <= COMPACT_LABEL_THRESHOLD)),
                uncheckedContainerColor = uncheckedContainerColor,
                onClick = { onTabSelected(index) },
                horizontalPadding = if (iconOnly) 6.dp else 12.dp,
                minWidth = if (iconOnly) 36.dp else 48.dp,
            )
        }
        if (trailing != null) {
            item(key = "trailing") {
                trailing()
            }
        }
    }
}

/** The most pills that share the dock width equally on a narrow phone; beyond this the strip scrolls. */
internal const val MAX_EQUAL_WIDTH_PILLS = 7

internal fun fitsWithoutScrolling(
    iconOnly: Boolean,
    tabCount: Int,
): Boolean = iconOnly && tabCount in 1..MAX_EQUAL_WIDTH_PILLS

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeTabPill(
    tab: UiTimelineTabItem,
    isSelected: Boolean,
    showLabel: Boolean,
    uncheckedContainerColor: Color,
    onClick: () -> Unit,
    horizontalPadding: Dp,
    minWidth: Dp,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = isSelected,
        onCheckedChange = { onClick() },
        modifier = modifier.defaultMinSize(minWidth = minWidth, minHeight = 44.dp),
        colors =
            ToggleButtonDefaults.colors(
                containerColor = uncheckedContainerColor,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
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
    bookmarkAction: (() -> Unit)? = null,
    onOpenBookmarks: (() -> Unit)? = null,
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
            iconOnly = tabs.size > 3,
            uncheckedContainerColor = Color.Transparent,
            modifier = Modifier.weight(1f),
        )
        // Outside the scrolling strip so it stays reachable however many feeds there are.
        loginButton?.invoke()
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 3.dp)
                    .width(1.dp)
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            // Both post actions show when a live tab offers both: saving never hides smart reply.
            if (bookmarkAction != null) {
                val openReadingListLabel = stringResource(R.string.live_web_open_reading_list)
                Box(
                    modifier =
                        Modifier
                            .size(DOCK_ACTION_SIZE)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = bookmarkAction,
                                onLongClickLabel = openReadingListLabel.takeIf { onOpenBookmarks != null },
                                onLongClick = {
                                    if (onOpenBookmarks != null) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenBookmarks()
                                    }
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Bookmark,
                        contentDescription = stringResource(R.string.live_web_save_post),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (smartReplyAction != null) {
                IconButton(
                    onClick = smartReplyAction,
                    modifier = Modifier.size(DOCK_ACTION_SIZE),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                        contentDescription = stringResource(R.string.live_web_smart_reply),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (bookmarkAction == null && smartReplyAction == null) {
                IconButton(
                    onClick = onEditTabs,
                    modifier = Modifier.size(DOCK_ACTION_SIZE),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Sliders,
                        contentDescription = stringResource(R.string.edit_tab_title),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

// Trailing save, wand and settings buttons stay compact while keeping comfortable thumb targets.
private val DOCK_ACTION_SIZE = 42.dp

// Above this many tabs, unselected tabs collapse to icon-only so the strip stays scannable.
private const val COMPACT_LABEL_THRESHOLD = 4

/**
 * How a page the dock steps aside on is laid out. [Media] (Reels, Stories) keeps one WebView size
 * and a brought-back dock floats over it; [Messages] moves the page back above a brought-back
 * dock, so the site's reply field is never under it for as long as the thread is open.
 */
internal enum class DockImmersiveKind { Media, Messages }

/**
 * Pages where the dock steps aside: Instagram Reels, Stories and DMs, and X and LinkedIn messages.
 * Those sites draw their own bottom bar or reply field there, and a pinned dock would stack a
 * second bar under it and take height from the video. Other sites keep the dock. Paths are
 * matched per site, so a profile named chat, direct or shorts keeps the dock.
 */
internal fun dockImmersiveKind(url: String?): DockImmersiveKind? {
    val uri = url?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return null
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
    val path = uri.path.orEmpty()
    return when {
        host.isOnDomain("instagram.com") ->
            when {
                INSTAGRAM_DOCK_MESSAGES.containsMatchIn(path) -> DockImmersiveKind.Messages
                INSTAGRAM_DOCK_MEDIA.containsMatchIn(path) -> DockImmersiveKind.Media
                else -> null
            }
        host.isOnDomain("x.com") || host.isOnDomain("twitter.com") ->
            when {
                X_DOCK_MESSAGES.containsMatchIn(path) -> DockImmersiveKind.Messages
                X_DOCK_MEDIA.containsMatchIn(path) -> DockImmersiveKind.Media
                else -> null
            }
        host.isOnDomain("linkedin.com") ->
            DockImmersiveKind.Messages.takeIf { LINKEDIN_DOCK_MESSAGES.containsMatchIn(path) }
        host.isOnDomain("reddit.com") ->
            DockImmersiveKind.Messages.takeIf { host == "chat.reddit.com" || REDDIT_DOCK_MESSAGES.containsMatchIn(path) }
        else -> null
    }
}

/** True on a page where the dock steps aside ([dockImmersiveKind]). */
internal fun isDockImmersiveUrl(url: String?): Boolean = dockImmersiveKind(url) != null

private val INSTAGRAM_DOCK_MEDIA = Regex("^/(?:(?:reels?|stories)(?:/|$)|[^/]+/reel(?:/|$))", RegexOption.IGNORE_CASE)
private val INSTAGRAM_DOCK_MESSAGES = Regex("^/direct(?:/|$)", RegexOption.IGNORE_CASE)
private val X_DOCK_MESSAGES = Regex("^/(?:messages|i/chat|i/messages)(?:/|$)", RegexOption.IGNORE_CASE)
private val X_DOCK_MEDIA = Regex("^/(?:[^/]+|i(?:/web)?)/status/\\d+/(?:photo|video)/\\d+(?:/|$)", RegexOption.IGNORE_CASE)
private val LINKEDIN_DOCK_MESSAGES = Regex("^/messaging(?:/|$)", RegexOption.IGNORE_CASE)
private val REDDIT_DOCK_MESSAGES = Regex("^/chat(?:/|$)", RegexOption.IGNORE_CASE)

private fun String.isOnDomain(domain: String): Boolean = this == domain || endsWith(".$domain")

/**
 * The bottom padding of the visible live tab on a page the dock steps aside on ([kind]; null is
 * any other page, which keeps [scaffoldBottom]).
 *
 * - Hidden dock: the system navigation bar plus [handleReserve], the strip the reveal handle sits
 *   in, so the handle is below the page and never takes a tap meant for the site.
 * - Brought back on [DockImmersiveKind.Media]: the same, so the WebView keeps its size and the
 *   dock floats over the video.
 * - Brought back on [DockImmersiveKind.Messages]: [scaffoldBottom], so the reply field moves
 *   above the dock.
 */
internal fun liveTabBottomPadding(
    scaffoldBottom: Dp,
    navigationBarBottom: Dp,
    kind: DockImmersiveKind?,
    dockPeeked: Boolean,
    handleReserve: Dp,
): Dp =
    when {
        kind == null -> scaffoldBottom
        kind == DockImmersiveKind.Messages && dockPeeked -> scaffoldBottom
        else -> immersiveBaseBottomPadding(scaffoldBottom, navigationBarBottom) + handleReserve
    }

/** The bottom inset the page and the reveal handle share on an immersive page: never more than the scaffold's. */
internal fun immersiveBaseBottomPadding(
    scaffoldBottom: Dp,
    navigationBarBottom: Dp,
): Dp = minOf(scaffoldBottom, navigationBarBottom)

/**
 * Tells the home dock which page the visible live tab shows, so it can step aside on an
 * immersive page ([isDockImmersiveUrl]) and come back when the tab leaves it, is hidden or
 * goes away. Call it from the live tab with its own [owner] (its site) and current URL.
 *
 * Keyed on the URL: a new address disposes (clearing this owner's claim and any brought-back
 * dock) and then reports again, so the next Reel or Story hides the dock again.
 */
@Composable
internal fun LiveWebDockImmersiveReporter(
    owner: Any,
    isVisible: Boolean,
    url: String?,
) {
    val accessory = LocalBottomBarAccessory.current ?: return
    val immersiveUrl = url?.takeIf { isVisible && isDockImmersiveUrl(it) }
    DisposableEffect(accessory, owner, immersiveUrl) {
        reportImmersivePage(accessory, owner, immersiveUrl)
        onDispose { accessory.clearWebPage(owner) }
    }
}

/** The effect body of [LiveWebDockImmersiveReporter], kept plain so tests run the same sequence. */
internal fun reportImmersivePage(
    accessory: BottomBarAccessory,
    owner: Any,
    immersiveUrl: String?,
) {
    if (immersiveUrl != null) {
        accessory.reportWebPage(owner, immersiveUrl)
    } else {
        accessory.clearWebPage(owner)
    }
}

/**
 * A small handle shown where the dock was while it is hidden on an immersive page. A tap or an
 * upward swipe brings the dock back, so it is never out of reach.
 */
@Composable
internal fun ImmersiveDockHandle(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.live_web_show_dock)
    val density = LocalDensity.current
    val revealDistancePx = with(density) { IMMERSIVE_HANDLE_SWIPE_DISTANCE.toPx() }
    val latestOnReveal by rememberUpdatedState(onReveal)
    Box(
        modifier =
            modifier
                .size(width = IMMERSIVE_HANDLE_TOUCH_WIDTH, height = IMMERSIVE_HANDLE_TOUCH_HEIGHT)
                .clip(CircleShape)
                .pointerInput(revealDistancePx) {
                    var dragged = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            dragged += dragAmount
                            if (dragged < 0f) change.consume()
                            if (-dragged >= revealDistancePx) {
                                dragged = 0f
                                latestOnReveal()
                            }
                        },
                    )
                }.clickable(onClickLabel = label, role = Role.Button) { latestOnReveal() }
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = IMMERSIVE_HANDLE_PILL_WIDTH, height = IMMERSIVE_HANDLE_PILL_HEIGHT)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)),
        )
    }
}

// Wide enough to find by thumb. The live tab reserves IMMERSIVE_HANDLE_TOUCH_HEIGHT below the page
// (liveTabBottomPadding), so the handle sits under the site's own bar and reply field, not on them.
private val IMMERSIVE_HANDLE_TOUCH_WIDTH = 72.dp
internal val IMMERSIVE_HANDLE_TOUCH_HEIGHT = 20.dp
private val IMMERSIVE_HANDLE_PILL_WIDTH = 36.dp
private val IMMERSIVE_HANDLE_PILL_HEIGHT = 4.dp

// An upward swipe this long on the handle brings the dock back.
private val IMMERSIVE_HANDLE_SWIPE_DISTANCE = 16.dp
