package dev.dimension.flare.ui.screen.status.action

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.ArrowRight
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.FileExport
import compose.icons.fontawesomeicons.solid.PaperPlane
import compose.icons.fontawesomeicons.solid.PenToSquare
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.data.ai.scribe.BroadcastTargetAccount
import dev.dimension.flare.data.ai.scribe.CarouselDeck
import dev.dimension.flare.data.ai.scribe.CarouselDeckGenerator
import dev.dimension.flare.data.ai.scribe.CarouselSlide
import dev.dimension.flare.data.ai.scribe.CarouselTemplate
import dev.dimension.flare.data.ai.scribe.OmniBroadcastService
import dev.dimension.flare.data.ai.scribe.OmniScribeEngine
import dev.dimension.flare.data.ai.scribe.OmniScribeFormat
import dev.dimension.flare.data.ai.scribe.OmniScribeInput
import dev.dimension.flare.data.ai.scribe.OmniScribeStyle
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.haptic.FlareHapticEvent
import dev.dimension.flare.ui.haptic.rememberFlareHapticFeedback
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.compose.ComposePresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import kotlin.math.absoluteValue

/**
 * Omni-Scribe Studio bottom sheet.
 * Deeply analyzes the narrative arc of any feed post and repackages it into a standalone,
 * original post with a 4:5 Carousel Poster Deck—ready to publish across connected networks
 * or share externally with zero source-author tagging.
 */
@Composable
internal fun OmniScribeBottomSheet(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    initialText: String,
    authorName: String,
    authorHandle: String,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    articleTitle: String = "",
    sourceUrl: String = "",
) {
    val context = LocalContext.current
    val haptic = rememberFlareHapticFeedback(true)
    val scope = rememberCoroutineScope()

    var selectedStyle by remember { mutableStateOf(OmniScribeStyle.Punchy) }
    var selectedFormat by remember { mutableStateOf(OmniScribeFormat.Story) }
    var attachCarouselToPost by remember { mutableStateOf(true) }
    var isPublishing by remember { mutableStateOf(false) }
    var publishDone by remember { mutableStateOf(false) }
    var isOpeningComposer by remember { mutableStateOf(false) }
    var isSavingGallery by remember { mutableStateOf(false) }
    var gallerySavedToast by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }

    var currentResult by remember {
        mutableStateOf(
            OmniScribeEngine.repackage(
                OmniScribeInput(
                    originalText = initialText,
                    authorName = authorName,
                    authorHandle = authorHandle,
                    style = selectedStyle,
                    includeAttribution = false,
                    articleTitle = articleTitle,
                    sourceUrl = sourceUrl,
                ),
            ),
        )
    }

    // Editable standalone draft content
    var editedText by remember(selectedFormat, currentResult) {
        mutableStateOf(
            if (selectedFormat == OmniScribeFormat.Micro) currentResult.microPost else currentResult.storyPost,
        )
    }

    // Standalone ComposePresenter (status = null so it always publishes as a separate new post)
    val composeState by producePresenter(key = "omni_scribe_standalone_compose") {
        remember { ComposePresenter(accountType = null, status = null, draftGroupId = null) }.invoke()
    }

    // Connected Accounts via AccountsPresenter
    val accountsState by producePresenter {
        remember { AccountsPresenter() }.invoke()
    }

    val selectedAccountKeys = remember { mutableStateListOf<MicroBlogKey>() }
    var availableTargets by remember { mutableStateOf<List<BroadcastTargetAccount>>(emptyList()) }

    var selectedCarouselTemplate by remember { mutableStateOf(CarouselTemplate.ObsidianGold) }

    val initialCarouselDeck =
        remember(editedText, currentResult.contextArc, selectedCarouselTemplate, authorName, authorHandle) {
            CarouselDeckGenerator.generateDeck(
                text = editedText,
                authorName = authorName,
                authorHandle = authorHandle,
                template = selectedCarouselTemplate,
                contextArc = currentResult.contextArc,
            )
        }

    val editableSlides = remember { mutableStateListOf<CarouselSlide>() }

    LaunchedEffect(initialCarouselDeck.slides) {
        editableSlides.clear()
        editableSlides.addAll(initialCarouselDeck.slides)
    }

    val activeDeck =
        remember(editableSlides.toList(), selectedCarouselTemplate, authorName, authorHandle) {
            CarouselDeck(
                template = selectedCarouselTemplate,
                authorName = authorName,
                authorHandle = authorHandle,
                slides = editableSlides.toList(),
            )
        }

    accountsState.accounts.onSuccess { items ->
        val targets = OmniBroadcastService.mapAccountItems(items)
        availableTargets = targets
        if (selectedAccountKeys.isEmpty() && targets.isNotEmpty()) {
            selectedAccountKeys.addAll(targets.map { it.accountKey })
            targets.forEach { target ->
                composeState.selectAccount(target.accountKey)
            }
        }
    }

    fun regenerate() {
        val res =
            OmniScribeEngine.repackage(
                OmniScribeInput(
                    originalText = initialText,
                    authorName = authorName,
                    authorHandle = authorHandle,
                    style = selectedStyle,
                    includeAttribution = false,
                    articleTitle = articleTitle,
                    sourceUrl = sourceUrl,
                ),
            )
        currentResult = res
        editedText = if (selectedFormat == OmniScribeFormat.Micro) res.microPost else res.storyPost
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = screenHorizontalPadding)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Studio Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF7C3AED), Color(0xFF2563EB), Color(0xFF06B6D4)),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.WandMagicSparkles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.omni_scribe_format_post),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        currentResult.contextArc?.let { arc ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            ) {
                                Text(
                                    text = arc.themeLabel,
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                        ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text =
                            if (articleTitle.isNotBlank()) {
                                "Article to social post • 4-slide carousel"
                            } else {
                                "Standalone post • Context and carousel"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Xmark,
                    contentDescription = stringResource(id = R.string.navigate_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 2. Source Context Breakdown Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.omni_scribe_source_post),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Standalone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = initialText.trim().replace(Regex("\\s+"), " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 3. Writing Voice Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(id = R.string.omni_scribe_voice_style),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OmniScribeStyle.entries.forEach { style ->
                    val isSelected = selectedStyle == style
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        border =
                            BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.35f,
                                    )
                                },
                            ),
                        modifier =
                            Modifier.clickable {
                                haptic.perform(FlareHapticEvent.Confirm)
                                selectedStyle = style
                                regenerate()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = style.iconEmoji)
                            Text(
                                text = style.displayName,
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    ),
                                color =
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            )
                        }
                    }
                }
            }
        }

        // 4. Format Target Selector (Short Post vs Multi-Act Story)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OmniScribeFormat.entries.forEach { format ->
                val isSelected = selectedFormat == format
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border =
                        BorderStroke(
                            1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = 0.35f,
                                )
                            },
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                haptic.perform(FlareHapticEvent.SelectionChange)
                                selectedFormat = format
                                editedText = if (format == OmniScribeFormat.Micro) currentResult.microPost else currentResult.storyPost
                            },
                ) {
                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (format == OmniScribeFormat.Micro) "Compact (<280 chars)" else "Full Story Essay",
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                ),
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }

        // 5. Live Standalone Draft Editor
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val charLimit = selectedFormat.maxCharacters
            val currentLen = editedText.length
            val isOverLimit = currentLen > charLimit

            OutlinedTextField(
                value = editedText,
                onValueChange = { editedText = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 135.dp, max = 240.dp),
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor =
                            if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor =
                            if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚡ Synthesized in ${currentResult.latencyMs}ms • Zero Cloud Egress",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "$currentLen / $charLimit",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color =
                        when {
                            isOverLimit -> MaterialTheme.colorScheme.error
                            currentLen > charLimit - 20 -> Color(0xFFFFA000)
                            else -> MaterialTheme.colorScheme.primary
                        },
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        // 6. Interactive In-App Carousel Live-Preview Widget & Theme Studio
        InteractiveCarouselStudioWidget(
            deck = activeDeck,
            editableSlides = editableSlides,
            selectedTemplate = selectedCarouselTemplate,
            onTemplateSelect = { tmpl ->
                selectedCarouselTemplate = tmpl
            },
            onSavePngs = {
                haptic.perform(FlareHapticEvent.Confirm)
                isSavingGallery = true
                scope.launch {
                    val count =
                        runCatching {
                            CarouselBitmapRenderer.saveDeckToGallery(context, activeDeck)
                        }.getOrDefault(0)
                    isSavingGallery = false
                    if (count > 0) {
                        gallerySavedToast = true
                        Toast
                            .makeText(
                                context,
                                context.applicationContext.getString(R.string.omni_scribe_slides_saved, count),
                                Toast.LENGTH_SHORT,
                            ).show()
                        delay(2500)
                        gallerySavedToast = false
                    }
                }
            },
            onSharePdf = {
                haptic.perform(FlareHapticEvent.Confirm)
                isExportingPdf = true
                scope.launch {
                    runCatching {
                        CarouselBitmapRenderer.sharePdfExternally(
                            context = context,
                            postText = editedText,
                            deck = activeDeck,
                        )
                    }
                    isExportingPdf = false
                }
            },
            isSavingPngs = isSavingGallery,
            pngsSavedToast = gallerySavedToast,
            isExportingPdf = isExportingPdf,
        )

        // Toggle: Attach Carousel Slides when Posting
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.perform(FlareHapticEvent.SelectionChange)
                        attachCarouselToPost = !attachCarouselToPost
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Attach Carousel Posters (${editableSlides.size} images) to Post",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Publishes both your rewritten text and the rendered 4:5 poster slides together",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = attachCarouselToPost,
                    onCheckedChange = {
                        haptic.perform(FlareHapticEvent.SelectionChange)
                        attachCarouselToPost = it
                    },
                )
            }
        }

        // 7. Connected Networks Target Selector
        if (availableTargets.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target Networks (${selectedAccountKeys.size} Selected)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableTargets.forEach { target ->
                        val isChecked = selectedAccountKeys.contains(target.accountKey)
                        val isValidForLength = OmniBroadcastService.isLengthValid(editedText, target.platformId)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color =
                                if (isChecked) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.7f,
                                    )
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (isChecked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(
                                            alpha = 0.35f,
                                        )
                                    },
                                ),
                            modifier =
                                Modifier.clickable {
                                    haptic.perform(FlareHapticEvent.SelectionChange)
                                    if (isChecked) {
                                        selectedAccountKeys.remove(target.accountKey)
                                    } else {
                                        selectedAccountKeys.add(target.accountKey)
                                    }
                                    composeState.selectAccount(target.accountKey)
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = target.platformId.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color =
                                        if (isChecked) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Text(
                                    text = target.handle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!isValidForLength) {
                                    Text(
                                        text = "⚠️ >${target.maxCharacterLimit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. Primary Standalone Post & Share Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Native External Share Sheet Button (Text + 1080x1350 Carousel PNGs)
            OutlinedButton(
                onClick = {
                    haptic.perform(FlareHapticEvent.Confirm)
                    scope.launch {
                        CarouselBitmapRenderer.shareDeckExternally(
                            context = context,
                            postText = editedText,
                            deck = activeDeck,
                            includeImages = attachCarouselToPost,
                        )
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.ShareNodes,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(id = R.string.omni_scribe_share), fontWeight = FontWeight.SemiBold)
            }

            // Open Standalone Composer with Rewritten Text + Rendered Carousel Slides Attached
            FilledTonalButton(
                onClick = {
                    if (isOpeningComposer) return@FilledTonalButton
                    haptic.perform(FlareHapticEvent.Confirm)
                    isOpeningComposer = true
                    scope.launch {
                        val mediaUris =
                            if (attachCarouselToPost) {
                                runCatching {
                                    CarouselBitmapRenderer
                                        .renderDeckToFileUris(context, activeDeck)
                                        .map { it.toString() }
                                }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }
                        isOpeningComposer = false
                        onBack()
                        navigate(
                            Route.Compose.OmniDraft(
                                initialText = editedText,
                                initialMediaUris = mediaUris,
                            ),
                        )
                    }
                },
                enabled = !isOpeningComposer && editedText.isNotBlank(),
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (isOpeningComposer) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.PenToSquare,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.omni_scribe_composer),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            // Direct Standalone Post Dispatch
            Button(
                onClick = {
                    haptic.perform(FlareHapticEvent.Confirm)
                    isPublishing = true
                    scope.launch {
                        val renderedFiles =
                            if (attachCarouselToPost) {
                                runCatching {
                                    CarouselBitmapRenderer.renderDeckToCacheFiles(context, activeDeck)
                                }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }

                        val medias =
                            renderedFiles.mapIndexed { idx, file ->
                                ComposeData.Media(
                                    file = FileItem(context, Uri.fromFile(file)),
                                    altText = activeDeck.slides.getOrNull(idx)?.body ?: "Carousel slide ${idx + 1}",
                                )
                            }

                        val standaloneComposeData =
                            ComposeData(
                                content = editedText,
                                medias = medias,
                                visibility = UiTimelineV2.Post.Visibility.Public,
                                referenceStatus = null, // Always a separate standalone post!
                            )

                        composeState.send(standaloneComposeData) { dispatched ->
                            scope.launch {
                                isPublishing = false
                                if (dispatched) {
                                    publishDone = true
                                    haptic.perform(FlareHapticEvent.Confirm)
                                    Toast
                                        .makeText(
                                            context,
                                            context.applicationContext.getString(
                                                R.string.omni_scribe_broadcast_success,
                                                medias.size,
                                                selectedAccountKeys.size,
                                            ),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    delay(500)
                                    onBack()
                                } else {
                                    // Fallback to opening the standalone composer pre-filled with text & carousel slides
                                    val uris = renderedFiles.map { Uri.fromFile(it).toString() }
                                    onBack()
                                    navigate(
                                        Route.Compose.OmniDraft(
                                            initialText = editedText,
                                            initialMediaUris = uris,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
                enabled = !isPublishing && !publishDone && editedText.isNotBlank(),
                modifier =
                    Modifier
                        .weight(1.15f)
                        .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                if (isPublishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Broadcasting...")
                } else if (publishDone) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Dispatched!")
                } else {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.PaperPlane,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedAccountKeys.size > 1) "Broadcast (${selectedAccountKeys.size})" else "Publish Separate",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * In-App Interactive Carousel Live-Preview Widget & Theme Studio.
 * Swipeable 1080x1350 preview with animated page dots, visual theme picker, and live canvas editing.
 */
@Composable
private fun InteractiveCarouselStudioWidget(
    deck: CarouselDeck,
    editableSlides: SnapshotStateList<CarouselSlide>,
    selectedTemplate: CarouselTemplate,
    onTemplateSelect: (CarouselTemplate) -> Unit,
    onSavePngs: () -> Unit,
    onSharePdf: () -> Unit,
    isSavingPngs: Boolean,
    pngsSavedToast: Boolean,
    isExportingPdf: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberFlareHapticFeedback(true)
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { editableSlides.size })
    val (bg, cardBg, textColor, mutedColor, accentColor) =
        when (selectedTemplate) {
            CarouselTemplate.ObsidianGold -> {
                arrayOf(
                    Color(0xFF0E0E0E),
                    Color(0xFF18181A),
                    Color(0xFFF4F1EA),
                    Color(0xFF9C9890),
                    Color(0xFFE8B948),
                )
            }

            CarouselTemplate.WarmLinen -> {
                arrayOf(
                    Color(0xFFF4F1EA),
                    Color(0xFFFCFAF5),
                    Color(0xFF161616),
                    Color(0xFF5A584F),
                    Color(0xFFB8341B),
                )
            }

            CarouselTemplate.CyberCyan -> {
                arrayOf(
                    Color(0xFF101318),
                    Color(0xFF181C24),
                    Color(0xFFF1F4F8),
                    Color(0xFF8C9AAC),
                    Color(0xFF00F0FF),
                )
            }

            CarouselTemplate.GradientAccent -> {
                arrayOf(
                    Color(0xFF0F0C1C),
                    Color(0xFF18142C),
                    Color(0xFFF8F6FF),
                    Color(0xFFA89EC6),
                    Color(0xFFA855F7),
                )
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Studio Header & Quick Export (LinkedIn PDF + Gallery PNGs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Carousel Poster Studio (${editableSlides.size} Slides • 1080×1350)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Swipe slides • Live-edit text • Export to LinkedIn or Gallery",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSharePdf,
                    enabled = !isExportingPdf,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.FileExport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExportingPdf) "Exporting…" else stringResource(id = R.string.omni_scribe_export_pdf),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }

                OutlinedButton(
                    onClick = onSavePngs,
                    enabled = !isSavingPngs,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    FAIcon(
                        imageVector = if (pngsSavedToast) FontAwesomeIcons.Solid.Check else FontAwesomeIcons.Solid.Download,
                        contentDescription = null,
                        tint = if (pngsSavedToast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (pngsSavedToast) "Saved" else stringResource(id = R.string.omni_scribe_save_pngs),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }

        // 2. Visual Theme Picker Chips
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CarouselTemplate.entries.forEach { tmpl ->
                val isSelected = selectedTemplate == tmpl
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    border =
                        BorderStroke(
                            1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = 0.35f,
                                )
                            },
                        ),
                    modifier =
                        Modifier.clickable {
                            haptic.perform(FlareHapticEvent.SelectionChange)
                            onTemplateSelect(tmpl)
                        },
                ) {
                    Text(
                        text =
                            when (tmpl) {
                                CarouselTemplate.ObsidianGold -> "⚡ Obsidian Gold"
                                CarouselTemplate.WarmLinen -> "📖 Editorial Minimal"
                                CarouselTemplate.CyberCyan -> "💻 Dark Terminal"
                                CarouselTemplate.GradientAccent -> "🔮 Gradient Accent"
                            },
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        // 3. Interactive Swipeable 4:5 Poster Pager
        if (editableSlides.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 46.dp),
                pageSpacing = 16.dp,
            ) { page ->
                val slide = editableSlides.getOrNull(page) ?: return@HorizontalPager
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val scale = (1f - pageOffset * 0.08f).coerceIn(0.92f, 1f)
                val alpha = (1f - pageOffset * 0.25f).coerceIn(0.72f, 1f)

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    CarouselSlidePreviewCard(
                        slide = slide,
                        template = selectedTemplate,
                        modifier =
                            Modifier
                                .width(260.dp)
                                .height(325.dp),
                        // 4:5 Aspect Ratio
                    )
                }
            }

            // 4. Smooth Page Dots Indicator with Quick Step Arrows
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            haptic.perform(FlareHapticEvent.SelectionChange)
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.size(48.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ArrowLeft,
                        contentDescription = stringResource(id = R.string.omni_scribe_previous_slide),
                        tint =
                            if (pagerState.currentPage > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            },
                        modifier = Modifier.size(14.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    editableSlides.indices.forEach { index ->
                        val isCurrent = pagerState.currentPage == index
                        val dotWidth by animateDpAsState(targetValue = if (isCurrent) 22.dp else 6.dp)
                        val dotColor = if (isCurrent) accentColor else mutedColor.copy(alpha = 0.35f)

                        Box(
                            modifier =
                                Modifier
                                    .width(dotWidth)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .clickable {
                                        haptic.perform(FlareHapticEvent.SelectionChange)
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (pagerState.currentPage < editableSlides.size - 1) {
                            haptic.perform(FlareHapticEvent.SelectionChange)
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    enabled = pagerState.currentPage < editableSlides.size - 1,
                    modifier = Modifier.size(48.dp),
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.ArrowRight,
                        contentDescription = stringResource(id = R.string.omni_scribe_next_slide),
                        tint =
                            if (pagerState.currentPage < editableSlides.size - 1) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            },
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // 5. In-Canvas Live Slide Text Editor
            val currentIndex = pagerState.currentPage
            val currentSlide = editableSlides.getOrNull(currentIndex)
            if (currentSlide != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FAIcon(
                                    imageVector = FontAwesomeIcons.Solid.PenToSquare,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "Live Edit Slide 0${currentIndex + 1} of 0${editableSlides.size}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.16f),
                            ) {
                                Text(
                                    text = currentSlide.kicker,
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = currentSlide.headline,
                            onValueChange = { newHeadline ->
                                editableSlides[currentIndex] = currentSlide.copy(headline = newHeadline)
                            },
                            label = { Text("Slide Headline") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value = currentSlide.body,
                            onValueChange = { newBody ->
                                editableSlides[currentIndex] = currentSlide.copy(body = newBody)
                            },
                            label = { Text("Slide Body Content") },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp, max = 135.dp),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4:5 Aspect Ratio Editorial Preview Card for an individual Carousel Poster Slide.
 */
@Composable
private fun CarouselSlidePreviewCard(
    slide: CarouselSlide,
    template: CarouselTemplate,
    modifier: Modifier = Modifier,
) {
    val (bg, cardBg, textColor, mutedColor, accentColor) =
        when (template) {
            CarouselTemplate.ObsidianGold -> {
                arrayOf(
                    Color(0xFF0E0E0E),
                    Color(0xFF18181A),
                    Color(0xFFF4F1EA),
                    Color(0xFF9C9890),
                    Color(0xFFE8B948),
                )
            }

            CarouselTemplate.WarmLinen -> {
                arrayOf(
                    Color(0xFFF4F1EA),
                    Color(0xFFFCFAF5),
                    Color(0xFF161616),
                    Color(0xFF5A584F),
                    Color(0xFFB8341B),
                )
            }

            CarouselTemplate.CyberCyan -> {
                arrayOf(
                    Color(0xFF101318),
                    Color(0xFF181C24),
                    Color(0xFFF1F4F8),
                    Color(0xFF8C9AAC),
                    Color(0xFF00F0FF),
                )
            }

            CarouselTemplate.GradientAccent -> {
                arrayOf(
                    Color(0xFF0F0C1C),
                    Color(0xFF18142C),
                    Color(0xFFF8F6FF),
                    Color(0xFFA89EC6),
                    Color(0xFFA855F7),
                )
            }
        }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bg,
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.38f)),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardBg)
                    .padding(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Header Row
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = slide.kicker,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.5.sp,
                                ),
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "0${slide.index}/0${slide.totalSlides}",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.5.sp,
                                ),
                            color = mutedColor,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(0.8.dp)
                                .background(accentColor.copy(alpha = 0.28f)),
                    )
                }

                // Middle Narrative Content
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = slide.headline,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = if (template == CarouselTemplate.WarmLinen) FontFamily.Serif else FontFamily.Default,
                                fontSize = 15.sp,
                                lineHeight = 19.sp,
                            ),
                        color = textColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier =
                            Modifier
                                .width(32.dp)
                                .height(3.dp)
                                .background(accentColor, shape = CircleShape),
                    )
                    Text(
                        text = slide.body,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            ),
                        color = textColor.copy(alpha = 0.92f),
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Bottom Editorial Progress Dots & CTA
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(0.8.dp)
                                .background(accentColor.copy(alpha = 0.28f)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (i in 1..slide.totalSlides) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(if (i == slide.index) 6.dp else 4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (i == slide.index) accentColor else mutedColor.copy(alpha = 0.4f),
                                            ),
                                )
                            }
                        }
                        Text(
                            text = if (slide.isFinal) "SAVE & SHARE" else "SWIPE →",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                ),
                            color = accentColor,
                        )
                    }
                }
            }
        }
    }
}
