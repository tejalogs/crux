package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.Res
import dev.dimension.flare.cancel
import dev.dimension.flare.edit
import dev.dimension.flare.ok
import dev.dimension.flare.settings_ai_config_agent_description
import dev.dimension.flare.settings_ai_config_api_key
import dev.dimension.flare.settings_ai_config_api_key_hint
import dev.dimension.flare.settings_ai_config_description
import dev.dimension.flare.settings_ai_config_enable_agent
import dev.dimension.flare.settings_ai_config_enable_pre_translation
import dev.dimension.flare.settings_ai_config_enable_tldr
import dev.dimension.flare.settings_ai_config_extra_body
import dev.dimension.flare.settings_ai_config_model
import dev.dimension.flare.settings_ai_config_model_description
import dev.dimension.flare.settings_ai_config_model_loading
import dev.dimension.flare.settings_ai_config_model_manual_input
import dev.dimension.flare.settings_ai_config_model_select
import dev.dimension.flare.settings_ai_config_pre_translation_description
import dev.dimension.flare.settings_ai_config_reasoning_effort
import dev.dimension.flare.settings_ai_config_reasoning_effort_default
import dev.dimension.flare.settings_ai_config_reasoning_effort_description
import dev.dimension.flare.settings_ai_config_reasoning_effort_high
import dev.dimension.flare.settings_ai_config_reasoning_effort_low
import dev.dimension.flare.settings_ai_config_reasoning_effort_medium
import dev.dimension.flare.settings_ai_config_server
import dev.dimension.flare.settings_ai_config_server_hint
import dev.dimension.flare.settings_ai_config_server_url_requirement
import dev.dimension.flare.settings_ai_config_tldr_description
import dev.dimension.flare.settings_ai_config_tldr_prompt
import dev.dimension.flare.settings_ai_config_title
import dev.dimension.flare.settings_ai_config_translate_prompt
import dev.dimension.flare.settings_ai_config_translate_provider
import dev.dimension.flare.settings_ai_config_translate_provider_ai
import dev.dimension.flare.settings_ai_config_translate_provider_deepl
import dev.dimension.flare.settings_ai_config_translate_provider_deepl_api_key
import dev.dimension.flare.settings_ai_config_translate_provider_deepl_use_pro
import dev.dimension.flare.settings_ai_config_translate_provider_deepl_use_pro_description
import dev.dimension.flare.settings_ai_config_translate_provider_description
import dev.dimension.flare.settings_ai_config_translate_provider_google_cloud
import dev.dimension.flare.settings_ai_config_translate_provider_google_cloud_api_key
import dev.dimension.flare.settings_ai_config_translate_provider_google_web
import dev.dimension.flare.settings_ai_config_translate_provider_libretranslate
import dev.dimension.flare.settings_ai_config_translate_provider_libretranslate_api_key
import dev.dimension.flare.settings_ai_config_translate_provider_libretranslate_base_url
import dev.dimension.flare.settings_ai_config_type
import dev.dimension.flare.settings_ai_config_type_description
import dev.dimension.flare.settings_ai_config_type_on_device
import dev.dimension.flare.settings_ai_config_type_openai
import dev.dimension.flare.settings_ai_config_value_empty_placeholder
import dev.dimension.flare.settings_services_title
import dev.dimension.flare.settings_translation_ai_test_action
import dev.dimension.flare.settings_translation_ai_test_description
import dev.dimension.flare.settings_translation_ai_test_original
import dev.dimension.flare.settings_translation_ai_test_result
import dev.dimension.flare.settings_translation_ai_test_title
import dev.dimension.flare.settings_translation_auto_excluded_languages
import dev.dimension.flare.settings_translation_description
import dev.dimension.flare.settings_translation_prefer_platform
import dev.dimension.flare.settings_translation_prefer_platform_description
import dev.dimension.flare.settings_translation_show_original_with_translation
import dev.dimension.flare.settings_translation_show_original_with_translation_description
import dev.dimension.flare.settings_translation_title
import dev.dimension.flare.ui.component.FlareScrollBar
import dev.dimension.flare.ui.component.Header
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AiConfigPresenter
import dev.dimension.flare.ui.presenter.settings.AiReasoningEffortOption
import dev.dimension.flare.ui.presenter.settings.AiTranslationTestPresenter
import dev.dimension.flare.ui.presenter.settings.AiTypeOption
import dev.dimension.flare.ui.presenter.settings.TranslateProviderOption
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AutoSuggestBoxDefaults
import io.github.composefluent.component.AutoSuggestionBox
import io.github.composefluent.component.Button
import io.github.composefluent.component.CheckBox
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.DropDownButton
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.FlyoutPlacement
import io.github.composefluent.component.ListItem
import io.github.composefluent.component.MenuFlyout
import io.github.composefluent.component.MenuFlyoutItem
import io.github.composefluent.component.Switcher
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

internal interface DesktopAiConfigState : AiConfigPresenter.State {
    val aiTranslationTestState: AiTranslationTestPresenter.State
    val expanded: Boolean
    val translationExpanded: Boolean
    val showTypeDropdown: Boolean
    val showModelDropdown: Boolean
    val showReasoningEffortDropdown: Boolean
    val showProviderDropdown: Boolean
    val showExcludedLanguagesDialog: Boolean
    val textEditDialog: TextEditDialogState?
    fun setExpanded(value: Boolean)
    fun setTranslationExpanded(value: Boolean)
    fun setShowTypeDropdown(value: Boolean)
    fun setShowModelDropdown(value: Boolean)
    fun setShowReasoningEffortDropdown(value: Boolean)
    fun setShowProviderDropdown(value: Boolean)
    fun setExcludedLanguagesDialog(value: Boolean)
    fun setTextEditDialog(value: TextEditDialogState?)
}

@Composable
internal fun aiConfigPresenter(): DesktopAiConfigState = run {
    var expanded by remember { mutableStateOf(false) }
    var translationExpanded by remember { mutableStateOf(false) }
    val state = remember { AiConfigPresenter() }.invoke()
    val aiTranslationTestState = remember { AiTranslationTestPresenter() }.invoke()
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showReasoningEffortDropdown by remember { mutableStateOf(false) }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var showExcludedLanguagesDialog by remember { mutableStateOf(false) }
    var textEditDialog by remember { mutableStateOf<TextEditDialogState?>(null) }
    object : DesktopAiConfigState, AiConfigPresenter.State by state {
        override val aiTranslationTestState = aiTranslationTestState
        override val expanded = expanded
        override val translationExpanded = translationExpanded
        override val showTypeDropdown = showTypeDropdown
        override val showModelDropdown = showModelDropdown
        override val showReasoningEffortDropdown = showReasoningEffortDropdown
        override val showProviderDropdown = showProviderDropdown
        override val showExcludedLanguagesDialog = showExcludedLanguagesDialog
        override val textEditDialog = textEditDialog

        override fun setExpanded(value: Boolean) {
            expanded = value
        }

        override fun setTranslationExpanded(value: Boolean) {
            translationExpanded = value
        }

        override fun setShowTypeDropdown(value: Boolean) {
            showTypeDropdown = value
        }

        override fun setShowModelDropdown(value: Boolean) {
            showModelDropdown = value
        }

        override fun setShowReasoningEffortDropdown(value: Boolean) {
            showReasoningEffortDropdown = value
        }

        override fun setShowProviderDropdown(value: Boolean) {
            showProviderDropdown = value
        }

        override fun setExcludedLanguagesDialog(value: Boolean) {
            showExcludedLanguagesDialog = value
        }

        override fun setTextEditDialog(value: TextEditDialogState?) {
            textEditDialog = value
        }
    }
}

internal data class TextEditDialogState(
    val title: String,
    val placeholder: String,
    val value: String,
    val suggestions: ImmutableList<String> = persistentListOf(),
    val hint: String = "",
    val onConfirm: (String) -> Unit,
)

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
            }.filter { it.label.isNotBlank() }
    val specialOptions =
        listOf(
            LanguageOption("zh-CN", Locale.forLanguageTag("zh-CN").getDisplayName(displayLocale)),
            LanguageOption("zh-TW", Locale.forLanguageTag("zh-TW").getDisplayName(displayLocale)),
        )
    val knownTags = (specialOptions + baseOptions).map(LanguageOption::tag).toSet()
    val customOptions = selectedLanguages.filterNot(knownTags::contains).map { LanguageOption(tag = it, label = it) }
    return (specialOptions + baseOptions + customOptions)
        .distinctBy(LanguageOption::tag)
        .sortedBy(LanguageOption::label)
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
    selectedLanguages: ImmutableList<String>,
    onDismiss: () -> Unit,
    onConfirm: (ImmutableList<String>) -> Unit,
) {
    val options = remember(selectedLanguages) { translationLanguageOptions(selectedLanguages) }
    var selectedTags by remember(selectedLanguages) { mutableStateOf(selectedLanguages.toSet()) }
    ContentDialog(
        title = title,
        visible = true,
        content = {
            val scrollState = rememberLazyListState()
            FlareScrollBar(state = scrollState) {
                LazyColumn(
                    state = scrollState,
                    modifier =
                        Modifier
                            .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                ) {
                    items(options, key = { it.tag }) { option ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTags =
                                            if (option.tag in selectedTags) {
                                                selectedTags - option.tag
                                            } else {
                                                selectedTags + option.tag
                                            }
                                    }.padding(vertical = 6.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(option.label)
                                if (option.label != option.tag) {
                                    Text(
                                        option.tag,
                                        color = FluentTheme.colors.text.text.secondary,
                                        style = FluentTheme.typography.caption,
                                    )
                                }
                            }
                            CheckBox(
                                checked = option.tag in selectedTags,
                                onCheckStateChange = { checked ->
                                    selectedTags =
                                        if (checked) {
                                            selectedTags + option.tag
                                        } else {
                                            selectedTags - option.tag
                                        }
                                },
                            )
                        }
                    }
                }
            }
        },
        primaryButtonText = stringResource(Res.string.ok),
        closeButtonText = stringResource(Res.string.cancel),
        onButtonClick = {
            when (it) {
                ContentDialogButton.Primary -> {
                    onConfirm(options.map(LanguageOption::tag).filter { tag -> tag in selectedTags }.toImmutableList())
                }

                else -> {
                    onDismiss()
                }
            }
        },
    )
}

@Composable
private fun translateProviderOptionLabel(provider: TranslateProviderOption): String =
    when (provider) {
        TranslateProviderOption.AI -> stringResource(Res.string.settings_ai_config_translate_provider_ai)
        TranslateProviderOption.GoogleWeb -> stringResource(Res.string.settings_ai_config_translate_provider_google_web)
        TranslateProviderOption.DeepL -> stringResource(Res.string.settings_ai_config_translate_provider_deepl)
        TranslateProviderOption.GoogleCloud -> stringResource(Res.string.settings_ai_config_translate_provider_google_cloud)
        TranslateProviderOption.LibreTranslate -> stringResource(Res.string.settings_ai_config_translate_provider_libretranslate)
    }

@Composable
private fun openAIReasoningEffortLabel(option: AiReasoningEffortOption): String =
    when (option) {
        AiReasoningEffortOption.Default -> stringResource(Res.string.settings_ai_config_reasoning_effort_default)
        AiReasoningEffortOption.Low -> stringResource(Res.string.settings_ai_config_reasoning_effort_low)
        AiReasoningEffortOption.Medium -> stringResource(Res.string.settings_ai_config_reasoning_effort_medium)
        AiReasoningEffortOption.High -> stringResource(Res.string.settings_ai_config_reasoning_effort_high)
    }

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsServicesSection(
    aiConfigState: DesktopAiConfigState,
) {
    Header(stringResource(Res.string.settings_services_title))

    aiConfigState.textEditDialog?.let { dialog ->
        val textState = rememberTextFieldState()
        var isSuggestionExpanded by remember(dialog) { mutableStateOf(false) }
        LaunchedEffect(dialog) {
            textState.edit {
                replace(0, length, dialog.value)
            }
        }
        val filteredSuggestions by remember(dialog) {
            snapshotFlow { textState.text.toString() }
                .map { query ->
                    dialog.suggestions.filter { item ->
                        query.isBlank() || item.contains(query, ignoreCase = true)
                    }
                }
        }.collectAsState(dialog.suggestions)
        ContentDialog(
            title = dialog.title,
            visible = true,
            content = {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    if (dialog.placeholder.isNotBlank()) {
                        Text(
                            text = dialog.placeholder,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    if (dialog.suggestions.isEmpty()) {
                        TextField(
                            state = textState,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                        )
                    } else {
                        AutoSuggestionBox(
                            expanded = isSuggestionExpanded,
                            onExpandedChange = { isSuggestionExpanded = it },
                        ) {
                            TextField(
                                state = textState,
                                shape = AutoSuggestBoxDefaults.textFieldShape(isSuggestionExpanded),
                                modifier = Modifier.fillMaxWidth().flyoutAnchor().heightIn(max = 480.dp),
                                lineLimits = TextFieldLineLimits.SingleLine,
                            )
                            AutoSuggestBoxDefaults.suggestFlyout(
                                expanded = isSuggestionExpanded && filteredSuggestions.isNotEmpty(),
                                onDismissRequest = { isSuggestionExpanded = false },
                                itemsContent = {
                                    items(filteredSuggestions) { item ->
                                        ListItem(
                                            onClick = {
                                                textState.edit {
                                                    replace(0, length, item)
                                                }
                                                isSuggestionExpanded = false
                                            },
                                            text = { Text(item, maxLines = 1) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                },
                                modifier = Modifier.flyoutSize(matchAnchorWidth = true),
                            )
                        }
                    }
                    if (dialog.hint.isNotBlank()) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            dialog.hint,
                            style = FluentTheme.typography.caption,
                            color = FluentTheme.colors.text.text.secondary,
                        )
                    }
                }
            },
            primaryButtonText = stringResource(Res.string.ok),
            closeButtonText = stringResource(Res.string.cancel),
            onButtonClick = {
                when (it) {
                    ContentDialogButton.Primary -> {
                        dialog.onConfirm(textState.text.toString())
                        aiConfigState.setTextEditDialog(null)
                    }

                    else -> {
                        aiConfigState.setTextEditDialog(null)
                    }
                }
            },
        )
    }

    if (aiConfigState.showExcludedLanguagesDialog) {
        ExcludedLanguagesDialog(
            title = stringResource(Res.string.settings_translation_auto_excluded_languages),
            selectedLanguages = aiConfigState.autoTranslateExcludedLanguages,
            onDismiss = {
                aiConfigState.setExcludedLanguagesDialog(false)
            },
            onConfirm = { selectedLanguages ->
                aiConfigState.setAutoTranslateExcludedLanguages(selectedLanguages)
                aiConfigState.setExcludedLanguagesDialog(false)
            },
        )
    }

    Expander(
        aiConfigState.expanded,
        onExpandedChanged = aiConfigState::setExpanded,
        heading = {
            Text(stringResource(Res.string.settings_ai_config_title))
        },
        caption = {
            Text(stringResource(Res.string.settings_ai_config_description))
        },
        icon = null,
    ) {
        val serverTitle = stringResource(Res.string.settings_ai_config_server)
        val serverHint = stringResource(Res.string.settings_ai_config_server_hint)
        val serverRequirementHint = stringResource(Res.string.settings_ai_config_server_url_requirement)
        val apiKeyTitle = stringResource(Res.string.settings_ai_config_api_key)
        val apiKeyHint = stringResource(Res.string.settings_ai_config_api_key_hint)
        val extraBodyTitle = stringResource(Res.string.settings_ai_config_extra_body)
        val modelTitle = stringResource(Res.string.settings_ai_config_model)
        val modelPlaceholder = stringResource(Res.string.settings_ai_config_model_select)
        val tldrPromptTitle = stringResource(Res.string.settings_ai_config_tldr_prompt)
        val shouldShowAiAgentSwitch =
            aiConfigState.aiType == AiTypeOption.OpenAI &&
                aiConfigState.openAIModel.isNotBlank()
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_ai_config_type))
            },
            caption = {
                Text(stringResource(Res.string.settings_ai_config_type_description))
            },
            trailing = {
                DropDownButton(
                    onClick = {
                        aiConfigState.setShowTypeDropdown(!aiConfigState.showTypeDropdown)
                    },
                ) {
                    Text(
                        stringResource(
                            when (aiConfigState.aiType) {
                                AiTypeOption.OnDevice -> Res.string.settings_ai_config_type_on_device
                                AiTypeOption.OpenAI -> Res.string.settings_ai_config_type_openai
                            },
                        ),
                    )
                }
                MenuFlyout(
                    visible = aiConfigState.showTypeDropdown,
                    onDismissRequest = { aiConfigState.setShowTypeDropdown(false) },
                    placement = FlyoutPlacement.BottomAlignedEnd,
                    modifier = Modifier.heightIn(max = 200.dp),
                ) {
                    aiConfigState.supportedTypes.forEach { type ->
                        MenuFlyoutItem(
                            text = {
                                Text(
                                    stringResource(
                                        when (type) {
                                            AiTypeOption.OnDevice -> Res.string.settings_ai_config_type_on_device
                                            AiTypeOption.OpenAI -> Res.string.settings_ai_config_type_openai
                                        },
                                    ),
                                )
                            },
                            onClick = {
                                aiConfigState.selectType(type)
                                aiConfigState.setShowTypeDropdown(false)
                            },
                        )
                    }
                }
            },
        )
        ExpanderItemSeparator()
        AnimatedVisibility(aiConfigState.aiType == AiTypeOption.OpenAI) {
            Column {
                ExpanderItem(
                    heading = { Text(stringResource(Res.string.settings_ai_config_server)) },
                    caption = {
                        Text(
                            aiConfigState.openAIServerUrl.ifBlank {
                                stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                            },
                        )
                    },
                    trailing = {
                        Button(
                            onClick = {
                                aiConfigState.setTextEditDialog(
                                    TextEditDialogState(
                                        title = serverTitle,
                                        placeholder = serverHint,
                                        value = aiConfigState.openAIServerUrl,
                                        suggestions = aiConfigState.serverSuggestions,
                                        hint = serverRequirementHint,
                                        onConfirm = { newValue ->
                                            aiConfigState.setOpenAIServerUrl(newValue)
                                        },
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(Res.string.edit))
                        }
                    },
                )
                ExpanderItemSeparator()
                ExpanderItem(
                    heading = { Text(stringResource(Res.string.settings_ai_config_api_key)) },
                    caption = {
                        Text(
                            aiConfigState.openAIApiKey.ifBlank {
                                stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                            },
                        )
                    },
                    trailing = {
                        Button(
                            onClick = {
                                aiConfigState.setTextEditDialog(
                                    TextEditDialogState(
                                        title = apiKeyTitle,
                                        placeholder = apiKeyHint,
                                        value = aiConfigState.openAIApiKey,
                                        onConfirm = { newValue ->
                                            aiConfigState.setOpenAIApiKey(newValue)
                                        },
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(Res.string.edit))
                        }
                    },
                )
                ExpanderItemSeparator()
                val shouldShowManualModelInput =
                    when (val openAIModels = aiConfigState.openAIModels) {
                        is UiState.Error -> true
                        is UiState.Success -> openAIModels.data.isEmpty()
                        is UiState.Loading -> false
                    }
                if (shouldShowManualModelInput) {
                    ExpanderItem(
                        heading = { Text(stringResource(Res.string.settings_ai_config_model_manual_input)) },
                        caption = {
                            Text(
                                aiConfigState.openAIModel.ifBlank {
                                    stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                },
                            )
                        },
                        trailing = {
                            Button(
                                onClick = {
                                aiConfigState.setTextEditDialog(
                                    TextEditDialogState(
                                        title = modelTitle,
                                        placeholder = modelPlaceholder,
                                        value = aiConfigState.openAIModel,
                                        onConfirm = aiConfigState::setOpenAIModel,
                                    ),
                                )
                            },
                        ) {
                            Text(stringResource(Res.string.edit))
                        }
                    },
                )
                ExpanderItemSeparator()
            } else {
                ExpanderItem(
                    heading = { Text(stringResource(Res.string.settings_ai_config_model)) },
                    caption = { Text(stringResource(Res.string.settings_ai_config_model_description)) },
                    trailing = {
                        DropDownButton(
                            onClick = {
                                aiConfigState.setShowModelDropdown(!aiConfigState.showModelDropdown)
                            },
                        ) {
                            Text(
                                aiConfigState.openAIModel.ifBlank {
                                    stringResource(Res.string.settings_ai_config_model_select)
                                },
                            )
                        }
                        MenuFlyout(
                            visible = aiConfigState.showModelDropdown,
                            onDismissRequest = { aiConfigState.setShowModelDropdown(false) },
                            placement = FlyoutPlacement.BottomAlignedEnd,
                            modifier = Modifier.heightIn(max = 200.dp),
                        ) {
                            aiConfigState.openAIModels
                                .onLoading {
                                    MenuFlyoutItem(
                                        text = { Text(stringResource(Res.string.settings_ai_config_model_loading)) },
                                        onClick = {},
                                    )
                                }.onSuccess { models ->
                                    models.forEach { model ->
                                        MenuFlyoutItem(
                                            text = { Text(model) },
                                            onClick = {
                                                aiConfigState.setOpenAIModel(model)
                                                aiConfigState.setShowModelDropdown(false)
                                            },
                                        )
                                    }
                                }
                        }
                    },
                )
                ExpanderItemSeparator()
            }
            ExpanderItem(
                heading = { Text(stringResource(Res.string.settings_ai_config_reasoning_effort)) },
                caption = {
                    Text(stringResource(Res.string.settings_ai_config_reasoning_effort_description))
                },
                trailing = {
                    DropDownButton(
                        onClick = {
                            aiConfigState.setShowReasoningEffortDropdown(
                                !aiConfigState.showReasoningEffortDropdown,
                            )
                        },
                    ) {
                        Text(openAIReasoningEffortLabel(aiConfigState.openAIReasoningEffort))
                    }
                    MenuFlyout(
                        visible = aiConfigState.showReasoningEffortDropdown,
                        onDismissRequest = { aiConfigState.setShowReasoningEffortDropdown(false) },
                        placement = FlyoutPlacement.BottomAlignedEnd,
                    ) {
                        aiConfigState.supportedOpenAIReasoningEfforts.forEach { effort ->
                            MenuFlyoutItem(
                                text = { Text(openAIReasoningEffortLabel(effort)) },
                                onClick = {
                                    aiConfigState.setOpenAIReasoningEffort(effort)
                                    aiConfigState.setShowReasoningEffortDropdown(false)
                                },
                            )
                        }
                    }
                },
            )
            ExpanderItemSeparator()
            ExpanderItem(
                heading = { Text(extraBodyTitle) },
                caption = {
                    if (aiConfigState.openAIExtraBody.isNotBlank()) {
                        Text(aiConfigState.openAIExtraBody)
                    }
                },
                trailing = {
                    Button(
                        onClick = {
                            aiConfigState.setTextEditDialog(
                                TextEditDialogState(
                                    title = extraBodyTitle,
                                    placeholder = "",
                                    value = aiConfigState.openAIExtraBody,
                                    hint = "{\"thinking\": {\"type\": \"enabled\"}}",
                                    onConfirm = aiConfigState::setOpenAIExtraBody,
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(Res.string.edit))
                    }
                },
            )
            ExpanderItemSeparator()
        }
    }
    if (shouldShowAiAgentSwitch) {
        ExpanderItem(
            heading = {
                Text(stringResource(Res.string.settings_ai_config_enable_agent))
            },
            caption = {
                Text(stringResource(Res.string.settings_ai_config_agent_description))
            },
            trailing = {
                Switcher(
                    checked = aiConfigState.aiAgent,
                    {
                        aiConfigState.setAIAgent(it)
                    },
                    textBefore = true,
                )
            },
        )
        ExpanderItemSeparator()
    }
    ExpanderItem(
        heading = {
            Text(stringResource(Res.string.settings_ai_config_enable_tldr))
        },
        caption = {
            Text(stringResource(Res.string.settings_ai_config_tldr_description))
        },
        trailing = {
            Switcher(
                checked = aiConfigState.aiTldr,
                {
                    aiConfigState.setAITldr(it)
                },
                textBefore = true,
            )
        },
    )
    AnimatedVisibility(aiConfigState.aiTldr) {
        Column {
            ExpanderItemSeparator()
            ExpanderItem(
                heading = { Text(stringResource(Res.string.settings_ai_config_tldr_prompt)) },
                caption = {
                    Text(
                        aiConfigState.tldrPrompt.ifBlank {
                            stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                        },
                    )
                },
                trailing = {
                    Button(
                        onClick = {
                            aiConfigState.setTextEditDialog(
                                TextEditDialogState(
                                    title = tldrPromptTitle,
                                    placeholder = "",
                                    value = aiConfigState.tldrPrompt,
                                    onConfirm = { newValue ->
                                        aiConfigState.setTldrPrompt(newValue)
                                    },
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(Res.string.edit))
                    }
                },
            )
        }
    }
}
Expander(
    aiConfigState.translationExpanded,
    onExpandedChanged = aiConfigState::setTranslationExpanded,
    heading = {
        Text(stringResource(Res.string.settings_translation_title))
    },
    caption = {
        Text(stringResource(Res.string.settings_translation_description))
    },
    icon = null,
) {
    val translatePromptTitle = stringResource(Res.string.settings_ai_config_translate_prompt)
    val deepLApiKeyTitle = stringResource(Res.string.settings_ai_config_translate_provider_deepl_api_key)
    val googleCloudApiKeyTitle = stringResource(Res.string.settings_ai_config_translate_provider_google_cloud_api_key)
    val libreTranslateBaseUrlTitle = stringResource(Res.string.settings_ai_config_translate_provider_libretranslate_base_url)
    val libreTranslateApiKeyTitle = stringResource(Res.string.settings_ai_config_translate_provider_libretranslate_api_key)
    val hasProviderSettings = aiConfigState.translateProvider != TranslateProviderOption.GoogleWeb
    ExpanderItem(
        heading = {
            Text(stringResource(Res.string.settings_ai_config_translate_provider))
        },
        caption = {
            Text(stringResource(Res.string.settings_ai_config_translate_provider_description))
        },
        trailing = {
            DropDownButton(
                onClick = {
                    aiConfigState.setShowProviderDropdown(!aiConfigState.showProviderDropdown)
                },
            ) {
                Text(
                    translateProviderOptionLabel(aiConfigState.translateProvider),
                )
            }
            MenuFlyout(
                visible = aiConfigState.showProviderDropdown,
                onDismissRequest = { aiConfigState.setShowProviderDropdown(false) },
                placement = FlyoutPlacement.BottomAlignedEnd,
                modifier = Modifier.heightIn(max = 200.dp),
            ) {
                aiConfigState.supportedTranslateProviders.forEach { provider ->
                    MenuFlyoutItem(
                        text = {
                            Text(translateProviderOptionLabel(provider))
                        },
                        onClick = {
                            aiConfigState.selectTranslateProvider(provider)
                            aiConfigState.setShowProviderDropdown(false)
                        },
                    )
                }
            }
        },
    )
    ExpanderItemSeparator()
    ExpanderItem(
        heading = {
            Text(stringResource(Res.string.settings_ai_config_enable_pre_translation))
        },
        caption = {
            Text(stringResource(Res.string.settings_ai_config_pre_translation_description))
        },
        trailing = {
            Switcher(
                checked = aiConfigState.preTranslate,
                {
                    aiConfigState.setPreTranslate(it)
                },
                textBefore = true,
            )
        },
    )
    ExpanderItemSeparator()
    ExpanderItem(
        heading = {
            Text(stringResource(Res.string.settings_translation_show_original_with_translation))
        },
        caption = {
            Text(stringResource(Res.string.settings_translation_show_original_with_translation_description))
        },
        trailing = {
            Switcher(
                checked = aiConfigState.showOriginalWithTranslation,
                aiConfigState::setShowOriginalWithTranslation,
                textBefore = true,
            )
        },
    )
    AnimatedVisibility(aiConfigState.preTranslate) {
        Column {
            ExpanderItemSeparator()
            ExpanderItem(
                heading = {
                    Text(stringResource(Res.string.settings_translation_prefer_platform))
                },
                caption = {
                    Text(stringResource(Res.string.settings_translation_prefer_platform_description))
                },
                trailing = {
                    Switcher(
                        checked = aiConfigState.preferPlatformTranslation,
                        aiConfigState::setPreferPlatformTranslation,
                        textBefore = true,
                    )
                },
            )
            ExpanderItemSeparator()
            ExpanderItem(
                heading = {
                    Text(stringResource(Res.string.settings_translation_auto_excluded_languages))
                },
                caption = {
                    Text(
                        excludedLanguageSummary(
                            selectedLanguages = aiConfigState.autoTranslateExcludedLanguages,
                            emptyPlaceholder = stringResource(Res.string.settings_ai_config_value_empty_placeholder),
                        ),
                    )
                },
                trailing = {
                    Button(
                        onClick = {
                            aiConfigState.setExcludedLanguagesDialog(true)
                        },
                    ) {
                        Text(stringResource(Res.string.edit))
                    }
                },
            )
        }
    }
    AnimatedVisibility(hasProviderSettings) {
        Column {
            ExpanderItemSeparator()
            when (aiConfigState.translateProvider) {
                TranslateProviderOption.AI -> {
                    ExpanderItem(
                        heading = { Text(stringResource(Res.string.settings_ai_config_translate_prompt)) },
                        caption = {
                            Text(
                                aiConfigState.translatePrompt.ifBlank {
                                    stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                },
                            )
                        },
                        trailing = {
                            Button(
                                onClick = {
                                    aiConfigState.setTextEditDialog(
                                        TextEditDialogState(
                                            title = translatePromptTitle,
                                            placeholder = "",
                                            value = aiConfigState.translatePrompt,
                                            onConfirm = { newValue ->
                                                aiConfigState.setTranslatePrompt(newValue)
                                            },
                                        ),
                                    )
                                },
                            ) {
                                Text(stringResource(Res.string.edit))
                            }
                        },
                    )
                    ExpanderItemSeparator()
                    ExpanderItem(
                        heading = { Text(stringResource(Res.string.settings_translation_ai_test_title)) },
                        caption = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(Res.string.settings_translation_ai_test_description))
                                Text(stringResource(Res.string.settings_translation_ai_test_original))
                                RichText(aiConfigState.aiTranslationTestState.sampleText)
                                Button(
                                    onClick = {
                                        aiConfigState.aiTranslationTestState.runTest()
                                    },
                                ) {
                                    Text(stringResource(Res.string.settings_translation_ai_test_action))
                                }
                                if (aiConfigState.aiTranslationTestState.isLoading) {
                                    Text(stringResource(Res.string.settings_ai_config_model_loading))
                                }
                                aiConfigState.aiTranslationTestState.errorMessage?.let { message ->
                                    Text(
                                        message,
                                        color = FluentTheme.colors.system.critical,
                                    )
                                }
                                aiConfigState.aiTranslationTestState.translatedText?.let { translated ->
                                    Text(stringResource(Res.string.settings_translation_ai_test_result))
                                    RichText(translated)
                                }
                            }
                        },
                    )
                }

                TranslateProviderOption.DeepL -> {
                    ExpanderItem(
                        heading = { Text(deepLApiKeyTitle) },
                        caption = {
                            Text(
                                aiConfigState.deepLApiKey.ifBlank {
                                    stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                },
                            )
                        },
                        trailing = {
                            Button(
                                onClick = {
                                    aiConfigState.setTextEditDialog(
                                        TextEditDialogState(
                                            title = deepLApiKeyTitle,
                                            placeholder = "",
                                            value = aiConfigState.deepLApiKey,
                                            onConfirm = { newValue ->
                                                aiConfigState.setDeepLApiKey(newValue)
                                            },
                                        ),
                                    )
                                },
                            ) {
                                Text(stringResource(Res.string.edit))
                            }
                        },
                    )
                    ExpanderItemSeparator()
                    ExpanderItem(
                        heading = { Text(stringResource(Res.string.settings_ai_config_translate_provider_deepl_use_pro)) },
                        caption = {
                            Text(stringResource(Res.string.settings_ai_config_translate_provider_deepl_use_pro_description))
                        },
                        trailing = {
                            Switcher(
                                checked = aiConfigState.deepLUsePro,
                                {
                                    aiConfigState.setDeepLUsePro(it)
                                },
                                textBefore = true,
                            )
                        },
                    )
                }

                TranslateProviderOption.GoogleCloud -> {
                    ExpanderItem(
                        heading = { Text(googleCloudApiKeyTitle) },
                        caption = {
                            Text(
                                aiConfigState.googleCloudApiKey.ifBlank {
                                    stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                },
                            )
                        },
                        trailing = {
                            Button(
                                onClick = {
                                    aiConfigState.setTextEditDialog(
                                        TextEditDialogState(
                                            title = googleCloudApiKeyTitle,
                                            placeholder = "",
                                            value = aiConfigState.googleCloudApiKey,
                                            onConfirm = { newValue ->
                                                aiConfigState.setGoogleCloudApiKey(newValue)
                                            },
                                        ),
                                    )
                                },
                            ) {
                                Text(stringResource(Res.string.edit))
                            }
                        },
                    )
                }

                TranslateProviderOption.LibreTranslate -> {
                    ExpanderItem(
                        heading = { Text(libreTranslateBaseUrlTitle) },
                        caption = {
                            Text(
                                aiConfigState.libreTranslateBaseUrl.ifBlank {
                                    stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                },
                            )
                        },
                        trailing = {
                            Button(
                                onClick = {
                                    aiConfigState.setTextEditDialog(
                                        TextEditDialogState(
                                            title = libreTranslateBaseUrlTitle,
                                            placeholder = "https://libretranslate.example.com",
                                            value = aiConfigState.libreTranslateBaseUrl,
                                            onConfirm = { newValue ->
                                                aiConfigState.setLibreTranslateBaseUrl(newValue)
                                            },
                                        ),
                                    )
                                },
                            ) {
                                Text(stringResource(Res.string.edit))
                            }
                        },
                    )
                    ExpanderItemSeparator()
                    ExpanderItem(
                        heading = { Text(libreTranslateApiKeyTitle) },
                        caption = {
                            Text(
                                aiConfigState.libreTranslateApiKey.ifBlank {
                                    stringResource(Res.string.settings_ai_config_value_empty_placeholder)
                                },
                            )
                        },
                        trailing = {
                            Button(
                                onClick = {
                                    aiConfigState.setTextEditDialog(
                                        TextEditDialogState(
                                            title = libreTranslateApiKeyTitle,
                                            placeholder = "",
                                            value = aiConfigState.libreTranslateApiKey,
                                            onConfirm = { newValue ->
                                                aiConfigState.setLibreTranslateApiKey(newValue)
                                            },
                                        ),
                                    )
                                },
                            ) {
                                Text(stringResource(Res.string.edit))
                            }
                        },
                    )
                }

                TranslateProviderOption.GoogleWeb -> {}
            }
        }
    }
}
}
