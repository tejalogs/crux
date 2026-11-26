package org.crux.ui.screen.crux

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.crux.R
import org.crux.data.datastore.model.AppSettings
import org.crux.data.model.Theme
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.data.repository.DefaultRssFeeds
import org.crux.data.repository.SettingsRepository
import org.crux.data.repository.SubscriptionRepository
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.screen.home.LiveWebPages
import org.crux.ui.screen.home.LiveWebScreen
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.LocalLiveWebPages
import org.crux.ui.screen.instagram.InstagramWebScreen
import org.crux.ui.screen.linkedin.LinkedInWebScreen
import org.koin.compose.koinInject

internal enum class OnboardingStep { Welcome, Platforms, SignIn, Calm }

/** Saves the picked platforms by name, so they survive activity recreation alongside the step. */
private val platformListSaver =
    listSaver<SnapshotStateList<CruxPlatform>, String>(
        save = { list -> list.map { it.name } },
        restore = { names -> mutableStateListOf<CruxPlatform>().apply { addAll(names.mapNotNull { CruxPlatform.fromName(it) }) } },
    )

/** The steps this user will actually see: Sign in only appears when a site (not just Feeds) is picked. */
internal fun visibleOnboardingSteps(selected: Collection<CruxPlatform>): List<OnboardingStep> =
    OnboardingStep.entries.filter { it != OnboardingStep.SignIn || selected.any { platform -> platform.site != null } }

/** The next visible step after [current], or null on the last one. */
internal fun nextOnboardingStep(
    current: OnboardingStep,
    visible: List<OnboardingStep>,
): OnboardingStep? = visible.firstOrNull { it.ordinal > current.ordinal }

/** The visible step before [current], so Back never lands on a skipped step. Null on the first one. */
internal fun previousOnboardingStep(
    current: OnboardingStep,
    visible: List<OnboardingStep>,
): OnboardingStep? = visible.lastOrNull { it.ordinal < current.ordinal }

/** 1-based position of [current] among the visible steps, for "Step x of y". */
internal fun onboardingStepPosition(
    current: OnboardingStep,
    visible: List<OnboardingStep>,
): Int = visible.count { it.ordinal <= current.ordinal }.coerceAtLeast(1)

/**
 * What the Platforms step starts with. A fresh install still carries the stored default platforms,
 * which the user never chose, so it starts with only Feeds and the user picks the rest. Anyone who
 * changed their platforms or signed in somewhere keeps what they have.
 */
internal fun initialOnboardingPicks(
    enabled: List<CruxPlatform>,
    signedIn: Set<CruxPlatform>,
    returningUser: Boolean = false,
): List<CruxPlatform> {
    if (enabled.isEmpty()) return listOf(CruxPlatform.Rss)
    // A replayed tour must not quietly drop tabs the user has been living with.
    if (returningUser) return enabled
    val untouchedDefaults =
        enabled.map { it.name }.toSet() == AppSettings.DEFAULT_CRUX_PLATFORMS.toSet() &&
            enabled.none { it.site != null && it in signedIn }
    return if (untouchedDefaults) listOf(CruxPlatform.Rss) else enabled
}

/**
 * Whether this person finished the tour before (Settings > Replay welcome only clears
 * onboardingCompleted). Finishing onboarding always syncs the live-web tabs, which sets
 * liveWebTabsMigrated; picking feeds sets rssDefaultsSeeded; a fresh install has neither and no feeds.
 */
internal fun isReturningOnboardingUser(
    settings: AppSettings,
    existingFeedCount: Int,
): Boolean = settings.liveWebTabsMigrated || settings.rssDefaultsSeeded || existingFeedCount > 0

/**
 * The platforms to save when the tour ends. Someone who never reached the Platforms step (Skip on
 * Welcome) keeps what they have unless they are known to be new, so a replay never removes tabs.
 * [returningUser] is null while settings are still loading.
 */
internal fun onboardingPlatformsToSave(
    selected: List<CruxPlatform>,
    enabled: List<CruxPlatform>,
    returningUser: Boolean?,
    platformsVisited: Boolean,
): List<CruxPlatform> =
    if (!platformsVisited && returningUser != false && enabled.isNotEmpty()) enabled else selected

/** Which picked sites can be browsed signed out, and which show a login page until signed in. */
internal data class SignInBannerParts(
    val browseSignedOut: List<CruxPlatform>,
    val needSignIn: List<CruxPlatform>,
)

internal fun signInBannerParts(
    platforms: List<CruxPlatform>,
    signedIn: Set<CruxPlatform>,
): SignInBannerParts {
    val pending = platforms.filter { it.site != null && it !in signedIn }
    return SignInBannerParts(
        browseSignedOut = pending.filter { it.worksSignedOut },
        needSignIn = pending.filterNot { it.worksSignedOut },
    )
}

/** "A", "A and B", "A, B and C", with the final joiner supplied by a string resource. */
internal fun joinPlatformNames(
    names: List<String>,
    joinLastTwo: (String, String) -> String,
): String =
    when (names.size) {
        0 -> ""
        1 -> names.first()
        else -> joinLastTwo(names.dropLast(1).joinToString(", "), names.last())
    }

/**
 * First-run flow. Crux is a set of real sites behind one calm shell, so setup is: understand what
 * Crux is, pick the platforms you actually use, sign in on the real sites (never in Crux), and
 * decide how calm the app should look. Every step can be skipped and changed later.
 */
@Composable
internal fun CruxOnboardingScreen(connections: CruxConnectionsState) {
    val subscriptionRepository = koinInject<SubscriptionRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val scope = rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    // Saveable like `step`, so a recreated activity does not come back on Sign in with the picks reset.
    val selected =
        rememberSaveable(saver = platformListSaver) {
            mutableStateListOf<CruxPlatform>().apply {
                addAll(initialOnboardingPicks(connections.enabled, connections.signedIn))
            }
        }
    var picksTouched by rememberSaveable { mutableStateOf(false) }
    var platformsVisited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(step) {
        if (step.ordinal >= OnboardingStep.Platforms.ordinal) platformsVisited = true
    }
    val storedSettings by produceState<AppSettings?>(initialValue = null) {
        value = settingsRepository.appSettings.first()
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
    // Null until both settings and the feed library have loaded.
    val returningUser: Boolean? =
        storedSettings?.let { settings ->
            existingFeedUrls?.let { feeds -> isReturningOnboardingUser(settings, feeds.size) }
        }
    LaunchedEffect(returningUser) {
        // The first picks were made before settings loaded; a returning user keeps their platforms.
        if (returningUser == true && !picksTouched) {
            val keep = initialOnboardingPicks(connections.enabled, connections.signedIn, returningUser = true)
            if (selected.toList() != keep) {
                selected.clear()
                selected.addAll(keep)
            }
        }
    }
    var signingIn by remember { mutableStateOf<CruxPlatform?>(null) }
    val haptics = LocalHapticFeedback.current
    var previousSignedIn by remember { mutableStateOf(connections.signedIn) }
    LaunchedEffect(connections.signedIn) {
        if ((connections.signedIn - previousSignedIn).isNotEmpty()) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        previousSignedIn = connections.signedIn
    }

    fun finish() {
        if (finishing) return
        finishing = true
        val chosenFeeds = if (starterFeedsPrimed) selectedStarterFeeds.toList() else starterFeedPicks()
        val platforms = onboardingPlatformsToSave(selected.toList(), connections.enabled, returningUser, platformsVisited)
        val rssChosen = CruxPlatform.Rss in platforms
        // Save before completing: completing removes this screen, which cancels `scope`.
        scope.launch {
            try {
                if (rssChosen) {
                    val existing = existingFeedUrls ?: subscriptionRepository.observeAll().first().map { it.url }.toSet()
                    val candidateFeeds = if (starterFeedsPrimed) chosenFeeds else if (existing.isEmpty()) starterFeedPicks() else emptyList()
                    val newFeeds = candidateFeeds.filter { it.url !in existing }
                    if (newFeeds.isNotEmpty()) {
                        subscriptionRepository.upsertAll(newFeeds.map { it.toSubscriptionSourceInput() })
                    }
                    // The user picked their starter feeds here, so the feeds screen must not
                    // later seed all defaults into an empty library.
                    settingsRepository.updateAppSettings { copy(rssDefaultsSeeded = true) }
                }
            } finally {
                connections.completeOnboarding(platforms)
            }
        }
    }

    val context = LocalContext.current
    val externalWebPages = LocalLiveWebPages.current
    val onboardingWebPages = remember(externalWebPages) { externalWebPages ?: LiveWebPages() }
    DisposableEffect(onboardingWebPages, externalWebPages) {
        onDispose {
            if (externalWebPages == null) {
                onboardingWebPages.destroy()
            }
        }
    }
    val nextPlatformToWarm = nextUnsignedPlatform(selected.toList(), connections.signedIn)
    LaunchedEffect(nextPlatformToWarm?.site) {
        nextPlatformToWarm?.site?.let { site ->
            onboardingWebPages.page(site, context)
        }
    }

    val activeSignIn = signingIn
    if (activeSignIn != null) {
        val close = {
            signingIn = null
            connections.refresh()
        }
        BackHandler(onBack = close)
        CompositionLocalProvider(LocalLiveWebPages provides onboardingWebPages) {
            activeSignIn.site?.let { site ->
                LiveWebScreen(
                    siteName = site.name,
                    onBack = close,
                    forSignIn = true,
                )
            }
        }
        return
    }

    val visibleSteps = visibleOnboardingSteps(selected)
    val goBack: () -> Unit = {
        previousOnboardingStep(step, visibleSteps)?.let { step = it }
    }
    BackHandler(enabled = step != OnboardingStep.Welcome, onBack = goBack)

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
                visibleSteps = visibleSteps,
                onBack = goBack,
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
                        WelcomeStep(onContinue = { nextOnboardingStep(current, visibleSteps)?.let { step = it } })
                    }

                    OnboardingStep.Platforms -> {
                        PlatformsStep(
                            selected = selected.toList(),
                            selectedStarterFeeds = selectedStarterFeeds.toList(),
                            existingFeedUrls = existingFeedUrls.orEmpty(),
                            onTogglePlatform = { platform ->
                                picksTouched = true
                                if (platform in selected) selected.remove(platform) else selected.add(platform)
                            },
                            onToggleStarterFeed = { feed, checked ->
                                if (checked) {
                                    if (feed !in selectedStarterFeeds) selectedStarterFeeds.add(feed)
                                } else {
                                    selectedStarterFeeds.remove(feed)
                                }
                            },
                            onReplaceStarterFeeds = { feeds ->
                                selectedStarterFeeds.clear()
                                selectedStarterFeeds.addAll(feeds)
                            },
                            onContinue = {
                                nextOnboardingStep(current, visibleOnboardingSteps(selected))?.let { step = it }
                            },
                        )
                    }

                    OnboardingStep.SignIn -> {
                        SignInStep(
                            platforms = selected.filter { it.site != null }.sortedBy { it.ordinal },
                            hasRss = CruxPlatform.Rss in selected,
                            connections = connections,
                            onSignIn = { signingIn = it },
                            onContinue = { nextOnboardingStep(current, visibleSteps)?.let { step = it } },
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
    visibleSteps: List<OnboardingStep>,
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
        val stepDescription =
            stringResource(
                R.string.crux_onboarding_step_of,
                onboardingStepPosition(step, visibleSteps),
                visibleSteps.size,
            )
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = stepDescription },
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            visibleSteps.forEach {
                key(it) {
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
    secondaryAction: (@Composable () -> Unit)? = null,
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                shape = RoundedCornerShape(18.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
            ) {
                Text(text = primaryLabel, style = MaterialTheme.typography.titleMedium)
            }
            if (secondaryAction != null) {
                secondaryAction()
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    val pillars =
        listOf(
            Triple(
                FontAwesomeIcons.Solid.LayerGroup,
                stringResource(R.string.crux_onboarding_value_one_title),
                stringResource(R.string.crux_onboarding_value_one_body),
            ),
            Triple(
                FontAwesomeIcons.Solid.ShieldHalved,
                stringResource(R.string.crux_onboarding_value_two_title),
                stringResource(R.string.crux_onboarding_value_two_body),
            ),
            Triple(
                FontAwesomeIcons.Solid.WandMagicSparkles,
                stringResource(R.string.crux_onboarding_value_three_title),
                stringResource(R.string.crux_onboarding_value_three_body),
            ),
        )
    val visiblePillars = remember { mutableStateListOf(false, false, false) }
    LaunchedEffect(Unit) {
        pillars.indices.forEach { index ->
            delay(65L)
            visiblePillars[index] = true
        }
    }

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
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_crux_mark),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(64.dp),
                )
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
            PlatformConstellation()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pillars.forEachIndexed { index, (icon, title, body) ->
                    AnimatedVisibility(
                        visible = visiblePillars[index],
                        enter =
                            fadeIn(animationSpec = tween(durationMillis = 260)) +
                                slideInVertically(animationSpec = tween(durationMillis = 260)) { it / 6 },
                    ) {
                        ValueCard(icon = icon, title = title, body = body)
                    }
                }
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
    val dockTitle = stringResource(R.string.crux_onboarding_dock_preview_title)
    val emptyHint = stringResource(R.string.crux_onboarding_dock_empty_hint)
    val activePlatforms = CruxPlatform.entries.filter { it in selected }
    val dockDescription =
        if (activePlatforms.isEmpty()) {
            "$dockTitle. $emptyHint"
        } else {
            val names = activePlatforms.joinToString(", ") { it.displayName }
            "$dockTitle. ${activePlatforms.size} active: $names"
        }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = dockDescription
                },
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
                            text = stringResource(R.string.crux_onboarding_dock_empty_hint),
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
    selected: List<CruxPlatform>,
    selectedStarterFeeds: List<DefaultRssFeeds.FeedItem>,
    existingFeedUrls: Set<String>,
    onTogglePlatform: (CruxPlatform) -> Unit,
    onToggleStarterFeed: (DefaultRssFeeds.FeedItem, Boolean) -> Unit,
    onReplaceStarterFeeds: (List<DefaultRssFeeds.FeedItem>) -> Unit,
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
                                        onTogglePlatform(platform)
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
                            RssStarterFeedsSelector(
                                selectedFeeds = selectedStarterFeeds,
                                existingFeedUrls = existingFeedUrls,
                                onToggleFeed = onToggleStarterFeed,
                                onReplaceFeeds = onReplaceStarterFeeds,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RssStarterFeedsSelector(
    selectedFeeds: List<DefaultRssFeeds.FeedItem>,
    existingFeedUrls: Set<String>,
    onToggleFeed: (DefaultRssFeeds.FeedItem, Boolean) -> Unit,
    onReplaceFeeds: (List<DefaultRssFeeds.FeedItem>) -> Unit,
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

            AnimatedVisibility(visible = !expanded && selectedFeeds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    selectedFeeds.take(4).forEach { feed ->
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = feed.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (selectedFeeds.size > 4) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "+${selectedFeeds.size - 4} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                onReplaceFeeds(starterFeedPicks())
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.crux_onboarding_rss_select_recommended),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        TextButton(
                            onClick = {
                                onReplaceFeeds(DefaultRssFeeds.allFeeds)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.crux_onboarding_rss_select_all),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        TextButton(
                            onClick = { onReplaceFeeds(emptyList()) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.crux_onboarding_rss_deselect_all),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                            )
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
                                    onToggleFeed(feed, !isChecked)
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
                                            onToggleFeed(feed, checked)
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

internal fun nextUnsignedPlatform(
    platforms: List<CruxPlatform>,
    signedIn: Set<CruxPlatform>,
): CruxPlatform? =
    platforms.firstOrNull { it.site != null && !it.worksSignedOut && it !in signedIn }
        ?: platforms.firstOrNull { it.site != null && it !in signedIn }

@Composable
private fun SignInStep(
    platforms: List<CruxPlatform>,
    hasRss: Boolean,
    connections: CruxConnectionsState,
    onSignIn: (CruxPlatform) -> Unit,
    onContinue: () -> Unit,
) {
    val signedInCount = platforms.count { connections.isSignedIn(it) }
    val allSignedIn = platforms.isNotEmpty() && signedInCount == platforms.size
    val nextPlatform = nextUnsignedPlatform(platforms, connections.signedIn)
    val bannerParts = signInBannerParts(platforms, connections.signedIn)
    val resources = LocalResources.current
    val joinNames = { picked: List<CruxPlatform> ->
        joinPlatformNames(picked.map { it.displayName }) { first, last ->
            resources.getString(R.string.crux_onboarding_names_and, first, last)
        }
    }
    val bannerBody =
        listOfNotNull(
            bannerParts.browseSignedOut.takeIf { it.isNotEmpty() }?.let {
                stringResource(R.string.crux_onboarding_signin_banner_signed_out, joinNames(it))
            },
            bannerParts.needSignIn.takeIf { it.isNotEmpty() }?.let {
                stringResource(R.string.crux_onboarding_signin_banner_needs_sign_in, joinNames(it))
            },
            stringResource(R.string.crux_onboarding_signin_optional_banner_body),
        ).joinToString(" ")
    StepScaffold(
        title = stringResource(R.string.crux_onboarding_signin_title),
        subtitle = stringResource(R.string.crux_onboarding_signin_subtitle),
        primaryLabel = stringResource(R.string.crux_onboarding_continue_to_calm),
        onPrimary = onContinue,
        secondaryAction =
            if (allSignedIn) {
                null
            } else {
                {
                    TextButton(
                        onClick = onContinue,
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text(
                            text =
                                if (signedInCount > 0) {
                                    "Skip remaining"
                                } else {
                                    stringResource(R.string.crux_onboarding_signin_later)
                                },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
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
                            text =
                                if (allSignedIn) {
                                    "All selected accounts connected"
                                } else if (signedInCount > 0) {
                                    "$signedInCount of ${platforms.size} accounts connected"
                                } else if (bannerParts.browseSignedOut.isEmpty()) {
                                    // Every pick needs an account, so "browse first" would not be true.
                                    stringResource(R.string.crux_onboarding_signin_banner_title_needed)
                                } else {
                                    stringResource(R.string.crux_onboarding_signin_optional_banner_title)
                                },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (!allSignedIn) {
                            Text(
                                text = bannerBody,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            platforms.forEach { platform ->
                val signedIn = connections.isSignedIn(platform)
                val isNext = platform == nextPlatform
                Surface(
                    onClick = { if (!signedIn) onSignIn(platform) },
                    enabled = !signedIn,
                    shape = RoundedCornerShape(20.dp),
                    color =
                        if (isNext) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    border =
                        if (isNext) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
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
                        } else if (isNext) {
                            Button(onClick = { onSignIn(platform) }) {
                                Text(stringResource(R.string.crux_action_sign_in))
                            }
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
private fun LiveCalmSamplePreview(
    grayscale: Boolean,
    paperTint: Boolean,
) {
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B82F6)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "CR",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Crux Editorial",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Calm Reader • 2 min read",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_appearance_theme_group_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val themes =
                            listOf(
                                Theme.SYSTEM to stringResource(R.string.settings_appearance_theme_auto),
                                Theme.DARK to stringResource(R.string.settings_appearance_theme_dark),
                                Theme.LIGHT to stringResource(R.string.settings_appearance_theme_light),
                            )
                        themes.forEach { (themeOption, label) ->
                            val isThemeSelected = appearance.theme == themeOption
                            FilterChip(
                                selected = isThemeSelected,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.updateAppearance(AppearanceKeys.Theme, themeOption)
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
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
