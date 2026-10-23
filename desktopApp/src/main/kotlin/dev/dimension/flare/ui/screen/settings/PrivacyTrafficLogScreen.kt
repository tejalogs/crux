package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.data.privacy.PrivacyCategory
import dev.dimension.flare.data.privacy.PrivacyLog
import dev.dimension.flare.settings_privacy_traffic_log_blocked
import dev.dimension.flare.settings_privacy_traffic_log_empty
import dev.dimension.flare.settings_privacy_traffic_log_stripped
import dev.dimension.flare.settings_privacy_traffic_log_summary
import dev.dimension.flare.settings_privacy_traffic_log_summary_detail
import dev.dimension.flare.settings_privacy_traffic_log_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PrivacyTrafficLogScreen() {
    val entries by PrivacyLog.get().entries.collectAsState()
    val listState = rememberLazyListState()

    val telemetryCount = entries.count { it.category == PrivacyCategory.Telemetry }
    val profilingCount = entries.count { it.category == PrivacyCategory.Profiling }
    val blockedCount = entries.count { it.blocked }
    val strippedParamsCount = entries.sumOf { it.strippedParameters.size }

    FlareScrollBar(listState) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = LocalWindowPadding.current,
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Header(stringResource(Res.string.settings_privacy_traffic_log_title))
                    if (entries.isNotEmpty()) {
                        AccentButton(
                            onClick = { PrivacyLog.get().clear() },
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Trash,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                            Text("Clear")
                        }
                    }
                }
            }

            if (entries.isNotEmpty()) {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(FluentTheme.colors.background.card.default)
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_privacy_traffic_log_summary, entries.size),
                            style = FluentTheme.typography.bodyStrong,
                        )
                        Text(
                            text =
                                stringResource(
                                    Res.string.settings_privacy_traffic_log_summary_detail,
                                    telemetryCount,
                                    profilingCount,
                                    blockedCount,
                                    strippedParamsCount,
                                ),
                            style = FluentTheme.typography.body,
                            color = FluentTheme.colors.text.text.secondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(entries.asReversed()) { entry ->
                    CardExpanderItem(
                        icon = null,
                        heading = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${entry.method} ${entry.path}",
                                    maxLines = 1,
                                    style = FluentTheme.typography.body,
                                )
                                if (entry.blocked) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(FluentTheme.colors.system.critical)
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.settings_privacy_traffic_log_blocked),
                                            style = FluentTheme.typography.caption,
                                            color = Color.White,
                                        )
                                    }
                                } else if (entry.category != PrivacyCategory.Content) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(FluentTheme.colors.background.layer.default)
                                                .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = entry.category.name,
                                            style = FluentTheme.typography.caption,
                                            color = FluentTheme.colors.text.text.primary,
                                        )
                                    }
                                }
                            }
                        },
                        caption = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = entry.host,
                                    style = FluentTheme.typography.caption,
                                    color = FluentTheme.colors.text.text.secondary,
                                )
                                if (entry.strippedParameters.isNotEmpty()) {
                                    Text(
                                        text =
                                            stringResource(
                                                Res.string.settings_privacy_traffic_log_stripped,
                                                entry.strippedParameters.joinToString(),
                                            ),
                                        style = FluentTheme.typography.caption,
                                        color = FluentTheme.colors.text.accent.primary,
                                    )
                                }
                            }
                        },
                        trailing = {
                            entry.status?.let { status ->
                                Text(
                                    text = status.toString(),
                                    style = FluentTheme.typography.body,
                                    color =
                                        if (status in 200..299) {
                                            FluentTheme.colors.text.accent.primary
                                        } else {
                                            FluentTheme.colors.system.critical
                                        },
                                )
                            }
                        },
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(Res.string.settings_privacy_traffic_log_empty),
                        style = FluentTheme.typography.body,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
            }
        }
    }
}
