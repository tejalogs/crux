package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.Trash
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.data.privacy.PrivacySettingsRepository
import dev.dimension.flare.data.privacy.normaliseBlockedHost
import dev.dimension.flare.settings_privacy_blocklist_add
import dev.dimension.flare.settings_privacy_blocklist_empty
import dev.dimension.flare.settings_privacy_blocklist_hint
import dev.dimension.flare.settings_privacy_blocklist_invalid
import dev.dimension.flare.settings_privacy_blocklist_remove
import dev.dimension.flare.settings_privacy_blocklist_suffix_note
import dev.dimension.flare.settings_privacy_blocklist_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.CardExpanderItem
import io.github.composefluent.component.SubtleButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun PrivacyBlocklistScreen() {
    val repository = koinInject<PrivacySettingsRepository>()
    val settings by repository.settings().collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val textState = rememberTextFieldState()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val invalidHostMsg = stringResource(Res.string.settings_privacy_blocklist_invalid)

    fun addHost() {
        val host = normaliseBlockedHost(textState.text.toString())
        when {
            host.isEmpty() -> errorMessage = invalidHostMsg
            host in settings.blockedHosts -> {
                textState.edit { replace(0, length, "") }
                errorMessage = null
            }
            else -> {
                scope.launch {
                    repository.update(settings.copy(blockedHosts = settings.blockedHosts + host))
                }
                textState.edit { replace(0, length, "") }
                errorMessage = null
            }
        }
    }

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
                Header(stringResource(Res.string.settings_privacy_blocklist_title))
                Text(
                    text = stringResource(Res.string.settings_privacy_blocklist_suffix_note),
                    style = FluentTheme.typography.caption,
                    color = FluentTheme.colors.text.text.secondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        state = textState,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(Res.string.settings_privacy_blocklist_hint)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                    )
                    AccentButton(
                        onClick = ::addHost,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Plus,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                        Text(stringResource(Res.string.settings_privacy_blocklist_add))
                    }
                }
                errorMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg,
                        style = FluentTheme.typography.caption,
                        color = FluentTheme.colors.system.critical,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (settings.blockedHosts.isNotEmpty()) {
                items(settings.blockedHosts.toList()) { host ->
                    CardExpanderItem(
                        icon = null,
                        heading = {
                            Text(
                                text = host,
                                style = FluentTheme.typography.body,
                                color = FluentTheme.colors.text.text.primary,
                            )
                        },
                        trailing = {
                            SubtleButton(
                                onClick = {
                                    scope.launch {
                                        repository.update(settings.copy(blockedHosts = settings.blockedHosts - host))
                                    }
                                },
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.Trash,
                                    contentDescription = stringResource(Res.string.settings_privacy_blocklist_remove),
                                )
                            }
                        },
                    )
                }
            } else {
                item {
                    Text(
                        text = stringResource(Res.string.settings_privacy_blocklist_empty),
                        style = FluentTheme.typography.body,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }
            }
        }
    }
}
