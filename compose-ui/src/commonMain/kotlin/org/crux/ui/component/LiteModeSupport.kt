package org.crux.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.crux.data.model.LiteMediaPolicy
import org.crux.data.model.LiteMediaPolicy.MediaDecision
import org.crux.data.model.LiteMode
import org.crux.data.model.VideoAutoplay

/**
 * Effective media policy for the current subtree.
 *
 * Lite mode is a *client-side* saving, so it composes with the existing per-timeline autoplay
 * preference rather than replacing it: the stricter of the two always wins. A user who set
 * "never autoplay" keeps that under lite mode, and lite mode can additionally suppress autoplay
 * for a user who had it on.
 */
@Composable
public fun rememberEffectiveLiteMode(): LiteMode = LocalGlobalAppearance.current.liteMode

/**
 * Resolves how a video should play, honouring lite mode first.
 *
 * @param configured the user's per-timeline autoplay preference
 * @param onUnmeteredConnection whether the device is on Wi-Fi or equivalent
 */
@Composable
public fun rememberEffectiveAutoplay(
    configured: VideoAutoplay,
    onUnmeteredConnection: Boolean,
): Boolean {
    val liteMode = rememberEffectiveLiteMode()
    return remember(liteMode, configured, onUnmeteredConnection) {
        if (!liteMode.allowsVideoAutoplay) {
            false
        } else {
            configured == VideoAutoplay.ALWAYS ||
                (configured == VideoAutoplay.WIFI && onUnmeteredConnection)
        }
    }
}

/** Whether images should be fetched at all under the active lite mode. */
@Composable
public fun rememberImagesEnabled(): Boolean = LocalGlobalAppearance.current.liteMode.loadsMedia

/**
 * Decides how one media item should be rendered under the active lite mode.
 */
@Composable
public fun rememberMediaDecision(
    isVideo: Boolean,
    hasPreviewAlternative: Boolean,
    onUnmeteredConnection: Boolean,
): MediaDecision {
    val liteMode = rememberEffectiveLiteMode()
    return remember(liteMode, isVideo, hasPreviewAlternative, onUnmeteredConnection) {
        LiteMediaPolicy.decide(
            liteMode = liteMode,
            isVideo = isVideo,
            hasPreviewAlternative = hasPreviewAlternative,
            isOnUnmeteredConnection = onUnmeteredConnection,
        )
    }
}
