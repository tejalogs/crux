package dev.dimension.flare.ui.screen.localfollow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.localfollow.EditLocalFollowPresenter

@Composable
internal fun EditLocalFollowSheet(
    accountKey: MicroBlogKey,
    userKey: MicroBlogKey,
    onDismiss: () -> Unit,
) {
    val state = remember(accountKey, userKey) { EditLocalFollowPresenter(accountKey, userKey) }.invoke()
    val user = remember(accountKey, userKey) { UserPresenter(AccountType.Specific(accountKey), userKey) }.invoke().user
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.groupId) {
        selectedGroup = state.groupId
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.local_follow_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.local_follow_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GroupOption(
            title = stringResource(R.string.local_follow_no_group),
            selected = selectedGroup == null,
            onSelect = { selectedGroup = null },
        )
        state.groups.onSuccess { groups ->
            groups.forEach { group ->
                GroupOption(
                    title = group.name,
                    selected = selectedGroup == group.id,
                    onSelect = { selectedGroup = group.id },
                )
            }
        }
        Button(
            enabled = user.takeSuccess() != null,
            onClick = {
                user.takeSuccess()?.let { state.save(it, selectedGroup) }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(if (state.isFollowing) R.string.local_follow_update else R.string.local_follow_title))
        }
        if (state.isFollowing) {
            OutlinedButton(
                onClick = {
                    state.remove()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.local_follow_remove))
            }
        }
    }
}

@Composable
private fun GroupOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = title, modifier = Modifier.padding(start = 12.dp))
    }
}
