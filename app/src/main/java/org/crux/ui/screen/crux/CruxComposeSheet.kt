package org.crux.ui.screen.crux

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Link
import org.crux.R
import org.crux.ui.component.FAIcon
import org.crux.ui.route.Route
import org.crux.ui.theme.screenHorizontalPadding

private const val X_CHARACTER_LIMIT = 280

/**
 * Write once, tap the platforms, post. Crux opens each site's own composer already filled in and
 * steps through them; the person presses Post on each, because auto-submitting inside a logged-in
 * account is what gets accounts flagged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CruxComposeSheet(
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
    initialText: String = "",
) {
    val connections = rememberCruxConnections()
    var text by rememberSaveable { mutableStateOf(initialText) }
    var link by rememberSaveable { mutableStateOf("") }
    var showLink by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<CruxPlatform>() }
    var initialised by rememberSaveable { mutableStateOf(false) }
    val targets = connections.postTargets
    LaunchedEffect(connections.loaded, connections.signedIn) {
        if (connections.loaded && !initialised) {
            selected.clear()
            selected.addAll(targets.filter { connections.isSignedIn(it) }.ifEmpty { targets })
            initialised = true
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenHorizontalPadding)
                .navigationBarsPadding()
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.crux_compose_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.crux_compose_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.crux_compose_placeholder)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .focusRequester(focusRequester),
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = {
                val overX = CruxPlatform.X in selected && text.length > X_CHARACTER_LIMIT
                Text(
                    text =
                        if (overX) {
                            stringResource(R.string.crux_compose_x_limit, text.length, X_CHARACTER_LIMIT)
                        } else {
                            text.length.toString()
                        },
                    color = if (overX) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        AnimatedVisibility(visible = showLink) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it.trim() },
                label = { Text(stringResource(R.string.crux_compose_link_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        if (!showLink) {
            TextButton(onClick = { showLink = true }) {
                FAIcon(FontAwesomeIcons.Solid.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.crux_compose_add_link))
            }
        }

        Text(
            text = stringResource(R.string.crux_compose_post_to),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (targets.isEmpty()) {
            Text(
                text = stringResource(R.string.crux_compose_no_platforms),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                onBack()
                navigate(Route.Settings.Accounts)
            }) {
                Text(stringResource(R.string.crux_manage_platforms))
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                targets.forEach { platform ->
                    val isSelected = platform in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = { if (isSelected) selected.remove(platform) else selected.add(platform) },
                        label = {
                            Column {
                                Text(platform.displayName)
                                if (!connections.isSignedIn(platform)) {
                                    Text(
                                        text = stringResource(R.string.crux_status_sign_in_needed),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            PlatformBadge(platform = platform, size = 22.dp)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.crux_compose_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.weight(1f))
            val ordered = targets.filter { it in selected }
            Button(
                onClick = {
                    val sites = ordered.mapNotNull { it.site?.name }
                    onBack()
                    navigate(Route.LiveShare(sites = sites, text = text.trim(), url = link, slidesSaved = false))
                },
                enabled = (text.isNotBlank() || link.isNotBlank()) && ordered.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text =
                        if (ordered.size <= 1) {
                            stringResource(R.string.crux_compose_post)
                        } else {
                            stringResource(R.string.crux_compose_post_count, ordered.size)
                        },
                )
            }
        }
    }
}
