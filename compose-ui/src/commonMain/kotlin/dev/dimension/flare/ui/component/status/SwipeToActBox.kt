package dev.dimension.flare.ui.component.status

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.haptic.FlareHapticEvent
import dev.dimension.flare.ui.haptic.rememberFlareHapticFeedback
import kotlinx.coroutines.launch

/** Affordance revealed when dragging toward one edge of a swipeable row. */
@Immutable
public data class SwipeActionConfig(
    val icon: ImageVector?,
    val contentDescription: String?,
    val background: Color,
    val foreground: Color = Color.Unspecified,
    val enabled: Boolean = true,
)

/**
 * Wraps [content] with a horizontal swipe-to-act affordance on both edges.
 *
 * The drag drives an [Animatable] offset; on release the pure policy in `resolveSwipeAction`
 * decides whether to commit and the offset settles with a spring instead of a linear tween, so a
 * cancelled gesture rebounds and a committed one snaps. Crossing the commit threshold fires one
 * haptic tick per crossing, so arming the action is felt as well as seen.
 *
 * @param thresholdFraction fraction of the measured width required to commit the gesture
 * @param onSwipeStart invoked when a start-edge gesture commits
 * @param onSwipeEnd invoked when an end-edge gesture commits
 */
@Composable
public fun SwipeToActBox(
    startConfig: SwipeActionConfig?,
    endConfig: SwipeActionConfig?,
    modifier: Modifier = Modifier,
    thresholdFraction: Float = DefaultSwipeThresholdFraction,
    hapticsEnabled: Boolean = true,
    onSwipeStart: () -> Unit = {},
    onSwipeEnd: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberFlareHapticFeedback(hapticsEnabled)
    var widthPx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }

    val thresholdPx = widthPx * thresholdFraction
    val flingCapPx = (widthPx * MaxFlingDistanceFraction).coerceAtLeast(1f)

    Box(
        modifier =
            modifier
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(widthPx, startConfig, endConfig) {
                    val velocityTracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity().x
                            val resolution =
                                resolveSwipeAction(
                                    offsetPx = offsetX.value,
                                    thresholdPx = thresholdPx,
                                    velocityPxPerSecond = velocity,
                                    flingVelocityThresholdPxPerSecond = FlingVelocityThreshold,
                                    maxFlingDistancePx = flingCapPx,
                                )
                            val settle =
                                settleOffset(
                                    triggered = resolution.triggered,
                                    offsetPx = offsetX.value,
                                    thresholdPx = thresholdPx,
                                )
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = settle,
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                )
                            }
                            if (resolution.triggered) {
                                when (resolution.direction) {
                                    SwipeActionDirection.Start -> onSwipeStart()
                                    SwipeActionDirection.End -> onSwipeEnd()
                                }
                            }
                            armed = false
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec =
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                )
                            }
                            armed = false
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val target = (offsetX.value + dragAmount).coerceIn(-widthPx, widthPx)
                        scope.launch { offsetX.snapTo(target) }

                        val resolved =
                            resolveSwipeAction(
                                offsetPx = target,
                                thresholdPx = thresholdPx,
                                velocityPxPerSecond = 0f,
                                flingVelocityThresholdPxPerSecond = FlingVelocityThreshold,
                                maxFlingDistancePx = flingCapPx,
                            )
                        if (resolved.triggered != armed) {
                            armed = resolved.triggered
                            haptics.perform(FlareHapticEvent.GestureThreshold)
                        }
                    }
                },
    ) {
        startConfig?.takeIf { it.icon != null }?.let { config ->
            SwipeAffordance(
                config = config,
                alignment = Alignment.CenterStart,
                offsetX = offsetX,
                thresholdPx = thresholdPx,
                mirrored = true,
            )
        }
        endConfig?.takeIf { it.icon != null }?.let { config ->
            SwipeAffordance(
                config = config,
                alignment = Alignment.CenterEnd,
                offsetX = offsetX,
                thresholdPx = thresholdPx,
                mirrored = false,
            )
        }
        content()
    }
}

@Composable
private fun BoxScope.SwipeAffordance(
    config: SwipeActionConfig,
    alignment: Alignment,
    offsetX: Animatable<Float, *>,
    thresholdPx: Float,
    mirrored: Boolean,
) {
    val icon = config.icon ?: return
    val progress = offsetX.value.progressFor(thresholdPx)
    FAIcon(
        imageVector = icon,
        contentDescription = config.contentDescription,
        modifier =
            Modifier
                .align(alignment)
                .alpha(swipeActionAlpha(progress))
                .scale(swipeActionScale(progress) * if (mirrored) -1f else 1f),
        tint = config.foreground.takeIf { it != Color.Unspecified } ?: Color.White,
    )
}
