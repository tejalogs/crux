package org.crux.ui.screen.crux

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import org.crux.R
import org.crux.ui.component.FAIcon
import org.crux.ui.route.Route

/** Round brand mark for a platform. Monochrome automatically when grayscale mode is on. */
@Composable
internal fun PlatformBadge(
    platform: CruxPlatform,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(platform.brandColor),
        contentAlignment = Alignment.Center,
    ) {
        FAIcon(
            imageVector = platform.icon,
            contentDescription = platform.displayName,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Small dot used next to platforms in the side panel: filled when signed in. */
@Composable
internal fun StatusDot(
    signedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
    )
}

@Composable
internal fun CruxPlatform.statusText(connections: CruxConnectionsState): String =
    when {
        !connections.isEnabled(this) -> {
            stringResource(R.string.crux_status_not_added)
        }

        this == CruxPlatform.Rss -> {
            stringResource(R.string.crux_status_rss)
        }

        connections.isSignedIn(this) -> {
            stringResource(R.string.crux_status_signed_in)
        }

        worksSignedOut -> {
            stringResource(R.string.crux_status_signed_out_ok)
        }

        else -> {
            stringResource(R.string.crux_status_sign_in_needed)
        }
    }

/**
 * Every Crux platform with its real state and the one action that matters next: add it, sign in,
 * or open it. Used by the accounts screen; one row per platform, so nothing is listed twice.
 */
@Composable
internal fun CruxPlatformList(
    connections: CruxConnectionsState,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CruxPlatform.entries.forEach { platform ->
            CruxPlatformRow(
                platform = platform,
                connections = connections,
                onAdd = {
                    connections.setEnabled(platform, true)
                    if (!platform.worksSignedOut) navigate(platform.route)
                },
                onOpen = { navigate(platform.route) },
            )
        }
    }
}

@Composable
private fun CruxPlatformRow(
    platform: CruxPlatform,
    connections: CruxConnectionsState,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
) {
    val enabled = connections.isEnabled(platform)
    val signedIn = connections.isSignedIn(platform)
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        onClick = if (enabled) onOpen else onAdd,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlatformBadge(platform = platform)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = platform.statusText(connections),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (enabled && signedIn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                !enabled -> {
                    FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.crux_action_add)) }
                }

                platform.site != null && !signedIn -> {
                    OutlinedButton(
                        onClick = onOpen,
                    ) { Text(stringResource(R.string.crux_action_sign_in)) }
                }

                else -> {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.crux_action_open)) }
                }
            }
            if (enabled) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        FAIcon(FontAwesomeIcons.Solid.EllipsisVertical, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (platform.site != null && signedIn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.crux_action_sign_out)) },
                                onClick = {
                                    showMenu = false
                                    connections.signOut(platform)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.crux_action_remove),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                showMenu = false
                                connections.setEnabled(platform, false)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CruxSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
