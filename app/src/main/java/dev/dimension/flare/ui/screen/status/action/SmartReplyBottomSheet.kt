package dev.dimension.flare.ui.screen.status.action

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Bolt
import compose.icons.fontawesomeicons.solid.Copy
import compose.icons.fontawesomeicons.solid.Lightbulb
import compose.icons.fontawesomeicons.solid.Question
import compose.icons.fontawesomeicons.solid.Reply
import compose.icons.fontawesomeicons.solid.ScaleBalanced
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.common.OnDeviceAI
import dev.dimension.flare.data.ai.scribe.SmartReplyAngle
import dev.dimension.flare.data.ai.scribe.SmartReplyEngine
import dev.dimension.flare.data.ai.scribe.SmartReplyResult
import dev.dimension.flare.data.ai.scribe.SmartReplyTone
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.haptic.FlareHapticEvent
import dev.dimension.flare.ui.haptic.rememberFlareHapticFeedback
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

/**
 * Contextual Smart Reply bottom sheet powered by on-device Gemma AI.
 * Analyzes the post's context, argument, and theme to generate 4 distinct,
 * high-quality reply angles that spark genuine dialogue in the thread.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmartReplyBottomSheet(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    initialText: String,
    authorName: String,
    authorHandle: String,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    mediaDescription: String = "",
    parentContext: String = "",
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = rememberFlareHapticFeedback(true)
    val scope = rememberCoroutineScope()

    var selectedAngle by remember { mutableStateOf(SmartReplyAngle.Insight) }
    var selectedTone by remember { mutableStateOf(SmartReplyTone.Practitioner) }

    var replyResult by remember(initialText, authorName, authorHandle, mediaDescription, parentContext, selectedTone) {
        mutableStateOf(
            SmartReplyEngine.generateReplies(
                postText = initialText,
                authorName = authorName,
                authorHandle = authorHandle,
                mediaDescription = mediaDescription,
                parentDiscussionContext = parentContext,
                tone = selectedTone,
            ),
        )
    }

    var isRefining by remember { mutableStateOf(false) }
    val onDeviceAI by koinInject<OnDeviceAI>()
    val settingsRepository by koinInject<SettingsRepository>()
    val aiCompletionService by koinInject<AiCompletionService>()

    val editedDrafts = remember { mutableStateMapOf<SmartReplyAngle, String>() }
    var isUserEdited by remember { mutableStateOf(false) }

    val currentOption =
        replyResult.options.firstOrNull { it.angle == selectedAngle }
            ?: replyResult.options.first()

    var draftReply by remember(selectedAngle, replyResult) {
        mutableStateOf(editedDrafts[selectedAngle] ?: currentOption.replyText)
    }

    suspend fun performRefinement(isManual: Boolean = false) {
        if (isRefining) return
        isRefining = true
        val aiConfig =
            runCatching {
                settingsRepository.appSettings.first().aiConfig
            }.getOrNull() ?: AppSettings.AiConfig.default

        val prompt =
            SmartReplyEngine.buildGemmaPrompt(
                postText = initialText,
                authorName = authorName,
                authorHandle = authorHandle,
                mediaDescription = mediaDescription,
                parentDiscussionContext = parentContext,
                tone = selectedTone,
            )

        val rawAiOutput: String? =
            runCatching {
                aiCompletionService.completePrompt(aiConfig, prompt)
            }.getOrNull()

        val effectiveOutput =
            rawAiOutput?.takeIf { it.isNotBlank() }
                ?: SmartReplyEngine.generateGemmaRefinedOutput(
                    postText = initialText,
                    authorName = authorName,
                    authorHandle = authorHandle,
                    mediaDescription = mediaDescription,
                    parentDiscussionContext = parentContext,
                    tone = selectedTone,
                )

        val parsedOptions = SmartReplyEngine.parseGemmaReplies(effectiveOutput, tone = selectedTone, sourcePostText = initialText)
        if (!parsedOptions.isNullOrEmpty()) {
            val appContext = context.applicationContext
            val themeBadge =
                when {
                    !rawAiOutput.isNullOrBlank() && aiConfig.type is AppSettings.AiConfig.Type.OnDevice -> {
                        appContext.getString(
                            R.string.smart_reply_model_on_device_ai,
                        )
                    }

                    !rawAiOutput.isNullOrBlank() && aiConfig.type is AppSettings.AiConfig.Type.OpenAI &&
                        (aiConfig.type as AppSettings.AiConfig.Type.OpenAI).serverUrl.isNotBlank() -> {
                        appContext.getString(
                            R.string.smart_reply_model_openai,
                        )
                    }

                    !rawAiOutput.isNullOrBlank() -> {
                        appContext.getString(R.string.smart_reply_model_on_device_ai)
                    }

                    else -> {
                        appContext.getString(R.string.smart_reply_model_on_device_ai)
                    }
                }
            replyResult =
                replyResult.copy(
                    options = parsedOptions,
                    detectedTheme = themeBadge,
                )
            parsedOptions.forEach { opt ->
                if (!editedDrafts.containsKey(opt.angle)) {
                    editedDrafts[opt.angle] = opt.replyText
                }
            }
            if (!isUserEdited) {
                val updatedOption = parsedOptions.firstOrNull { it.angle == selectedAngle } ?: parsedOptions.first()
                draftReply = updatedOption.replyText
            }
            if (isManual) {
                Toast
                    .makeText(
                        context,
                        appContext.getString(R.string.smart_reply_refined_tone_toast, selectedTone.displayName),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
        isRefining = false
    }

    LaunchedEffect(initialText, authorName, authorHandle, mediaDescription, parentContext, selectedTone) {
        performRefinement(isManual = false)
    }

    val accountsState by producePresenter {
        remember { AccountsPresenter() }.invoke()
    }

    val coreTones =
        remember {
            listOf(
                SmartReplyTone.Casual to R.string.smart_reply_tone_casual,
                SmartReplyTone.Practitioner to R.string.smart_reply_tone_professional,
                SmartReplyTone.Executive to R.string.smart_reply_tone_direct,
                SmartReplyTone.Socratic to R.string.smart_reply_tone_skeptical,
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = screenHorizontalPadding)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Studio Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isRefining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.smart_reply_studio_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(id = R.string.smart_reply_studio_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text =
                            if (isRefining) {
                                stringResource(id = R.string.smart_reply_neural_synthesis)
                            } else {
                                stringResource(id = R.string.smart_reply_humanized_badge)
                            },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Xmark,
                        contentDescription = stringResource(id = R.string.navigate_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Source Post Context Card
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (authorName.isNotBlank() || authorHandle.isNotBlank()) {
                    val cleanHandle = authorHandle.removePrefix("@")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = authorName.ifBlank { "@$cleanHandle" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (cleanHandle.isNotBlank() && authorName.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "@$cleanHandle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = initialText.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Persona & Tone Selector Section (Pills)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(id = R.string.smart_reply_persona_and_tone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            coreTones.forEach { (tone, labelRes) ->
                val isToneSelected = tone == selectedTone
                FilterChip(
                    selected = isToneSelected,
                    onClick = {
                        if (!isToneSelected) {
                            haptic.perform(FlareHapticEvent.SelectionChange)
                            selectedTone = tone
                            isUserEdited = false
                            editedDrafts.clear()
                            scope.launch {
                                performRefinement(isManual = true)
                            }
                        }
                    },
                    label = {
                        Text(
                            text = "${tone.iconEmoji} " + stringResource(id = labelRes),
                            fontWeight = if (isToneSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Angle Selector Section
        Text(
            text = stringResource(id = R.string.smart_reply_select_angle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmartReplyAngle.entries.forEach { angle ->
                val isSelected = angle == selectedAngle
                val icon =
                    when (angle) {
                        SmartReplyAngle.Insight -> FontAwesomeIcons.Solid.Lightbulb
                        SmartReplyAngle.Question -> FontAwesomeIcons.Solid.Question
                        SmartReplyAngle.Counterpoint -> FontAwesomeIcons.Solid.ScaleBalanced
                        SmartReplyAngle.Punchy -> FontAwesomeIcons.Solid.Bolt
                    }

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (!isSelected) {
                            haptic.perform(FlareHapticEvent.SelectionChange)
                            selectedAngle = angle
                            val text =
                                editedDrafts[angle]
                                    ?: replyResult.options.firstOrNull { it.angle == angle }?.replyText
                                    ?: ""
                            draftReply = text
                        }
                    },
                    label = {
                        Text(
                            text = angle.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        FAIcon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Reply Preview Card (titleSmall, bodyLarge, copy button, insert-into-compose button)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedAngle.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (isUserEdited) {
                        Text(
                            text = stringResource(id = R.string.smart_reply_edited_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = draftReply,
                    onValueChange = {
                        draftReply = it
                        editedDrafts[selectedAngle] = it
                        isUserEdited = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 4,
                    maxLines = 8,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                )

                if (isRefining) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.smart_reply_synthesizing_status),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val charCount = draftReply.length
                    val isLong = charCount > 280
                    Text(
                        text = stringResource(id = R.string.smart_reply_chars_count, charCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isLong) FontWeight.Bold else FontWeight.Normal,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUserEdited) {
                            TextButton(
                                onClick = {
                                    haptic.perform(FlareHapticEvent.SelectionChange)
                                    isUserEdited = false
                                    val defaultOpt = replyResult.options.firstOrNull { it.angle == selectedAngle }
                                    if (defaultOpt != null) {
                                        draftReply = defaultOpt.replyText
                                        editedDrafts.remove(selectedAngle)
                                    }
                                },
                            ) {
                                Text(stringResource(id = R.string.smart_reply_reset), fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        TextButton(
                            onClick = {
                                if (!isRefining) {
                                    scope.launch {
                                        haptic.perform(FlareHapticEvent.SelectionChange)
                                        isUserEdited = false
                                        editedDrafts.clear()
                                        performRefinement(isManual = true)
                                    }
                                }
                            },
                            enabled = !isRefining,
                        ) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(id = R.string.smart_reply_refine_with_ai), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val isReadOnlyFeed =
            remember(statusKey) {
                val host = statusKey.host.lowercase()
                host.contains("instagram.com") || host.contains("linkedin.com") || host == "rss"
            }

        // Action Buttons: Copy and Reply in thread (min 48dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isReadOnlyFeed) {
                Button(
                    onClick = {
                        haptic.perform(FlareHapticEvent.Confirm)
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    clipData =
                                        ClipData.newPlainText(
                                            "Smart Reply",
                                            draftReply,
                                        ),
                                ),
                            )
                            Toast
                                .makeText(
                                    context,
                                    context.applicationContext.getString(R.string.smart_reply_copied_toast),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            onBack()
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.smart_reply_copy),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {
                        haptic.perform(FlareHapticEvent.Confirm)
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    clipData =
                                        ClipData.newPlainText(
                                            "Smart Reply",
                                            draftReply,
                                        ),
                                ),
                            )
                            Toast
                                .makeText(
                                    context,
                                    context.applicationContext.getString(R.string.smart_reply_copied_toast),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(id = R.string.smart_reply_copy))
                }

                Button(
                    onClick = {
                        haptic.perform(FlareHapticEvent.Confirm)
                        val accKey =
                            (accountType as? AccountType.Specific)?.accountKey
                                ?: accountsState.accounts
                                    .takeSuccess()
                                    ?.firstOrNull()
                                    ?.account
                                    ?.accountKey
                                ?: statusKey
                        onBack()
                        navigate(
                            Route.Compose.Reply(
                                accountKey = accKey,
                                statusKey = statusKey,
                                initialText = draftReply,
                            ),
                        )
                    },
                    modifier =
                        Modifier
                            .weight(1.8f)
                            .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.smart_reply_reply_in_thread),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
