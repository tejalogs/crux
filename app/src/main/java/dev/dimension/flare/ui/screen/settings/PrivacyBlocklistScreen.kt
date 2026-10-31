package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.R
import dev.dimension.flare.data.privacy.PrivacySettingsRepository
import dev.dimension.flare.data.privacy.normaliseBlockedHost
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.theme.item
import dev.dimension.flare.ui.theme.last
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Editor for the local host blocklist.
 *
 * The blocklist is enforced client-side by the privacy plugin: a matching host is refused before
 * any socket is opened, so a blocked domain never learns that the app exists, let alone that it
 * was opened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyBlocklistScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val repository = koinInject<PrivacySettingsRepository>()
    val settings by repository.settings().collectAsState()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidHostMessage = stringResource(id = R.string.settings_privacy_blocklist_invalid)

    fun commit() {
        val host = normaliseBlockedHost(draft)
        when {
            host.isEmpty() -> {
                error = invalidHostMessage
            }

            host in settings.blockedHosts -> {
                draft = ""
                error = null
            }

            else -> {
                scope.launch {
                    repository.update(settings.copy(blockedHosts = settings.blockedHosts + host))
                }
                draft = ""
                error = null
            }
        }
    }

    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_privacy_blocklist_title)) },
                navigationIcon = { BackButton(onBack = onBack) },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = screenHorizontalPadding),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    error = null
                },
                label = { Text(text = stringResource(id = R.string.settings_privacy_blocklist_hint)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(text = it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(
                onClick = { commit() },
                enabled = draft.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(text = stringResource(id = R.string.settings_privacy_blocklist_add))
            }
            Text(
                text = stringResource(id = R.string.settings_privacy_blocklist_suffix_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )

            if (settings.blockedHosts.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.settings_privacy_blocklist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(settings.blockedHosts.sorted(), key = { it }) { host ->
                    SegmentedListItem(
                        onClick = {},
                        enabled = false,
                        shapes = ListItemDefaults.item(),
                        content = { Text(text = host) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        repository.update(
                                            settings.copy(blockedHosts = settings.blockedHosts - host),
                                        )
                                    }
                                },
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Trash,
                                    contentDescription =
                                        stringResource(id = R.string.settings_privacy_blocklist_remove),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
