package org.crux.ui.screen.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.BookOpen
import compose.icons.fontawesomeicons.solid.ChevronRight
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.Database
import compose.icons.fontawesomeicons.solid.Feather
import compose.icons.fontawesomeicons.solid.Filter
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Leaf
import compose.icons.fontawesomeicons.solid.Lock
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Newspaper
import compose.icons.fontawesomeicons.solid.Palette
import compose.icons.fontawesomeicons.solid.PhotoFilm
import compose.icons.fontawesomeicons.solid.Robot
import compose.icons.fontawesomeicons.solid.ShieldHalved
import compose.icons.fontawesomeicons.solid.Sliders
import compose.icons.fontawesomeicons.solid.TableList
import compose.icons.fontawesomeicons.solid.Xmark
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.BuildConfig
import org.crux.R
import org.crux.data.model.BottomBarBehavior
import org.crux.data.model.Theme
import org.crux.data.model.appearance.AppearanceKeys
import org.crux.data.privacy.PrivacyLog
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.component.LocalGlobalAppearance
import org.crux.ui.component.ThemeIconData
import org.crux.ui.component.ThemedIcon
import org.crux.ui.model.takeSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.AccountsPresenter
import org.crux.ui.screen.crux.CalmToggle
import org.crux.ui.screen.crux.rememberCruxConnections
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.cookieManager
import org.crux.ui.screen.rss.ReaderFont
import org.crux.ui.screen.rss.ReaderMode
import org.crux.ui.screen.rss.ReaderTheme
import org.crux.ui.screen.rss.rememberReaderSettings
import org.crux.ui.theme.first
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.single

internal enum class SettingsCategory(
    val title: String,
) {
    SocialAndWeb("Social & Live Web"),
    AppearanceAndReader("Appearance & Reader"),
    Intelligence("Intelligence & Language"),
    PrivacyAndData("Privacy, Storage & System"),
}

internal data class SettingsHubItem(
    val id: String,
    val category: SettingsCategory,
    val title: String,
    val subtitle: String,
    val badge: String? = null,
    val keywords: List<String> = emptyList(),
)

internal fun filterSettingsItems(
    items: List<SettingsHubItem>,
    query: String,
): List<SettingsHubItem> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return items
    val terms = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    return items.filter { item ->
        val haystack =
            buildString {
                append(item.title.lowercase()).append(' ')
                append(item.subtitle.lowercase()).append(' ')
                append(item.category.title.lowercase()).append(' ')
                item.badge?.let { append(it.lowercase()).append(' ') }
                item.keywords.forEach { append(it.lowercase()).append(' ') }
            }
        terms.all { term -> haystack.contains(term) }
    }
}

private data class SettingsDestinationEntry(
    val hubItem: SettingsHubItem,
    val icon: ImageVector,
    val iconColor: ThemeIconData.Color,
    val onClick: () -> Unit,
)

@Composable
internal fun SettingsDetailPlaceholder(modifier: Modifier = Modifier) {
    CruxScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp,
                    alignment = Alignment.CenterVertically,
                ),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.Gear,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(
    toAccounts: () -> Unit,
    toAppearanceTheme: () -> Unit,
    toAppearanceLayout: () -> Unit,
    toAppearanceDisplay: () -> Unit,
    toAppearanceMedia: () -> Unit,
    toBehavior: () -> Unit,
    toHardenedBrowser: () -> Unit,
    toPrivacyTweaks: () -> Unit,
    toPrivacy: () -> Unit,
    toStorage: () -> Unit,
    toAbout: () -> Unit,
    toColorSpace: () -> Unit,
    toLocalFilter: () -> Unit,
    toAiConfig: () -> Unit,
    toTranslationConfig: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val appearanceState by producePresenter { appearancePresenter() }
    val globalAppearance = LocalGlobalAppearance.current
    val readerState = rememberReaderSettings()
    val reader = readerState.value
    val connections = rememberCruxConnections()
    val connectedAccountsCount = connections.enabled.size
    val privacyLogEntries by PrivacyLog.get().entries.collectAsState()
    val blockedTrackersCount = privacyLogEntries.count { it.blocked }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showLiveWebSheet by rememberSaveable { mutableStateOf(false) }

    val webPlatformCount = connections.enabled.count { it.site != null }
    val activeWebSessionsCount = connections.enabled.count { it.site != null && connections.isSignedIn(it) }

    val themeSummaryLabel =
        when (globalAppearance.theme) {
            Theme.LIGHT -> stringResource(R.string.settings_appearance_theme_light)
            Theme.SYSTEM -> stringResource(R.string.settings_appearance_theme_auto)
            Theme.DARK -> stringResource(R.string.settings_appearance_theme_dark)
        } + if (globalAppearance.pureColorMode) " • OLED" else ""

    val readerSummaryLabel = "${reader.mode.name} • ${reader.theme.name} • ${reader.font.name}"

    val accountsTitle = stringResource(id = R.string.settings_accounts_title)
    val accountsSubtitle = stringResource(id = R.string.settings_accounts_subtitle)
    val behaviorTitle = stringResource(id = R.string.settings_behavior_title)
    val behaviorSubtitle = stringResource(id = R.string.settings_behavior_description)
    val localFilterTitle = stringResource(id = R.string.settings_local_filter_title)
    val localFilterSubtitle = stringResource(id = R.string.settings_local_filter_description)
    val themeTitle = stringResource(id = R.string.settings_appearance_theme_group_title)
    val layoutTitle = stringResource(id = R.string.settings_appearance_layout_group_title)
    val displayTitle = stringResource(id = R.string.settings_appearance_display_group_title)
    val mediaTitle = stringResource(id = R.string.settings_appearance_media_group_title)
    val languageTitle = stringResource(id = R.string.settings_language_title)
    val languageSubtitle = stringResource(id = R.string.settings_language_description)
    val aiTitle = stringResource(id = R.string.settings_ai_config_title)
    val aiSubtitle = stringResource(id = R.string.settings_ai_config_description)
    val translationTitle = stringResource(id = R.string.settings_translation_title)
    val translationSubtitle = stringResource(id = R.string.settings_translation_description)
    val privacyTitle = stringResource(id = R.string.privacy_hub_title)
    val privacySubtitle = stringResource(id = R.string.privacy_hub_subtitle)
    val hardenedBrowserTitle = stringResource(id = R.string.settings_hardened_browser_title)
    val hardenedBrowserSubtitle = stringResource(id = R.string.settings_hardened_browser_description)
    val privacyTweaksTitle = stringResource(id = R.string.web_tweaks_title)
    val privacyTweaksSubtitle = stringResource(id = R.string.web_tweaks_subtitle)
    val storageTitle = stringResource(id = R.string.settings_storage_title)
    val storageSubtitle = stringResource(id = R.string.settings_storage_subtitle)
    val aboutTitle = stringResource(id = R.string.settings_about_title)
    val aboutSubtitle = stringResource(id = R.string.settings_about_subtitle)

    val allEntries =
        remember(
            connectedAccountsCount,
            activeWebSessionsCount,
            themeSummaryLabel,
            readerSummaryLabel,
            blockedTrackersCount,
        ) {
            buildList {
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "accounts",
                                category = SettingsCategory.SocialAndWeb,
                                title = accountsTitle,
                                subtitle = accountsSubtitle,
                                badge = if (connectedAccountsCount > 0) "$connectedAccountsCount platforms" else "Manage",
                                keywords = listOf("rss", "login", "profile", "sync"),
                            ),
                        icon = FontAwesomeIcons.Solid.CircleUser,
                        iconColor = ThemeIconData.Color.ImperialMagenta,
                        onClick = toAccounts,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "live_web_sessions",
                                category = SettingsCategory.SocialAndWeb,
                                title = "Live Web Profiles & Sessions",
                                subtitle = "Isolated WebView sandboxes for X, Instagram, LinkedIn & Reddit",
                                badge = "$activeWebSessionsCount/$webPlatformCount signed in",
                                keywords =
                                    listOf(
                                        "instagram",
                                        "linkedin",
                                        "x",
                                        "twitter",
                                        "reddit",
                                        "cookies",
                                        "logout",
                                        "clear session",
                                        "webview",
                                    ),
                            ),
                        icon = FontAwesomeIcons.Solid.Globe,
                        iconColor = ThemeIconData.Color.SapphireBlue,
                        onClick = { showLiveWebSheet = true },
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "behavior",
                                category = SettingsCategory.SocialAndWeb,
                                title = behaviorTitle,
                                subtitle = behaviorSubtitle,
                                keywords = listOf("auto refresh", "launch", "links", "in-app browser", "timeline"),
                            ),
                        icon = FontAwesomeIcons.Solid.Sliders,
                        iconColor = ThemeIconData.Color.SapphireBlue,
                        onClick = toBehavior,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "local_filter",
                                category = SettingsCategory.SocialAndWeb,
                                title = localFilterTitle,
                                subtitle = localFilterSubtitle,
                                keywords = listOf("mute", "keywords", "block words", "filter", "hide posts"),
                            ),
                        icon = FontAwesomeIcons.Solid.Filter,
                        iconColor = ThemeIconData.Color.BurntUmber,
                        onClick = toLocalFilter,
                    ),
                )

                // Appearance & Reader consolidated studio + direct tab jumps
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "appearance_studio",
                                category = SettingsCategory.AppearanceAndReader,
                                title = "Appearance & Reader Studio",
                                subtitle = "Unified studio for $themeTitle, $layoutTitle, $displayTitle, $mediaTitle & Kindle Reader",
                                badge = themeSummaryLabel,
                                keywords =
                                    listOf(
                                        "dark mode",
                                        "light mode",
                                        "oled",
                                        "pure black",
                                        "dynamic color",
                                        "font size",
                                        "avatar",
                                        "reader",
                                        "kindle",
                                        "sepia",
                                        "serif",
                                        "pages",
                                    ),
                            ),
                        icon = FontAwesomeIcons.Solid.Palette,
                        iconColor = ThemeIconData.Color.RoyalPurple,
                        onClick = toAppearanceTheme,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "appearance_layout",
                                category = SettingsCategory.AppearanceAndReader,
                                title = layoutTitle,
                                subtitle = "Navigation dock style, scroll behavior, haptics, swipe actions & post action buttons",
                                keywords =
                                    listOf(
                                        "bottom bar",
                                        "dock",
                                        "floating",
                                        "minimize",
                                        "hide on scroll",
                                        "haptics",
                                        "swipe",
                                        "deck",
                                    ),
                            ),
                        icon = FontAwesomeIcons.Solid.TableList,
                        iconColor = ThemeIconData.Color.SapphireBlue,
                        onClick = toAppearanceLayout,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "appearance_display_media",
                                category = SettingsCategory.AppearanceAndReader,
                                title = "$displayTitle & $mediaTitle",
                                subtitle = "Timestamps, platform logos, link previews, lite mode, video autoplay & download folder",
                                keywords =
                                    listOf(
                                        "media",
                                        "video",
                                        "autoplay",
                                        "lite mode",
                                        "downloads",
                                        "link preview",
                                        "timestamp",
                                        "sensitive",
                                    ),
                            ),
                        icon = FontAwesomeIcons.Solid.PhotoFilm,
                        iconColor = ThemeIconData.Color.ForestGreen,
                        onClick = toAppearanceMedia,
                    ),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(
                        SettingsDestinationEntry(
                            hubItem =
                                SettingsHubItem(
                                    id = "app_language",
                                    category = SettingsCategory.AppearanceAndReader,
                                    title = languageTitle,
                                    subtitle = languageSubtitle,
                                    keywords = listOf("locale", "language", "regional"),
                                ),
                            icon = FontAwesomeIcons.Solid.Language,
                            iconColor = ThemeIconData.Color.SapphireBlue,
                            onClick = {
                                runCatching {
                                    val intent =
                                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                            data = "package:${BuildConfig.APPLICATION_ID}".toUri()
                                        }
                                    context.startActivity(intent)
                                }
                            },
                        ),
                    )
                }

                // Intelligence & Language
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "ai_config",
                                category = SettingsCategory.Intelligence,
                                title = aiTitle,
                                subtitle = aiSubtitle,
                                badge = "Gemma On-Device",
                                keywords = listOf("gemma", "ai", "smart reply", "omni scribe", "tldr", "summary", "carousel", "llm"),
                            ),
                        icon = FontAwesomeIcons.Solid.Robot,
                        iconColor = ThemeIconData.Color.ForestGreen,
                        onClick = toAiConfig,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "translation",
                                category = SettingsCategory.Intelligence,
                                title = translationTitle,
                                subtitle = translationSubtitle,
                                keywords = listOf("translate", "language", "auto translate", "rss translate"),
                            ),
                        icon = FontAwesomeIcons.Solid.Language,
                        iconColor = ThemeIconData.Color.SapphireBlue,
                        onClick = toTranslationConfig,
                    ),
                )

                // Privacy, Storage & System
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "privacy",
                                category = SettingsCategory.PrivacyAndData,
                                title = privacyTitle,
                                subtitle = privacySubtitle,
                                badge = if (blockedTrackersCount > 0) "$blockedTrackersCount blocked" else "Zero-Trust",
                                keywords =
                                    listOf(
                                        "privacy",
                                        "trackers",
                                        "telemetry",
                                        "blocklist",
                                        "traffic log",
                                        "app lock",
                                        "fingerprint",
                                        "benchmark",
                                    ),
                            ),
                        icon = FontAwesomeIcons.Solid.Lock,
                        iconColor = ThemeIconData.Color.ForestGreen,
                        onClick = toPrivacy,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "hardened_browser",
                                category = SettingsCategory.PrivacyAndData,
                                title = hardenedBrowserTitle,
                                subtitle = hardenedBrowserSubtitle,
                                keywords = listOf("browser", "sandbox", "webview", "ephemeral"),
                            ),
                        icon = FontAwesomeIcons.Solid.Newspaper,
                        iconColor = ThemeIconData.Color.SapphireBlue,
                        onClick = toHardenedBrowser,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "web_tweaks",
                                category = SettingsCategory.PrivacyAndData,
                                title = privacyTweaksTitle,
                                subtitle = privacyTweaksSubtitle,
                                keywords = listOf("tweaks", "ads", "trackers", "strict", "autoplay", "gpc", "screenshot", "clean links"),
                            ),
                        icon = FontAwesomeIcons.Solid.ShieldHalved,
                        iconColor = ThemeIconData.Color.ForestGreen,
                        onClick = toPrivacyTweaks,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "storage",
                                category = SettingsCategory.PrivacyAndData,
                                title = storageTitle,
                                subtitle = storageSubtitle,
                                keywords = listOf("cache", "storage", "database", "clear cache", "logs", "offline", "reading list"),
                            ),
                        icon = FontAwesomeIcons.Solid.Database,
                        iconColor = ThemeIconData.Color.DarkAmber,
                        onClick = toStorage,
                    ),
                )
                if (BuildConfig.DEBUG) {
                    add(
                        SettingsDestinationEntry(
                            hubItem =
                                SettingsHubItem(
                                    id = "color_space",
                                    category = SettingsCategory.PrivacyAndData,
                                    title = "Color Space Diagnostics",
                                    subtitle = "Inspect active Material 3 color tokens and palette roles",
                                    badge = "Debug",
                                    keywords = listOf("debug", "palette", "color space", "tokens"),
                                ),
                            icon = FontAwesomeIcons.Solid.CircleInfo,
                            iconColor = ThemeIconData.Color.CharcoalGrey,
                            onClick = toColorSpace,
                        ),
                    )
                }
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "about",
                                category = SettingsCategory.PrivacyAndData,
                                title = aboutTitle,
                                subtitle = aboutSubtitle,
                                badge = "v${BuildConfig.VERSION_NAME}",
                                keywords = listOf("version", "about", "crux", "licenses", "build"),
                            ),
                        icon = FontAwesomeIcons.Solid.CircleInfo,
                        iconColor = ThemeIconData.Color.CharcoalGrey,
                        onClick = toAbout,
                    ),
                )
            }
        }

    val matchingHubIds =
        remember(allEntries, searchQuery) {
            filterSettingsItems(allEntries.map { it.hubItem }, searchQuery)
                .map { it.id }
                .toSet()
        }
    val filteredEntries =
        remember(allEntries, matchingHubIds) {
            allEntries.filter { it.hubItem.id in matchingHubIds }
        }

    if (showLiveWebSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLiveWebSheet = false },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenHorizontalPadding)
                        .padding(bottom = 28.dp),
            ) {
                LiveWebProfilesSandboxCard(
                    onOpenHardenedBrowser = {
                        showLiveWebSheet = false
                        toHardenedBrowser()
                    },
                )
            }
        }
    }

    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_title))
                },
                subtitle = {
                    Text(text = "Crux Personal Social & Reading Command Center")
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(horizontal = screenHorizontalPadding)
                    .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 1. Instant Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = {
                    Text(
                        text = "Search theme, reader, AI, web sessions, privacy…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Solid.MagnifyingGlass,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            FAIcon(
                                imageVector = FontAwesomeIcons.Solid.Xmark,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    ),
            )

            if (searchQuery.isBlank()) {
                // 2. 2x2 Command Center Hero Bento Grid
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CommandCenterBentoCard(
                            title = "Platforms",
                            metric = "$connectedAccountsCount in use",
                            caption = "X, Instagram, LinkedIn, Reddit, RSS",
                            icon = FontAwesomeIcons.Solid.CircleUser,
                            iconColor = ThemeIconData.Color.ImperialMagenta,
                            onClick = toAccounts,
                            modifier = Modifier.weight(1f),
                        )
                        CommandCenterBentoCard(
                            title = "Live Web Tabs",
                            metric = "$activeWebSessionsCount/$webPlatformCount Signed In",
                            caption = "Isolated • 3P cookies off",
                            icon = FontAwesomeIcons.Solid.Globe,
                            iconColor = ThemeIconData.Color.SapphireBlue,
                            onClick = { showLiveWebSheet = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CommandCenterBentoCard(
                            title = "Studio & Reader",
                            metric = themeSummaryLabel,
                            caption = readerSummaryLabel,
                            icon = FontAwesomeIcons.Solid.BookOpen,
                            iconColor = ThemeIconData.Color.RoyalPurple,
                            onClick = toAppearanceTheme,
                            modifier = Modifier.weight(1f),
                        )
                        CommandCenterBentoCard(
                            title = "AI & Shield",
                            metric = "On-Device Gemma",
                            caption = "$blockedTrackersCount trackers blocked",
                            icon = FontAwesomeIcons.Solid.Robot,
                            iconColor = ThemeIconData.Color.ForestGreen,
                            onClick = toAiConfig,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Calm mode: the paper-like reading look that makes feeds less compelling.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalmToggle(
                        icon = FontAwesomeIcons.Solid.Leaf,
                        title = stringResource(R.string.crux_grayscale_title),
                        body = stringResource(R.string.crux_grayscale_description),
                        checked = globalAppearance.grayscale,
                        onCheckedChange = { appearanceState.update(AppearanceKeys.Grayscale, it) },
                    )
                    CalmToggle(
                        icon = FontAwesomeIcons.Solid.Feather,
                        title = stringResource(R.string.crux_paper_tint_title),
                        body = stringResource(R.string.crux_paper_tint_description),
                        checked = globalAppearance.paperTint,
                        onCheckedChange = { appearanceState.update(AppearanceKeys.PaperTint, it) },
                    )
                    TextButton(onClick = { connections.resetOnboarding() }) {
                        Text(stringResource(R.string.crux_replay_welcome))
                    }
                }

                // 3. Inline Quick Controls Card (1-Tap Everyday Adjustments)
                QuickControlsCard(
                    selectedTheme = globalAppearance.theme,
                    pureColorMode = globalAppearance.pureColorMode,
                    dynamicTheme = globalAppearance.dynamicTheme,
                    bottomBarBehavior = globalAppearance.bottomBarBehavior,
                    readerMode = reader.mode,
                    readerTheme = reader.theme,
                    readerFont = reader.font,
                    onSelectTheme = { appearanceState.update(AppearanceKeys.Theme, it) },
                    onTogglePureColor = {
                        appearanceState.update(AppearanceKeys.PureColorMode, !globalAppearance.pureColorMode)
                    },
                    onToggleDynamicTheme = {
                        appearanceState.update(AppearanceKeys.DynamicTheme, !globalAppearance.dynamicTheme)
                    },
                    onSelectBottomBarBehavior = {
                        appearanceState.update(AppearanceKeys.BottomBarBehavior, it)
                    },
                    onSelectReaderMode = { readerState.update(reader.copy(mode = it)) },
                    onSelectReaderTheme = { readerState.update(reader.copy(theme = it)) },
                    onSelectReaderFont = { readerState.update(reader.copy(font = it)) },
                )
            }

            // 4. Categorized Settings Sections (or Filtered Search Results)
            if (filteredEntries.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No settings matched \"$searchQuery\"",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "Try searching for theme, reader, sepia, cookies, gemma, or cache.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { searchQuery = "" }) {
                            Text(text = "Reset search")
                        }
                    }
                }
            } else {
                SettingsCategory.entries.forEach { category ->
                    val categoryItems = filteredEntries.filter { it.hubItem.category == category }
                    if (categoryItems.isNotEmpty()) {
                        SettingsCategoryGroup(
                            category = category,
                            entries = categoryItems,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCenterBentoCard(
    title: String,
    metric: String,
    caption: String,
    icon: ImageVector,
    iconColor: ThemeIconData.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemedIcon(
                    imageVector = icon,
                    contentDescription = null,
                    color = iconColor,
                )
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = metric,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickControlsCard(
    selectedTheme: Theme,
    pureColorMode: Boolean,
    dynamicTheme: Boolean,
    bottomBarBehavior: BottomBarBehavior,
    readerMode: ReaderMode,
    readerTheme: ReaderTheme,
    readerFont: ReaderFont,
    onSelectTheme: (Theme) -> Unit,
    onTogglePureColor: () -> Unit,
    onToggleDynamicTheme: () -> Unit,
    onSelectBottomBarBehavior: (BottomBarBehavior) -> Unit,
    onSelectReaderMode: (ReaderMode) -> Unit,
    onSelectReaderTheme: (ReaderTheme) -> Unit,
    onSelectReaderFont: (ReaderFont) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Quick Controls",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "1-tap adjustments",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Row 1: App Theme & OLED
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedTheme == Theme.LIGHT,
                        onClick = { onSelectTheme(Theme.LIGHT) },
                        label = { Text(stringResource(R.string.settings_appearance_theme_light)) },
                    )
                    FilterChip(
                        selected = selectedTheme == Theme.SYSTEM,
                        onClick = { onSelectTheme(Theme.SYSTEM) },
                        label = { Text(stringResource(R.string.settings_appearance_theme_auto)) },
                    )
                    FilterChip(
                        selected = selectedTheme == Theme.DARK,
                        onClick = { onSelectTheme(Theme.DARK) },
                        label = { Text(stringResource(R.string.settings_appearance_theme_dark)) },
                    )
                    FilterChip(
                        selected = pureColorMode,
                        onClick = onTogglePureColor,
                        label = { Text("OLED Black") },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        FilterChip(
                            selected = dynamicTheme,
                            onClick = onToggleDynamicTheme,
                            label = { Text("Dynamic") },
                        )
                    }
                }
            }

            // Row 2: Kindle Reader Defaults
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Kindle Reader Defaults",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderMode.entries.forEach { mode ->
                        FilterChip(
                            selected = readerMode == mode,
                            onClick = { onSelectReaderMode(mode) },
                            label = { Text(stringResource(mode.labelRes)) },
                        )
                    }
                    ReaderTheme.entries.forEach { paper ->
                        FilterChip(
                            selected = readerTheme == paper,
                            onClick = { onSelectReaderTheme(paper) },
                            label = { Text(stringResource(paper.labelRes)) },
                        )
                    }
                    ReaderFont.entries.forEach { font ->
                        FilterChip(
                            selected = readerFont == font,
                            onClick = { onSelectReaderFont(font) },
                            label = { Text(stringResource(font.labelRes)) },
                        )
                    }
                }
            }

            // Row 3: Navigation Dock Behavior
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Navigation Dock",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = bottomBarBehavior == BottomBarBehavior.AlwaysShow,
                        onClick = { onSelectBottomBarBehavior(BottomBarBehavior.AlwaysShow) },
                        label = { Text(stringResource(R.string.settings_appearance_bottombar_behavior_fixed)) },
                    )
                    FilterChip(
                        selected = bottomBarBehavior == BottomBarBehavior.MinimizeOnScroll,
                        onClick = { onSelectBottomBarBehavior(BottomBarBehavior.MinimizeOnScroll) },
                        label = { Text(stringResource(R.string.settings_appearance_bottombar_behavior_minimize_on_scroll)) },
                    )
                    FilterChip(
                        selected = bottomBarBehavior == BottomBarBehavior.HideOnScroll,
                        onClick = { onSelectBottomBarBehavior(BottomBarBehavior.HideOnScroll) },
                        label = { Text(stringResource(R.string.settings_appearance_bottombar_behavior_hide_on_scroll)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsCategoryGroup(
    category: SettingsCategory,
    entries: List<SettingsDestinationEntry>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = category.title.uppercase(),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            entries.forEachIndexed { index, entry ->
                val shapes =
                    when {
                        entries.size == 1 -> ListItemDefaults.single()
                        index == 0 -> ListItemDefaults.first()
                        index == entries.lastIndex -> ListItemDefaults.last()
                        else -> ListItemDefaults.item()
                    }
                SegmentedListItem(
                    onClick = entry.onClick,
                    shapes = shapes,
                    leadingContent = {
                        ThemedIcon(
                            imageVector = entry.icon,
                            contentDescription = entry.hubItem.title,
                            color = entry.iconColor,
                        )
                    },
                    content = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = entry.hubItem.title,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            entry.hubItem.badge?.let { badgeText ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        Text(text = entry.hubItem.subtitle)
                    },
                    trailingContent = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
