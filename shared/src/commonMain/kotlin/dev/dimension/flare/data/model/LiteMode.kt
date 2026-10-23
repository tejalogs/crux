package dev.dimension.flare.data.model

import kotlinx.serialization.Serializable

/**
 * Data-saving profile.
 *
 * Unlike a server-side "lite" build, this is a client policy applied at decode and fetch time, so
 * it works uniformly across every network Flare supports and cannot break when a platform rotates
 * an internal format.
 */
@Serializable
public enum class LiteMode {
    /** Full fidelity: media loads, video plays, high-resolution sources allowed. */
    OFF,

    /** Text-first: images still load, video never autoplays, originals are not fetched. */
    BALANCED,

    /** Text-only: no media is fetched or decoded at all, video is fully inert. */
    TEXT_ONLY,
    ;

    public val loadsMedia: Boolean get() = this != TEXT_ONLY

    public val allowsVideoAutoplay: Boolean get() = this == OFF

    /**
     * Whether an image should be fetched at all.
     *
     * @param onUnmetered true when the device is on an unmetered connection
     */
    public fun shouldFetchImage(onUnmetered: Boolean): Boolean =
        when (this) {
            OFF -> true
            BALANCED -> true
            TEXT_ONLY -> false
        }
}

/**
 * Pure decision for a media request under [LiteMode].
 *
 * Kept separate from the fetch layer so the rules are unit-testable and the composables only
 * react to a decision rather than re-deriving policy.
 *
 * @param isVideo whether the media is a video/animation
 * @param hasPreviewAlternative true when the post offers a static preview image that can stand in
 *   for the full asset
 */
public object LiteMediaPolicy {
    /**
     * What to render for a media item.
     */
    public enum class MediaDecision {
        /** Load the media normally. */
        Full,

        /** Load only the cheap preview image, never the original or the video. */
        PreviewOnly,

        /** Render a placeholder; do not fetch anything. */
        Hidden,
    }

    /**
     * Decides how to handle one media item.
     *
     * @param liteMode active profile
     * @param isVideo whether the item is video or an animated format
     * @param hasPreviewAlternative whether a cheaper preview exists
     * @param isOnUnmeteredConnection whether the device is on Wi-Fi or equivalent
     */
    public fun decide(
        liteMode: LiteMode,
        isVideo: Boolean,
        hasPreviewAlternative: Boolean,
        isOnUnmeteredConnection: Boolean,
    ): MediaDecision =
        when {
            liteMode == LiteMode.TEXT_ONLY -> MediaDecision.Hidden
            liteMode == LiteMode.OFF -> MediaDecision.Full
            // BALANCED: video becomes a static preview; everything else still loads, since
            // blocking it entirely would gut the timeline rather than trim it.
            isVideo -> if (hasPreviewAlternative) MediaDecision.PreviewOnly else MediaDecision.PreviewOnly
            else -> MediaDecision.Full
        }
}
