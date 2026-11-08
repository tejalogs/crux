package org.crux.ui.screen.crux

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ArrowLeft
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.Feather
import compose.icons.fontawesomeicons.solid.LayerGroup
import compose.icons.fontawesomeicons.solid.Leaf
import compose.icons.fontawesomeicons.solid.ShieldHalved
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.data.repository.DefaultRssFeeds
import org.crux.data.repository.SettingsRepository
import org.crux.data.repository.SubscriptionRepository
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.screen.home.LiveWebScreen
import org.crux.ui.screen.instagram.InstagramWebScreen
import org.crux.ui.screen.linkedin.LinkedInWebScreen
import org.koin.compose.koinInject

private enum class OnboardingStep { Welcome, Platforms, SignIn, Calm }

/**
 * First-run flow. Crux is a set of real sites behind one calm shell, so setup is: understand what
 * Crux is, pick the platforms you actually use, sign in on the real sites (never in Crux), and
 * decide how calm the app should look. Every step can be skipped and changed later.
 */
@Composable
internal fun CruxOnboardingScreen(connections: CruxConnectionsState) {
    val subscriptionRepository = koinInject<SubscriptionRepository>()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    val selected =
        remember {
            mutableStateListOf<CruxPlatform>().apply {
                addAll(connections.enabled.ifEmpty { CruxPlatform.entries })
            }
        }
    val selectedStarterFeeds = remember { mutableStateListOf<DefaultRssFeeds.FeedItem>() }
    // Feeds already in the library. Null until loaded; on a replayed tour nothing is preselected,
    // so feeds the user removed are not quietly added back.
    val existingFeedUrls by produceState<Set<String>?>(initialValue = null) {
        value =
            subscriptionRepository
                .observeAll()
                .first()
                .map { it.url }
                .toSet()
    }
    var starterFeedsPrimed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existingFeedUrls) {
        val existing = existingFeedUrls ?: return@LaunchedEffect
        if (!starterFeedsPrimed) {
            if (existing.isEmpty()) selectedStarterFeeds.addAll(starterFeedPicks())
            starterFeedsPrimed = true
        }
    }
    var signingIn by remember { mutableStateOf<CruxPlatform?>(null) }

    fun finish() {
        val existing = existingFeedUrls.orEmpty()
        val newFeeds = selectedStarterFeeds.filter { it.url !in existing }
        if (CruxPlatform.Rss in selected && newFeeds.isNotEmpty()) {
            scope.launch {
                subscriptionRepository.upsertAll(newFeeds.map { it.toSubscriptionSourceInput() })
            }
        }
        connections.completeOnboarding(selected.toList())
    }

    val activeSignIn = signingIn
    if (activeSignIn != null) {
        val close = {
            signingIn = null
            connections.refresh()
        }
        BackHandler(onBack = close)
        when (activeSignIn) {
            CruxPlatform.Instagram -> InstagramWebScreen(onBack = close)
            CruxPlatform.LinkedIn -> LinkedInWebScreen(onBack = close)
            else -> activeSignIn.site?.let { LiveWebScreen(siteName = it.name, onBack = close) }
        }
        return
    }

    BackHandler(enabled = step != OnboardingStep.Welcome) {
        step = OnboardingStep.entries[step.ordinal - 1]
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
        ) {
            OnboardingTopBar(
                step = step,
                onBack = { step = OnboardingStep.entries[step.ordinal - 1] },
                onSkip = ::finish,
            )
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_step",
            ) { current ->
                when (current) {
                    OnboardingStep.Welcome -> {
                        WelcomeStep(onContinue = { step = OnboardingStep.Platforms })
                    }

                    OnboardingStep.Platforms -> {
                        PlatformsStep(
                            selected = selected,
                            selectedStarterFeeds = selectedStarterFeeds,
                            existingFeedUrls = existingFeedUrls.orEmpty(),
                            onContinue = {
                                step = if (selected.any { it.site != null }) OnboardingStep.SignIn else OnboardingStep.Calm
                            },
                        )
                    }

                    OnboardingStep.SignIn -> {
                        SignInStep(
                            platforms = selected.filter { it.site != null }.sortedBy { it.ordinal },
                            hasRss = CruxPlatform.Rss in selected,
                            connections = connections,
                            onSignIn = { signingIn = it },
                            onContinue = { step = OnboardingStep.Calm },
                        )
                    }

                    OnboardingStep.Calm -> {
                        CalmStep(onFinish = ::finish)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(
    step: OnboardingStep,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (step != OnboardingStep.Welcome) {
                IconButton(onClick = onBack) {
                    FAIcon(FontAwesomeIcons.Solid.ArrowLeft, contentDescription = stringResource(R.string.navigate_back))
                }
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            OnboardingStep.entries.forEach {
                val width by animateDpAsState(if (it == step) 22.dp else 8.dp, label = "dot")
                Box(
                    modifier =
                        Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (it.ordinal <= step.ordinal) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                )
            }
        }
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.crux_onboarding_skip))
        }
    }
}

@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            shape = RoundedCornerShape(18.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .heightIn(min = 56.dp),
        ) {
            Text(text = primaryLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            PlatformConstellation()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style =
                        MaterialTheme.typography.displayMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                        ),
                )
                Text(
                    text = stringResource(R.string.crux_onboarding_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ValueCard(
                    icon = FontAwesomeIcons.Solid.LayerGroup,
                    title = stringResource(R.string.crux_onboarding_value_one_title),
                    body = stringResource(R.string.crux_onboarding_value_one_body),
                )
                ValueCard(
                    icon = FontAwesomeIcons.Solid.ShieldHalved,
                    title = stringResource(R.string.crux_onboarding_value_two_title),
                    body = stringResource(R.string.crux_onboarding_value_two_body),
                )
                ValueCard(
                    icon = FontAwesomeIcons.Solid.Feather,
                    title = stringResource(R.string.crux_onboarding_value_three_title),
                    body = stringResource(R.string.crux_onboarding_value_three_body),
                )
            }
        }
        Button(
            onClick = onContinue,
            shape = RoundedCornerShape(18.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .heightIn(min = 56.dp),
        ) {
            Text(stringResource(R.string.crux_onboarding_get_started), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Converging hub: tactile pebble badges for every supported network & RSS. */
@Composable
private fun PlatformConstellation() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(100.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.ShieldHalved,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = stringResource(R.string.crux_onboarding_sovereign_pill),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            CruxPlatform.entries.forEach { platform ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    shadowElevation = 2.dp,
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        PlatformBadge(platform = platform, size = 36.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                FAIcon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun LiveDockPreview(selected: List<CruxPlatform>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                Text(
                    text = stringResource(R.string.crux_onboarding_dock_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${selected.size} active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.crux_onboarding_dock_preview_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .animateContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected.isEmpty()) {
                        Text(
                            text = "No platforms selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        CruxPlatform.entries.filter { it in selected }.forEach { platform ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                PlatformBadge(platform = platform, size = 30.dp)
                                Text(
                                    text = platform.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformsStep(
    selected: MutableList<CruxPlatform>,
    selectedStarterFeeds: MutableList<DefaultRssFeeds.FeedItem>,
    existingFeedUrls: Set<String>,
    onContinue: () -> Unit,
) {
    StepScaffold(
        title = stringResource(R.string.crux_onboarding_platforms_title),
        subtitle = stringResource(R.string.crux_onboarding_platforms_subtitle),
        primaryLabel = stringResource(R.string.crux_onboarding_continue),
        onPrimary = onContinue,
        primaryEnabled = selected.isNotEmpty(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveDockPreview(selected = selected)

            CruxPlatform.entries.forEach { platform ->
                val isSelected = platform in selected
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border =
                        BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSelected) selected.remove(platform) else selected.add(platform)
                                    },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            PlatformBadge(platform = platform, size = 44.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(platform.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = stringResource(platform.tagline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.CircleCheck,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(visible = platform == CruxPlatform.Rss && isSelected) {
                            RssStarterFeedsSelector(selectedFeeds = selectedStarterFeeds, existingFeedUrls = existingFeedUrls)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RssStarterFeedsSelector(
    selectedFeeds: MutableList<DefaultRssFeeds.FeedItem>,
    existingFeedUrls: Set<String>,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryIndex by remember { mutableStateOf<Int?>(null) }
    val categories = remember { DefaultRssFeeds.categories }
    val displayedFeeds =
        remember(selectedCategoryIndex) {
            if (selectedCategoryIndex == null) DefaultRssFeeds.allFeeds else categories[selectedCategoryIndex!!].feeds
        }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.crux_onboarding_rss_starter_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.crux_onboarding_rss_starter_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) {
                            stringResource(R.string.onboarding_hide)
                        } else {
                            stringResource(R.string.crux_onboarding_rss_selected_count, selectedFeeds.size)
                        },
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                selectedFeeds.clear()
                                selectedFeeds.addAll(starterFeedPicks())
                            },
                        ) {
                            Text(stringResource(R.string.crux_onboarding_rss_select_recommended), style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = {
                                selectedFeeds.clear()
                                selectedFeeds.addAll(DefaultRssFeeds.allFeeds)
                            },
                        ) {
                            Text(stringResource(R.string.crux_onboarding_rss_select_all), style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = { selectedFeeds.clear() },
                        ) {
                            Text(stringResource(R.string.crux_onboarding_rss_deselect_all), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = selectedCategoryIndex == null,
                            onClick = { selectedCategoryIndex = null },
                            label = { Text(stringResource(R.string.rss_curated_category_all)) },
                        )
                        categories.forEachIndexed { index, cat ->
                            FilterChip(
                                selected = selectedCategoryIndex == index,
                                onClick = { selectedCategoryIndex = index },
                                label = { Text(cat.name) },
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        displayedFeeds.forEach { feed ->
                            val alreadyAdded = feed.url in existingFeedUrls
                            val isChecked = alreadyAdded || feed in selectedFeeds
                            Surface(
                                onClick = {
                                    if (isChecked) selectedFeeds.remove(feed) else selectedFeeds.add(feed)
                                },
                                enabled = !alreadyAdded,
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Checkbox(
                                        enabled = !alreadyAdded,
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (feed !in selectedFeeds) selectedFeeds.add(feed)
                                            } else {
                                                selectedFeeds.remove(feed)
                                            }
                                        },
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(feed.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (alreadyAdded) stringResource(R.string.crux_onboarding_rss_added) else feed.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInStep(
    platforms: List<CruxPlatform>,
    hasRss: Boolean,
    connections: CruxConnectionsState,
    onSignIn: (CruxPlatform) -> Unit,
    onContinue: () -> Unit,
) {
    StepScaffold(
        title = stringResource(R.string.crux_onboarding_signin_title),
        subtitle = stringResource(R.string.crux_onboarding_signin_subtitle),
        primaryLabel = stringResource(R.string.crux_onboarding_continue),
        onPrimary = onContinue,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.ShieldHalved,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.crux_onboarding_signin_optional_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.crux_onboarding_signin_optional_banner_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            platforms.forEach { platform ->
                val signedIn = connections.isSignedIn(platform)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PlatformBadge(platform = platform)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(platform.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text =
                                    when {
                                        signedIn -> stringResource(R.string.crux_status_signed_in)
                                        platform.worksSignedOut -> stringResource(R.string.crux_status_signed_out_ok)
                                        else -> stringResource(R.string.crux_status_sign_in_needed)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (signedIn) {
                            FAIcon(
                                FontAwesomeIcons.Solid.CircleCheck,
                                contentDescription = stringResource(R.string.crux_status_signed_in),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            FilledTonalButton(onClick = { onSignIn(platform) }) {
                                Text(stringResource(R.string.crux_action_sign_in))
                            }
                        }
                    }
                }
            }
            if (hasRss) {
                Text(
                    text = stringResource(R.string.crux_onboarding_rss_later),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                FAIcon(
                    FontAwesomeIcons.Solid.ShieldHalved,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.crux_onboarding_signin_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveCalmSamplePreview(grayscale: Boolean, paperTint: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.crux_onboarding_calm_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (grayscale) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "B&W",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (paperTint) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "Paper",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.crux_onboarding_calm_sample_headline),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                    Text(
                        text = stringResource(R.string.crux_onboarding_calm_sample_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalmStep(onFinish: () -> Unit) {
    val settingsRepository = koinInject<SettingsRepository>()
    val scope = rememberCoroutineScope()
    val appearance = LocalGlobalAppearance.current
    StepScaffold(
        title = stringResource(R.string.crux_onboarding_calm_title),
        subtitle = stringResource(R.string.crux_onboarding_calm_subtitle),
        primaryLabel = stringResource(R.string.crux_onboarding_finish),
        onPrimary = onFinish,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LiveCalmSamplePreview(grayscale = appearance.grayscale, paperTint = appearance.paperTint)
            CalmToggle(
                icon = FontAwesomeIcons.Solid.Leaf,
                title = stringResource(R.string.crux_grayscale_title),
                body = stringResource(R.string.crux_grayscale_description),
                checked = appearance.grayscale,
                onCheckedChange = { scope.launch { settingsRepository.updateAppearance(AppearanceKeys.Grayscale, it) } },
            )
            CalmToggle(
                icon = FontAwesomeIcons.Solid.Feather,
                title = stringResource(R.string.crux_paper_tint_title),
                body = stringResource(R.string.crux_paper_tint_description),
                checked = appearance.paperTint,
                onCheckedChange = { scope.launch { settingsRepository.updateAppearance(AppearanceKeys.PaperTint, it) } },
            )
            ProtectionNote()
            Text(
                text = stringResource(R.string.crux_onboarding_calm_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tells new users the web tabs are already protected, without adding another decision. */
@Composable
private fun ProtectionNote() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.ShieldHalved,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.crux_onboarding_protection_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.crux_onboarding_protection_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A small, varied starting set: the first two feeds of each category. */
private fun starterFeedPicks(): List<DefaultRssFeeds.FeedItem> =
    DefaultRssFeeds.categories.flatMap {
        it.feeds.take(STARTER_FEEDS_PER_CATEGORY)
    }

private const val STARTER_FEEDS_PER_CATEGORY = 2

@Composable
internal fun CalmToggle(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FAIcon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/**
 * Side panel header: the Crux name and the platforms in use, each marked signed in or not.
 * Replaces the old "N accounts" button that opened a sheet listing every account twice.
 */
@Composable
internal fun CruxDrawerHeader(
    connections: CruxConnectionsState,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val socialPlatforms = connections.enabled.filter { it.site != null }
    val signedInCount = socialPlatforms.count { connections.isSignedIn(it) }
    Surface(
        onClick = onManage,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
                )
                Text(
                    text =
                        if (socialPlatforms.isEmpty()) {
                            stringResource(R.string.crux_drawer_no_platforms)
                        } else {
                            stringResource(R.string.crux_drawer_signed_in_count, signedInCount, socialPlatforms.size)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (connections.enabled.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    connections.enabled.forEach { platform ->
                        Box {
                            PlatformBadge(platform = platform, size = 30.dp)
                            if (platform.site != null) {
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    StatusDot(signedIn = connections.isSignedIn(platform))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
