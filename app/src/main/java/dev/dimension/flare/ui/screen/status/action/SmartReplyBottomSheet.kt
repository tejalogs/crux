package dev.dimension.flare.ui.screen.status.action

import android.content.ClipData
import android.widget.Toast
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
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
import dev.dimension.flare.data.ai.scribe.SmartReplyAngle
import dev.dimension.flare.data.ai.scribe.SmartReplyEngine
import dev.dimension.flare.data.ai.scribe.SmartReplyResult
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.haptic.FlareHapticEvent
import dev.dimension.flare.ui.haptic.rememberFlareHapticFeedback
import dev.dimension.flare.ui.route.Route
import kotlinx.coroutines.launch

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
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptic = rememberFlareHapticFeedback(true)
    val scope = rememberCoroutineScope()

    var selectedAngle by remember { mutableStateOf(SmartReplyAngle.Insight) }

    val replyResult by remember(initialText, authorName, authorHandle) {
        mutableStateOf(
            SmartReplyEngine.generateReplies(
                postText = initialText,
                authorName = authorName,
                authorHandle = authorHandle,
            ),
        )
    }

    val currentOption = replyResult.options.firstOrNull { it.angle == selectedAngle }
        ?: replyResult.options.first()

    var draftReply by remember(selectedAngle) {
        mutableStateOf(currentOption.replyText)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Drag Handle Bar
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Studio Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Reply with Gemma",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "On-Device Contextual Co-Pilot • Zero Egress",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Theme badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        text = replyResult.detectedTheme,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Source Post Context Card
            Surface(
                modifier = Modifier
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = authorName.ifBlank { "@$authorHandle" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (authorHandle.isNotBlank() && authorName.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "@$authorHandle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Spacer(modifier = Modifier.height(16.dp))

            // Angle Selector Section
            Text(
                text = "SELECT CONVERSATIONAL ANGLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmartReplyAngle.entries.forEach { angle ->
                    val isSelected = angle == selectedAngle
                    val icon = when (angle) {
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
                                val opt = replyResult.options.firstOrNull { it.angle == angle }
                                if (opt != null) {
                                    draftReply = opt.replyText
                                }
                            }
                        },
                        label = {
                            Text(
                                text = angle.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        leadingIcon = {
                            FAIcon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            // Subtitle of selected angle
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedAngle.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Editable Reply Field
            OutlinedTextField(
                value = draftReply,
                onValueChange = { draftReply = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 4,
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${draftReply.length} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Gemma distilled in ${replyResult.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Copy Button
                OutlinedButton(
                    onClick = {
                        haptic.perform(FlareHapticEvent.Confirm)
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    clipData = ClipData.newPlainText(
                                        "Smart Reply",
                                        draftReply,
                                    ),
                                ),
                            )
                            Toast.makeText(context, "Reply copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Copy,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Copy")
                }

                // Reply in Thread Button
                Button(
                    onClick = {
                        haptic.perform(FlareHapticEvent.Confirm)
                        onBack()
                        val accKey = (accountType as? AccountType.Specific)?.accountKey ?: statusKey
                        navigate(
                            Route.Compose.Reply(
                                accountKey = accKey,
                                statusKey = statusKey,
                                initialText = draftReply,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1.8f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
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
                        text = "Reply in Thread",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
