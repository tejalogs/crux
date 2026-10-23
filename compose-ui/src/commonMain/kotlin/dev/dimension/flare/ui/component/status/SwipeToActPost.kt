package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Reply
import compose.icons.fontawesomeicons.solid.RotateRight
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.haptic.FlareHapticEvent
import dev.dimension.flare.ui.haptic.rememberFlareHapticFeedback
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.PlatformTheme

/**
 * Finds the first top-level action matching [family] on a post.
 *
 * Only top-level items are considered: actions nested inside a dropdown belong to the overflow
 * menu, and surfacing one of those behind a swipe would duplicate a control the user can already
 * reach but change its meaning.
 */
private fun UiTimelineV2.Post.firstAction(family: PostActionFamily): ActionMenu.Item? =
    actions
        .asSequence()
        .filterIsInstance<ActionMenu.Item>()
        .firstOrNull { it.actionFamily == family || (family == PostActionFamily.Repost && it.actionFamily == PostActionFamily.Quote) }

/**
 * Wraps a post row in swipe-to-act when the user has enabled it.
 *
 * Swiping toward the end edge triggers the primary action (repost where available, otherwise
 * reply); swiping toward the start edge replies. Both resolve through [SwipeToActBox] and fire a
 * confirmation haptic on commit.
 *
 * When the feature is disabled this is a pass-through, so callers do not need to branch.
 */
@Composable
internal fun SwipeToActPost(
    item: UiTimelineV2?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enabled = LocalGlobalAppearance.current.swipeToActEnabled
    val post = item as? UiTimelineV2.Post
    val hapticsEnabled = LocalGlobalAppearance.current.hapticsEnabled

    if (!enabled || post == null) {
        content()
        return
    }

    val launcher = LocalUriHandler.current
    val haptics = rememberFlareHapticFeedback(hapticsEnabled)
    val clickContext = remember(launcher) { ClickContext(launcher = { launcher.openUri(it) }) }

    val replyAction = post.firstAction(PostActionFamily.Reply)
    val repostAction =
        post.firstAction(PostActionFamily.Repost)
            ?: post.firstAction(PostActionFamily.Quote)

    SwipeToActBox(
        modifier = modifier.fillMaxWidth(),
        startConfig =
            replyAction?.let {
                SwipeActionConfig(
                    icon = FontAwesomeIcons.Solid.Reply,
                    contentDescription = null,
                    background = PlatformTheme.colorScheme.primary,
                    enabled = it.enabled,
                )
            },
        endConfig =
            (repostAction ?: replyAction)?.let {
                SwipeActionConfig(
                    icon = FontAwesomeIcons.Solid.RotateRight,
                    contentDescription = null,
                    background = PlatformTheme.colorScheme.primary,
                    enabled = it.enabled,
                )
            },
        hapticsEnabled = hapticsEnabled,
        onSwipeStart = {
            haptics.perform(FlareHapticEvent.Confirm)
            replyAction?.onClicked?.invoke(clickContext)
        },
        onSwipeEnd = {
            haptics.perform(FlareHapticEvent.Confirm)
            (repostAction ?: replyAction)?.onClicked?.invoke(clickContext)
        },
    ) {
        content()
    }
}
