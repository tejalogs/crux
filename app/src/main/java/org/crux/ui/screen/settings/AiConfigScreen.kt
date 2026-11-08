package org.crux.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import kotlinx.collections.immutable.toImmutableList
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.ai.OnDeviceHardwareBackend
import org.crux.data.ai.OnDeviceModelSpec
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxDropdownMenu
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.RichText
import org.crux.ui.model.UiState
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.AiConfigPresenter
import org.crux.ui.presenter.settings.AiReasoningEffortOption
import org.crux.ui.presenter.settings.AiTranslationTestPresenter
import org.crux.ui.presenter.settings.AiTypeOption
import org.crux.ui.presenter.settings.TranslateProviderOption
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.segmentedShapes2
import org.crux.ui.theme.single
import java.util.Locale

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

            val currentSpec =
                OnDeviceModelSpec.SupportedModels.firstOrNull {
                    it.displayName.equals(state.onDeviceModel, ignoreCase = true) ||
                        it.modelId.equals(state.onDeviceModel, ignoreCase = true)
                } ?: OnDeviceModelSpec.Gemma4E2B
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
                    state.setAIAgent(!state.aiAgent)
                },
                shapes = ListItemDefaults.single(),
                content = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_enable_agent),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_agent_description),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.aiAgent,
                        onCheckedChange = {
                            state.setAIAgent(it)
                        },
                    )
                },
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslationConfigScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter { translationPresenter() }
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_translation_title))
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
            val translatePromptTitle = stringResource(id = R.string.settings_ai_config_translate_prompt)
            val deepLApiKeyTitle = stringResource(id = R.string.settings_ai_config_translate_provider_deepl_api_key)
            val googleCloudApiKeyTitle = stringResource(id = R.string.settings_ai_config_translate_provider_google_cloud_api_key)
            val libreTranslateBaseUrlTitle = stringResource(id = R.string.settings_ai_config_translate_provider_libretranslate_base_url)
            val libreTranslateApiKeyTitle = stringResource(id = R.string.settings_ai_config_translate_provider_libretranslate_api_key)
            val excludedLanguagesTitle = stringResource(id = R.string.settings_translation_auto_excluded_languages)
            val emptyPlaceholder = stringResource(id = R.string.settings_ai_config_value_empty_placeholder)
            val hasProviderSettings = state.translateProvider != TranslateProviderOption.GoogleWeb
            SegmentedListItem(
                checked = state.showProviderDropdown,
                onCheckedChange = { checked ->
                    state.setShowProviderDropdown(checked)
                },
                shapes = ListItemDefaults.first(),
                content = {
                    Text(text = stringResource(id = R.string.settings_ai_config_translate_provider))
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_translate_provider_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    Box {
                        TextButton(
                            onClick = {
                                state.setShowProviderDropdown(true)
                            },
                        ) {
                            Text(
                                text = translateProviderOptionLabel(state.translateProvider),
                            )
                        }
                        CruxDropdownMenu(
                            expanded = state.showProviderDropdown,
                            onDismissRequest = {
                                state.setShowProviderDropdown(false)
                            },
                        ) {
                            state.supportedTranslateProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = translateProviderOptionLabel(provider))
                                    },
                                    onClick = {
                                        state.setShowProviderDropdown(false)
                                        state.selectTranslateProvider(provider)
                                    },
                                )
                            }
                        }
                    }
                },
            )
            SegmentedListItem(
                onClick = {
                    state.setPreTranslate(!state.preTranslate)
                },
                shapes = ListItemDefaults.item(),
                content = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_enable_pre_translation),
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(id = R.string.settings_ai_config_pre_translation_description),
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.preTranslate,
                        onCheckedChange = {
                            state.setPreTranslate(it)
                        },
                    )
                },
            )
            SegmentedListItem(
                onClick = {
                    state.setShowOriginalWithTranslation(!state.showOriginalWithTranslation)
                },
                shapes =
                    if (state.preTranslate || hasProviderSettings) {
                        ListItemDefaults.item()
                    } else {
                        ListItemDefaults.last()
                    },
                content = {
                    Text(text = stringResource(id = R.string.settings_translation_show_original_with_translation))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_translation_show_original_with_translation_description))
                },
                trailingContent = {
                    Switch(
                        checked = state.showOriginalWithTranslation,
                        onCheckedChange = state::setShowOriginalWithTranslation,
                    )
                },
            )
            AnimatedVisibility(state.preTranslate) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SegmentedListItem(
                        onClick = {
                            state.setPreferPlatformTranslation(!state.preferPlatformTranslation)
                        },
                        shapes = ListItemDefaults.item(),
                        content = {
                            Text(text = stringResource(id = R.string.settings_translation_prefer_platform))
                        },
                        supportingContent = {
                            Text(text = stringResource(id = R.string.settings_translation_prefer_platform_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = state.preferPlatformTranslation,
                                onCheckedChange = state::setPreferPlatformTranslation,
                            )
                        },
                    )
                    SegmentedListItem(
                        checked = state.showExcludedLanguagesDialog,
                        onCheckedChange = { checked ->
                            state.setShowExcludedLanguagesDialog(checked)
                        },
                        shapes =
                            if (hasProviderSettings) {
                                ListItemDefaults.item()
                            } else {
                                ListItemDefaults.last()
                            },
                        content = {
                            Text(text = excludedLanguagesTitle)
                        },
                        supportingContent = {
                            Text(
                                text =
                                    state.autoTranslateExcludedLanguages
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString()
                                        ?: emptyPlaceholder,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = hasProviderSettings,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    when (state.translateProvider) {
                        TranslateProviderOption.AI -> {
                            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                                SegmentedListItem(
                                    checked = state.textEditDialog?.field == TranslationEditField.TranslatePrompt,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            state.setTextEditDialog(
                                                TranslationTextEditDialogState(
                                                    field = TranslationEditField.TranslatePrompt,
                                                    title = translatePromptTitle,
                                                    placeholder = "",
                                                    value = state.translatePrompt,
                                                    onConfirm = { newValue ->
                                                        state.setTranslatePrompt(newValue)
                                                    },
                                                ),
                                            )
                                        } else if (state.textEditDialog?.field == TranslationEditField.TranslatePrompt) {
                                            state.setTextEditDialog(null)
                                        }
                                    },
                                    shapes = ListItemDefaults.item(),
                                    content = {
                                        Text(text = translatePromptTitle)
                                    },
                                    supportingContent = {
                                        Text(
                                            text = state.translatePrompt.ifBlank { emptyPlaceholder },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                )
                                SegmentedListItem(
                                    onClick = {},
                                    shapes = ListItemDefaults.last(),
                                    content = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(text = stringResource(id = R.string.settings_translation_ai_test_title))
                                            Text(
                                                text = stringResource(id = R.string.settings_translation_ai_test_description),
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                text = stringResource(id = R.string.settings_translation_ai_test_original),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                            RichText(text = state.aiTranslationTestState.sampleText)
                                            Button(
                                                onClick = {
                                                    state.aiTranslationTestState.runTest()
                                                },
                                            ) {
                                                Text(text = stringResource(id = R.string.settings_translation_ai_test_action))
                                            }
                                            if (state.aiTranslationTestState.isLoading) {
                                                Text(
                                                    text = stringResource(id = R.string.settings_ai_config_model_loading),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            state.aiTranslationTestState.errorMessage?.let { message ->
                                                Text(
                                                    text = message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            state.aiTranslationTestState.translatedText?.let { translated ->
                                                Text(
                                                    text = stringResource(id = R.string.settings_translation_ai_test_result),
                                                    style = MaterialTheme.typography.labelMedium,
                                                )
                                                RichText(text = translated)
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        TranslateProviderOption.DeepL -> {
                            SegmentedListItem(
                                checked = state.textEditDialog?.field == TranslationEditField.DeepLApiKey,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        state.setTextEditDialog(
                                            TranslationTextEditDialogState(
                                                field = TranslationEditField.DeepLApiKey,
                                                title = deepLApiKeyTitle,
                                                placeholder = "",
                                                value = state.deepLApiKey,
                                                onConfirm = { newValue ->
                                                    state.setDeepLApiKey(newValue)
                                                },
                                            ),
                                        )
                                    } else if (state.textEditDialog?.field == TranslationEditField.DeepLApiKey) {
                                        state.setTextEditDialog(null)
                                    }
                                },
                                shapes = ListItemDefaults.item(),
                                content = {
                                    Text(text = deepLApiKeyTitle)
                                },
                                supportingContent = {
                                    Text(
                                        text = state.deepLApiKey.ifBlank { emptyPlaceholder },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                            )
                            SegmentedListItem(
                                onClick = {
                                    state.setDeepLUsePro(!state.deepLUsePro)
                                },
                                shapes = ListItemDefaults.last(),
                                content = {
                                    Text(text = stringResource(id = R.string.settings_ai_config_translate_provider_deepl_use_pro))
                                },
                                supportingContent = {
                                    Text(
                                        text =
                                            stringResource(
                                                id = R.string.settings_ai_config_translate_provider_deepl_use_pro_description,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = state.deepLUsePro,
                                        onCheckedChange = {
                                            state.setDeepLUsePro(it)
                                        },
                                    )
                                },
                            )
                        }

                        TranslateProviderOption.GoogleCloud -> {
                            SegmentedListItem(
                                checked = state.textEditDialog?.field == TranslationEditField.GoogleCloudApiKey,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        state.setTextEditDialog(
                                            TranslationTextEditDialogState(
                                                field = TranslationEditField.GoogleCloudApiKey,
                                                title = googleCloudApiKeyTitle,
                                                placeholder = "",
                                                value = state.googleCloudApiKey,
                                                onConfirm = { newValue ->
                                                    state.setGoogleCloudApiKey(newValue)
                                                },
                                            ),
                                        )
                                    } else if (state.textEditDialog?.field == TranslationEditField.GoogleCloudApiKey) {
                                        state.setTextEditDialog(null)
                                    }
                                },
                                content = {
                                    Text(text = googleCloudApiKeyTitle)
                                },
                                supportingContent = {
                                    Text(
                                        text = state.googleCloudApiKey.ifBlank { emptyPlaceholder },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                shapes = ListItemDefaults.last(),
                            )
                        }

                        TranslateProviderOption.LibreTranslate -> {
                            SegmentedListItem(
                                checked = state.textEditDialog?.field == TranslationEditField.LibreTranslateBaseUrl,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        state.setTextEditDialog(
                                            TranslationTextEditDialogState(
                                                field = TranslationEditField.LibreTranslateBaseUrl,
                                                title = libreTranslateBaseUrlTitle,
                                                placeholder = "https://libretranslate.example.com",
                                                value = state.libreTranslateBaseUrl,
                                                onConfirm = { newValue ->
                                                    state.setLibreTranslateBaseUrl(newValue)
                                                },
                                            ),
                                        )
                                    } else if (state.textEditDialog?.field == TranslationEditField.LibreTranslateBaseUrl) {
                                        state.setTextEditDialog(null)
                                    }
                                },
                                shapes = ListItemDefaults.item(),
                                content = {
                                    Text(text = libreTranslateBaseUrlTitle)
                                },
                                supportingContent = {
                                    Text(
                                        text = state.libreTranslateBaseUrl.ifBlank { emptyPlaceholder },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                            )
                            SegmentedListItem(
                                checked = state.textEditDialog?.field == TranslationEditField.LibreTranslateApiKey,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        state.setTextEditDialog(
                                            TranslationTextEditDialogState(
                                                field = TranslationEditField.LibreTranslateApiKey,
                                                title = libreTranslateApiKeyTitle,
                                                placeholder = "",
                                                value = state.libreTranslateApiKey,
                                                onConfirm = { newValue ->
                                                    state.setLibreTranslateApiKey(newValue)
                                                },
                                            ),
                                        )
                                    } else if (state.textEditDialog?.field == TranslationEditField.LibreTranslateApiKey) {
                                        state.setTextEditDialog(null)
                                    }
                                },
                                shapes = ListItemDefaults.last(),
                                content = {
                                    Text(text = libreTranslateApiKeyTitle)
                                },
                                supportingContent = {
                                    Text(
                                        text = state.libreTranslateApiKey.ifBlank { emptyPlaceholder },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                            )
                        }

                        TranslateProviderOption.GoogleWeb -> {}
                    }
                }
            }
        }
    }
    if (state.showExcludedLanguagesDialog) {
        ExcludedLanguagesDialog(
            title = stringResource(id = R.string.settings_translation_auto_excluded_languages),
            options = remember(state.autoTranslateExcludedLanguages) { translationLanguageOptions(state.autoTranslateExcludedLanguages) },
            selectedLanguages = state.autoTranslateExcludedLanguages,
            onDismiss = {
                state.setShowExcludedLanguagesDialog(false)
            },
            onConfirm = { selectedLanguages ->
                state.setAutoTranslateExcludedLanguages(selectedLanguages)
                state.setShowExcludedLanguagesDialog(false)
            },
        )
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
private fun translationPresenter() =
    run {
        val businessState = remember { AiConfigPresenter() }.invoke()
        val aiTranslationTestState = remember { AiTranslationTestPresenter() }.invoke()
        var showProviderDropdown by remember { mutableStateOf(false) }
        var showExcludedLanguagesDialog by remember { mutableStateOf(false) }
        var textEditDialog by remember { mutableStateOf<TranslationTextEditDialogState?>(null) }

        object : AiConfigPresenter.State by businessState {
            val aiTranslationTestState = aiTranslationTestState
            val showProviderDropdown = showProviderDropdown
            val showExcludedLanguagesDialog = showExcludedLanguagesDialog
            val textEditDialog = textEditDialog

            fun setShowProviderDropdown(value: Boolean) {
                showProviderDropdown = value
            }

            fun setShowExcludedLanguagesDialog(value: Boolean) {
                showExcludedLanguagesDialog = value
            }

            fun setTextEditDialog(value: TranslationTextEditDialogState?) {
                textEditDialog = value
            }
        }
    }

private data class TranslationTextEditDialogState(
    val field: TranslationEditField,
    val title: String,
    val placeholder: String,
    val value: String,
    val suggestions: ImmutableList<String> = persistentListOf(),
    val hint: String = "",
    val onConfirm: (String) -> Unit,
)

private enum class TranslationEditField {
    TranslatePrompt,
    DeepLApiKey,
    GoogleCloudApiKey,
    LibreTranslateBaseUrl,
    LibreTranslateApiKey,
}

@Composable
private fun translateProviderOptionLabel(provider: TranslateProviderOption): String =
    when (provider) {
        TranslateProviderOption.AI -> stringResource(id = R.string.settings_ai_config_translate_provider_ai)
        TranslateProviderOption.GoogleWeb -> stringResource(id = R.string.settings_ai_config_translate_provider_google_web)
        TranslateProviderOption.DeepL -> stringResource(id = R.string.settings_ai_config_translate_provider_deepl)
        TranslateProviderOption.GoogleCloud -> stringResource(id = R.string.settings_ai_config_translate_provider_google_cloud)
        TranslateProviderOption.LibreTranslate -> stringResource(id = R.string.settings_ai_config_translate_provider_libretranslate)
    }

private data class LanguageOption(
    val tag: String,
    val label: String,
)

private fun translationLanguageOptions(selectedLanguages: ImmutableList<String>): ImmutableList<LanguageOption> {
    val displayLocale = Locale.getDefault()
    val baseOptions =
        Locale
            .getISOLanguages()
            .map { code ->
                @Suppress("DEPRECATION")
                LanguageOption(tag = code, label = Locale(code).getDisplayLanguage(displayLocale))
            }
    val specialOptions =
        listOf(
            LanguageOption("zh-CN", Locale.forLanguageTag("zh-CN").getDisplayName(displayLocale)),
            LanguageOption("zh-TW", Locale.forLanguageTag("zh-TW").getDisplayName(displayLocale)),
        )
    val customOptions =
        selectedLanguages
            .filterNot { selected ->
                selected in specialOptions.map(LanguageOption::tag) || selected in baseOptions.map(LanguageOption::tag)
            }.map { LanguageOption(tag = it, label = it) }
    return (specialOptions + baseOptions + customOptions)
        .distinctBy(LanguageOption::tag)
        .sortedBy(LanguageOption::label)
        .filter { it.label.isNotBlank() && it.label.isNotEmpty() }
        .toImmutableList()
}

private fun excludedLanguageSummary(
    selectedLanguages: ImmutableList<String>,
    emptyPlaceholder: String,
): String {
    if (selectedLanguages.isEmpty()) {
        return emptyPlaceholder
    }
    val labels = translationLanguageOptions(selectedLanguages).associate { it.tag to it.label }
    return selectedLanguages.joinToString { tag -> labels[tag] ?: tag }
}

@Composable
private fun ExcludedLanguagesDialog(
    title: String,
    options: ImmutableList<LanguageOption>,
    selectedLanguages: ImmutableList<String>,
    onDismiss: () -> Unit,
    onConfirm: (ImmutableList<String>) -> Unit,
) {
    var selected by remember(selectedLanguages) { mutableStateOf(selectedLanguages.toSet()) }
    var query by remember { mutableStateOf("") }
    val filteredOptions =
        remember(options, query) {
            if (query.isBlank()) {
                options
            } else {
                options
                    .filter { option ->
                        option.label.contains(query, ignoreCase = true) ||
                            option.tag.contains(query, ignoreCase = true)
                    }.toImmutableList()
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(text = stringResource(id = R.string.settings_ai_config_search_language))
                    },
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    state = rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    items(
                        items = filteredOptions,
                        key = { it.tag },
                    ) { option ->
                        val index = filteredOptions.indexOf(option)
                        SegmentedListItem(
                            checked = option.tag in selected,
                            onCheckedChange = { checked ->
                                selected =
                                    if (checked) {
                                        selected + option.tag
                                    } else {
                                        selected - option.tag
                                    }
                            },
                            shapes = ListItemDefaults.segmentedShapes2(index, filteredOptions.size),
                            content = {
                                Text(text = option.label)
                            },
                            supportingContent = {
                                if (option.label != option.tag) {
                                    Text(text = option.tag)
                                }
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = option.tag in selected,
                                    onCheckedChange = { checked ->
                                        selected =
                                            if (checked) {
                                                selected + option.tag
                                            } else {
                                                selected - option.tag
                                            }
                                    },
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    selected =
                                        if (option.tag in selected) {
                                            selected - option.tag
                                        } else {
                                            selected + option.tag
                                        }
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        options
                            .map(LanguageOption::tag)
                            .filter { it in selected }
                            .toImmutableList(),
                    )
                },
            ) {
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
