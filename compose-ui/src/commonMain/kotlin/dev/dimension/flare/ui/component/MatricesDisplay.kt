package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.profile_header_fans_count
import dev.dimension.flare.compose.ui.profile_header_following_count
import dev.dimension.flare.compose.ui.profile_misskey_header_status_count
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.theme.PlatformTheme
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatricesDisplay(
    data: UiProfile.Matrices,
    onClicked: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val data =
        remember(data) {
            persistentMapOf(
                Res.string.profile_misskey_header_status_count to data.statusesCountHumanized,
                Res.string.profile_header_following_count to data.followsCountHumanized,
                Res.string.profile_header_fans_count to data.fansCountHumanized,
            )
        }
    if (expanded) {
        Row(
            modifier = modifier.wrapContentHeight(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data.onEachIndexed { index, (resId, text) ->
                val label = stringResource(resId, 0).removePrefix("0 ").trim()
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.5f))
                            .border(
                                0.6.dp,
                                PlatformTheme.colorScheme.outline.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp),
                            ).clickable {
                                onClicked.invoke(index)
                            }.padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        PlatformText(
                            text = text,
                            style = PlatformTheme.typography.title,
                        )
                        PlatformText(
                            text = label,
                            style = PlatformTheme.typography.caption,
                            color = PlatformTheme.colorScheme.caption,
                        )
                    }
                }
            }
        }
    } else {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            data.onEachIndexed { index, (resId, text) ->
                val label = stringResource(resId, 0).removePrefix("0 ").trim()
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PlatformTheme.colorScheme.cardAlt.copy(alpha = 0.5f))
                            .border(
                                0.6.dp,
                                PlatformTheme.colorScheme.outline.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp),
                            ).clickable {
                                onClicked.invoke(index)
                            }.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        PlatformText(
                            text = text,
                            style = PlatformTheme.typography.body,
                        )
                        PlatformText(
                            text = label,
                            style = PlatformTheme.typography.caption,
                            color = PlatformTheme.colorScheme.caption,
                        )
                    }
                }
            }
        }
    }
}
