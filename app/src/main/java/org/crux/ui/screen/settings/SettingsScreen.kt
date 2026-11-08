package org.crux.ui.screen.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
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
    @StringRes val titleRes: Int,
    /** Fixed words matched by the settings search, independent of the display language. */
    val searchName: String,
) {
    SocialAndWeb(R.string.settings_category_platforms, "platforms browser links"),
    AppearanceAndReader(R.string.settings_category_appearance, "appearance reader"),
    Intelligence(R.string.settings_category_smart, "smart ai translation language"),
    PrivacyAndData(R.string.settings_category_privacy, "privacy storage about"),
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
                append(item.category.searchName).append(' ')
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
    toBehavior: () -> Unit,
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

    val themeSummaryLabel =
        when (globalAppearance.theme) {
            Theme.LIGHT -> stringResource(R.string.settings_appearance_theme_light)
            Theme.SYSTEM -> stringResource(R.string.settings_appearance_theme_auto)
            Theme.DARK -> stringResource(R.string.settings_appearance_theme_dark)
        }

    val platformsTitle = stringResource(id = R.string.settings_accounts_title)
    val platformsSubtitle = stringResource(id = R.string.settings_accounts_subtitle)
    val platformsBadge =
        if (connectedAccountsCount > 0) stringResource(id = R.string.settings_platforms_in_use, connectedAccountsCount) else null
    val behaviorTitle = stringResource(id = R.string.settings_behavior_title)
    val behaviorSubtitle = stringResource(id = R.string.settings_behavior_description)
    val localFilterTitle = stringResource(id = R.string.settings_local_filter_title)
    val localFilterSubtitle = stringResource(id = R.string.settings_local_filter_description)
    val appearanceTitle = stringResource(id = R.string.settings_appearance_hub_title)
    val appearanceSubtitle = stringResource(id = R.string.settings_appearance_hub_subtitle)
    val languageTitle = stringResource(id = R.string.settings_language_title)
    val languageSubtitle = stringResource(id = R.string.settings_language_description)
    val aiTitle = stringResource(id = R.string.settings_ai_config_title)
    val aiSubtitle = stringResource(id = R.string.settings_ai_config_description)
    val translationTitle = stringResource(id = R.string.settings_translation_title)
    val translationSubtitle = stringResource(id = R.string.settings_translation_description)
    val privacyTitle = stringResource(id = R.string.privacy_hub_title)
    val privacySubtitle = stringResource(id = R.string.privacy_hub_subtitle)
    val privacyBadge =
        if (blockedTrackersCount > 0) stringResource(id = R.string.settings_privacy_blocked_badge, blockedTrackersCount) else null
    val storageTitle = stringResource(id = R.string.settings_storage_title)
    val storageSubtitle = stringResource(id = R.string.settings_storage_subtitle)
    val colorSpaceTitle = stringResource(id = R.string.settings_color_space_title)
    val colorSpaceSubtitle = stringResource(id = R.string.settings_color_space_subtitle)
    val debugBadge = stringResource(id = R.string.settings_badge_debug)
    val aboutTitle = stringResource(id = R.string.settings_about_title)
    val aboutSubtitle = stringResource(id = R.string.settings_about_subtitle)

    val allEntries =
        remember(
            platformsBadge,
            themeSummaryLabel,
            privacyBadge,
            platformsTitle,
            aiTitle,
        ) {
            buildList {
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "platforms",
                                category = SettingsCategory.SocialAndWeb,
                                title = platformsTitle,
                                subtitle = platformsSubtitle,
                                badge = platformsBadge,
                                keywords =
                                    listOf(
                                        "rss",
                                        "feeds",
                                        "login",
                                        "sign in",
                                        "instagram",
                                        "linkedin",
                                        "x",
                                        "twitter",
                                        "reddit",
                                        "sessions",
                                    ),
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
                                id = "behavior",
                                category = SettingsCategory.SocialAndWeb,
                                title = behaviorTitle,
                                subtitle = behaviorSubtitle,
                                keywords = listOf("browser", "links", "private browser", "auto refresh", "launch", "default browser"),
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
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "appearance",
                                category = SettingsCategory.AppearanceAndReader,
                                title = appearanceTitle,
                                subtitle = appearanceSubtitle,
                                badge = themeSummaryLabel,
                                keywords =
                                    listOf(
                                        "dark mode",
                                        "light mode",
                                        "oled",
                                        "pure black",
                                        "dynamic color",
                                        "font size",
                                        "reader",
                                        "sepia",
                                        "serif",
                                        "pages",
                                        "dock",
                                        "haptics",
                                        "media",
                                        "timestamps",
                                    ),
                            ),
                        icon = FontAwesomeIcons.Solid.Palette,
                        iconColor = ThemeIconData.Color.RoyalPurple,
                        onClick = toAppearanceTheme,
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
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "ai_config",
                                category = SettingsCategory.Intelligence,
                                title = aiTitle,
                                subtitle = aiSubtitle,
                                keywords = listOf("ai", "smart reply", "tldr", "summary", "carousel", "llm", "server", "api key"),
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
                                keywords = listOf("translate", "language", "auto translate", "deepl", "google", "libretranslate"),
                            ),
                        icon = FontAwesomeIcons.Solid.Language,
                        iconColor = ThemeIconData.Color.SapphireBlue,
                        onClick = toTranslationConfig,
                    ),
                )
                add(
                    SettingsDestinationEntry(
                        hubItem =
                            SettingsHubItem(
                                id = "privacy",
                                category = SettingsCategory.PrivacyAndData,
                                title = privacyTitle,
                                subtitle = privacySubtitle,
                                badge = privacyBadge,
                                keywords =
                                    listOf(
                                        "privacy",
                                        "trackers",
                                        "telemetry",
                                        "blocklist",
                                        "traffic log",
                                        "app lock",
                                        "fingerprint",
                                        "tweaks",
                                        "protection",
                                        "cookies",
                                        "clear session",
                                        "screenshot",
                                        "clean links",
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
                                id = "storage",
                                category = SettingsCategory.PrivacyAndData,
                                title = storageTitle,
                                subtitle = storageSubtitle,
                                keywords =
                                    listOf(
                                        "cache",
                                        "storage",
                                        "database",
                                        "clear cache",
                                        "logs",
                                        "offline",
                                        "reading list",
                                        "export",
                                    ),
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
                                    title = colorSpaceTitle,
                                    subtitle = colorSpaceSubtitle,
                                    badge = debugBadge,
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
                                keywords = listOf("version", "about", "crux", "licenses", "license", "source", "build", "report issue"),
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

    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_title))
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.settings_search_hint),
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
                                contentDescription = stringResource(id = R.string.settings_search_clear),
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
                            text = stringResource(id = R.string.settings_search_empty, searchQuery),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = stringResource(id = R.string.settings_search_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { searchQuery = "" }) {
                            Text(text = stringResource(id = R.string.settings_search_reset))
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
                    text = stringResource(id = R.string.settings_quick_controls),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(id = R.string.settings_quick_controls_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Row 1: App Theme & OLED
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_quick_app_theme),
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
                        label = { Text(stringResource(id = R.string.settings_quick_oled)) },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        FilterChip(
                            selected = dynamicTheme,
                            onClick = onToggleDynamicTheme,
                            label = { Text(stringResource(id = R.string.settings_quick_dynamic)) },
                        )
                    }
                }
            }

            // Row 2: Kindle Reader Defaults
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_quick_reader),
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
                    text = stringResource(id = R.string.settings_quick_dock),
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
            text = stringResource(id = category.titleRes).uppercase(),
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
