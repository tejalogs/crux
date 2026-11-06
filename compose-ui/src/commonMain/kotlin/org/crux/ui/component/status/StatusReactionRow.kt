package org.crux.ui.component.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ContextualFlowRow
import androidx.compose.foundation.layout.ContextualFlowRowOverflow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.crux.compose.ui.Res
import org.crux.compose.ui.mastodon_item_show_more
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
// The indexed layout in our Compose version bounds composition to the visible rows.
@Suppress("DEPRECATION")
@Composable
internal fun StatusReactionRow(
    itemCount: Int,
    isDetail: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    ContextualFlowRow(
        itemCount = itemCount,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        maxLines = if (isDetail) Int.MAX_VALUE else 2,
        overflow =
            ContextualFlowRowOverflow.expandIndicator {
                PlatformText(
                    text = stringResource(Res.string.mastodon_item_show_more),
                    style = PlatformTheme.typography.caption,
                    color = PlatformTheme.colorScheme.caption,
                )
            },
    ) { index ->
        // Old subcompositions can recompose before a shrinking list is remeasured.
        if (index < itemCount) {
            content(index)
        }
    }
}
