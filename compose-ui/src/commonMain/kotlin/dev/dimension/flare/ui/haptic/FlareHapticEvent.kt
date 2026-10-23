package dev.dimension.flare.ui.haptic

/**
 * Platform-independent description of a haptic effect.
 *
 * Callers describe the *intent* of the feedback rather than a platform primitive, so the
 * same call site can produce a light tick on Android and a no-op on desktop.
 */
public enum class FlareHapticEvent {
    /** Light tick used for discrete counting steps. */
    Tick,

    /** Light tick for a value change that should feel like a detent. */
    SelectionChange,

    /** Fired when a drag crosses a snap threshold. */
    GestureThreshold,

    /** Long press acknowledgement. */
    LongPress,

    /** A completed, positive, committed action. */
    Confirm,

    /** A cancelled or undone action. */
    Reject,

    /** Something needs attention but is not an error. */
    Warning,

    /** A failed action. */
    Error,
}
