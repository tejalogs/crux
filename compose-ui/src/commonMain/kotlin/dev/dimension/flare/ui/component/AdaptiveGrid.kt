package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.theme.PlatformTheme

private const val ADAPTIVE_GRID_MAX_ITEMS = 9
private const val ADAPTIVE_GRID_OVERFLOW_DISPLAY_LIMIT = 100

@Composable
internal fun AdaptiveGrid(
    itemCount: Int,
    itemContent: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    expandedSize: Boolean = true,
    maxItems: Int = ADAPTIVE_GRID_MAX_ITEMS,
    overflowContent: @Composable (Int) -> Unit = { overflowCount ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            PlatformText(
                text = overflowCount.displayText,
                color = Color.White,
                style = PlatformTheme.typography.headline,
            )
        }
    },
) {
    val visibleCount = minOf(itemCount, maxItems)
    val overflowCount = itemCount - visibleCount
    // Compose the count and indexed items together to avoid stale indices when the list shrinks.
    Layout(
        contents =
            listOf(
                { repeat(visibleCount) { itemContent(it) } },
                { if (visibleCount > 0 && overflowCount > 0) overflowContent(overflowCount) },
            ),
        modifier = modifier,
        measurePolicy = { (visibleMeasurables, overflowMeasurables), constraints ->
            var overflowX = 0
            var overflowY = 0
            var overflowWidth = 0
            var overflowHeight = 0

            if (visibleMeasurables.isEmpty()) {
                return@Layout layout(0, 0) {}
            }

            val width =
                if (constraints.hasBoundedWidth) {
                    constraints.maxWidth
                } else if (constraints.minWidth in 1..99_999) {
                    constraints.minWidth
                } else {
                    0
                }

            if (width <= 0) {
                return@Layout layout(0, 0) {}
            }

            if (visibleMeasurables.size == 1) {
                val targetHeight =
                    if (expandedSize) {
                        ((width.toLong() * 21) / 9).toInt()
                    } else {
                        ((width.toLong() * 9) / 16).toInt()
                    }
                val safeMaxHeight =
                    if (constraints.hasBoundedHeight) {
                        targetHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
                    } else {
                        targetHeight.coerceAtLeast(constraints.minHeight)
                    }
                val childConstraints =
                    Constraints(
                        minWidth = 0,
                        maxWidth = width,
                        minHeight = 0,
                        maxHeight = safeMaxHeight,
                    )
                val placeable = visibleMeasurables[0].measure(childConstraints)
                overflowWidth = placeable.width
                overflowHeight = placeable.height
                val overflowPlaceables =
                    if (overflowCount > 0) {
                        overflowMeasurables.map {
                            it.measure(Constraints.fixed(overflowWidth, overflowHeight))
                        }
                    } else {
                        emptyList()
                    }
                return@Layout layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                    overflowPlaceables.fastForEach {
                        it.placeRelative(overflowX, overflowY)
                    }
                }
            }

            val gapPx = spacing.roundToPx()
            if (visibleMeasurables.size in 2..4) {
                val height = ((width.toLong() * 9) / 16).toInt()
                val containerHeight =
                    if (expandedSize && constraints.hasBoundedHeight) {
                        height.coerceIn(constraints.minHeight, constraints.maxHeight)
                    } else {
                        height
                    }

                val placeables =
                    when (visibleMeasurables.size) {
                        2 -> {
                            val childWidth = ((width - gapPx) / 2).coerceAtLeast(0)
                            visibleMeasurables.map { child ->
                                child.measure(
                                    Constraints.fixed(childWidth, containerHeight),
                                )
                            }
                        }

                        3 -> {
                            val leftWidth = ((width - gapPx) / 2).coerceAtLeast(0)
                            val rightWidth = (width - leftWidth - gapPx).coerceAtLeast(0)
                            val rightHeight = ((containerHeight - gapPx) / 2).coerceAtLeast(0)

                            visibleMeasurables.mapIndexed { index, measurable ->
                                when (index) {
                                    0 -> {
                                        measurable.measure(
                                            Constraints.fixed(leftWidth, containerHeight),
                                        )
                                    }

                                    else -> {
                                        measurable.measure(
                                            Constraints.fixed(rightWidth, rightHeight),
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            val cellW = ((width - gapPx) / 2).coerceAtLeast(0)
                            val cellH = ((containerHeight - gapPx) / 2).coerceAtLeast(0)
                            visibleMeasurables.map { child ->
                                child.measure(
                                    Constraints.fixed(cellW, cellH),
                                )
                            }
                        }
                    }

                when (visibleMeasurables.size) {
                    2 -> {
                        overflowX = placeables[0].width + gapPx
                        overflowWidth = placeables[1].width
                        overflowHeight = placeables[1].height
                    }

                    3 -> {
                        overflowX = placeables[0].width + gapPx
                        overflowY = placeables[1].height + gapPx
                        overflowWidth = placeables[2].width
                        overflowHeight = placeables[2].height
                    }

                    4 -> {
                        overflowX = placeables[0].width + gapPx
                        overflowY = placeables[0].height + gapPx
                        overflowWidth = placeables[3].width
                        overflowHeight = placeables[3].height
                    }
                }
                val overflowPlaceables =
                    if (overflowCount > 0) {
                        overflowMeasurables.map {
                            it.measure(Constraints.fixed(overflowWidth, overflowHeight))
                        }
                    } else {
                        emptyList()
                    }

                return@Layout layout(width, containerHeight) {
                    when (visibleMeasurables.size) {
                        2 -> {
                            placeables[0].placeRelative(0, 0)
                            placeables[1].placeRelative(placeables[0].width + gapPx, 0)
                        }

                        3 -> {
                            placeables[0].placeRelative(0, 0)
                            val xRight = placeables[0].width + gapPx
                            placeables[1].placeRelative(xRight, 0)
                            placeables[2].placeRelative(
                                xRight,
                                placeables[1].height + gapPx,
                            )
                        }

                        4 -> {
                            val cellW = placeables[0].width
                            val cellH = placeables[0].height
                            placeables.fastForEachIndexed { index, p ->
                                val col = index % 2
                                val row = index / 2
                                p.placeRelative(
                                    x = col * (cellW + gapPx),
                                    y = row * (cellH + gapPx),
                                )
                            }
                        }
                    }
                    overflowPlaceables.fastForEach {
                        it.placeRelative(overflowX, overflowY)
                    }
                }
            }

            val maxColumns = 3
            val columns = maxColumns
            val itemSize = ((width - gapPx * (columns - 1)) / columns).coerceAtLeast(0)
            val rows = (visibleMeasurables.size + columns - 1) / columns

            val lastRowCount = visibleMeasurables.size - (rows - 1) * columns
            val lastRowFlexible = lastRowCount != columns

            val placeables =
                visibleMeasurables.mapIndexed { index, measurable ->
                    val isInLastRow = index / columns == rows - 1
                    val isFlexible = lastRowFlexible && isInLastRow
                    val childWidth =
                        if (isFlexible) {
                            ((width - gapPx * (lastRowCount - 1)) / lastRowCount).coerceAtLeast(0)
                        } else {
                            itemSize
                        }

                    measurable.measure(
                        Constraints.fixed(childWidth, itemSize),
                    )
                }

            val totalHeight = rows * itemSize + gapPx * (rows - 1)
            val lastIndex = placeables.lastIndex
            val lastRow = lastIndex / columns
            val lastCol = lastIndex % columns
            val lastRowIsFlexible = lastRow == rows - 1 && lastRowFlexible
            val lastRowColumns = if (lastRowIsFlexible) lastRowCount else columns
            val lastCellWidth =
                if (lastRowIsFlexible) {
                    ((width - gapPx * (lastRowColumns - 1)) / lastRowColumns).coerceAtLeast(0)
                } else {
                    itemSize
                }
            overflowX = lastCol * (lastCellWidth + gapPx)
            overflowY = lastRow * (itemSize + gapPx)
            overflowWidth = placeables[lastIndex].width
            overflowHeight = placeables[lastIndex].height
            val overflowPlaceables =
                if (overflowCount > 0) {
                    overflowMeasurables.map {
                        it.measure(Constraints.fixed(overflowWidth, overflowHeight))
                    }
                } else {
                    emptyList()
                }

            layout(width, totalHeight) {
                placeables.fastForEachIndexed { index, p ->
                    val row = index / columns
                    val col = index % columns
                    val isLastRow = row == rows - 1 && lastRowFlexible
                    val effectiveColumns = if (isLastRow) lastRowCount else columns
                    val cellW =
                        if (isLastRow) {
                            ((width - gapPx * (effectiveColumns - 1)) / effectiveColumns).coerceAtLeast(0)
                        } else {
                            itemSize
                        }

                    p.placeRelative(
                        x = col * (cellW + gapPx),
                        y = row * (itemSize + gapPx),
                    )
                }
                overflowPlaceables.fastForEach {
                    it.placeRelative(overflowX, overflowY)
                }
            }
        },
    )
}

private val Int.displayText: String
    get() =
        if (this >= ADAPTIVE_GRID_OVERFLOW_DISPLAY_LIMIT) {
            "99+"
        } else {
            "+$this"
        }
