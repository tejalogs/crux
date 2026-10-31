package dev.dimension.flare.ui.screen.localfollow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.localfollow.LocalFollowsPresenter
import dev.dimension.flare.ui.presenter.localfollow.UiLocalFollowGroup
import dev.dimension.flare.ui.theme.screenHorizontalPadding

private sealed interface GroupDialog {
    data object Create : GroupDialog

    data class Rename(
        val group: UiLocalFollowGroup,
    ) : GroupDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalFollowsScreen(
    onOpenFeed: (groupId: String?, title: String) -> Unit,
    onBack: () -> Unit,
) {
    val state = remember { LocalFollowsPresenter() }.invoke()
    val follows = state.follows.takeSuccess().orEmpty()
    val groups = state.groups.takeSuccess().orEmpty()
    val allTitle = stringResource(R.string.local_follow_all)
    var dialog by remember { mutableStateOf<GroupDialog?>(null) }

    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = { Text(text = stringResource(R.string.local_follows_title)) },
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    IconButton(onClick = { dialog = GroupDialog.Create }) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Plus,
                            contentDescription = stringResource(R.string.local_follow_new_group),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = screenHorizontalPadding),
            contentPadding = contentPadding,
        ) {
            if (state.follows is UiState.Success && follows.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.local_follow_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            item(key = "all") {
                FeedRow(
                    title = allTitle,
                    count = follows.size,
                    onOpen = { onOpenFeed(null, allTitle) },
                    onPin = { state.pinToHome(null, allTitle) },
                )
            }
            items(groups, key = { it.id }) { group ->
                FeedRow(
                    title = group.name,
                    count = group.count,
                    onOpen = { onOpenFeed(group.id, group.name) },
                    onPin = { state.pinToHome(group.id, group.name) },
                    onRename = { dialog = GroupDialog.Rename(group) },
                    onDelete = { state.deleteGroup(group.id) },
                )
            }
            if (follows.isNotEmpty()) {
                item(key = "accounts") {
                    Text(
                        text = stringResource(R.string.local_follow_accounts),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            items(follows, key = { "${it.accountKey}/${it.userKey}" }) { follow ->
                ListItem(
                    headlineContent = { Text(text = follow.displayName) },
                    supportingContent = { Text(text = follow.handle) },
                    trailingContent = {
                        IconButton(onClick = { state.remove(follow) }) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Trash,
                                contentDescription = stringResource(R.string.local_follow_remove),
                            )
                        }
                    },
                )
            }
        }
    }

    dialog?.let { current ->
        GroupNameDialog(
            initialName = (current as? GroupDialog.Rename)?.group?.name.orEmpty(),
            onConfirm = { name ->
                when (current) {
                    GroupDialog.Create -> state.createGroup(name)
                    is GroupDialog.Rename -> state.renameGroup(current.group.id, name)
                }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun FeedRow(
    title: String,
    count: Int,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = pluralStringResource(R.plurals.local_follow_count, count, count)) },
        trailingContent = {
            Row {
                IconButton(onClick = onPin) {
                    FAIcon(
                        FontAwesomeIcons.Solid.Thumbtack,
                        contentDescription = stringResource(R.string.local_follow_pin),
                    )
                }
                onRename?.let {
                    IconButton(onClick = it) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Pen,
                            contentDescription = stringResource(R.string.local_follow_rename_group),
                        )
                    }
                }
                onDelete?.let {
                    IconButton(onClick = it) {
                        FAIcon(
                            FontAwesomeIcons.Solid.Trash,
                            contentDescription = stringResource(R.string.local_follow_delete_group),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun GroupNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.local_follow_group_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}
