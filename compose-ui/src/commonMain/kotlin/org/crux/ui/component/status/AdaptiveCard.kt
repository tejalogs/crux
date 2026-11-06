package org.crux.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.crux.data.model.TimelineDisplayMode
import org.crux.ui.component.HorizontalDivider
import org.crux.ui.component.LocalTimelineAppearance
import org.crux.ui.component.listCard
import org.crux.ui.component.listCardShape
import org.crux.ui.component.platform.PlatformCard
import org.crux.ui.theme.PlatformTheme

@Composable
public fun AdaptiveCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
    respectTimelineMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bigScreen = LocalMultipleColumns.current
    val displayMode = LocalTimelineAppearance.current.timelineDisplayMode
    if (bigScreen) {
        PlatformCard(
            modifier =
                modifier
                    .padding(
                        horizontal = 2.dp,
                        vertical = 6.dp,
                    ),
            elevated = false,
            containerColor = PlatformTheme.colorScheme.card,
        ) {
            content.invoke()
        }
    } else if (respectTimelineMode && displayMode == TimelineDisplayMode.Plain) {
        Column(modifier = modifier.fillMaxWidth()) {
            content.invoke()
            if (totalCount <= 0 || index < totalCount - 1) {
                HorizontalDivider()
            }
        }
    } else {
        val cardShape = listCardShape(index = index, totalCount = totalCount)
        Box(
            modifier =
                modifier
                    .clip(cardShape)
                    .background(PlatformTheme.colorScheme.card)
                    .border(
                        0.6.dp,
                        PlatformTheme.colorScheme.outline.copy(alpha = 0.25f),
                        cardShape,
                    ),
        ) {
            content.invoke()
        }
    }
}

@Composable
public fun AdaptiveOutlinedCard(
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalCount: Int = 0,
    respectTimelineMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bigScreen = LocalMultipleColumns.current
    val displayMode = LocalTimelineAppearance.current.timelineDisplayMode
    if (bigScreen || displayMode != TimelineDisplayMode.Plain) {
        AdaptiveCard(
            modifier = modifier,
            index = index,
            totalCount = totalCount,
            respectTimelineMode = respectTimelineMode,
            content = content,
        )
    } else {
        Box(
            modifier =
                modifier
                    .listCard(
                        index = index,
                        totalCount = totalCount,
                    ).background(PlatformTheme.colorScheme.card)
                    .border(
                        1.dp,
                        PlatformTheme.colorScheme.outline,
                        listCardShape(
                            index = index,
                            totalCount = totalCount,
                        ),
                    ),
        ) {
            content.invoke()
        }
    }
}
