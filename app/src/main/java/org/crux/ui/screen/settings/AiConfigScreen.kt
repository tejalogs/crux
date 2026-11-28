package org.crux.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.ai.OnDeviceHardwareBackend
import org.crux.data.ai.OnDeviceModelSpec
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.model.UiState
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.AiConfigPresenter
import org.crux.ui.presenter.settings.AiReasoningEffortOption
import org.crux.ui.presenter.settings.AiTypeOption
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.single

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiConfigScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { presenter() }
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_ai_config_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(it)
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            val tldrPromptTitle = stringResource(id = R.string.settings_ai_config_tldr_prompt)
            val onDeviceOllamaTitle = stringResource(id = R.string.settings_ai_config_on_device_ollama)
            val onDeviceOllamaDesc = stringResource(id = R.string.settings_ai_config_on_device_ollama_desc)
            val onDeviceOllamaHint = stringResource(id = R.string.settings_ai_config_on_device_ollama_hint)

            Text(
                text = stringResource(id = R.string.settings_ai_section_provider),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            SegmentedListItem(
                shapes = ListItemDefaults.first(),
                content = {
                    Text(text = stringResource(id = R.string.settings_ai_config_on_device_privacy))
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_on_device_privacy_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )

            val currentSpec = OnDeviceModelSpec.findById(state.onDeviceModel)
            OnDeviceModelsSection(
                activeModelId = currentSpec.modelId,
                onSelectModel = { state.setOnDeviceModel(it) },
            )
            SegmentedListItem(
                checked = state.showOnDeviceModelDropdown,
                onCheckedChange = { state.setShowOnDeviceModelDropdown(it) },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_ai_config_on_device_model))
                },
                supportingContent = {
                    Text(
                        text =
                            "${currentSpec.displayName} • LiteRT-LM " +
                                "(${currentSpec.parameterCount}, ~${currentSpec.memoryFootprintMb}MB RAM)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Box {
                        TextButton(onClick = { state.setShowOnDeviceModelDropdown(true) }) {
                            Text(text = currentSpec.displayName)
                        }
                        CruxDropdownMenu(
                            expanded = state.showOnDeviceModelDropdown,
                            onDismissRequest = { state.setShowOnDeviceModelDropdown(false) },
                        ) {
                            OnDeviceModelSpec.SupportedModels.forEach { spec ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(text = spec.displayName)
                                            Text(
                                                text = "${spec.parameterCount} params • ~${spec.memoryFootprintMb}MB RAM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    onClick = {
                                        state.setShowOnDeviceModelDropdown(false)
                                        state.setOnDeviceModel(spec.displayName)
                                    },
                                )
                            }
                        }
                    }
                },
            )

            SegmentedListItem(
                checked = state.showOnDeviceBackendDropdown,
                onCheckedChange = { state.setShowOnDeviceBackendDropdown(it) },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_ai_config_on_device_backend))
                },
                supportingContent = {
                    Text(
                        text = OnDeviceHardwareBackend.fromId(state.onDeviceHardwareBackend).label,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Box {
                        TextButton(onClick = { state.setShowOnDeviceBackendDropdown(true) }) {
                            Text(text = OnDeviceHardwareBackend.fromId(state.onDeviceHardwareBackend).name)
                        }
                        CruxDropdownMenu(
                            expanded = state.showOnDeviceBackendDropdown,
                            onDismissRequest = { state.setShowOnDeviceBackendDropdown(false) },
                        ) {
                            OnDeviceHardwareBackend.entries.forEach { backend ->
                                DropdownMenuItem(
                                    text = { Text(text = backend.label) },
                                    onClick = {
                                        state.setShowOnDeviceBackendDropdown(false)
                                        state.setOnDeviceHardwareBackend(backend.id)
                                    },
                                )
                            }
                        }
                    }
                },
            )

            SegmentedListItem(
                onClick = { state.setOnDeviceMtpEnabled(!state.onDeviceMtpEnabled) },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(text = stringResource(id = R.string.settings_ai_config_on_device_mtp))
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_on_device_mtp_desc),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.onDeviceMtpEnabled,
                        onCheckedChange = { state.setOnDeviceMtpEnabled(it) },
                    )
                },
            )

            SegmentedListItem(
                checked = state.textEditDialog?.field == AiConfigEditField.OnDeviceEndpoint,
                onCheckedChange = { checked ->
                    if (checked) {
                        state.setTextEditDialog(
                            TextEditDialogState(
                                field = AiConfigEditField.OnDeviceEndpoint,
                                title = onDeviceOllamaTitle,
                                placeholder = onDeviceOllamaHint,
                                value = state.onDeviceLocalhostEndpoint,
                                hint = onDeviceOllamaDesc,
                                onConfirm = { newValue ->
                                    state.setOnDeviceLocalhostEndpoint(newValue)
                                },
                            ),
                        )
                    } else if (state.textEditDialog?.field == AiConfigEditField.OnDeviceEndpoint) {
                        state.setTextEditDialog(null)
                    }
                },
                shapes = ListItemDefaults.last(),
                content = {
                    Text(text = onDeviceOllamaTitle)
                },
                supportingContent = {
                    Text(
                        text =
                            state.onDeviceLocalhostEndpoint.ifBlank {
                                onDeviceOllamaHint
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.settings_ai_section_features),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            SegmentedListItem(
                onClick = {
                    state.setAITldr(!state.aiTldr)
                },
                shapes =
                    if (state.aiTldr) {
                        ListItemDefaults.first()
                    } else {
                        ListItemDefaults.single()
                    },
                content = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_enable_tldr),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_tldr_description),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.aiTldr,
                        onCheckedChange = {
                            state.setAITldr(it)
                        },
                    )
                },
            )
            AnimatedVisibility(visible = state.aiTldr) {
                SegmentedListItem(
                    checked = state.textEditDialog?.field == AiConfigEditField.TldrPrompt,
                    onCheckedChange = { checked ->
                        if (checked) {
                            state.setTextEditDialog(
                                TextEditDialogState(
                                    field = AiConfigEditField.TldrPrompt,
                                    title = tldrPromptTitle,
                                    placeholder = "",
                                    value = state.tldrPrompt,
                                    onConfirm = { newValue ->
                                        state.setTldrPrompt(newValue)
                                    },
                                ),
                            )
                        } else if (state.textEditDialog?.field == AiConfigEditField.TldrPrompt) {
                            state.setTextEditDialog(null)
                        }
                    },
                    shapes = ListItemDefaults.last(),
                    content = {
                        Text(text = tldrPromptTitle)
                    },
                    supportingContent = {
                        Text(
                            text =
                                state.tldrPrompt.ifBlank {
                                    stringResource(id = R.string.settings_ai_config_value_empty_placeholder)
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }
    }
    state.textEditDialog?.let { dialog ->
        TextEditDialog(
            title = dialog.title,
            placeholder = dialog.placeholder,
            value = dialog.value,
            suggestions = dialog.suggestions,
            hint = dialog.hint,
            onDismiss = {
                state.setTextEditDialog(null)
            },
            onConfirm = { newValue ->
                dialog.onConfirm(newValue)
                state.setTextEditDialog(null)
            },
        )
    }
}

@Composable
private fun presenter() =
    run {
        val businessState = remember { AiConfigPresenter() }.invoke()
        var showOnDeviceModelDropdown by remember { mutableStateOf(false) }
        var showOnDeviceBackendDropdown by remember { mutableStateOf(false) }
        var textEditDialog by remember { mutableStateOf<TextEditDialogState?>(null) }

        object : AiConfigPresenter.State by businessState {
            val showOnDeviceModelDropdown = showOnDeviceModelDropdown
            val showOnDeviceBackendDropdown = showOnDeviceBackendDropdown
            val textEditDialog = textEditDialog

            fun setShowOnDeviceModelDropdown(value: Boolean) {
                showOnDeviceModelDropdown = value
            }

            fun setShowOnDeviceBackendDropdown(value: Boolean) {
                showOnDeviceBackendDropdown = value
            }

            fun setTextEditDialog(value: TextEditDialogState?) {
                textEditDialog = value
            }
        }
    }

private data class TextEditDialogState(
    val field: AiConfigEditField,
    val title: String,
    val placeholder: String,
    val value: String,
    val suggestions: ImmutableList<String> = persistentListOf(),
    val hint: String = "",
    val onConfirm: (String) -> Unit,
)

private enum class AiConfigEditField {
    TldrPrompt,
    OnDeviceEndpoint,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextEditDialog(
    title: String,
    placeholder: String,
    value: String,
    suggestions: ImmutableList<String>,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    var showSuggestions by remember(value) { mutableStateOf(false) }
    val filteredSuggestions =
        remember(text, suggestions) {
            suggestions.filter { item ->
                text.isBlank() || item.contains(text, ignoreCase = true)
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column {
                if (suggestions.isEmpty()) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            if (placeholder.isNotBlank()) {
                                Text(text = placeholder)
                            }
                        },
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                        onExpandedChange = { showSuggestions = it },
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {
                                text = it
                                showSuggestions = true
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                            placeholder = {
                                if (placeholder.isNotBlank()) {
                                    Text(text = placeholder)
                                }
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                                )
                            },
                        )
                        ExposedDropdownMenu(
                            expanded = showSuggestions && filteredSuggestions.isNotEmpty(),
                            onDismissRequest = { showSuggestions = false },
                            modifier = Modifier.heightIn(max = 240.dp),
                        ) {
                            filteredSuggestions.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        text = item
                                        showSuggestions = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (hint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        },
    )
}

