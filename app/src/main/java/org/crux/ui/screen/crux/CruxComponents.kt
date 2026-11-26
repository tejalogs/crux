package org.crux.ui.screen.crux

import android.widget.Toast
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 *
 * With [snackbarHostState], hiding a tab shows "X is no longer in your tabs", with Undo only when
 * Undo restores everything (see [RemoveAction.offersUndo]); without it, a plain Toast. Pass [snackbarScope] from the screen when this list sits in a lazy list, so the
 * snackbar (and its Undo) outlives the list scrolling out of view.
 */
@Composable
internal fun CruxPlatformList(
    connections: CruxConnectionsState,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    snackbarScope: CoroutineScope? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val localScope = rememberCoroutineScope()
    val scope = snackbarScope ?: localScope
    // Undo can fire after the list has recomposed, so it must act on the newest state.
    val latestConnections by rememberUpdatedState(connections)
    val onHidden: (CruxPlatform, Boolean) -> Unit = { platform, offerUndo ->
        val message = resources.getString(R.string.crux_platform_hidden, platform.displayName)
        if (snackbarHostState != null) {
            // Drop the snackbar for an earlier hide, so this one (and its Undo) shows at once
            // instead of queueing behind it.
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                val result =
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = if (offerUndo) resources.getString(R.string.crux_action_undo) else null,
                        duration = SnackbarDuration.Short,
                    )
                if (offerUndo && result == SnackbarResult.ActionPerformed) {
                    latestConnections.setEnabled(platform, true)
                }
            }
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CruxPlatform.entries.forEach { platform ->
            val signedIn = connections.isSignedIn(platform)
            CruxPlatformRow(
                platform = platform,
                connections = connections,
                onAdd = {
                    connections.setEnabled(platform, true)
                    if (!platform.worksSignedOut && !signedIn) navigate(platform.signInRoute)
                },
                onSignIn = { navigate(platform.signInRoute) },
                onOpen = { navigate(platform.route) },
                onHidden = { offerUndo -> onHidden(platform, offerUndo) },
            )
        }
    }
}

/** Whether Remove asks "Hide tab" or "Remove and sign out": only when a detected session would be lost. */
internal fun removeNeedsChoice(
    platform: CruxPlatform,
    signedIn: Boolean,
): Boolean = platform.site != null && signedIn

/**
 * What tapping Remove in a platform's menu does. [offersUndo] is true only when Undo would restore
 * everything Remove took away; Undo only turns the tab back on, it cannot bring back cleared cookies.
 */
internal enum class RemoveAction(
    val offersUndo: Boolean,
) {
    /** A detected session would be lost: ask "Hide tab" or "Remove and sign out". */
    AskHideOrSignOut(offersUndo = false),

    /**
     * A site with no detected session: clear its cookies and saved page. Detection can miss a session,
     * so no Undo: it would bring the tab back but not the sign-in or place, and look like a full reversal.
     */
    ClearWithoutUndo(offersUndo = false),

    /** Feeds has no sign-in: only the tab goes, the sources stay, and Undo brings it back. */
    HideAndOfferUndo(offersUndo = true),
}

/** "Hide tab (stay signed in)" only turns the tab off, so Undo restores it fully. */
internal const val HIDE_TAB_OFFERS_UNDO = true

internal fun removeAction(
    platform: CruxPlatform,
    signedIn: Boolean,
): RemoveAction =
    when {
        removeNeedsChoice(platform, signedIn) -> RemoveAction.AskHideOrSignOut
        platform.site != null -> RemoveAction.ClearWithoutUndo
        else -> RemoveAction.HideAndOfferUndo
    }

/** Sign out is offered only for a site with a detected session; Feeds has no sign-in at all. */
internal fun offersSignOut(
    platform: CruxPlatform,
    signedIn: Boolean,
): Boolean = platform.site != null && signedIn

private enum class PlatformConfirm { SignOut, Remove }

@Composable
private fun CruxPlatformRow(
    platform: CruxPlatform,
    connections: CruxConnectionsState,
    onAdd: () -> Unit,
    onSignIn: () -> Unit,
    onOpen: () -> Unit,
    onHidden: (offerUndo: Boolean) -> Unit,
) {
    val enabled = connections.isEnabled(platform)
    val signedIn = connections.isSignedIn(platform)
    var showMenu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<PlatformConfirm?>(null) }
    val hide = {
        connections.hide(platform)
        onHidden(HIDE_TAB_OFFERS_UNDO)
    }
    Surface(
        onClick =
            when {
                !enabled -> onAdd
                platform.site != null && !signedIn -> onSignIn
                else -> onOpen
            },
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
                        onClick = onSignIn,
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
                        if (offersSignOut(platform, signedIn)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.crux_action_sign_out)) },
                                onClick = {
                                    showMenu = false
                                    confirm = PlatformConfirm.SignOut
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
                                val action = removeAction(platform, signedIn)
                                when (action) {
                                    RemoveAction.AskHideOrSignOut -> {
                                        confirm = PlatformConfirm.Remove
                                    }

                                    RemoveAction.ClearWithoutUndo -> {
                                        // No session detected, but detection can miss one, so Remove keeps
                                        // its meaning: it still clears the site's cookies and saved page.
                                        // Undo could not restore those, so the snackbar offers none.
                                        connections.setEnabled(platform, false)
                                        onHidden(action.offersUndo)
                                    }

                                    // Feeds has no session: hiding loses nothing, and the Undo snackbar
                                    // (or the row's Add button) brings the tab back after a mis-tap.
                                    RemoveAction.HideAndOfferUndo -> {
                                        hide()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    when (confirm) {
        PlatformConfirm.SignOut -> {
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(stringResource(R.string.crux_sign_out_confirm_title, platform.displayName)) },
                text = { Text(stringResource(R.string.crux_sign_out_confirm_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirm = null
                            connections.signOut(platform)
                        },
                    ) {
                        Text(stringResource(R.string.crux_action_sign_out), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }

        PlatformConfirm.Remove -> {
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(stringResource(R.string.crux_remove_confirm_title, platform.displayName)) },
                text = { Text(stringResource(R.string.crux_remove_confirm_body)) },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(
                            onClick = {
                                confirm = null
                                hide()
                            },
                        ) {
                            Text(stringResource(R.string.crux_action_hide_tab))
                        }
                        TextButton(
                            onClick = {
                                confirm = null
                                connections.setEnabled(platform, false)
                            },
                        ) {
                            Text(stringResource(R.string.crux_action_remove_and_sign_out), color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) }
                    }
                },
            )
        }

        null -> Unit
    }
}

@Composable
internal fun CruxSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
