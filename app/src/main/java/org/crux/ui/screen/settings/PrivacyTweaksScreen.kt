package org.crux.ui.screen.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ChevronRight
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.PlatformBadge
import org.crux.ui.screen.home.LiveWebPrivacyCounters
import org.crux.ui.screen.home.LiveWebPrivacyStore
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.LocalLiveWebPages
import org.crux.ui.screen.home.ProtectionLevel
import org.crux.ui.screen.home.SiteTweak
import org.crux.ui.screen.home.WebPrivacySettings
import org.crux.ui.screen.home.clearSession
import org.crux.ui.screen.home.multiProfileSupported
import org.crux.ui.theme.screenHorizontalPadding

@StringRes
private fun SiteTweak.labelRes(): Int =
    when (this) {
        SiteTweak.InstagramAds -> R.string.web_tweak_instagram_ads
        SiteTweak.InstagramSuggested -> R.string.web_tweak_instagram_suggested
        SiteTweak.InstagramAppPrompts -> R.string.web_tweak_instagram_app_prompts
        SiteTweak.InstagramReels -> R.string.web_tweak_instagram_reels
        SiteTweak.InstagramExplore -> R.string.web_tweak_instagram_explore
        SiteTweak.InstagramAi -> R.string.web_tweak_instagram_ai
        SiteTweak.InstagramStories -> R.string.web_tweak_instagram_stories
        SiteTweak.XPromoted -> R.string.web_tweak_x_promoted
        SiteTweak.XWhoToFollow -> R.string.web_tweak_x_who_to_follow
        SiteTweak.XTrends -> R.string.web_tweak_x_trends
        SiteTweak.XPremiumGrok -> R.string.web_tweak_x_premium_grok
        SiteTweak.XSpaces -> R.string.web_tweak_x_spaces
        SiteTweak.XAppPrompts -> R.string.web_tweak_x_app_prompts
        SiteTweak.XHideForYou -> R.string.web_tweak_x_hide_for_you
        SiteTweak.LinkedInPromoted -> R.string.web_tweak_linkedin_promoted
        SiteTweak.LinkedInSuggested -> R.string.web_tweak_linkedin_suggested
        SiteTweak.LinkedInPremium -> R.string.web_tweak_linkedin_premium
        SiteTweak.LinkedInAppPrompts -> R.string.web_tweak_linkedin_app_prompts
        SiteTweak.LinkedInNews -> R.string.web_tweak_linkedin_news
        SiteTweak.LinkedInAi -> R.string.web_tweak_linkedin_ai
        SiteTweak.LinkedInJobsAndGames -> R.string.web_tweak_linkedin_games
        SiteTweak.RedditPromoted -> R.string.web_tweak_reddit_promoted
        SiteTweak.RedditNags -> R.string.web_tweak_reddit_nags
        SiteTweak.RedditRecommended -> R.string.web_tweak_reddit_recommended
        SiteTweak.RedditAi -> R.string.web_tweak_reddit_ai
        SiteTweak.RedditTrending -> R.string.web_tweak_reddit_trending
        SiteTweak.RedditUnblurNsfw -> R.string.web_tweak_reddit_unblur_nsfw
    }

@StringRes
private fun SiteTweak.descriptionRes(): Int? =
    when (this) {
        SiteTweak.XHideForYou -> R.string.web_tweak_x_hide_for_you_description
        SiteTweak.RedditUnblurNsfw -> R.string.web_tweak_reddit_unblur_nsfw_description
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyTweaksScreen(
    onBack: () -> Unit,
    onOpenSiteUrl: (LiveWebSite, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val store = remember(context) { LiveWebPrivacyStore.get(context) }
    val settings by store.settings.collectAsState()
    val counters = remember(context) { LiveWebPrivacyCounters.get(context) }
    val blockedBySite by counters.blockedRequestsBySite.collectAsState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = { Text(text = stringResource(id = R.string.web_tweaks_title)) },
                navigationIcon = { BackButton(onBack = onBack) },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = screenHorizontalPadding)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.web_tweaks_reload_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlobalCard(
                settings = settings,
                onChange = store::update,
                onResetDefaults = store::resetToDefaults,
            )
            CruxPlatform.entries
                .mapNotNull { platform -> platform.site?.let { platform to it } }
                .forEach { (platform, site) ->
                    SiteCard(
                        platform = platform,
                        site = site,
                        settings = settings,
                        blockedCount = blockedBySite[site] ?: 0L,
                        onChange = store::update,
                        onOpenSiteUrl = onOpenSiteUrl,
                    )
                }
        }
    }
}

@Composable
private fun SiteCard(
    platform: CruxPlatform,
    site: LiveWebSite,
    settings: WebPrivacySettings,
    blockedCount: Long,
    onChange: ((WebPrivacySettings) -> WebPrivacySettings) -> Unit,
    onOpenSiteUrl: (LiveWebSite, String) -> Unit,
) {
    val siteSettings = settings.site(site)
    val tweaksForSite = remember(site) { SiteTweak.forSite(site) }
    val enabledCount = tweaksForSite.count { siteSettings.isOn(it) }
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(targetValue = if (expanded) 90f else 0f, label = "siteCardChevron")
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlatformBadge(platform = platform, size = 36.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platform.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val levelLabel =
                        stringResource(
                            if (siteSettings.protection == ProtectionLevel.Strict) R.string.web_tweaks_strict else R.string.web_tweaks_standard,
                        )
                    val summary =
                        if (blockedCount > 0L) {
                            stringResource(
                                R.string.web_tweaks_site_summary_blocked,
                                levelLabel,
                                enabledCount,
                                tweaksForSite.size,
                                blockedCount.toInt(),
                            )
                        } else {
                            stringResource(
                                R.string.web_tweaks_site_summary,
                                levelLabel,
                                enabledCount,
                                tweaksForSite.size,
                            )
                        }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SectionLabel(R.string.web_tweaks_protection)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProtectionLevel.entries.forEach { level ->
                            FilterChip(
                                selected = siteSettings.protection == level,
                                onClick = { onChange { it.updateSite(site) { s -> s.withProtection(level) } } },
                                label = {
                                    Text(
                                        text =
                                            stringResource(
                                                if (level == ProtectionLevel.Standard) R.string.web_tweaks_standard else R.string.web_tweaks_strict,
                                            ),
                                    )
                                },
                            )
                        }
                    }
                    val strict = siteSettings.protection == ProtectionLevel.Strict
                    Text(
                        text =
                            stringResource(
                                if (strict) R.string.web_tweaks_strict_description else R.string.web_tweaks_standard_description,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (strict && site.isSignedInAccount) {
                        Text(
                            text = stringResource(R.string.web_tweaks_strict_account_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    SectionLabel(R.string.web_tweaks_focus_header)
                    tweaksForSite.forEach { tweak ->
                        ToggleRow(
                            title = stringResource(tweak.labelRes()),
                            description = tweak.descriptionRes()?.let { stringResource(it) },
                            checked = siteSettings.isOn(tweak),
                            onCheckedChange = { on -> onChange { it.updateSite(site) { s -> s.withTweak(tweak, on) } } },
                        )
                    }
                    ToggleRow(
                        title = stringResource(R.string.web_tweaks_block_autoplay),
                        description = stringResource(R.string.web_tweaks_block_autoplay_description),
                        checked = siteSettings.blockAutoplay,
                        onCheckedChange = { on -> onChange { it.updateSite(site) { s -> s.withBlockAutoplay(on) } } },
                    )

                    Text(
                        text = stringResource(R.string.web_tweaks_ai_privacy_note, platform.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onOpenSiteUrl(site, site.aiPrivacyUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.web_tweaks_ai_privacy_button))
                    }

                    ClearDataButton(platform = platform, site = site)
                }
            }
        }
    }
}

@Composable
private fun ClearDataButton(
    platform: CruxPlatform,
    site: LiveWebSite,
) {
    val context = LocalContext.current
    val pages = LocalLiveWebPages.current
    var confirming by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.web_tweaks_clear_data), color = MaterialTheme.colorScheme.error)
    }
    if (confirming) {
        val cleared = stringResource(R.string.web_tweaks_cleared, platform.displayName)
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(text = stringResource(R.string.web_tweaks_clear_title, platform.displayName)) },
            text = {
                Text(
                    text =
                        stringResource(
                            if (multiProfileSupported) R.string.web_tweaks_clear_message else R.string.web_tweaks_clear_message_shared,
                            platform.displayName,
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        site.clearSession(context)
                        pages?.clearAndReload(site)
                        Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(text = stringResource(R.string.web_tweaks_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(text = stringResource(R.string.web_tweaks_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun GlobalCard(
    settings: WebPrivacySettings,
    onChange: ((WebPrivacySettings) -> WebPrivacySettings) -> Unit,
    onResetDefaults: () -> Unit,
) {
    val context = LocalContext.current
    val pages = LocalLiveWebPages.current
    var confirmingClearAll by remember { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(R.string.web_tweaks_global_header)
            ToggleRow(
                title = stringResource(R.string.web_tweaks_clean_links),
                description = stringResource(R.string.web_tweaks_clean_links_description),
                checked = settings.cleanOutgoingLinks,
                onCheckedChange = { on -> onChange { it.copy(cleanOutgoingLinks = on) } },
            )
            ToggleRow(
                title = stringResource(R.string.web_tweaks_open_in_reader),
                description = stringResource(R.string.web_tweaks_open_in_reader_description),
                checked = settings.openLinksInReader,
                onCheckedChange = { on -> onChange { it.copy(openLinksInReader = on) } },
            )
            ToggleRow(
                title = stringResource(R.string.web_tweaks_block_third_party_cookies),
                description = stringResource(R.string.web_tweaks_block_third_party_cookies_description),
                checked = settings.blockThirdPartyCookies,
                onCheckedChange = { on -> onChange { it.copy(blockThirdPartyCookies = on) } },
            )
            ToggleRow(
                title = stringResource(R.string.web_tweaks_remember_tab_position),
                description = stringResource(R.string.web_tweaks_remember_tab_position_description),
                checked = settings.rememberTabPosition,
                onCheckedChange = { on -> onChange { it.copy(rememberTabPosition = on) } },
            )
            ToggleRow(
                title = stringResource(R.string.web_tweaks_calm_metrics),
                description = stringResource(R.string.web_tweaks_calm_metrics_description),
                checked = settings.hideEngagementCounts,
                onCheckedChange = { on -> onChange { it.copy(hideEngagementCounts = on) } },
            )
            ToggleRow(
                title = stringResource(R.string.web_tweaks_secure_window),
                description = stringResource(R.string.web_tweaks_secure_window_description),
                checked = settings.secureWindow,
                onCheckedChange = { on -> onChange { it.copy(secureWindow = on) } },
            )
            ToggleRow(
                title = stringResource(R.string.web_tweaks_gpc),
                description = stringResource(R.string.web_tweaks_gpc_description),
                checked = settings.sendGlobalPrivacyControl,
                onCheckedChange = { on -> onChange { it.copy(sendGlobalPrivacyControl = on) } },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmingReset = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.web_tweaks_reset_defaults))
                }
                OutlinedButton(
                    onClick = { confirmingClearAll = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.web_tweaks_clear_all_data),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (confirmingClearAll) {
        val clearedAllMsg = stringResource(R.string.web_tweaks_cleared_all)
        AlertDialog(
            onDismissRequest = { confirmingClearAll = false },
            title = { Text(text = stringResource(R.string.web_tweaks_clear_all_title)) },
            text = { Text(text = stringResource(R.string.web_tweaks_clear_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClearAll = false
                        LiveWebSite.entries.forEach { site ->
                            site.clearSession(context)
                            pages?.clearAndReload(site)
                        }
                        Toast.makeText(context, clearedAllMsg, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(text = stringResource(R.string.web_tweaks_clear_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearAll = false }) {
                    Text(text = stringResource(R.string.web_tweaks_clear_cancel))
                }
            },
        )
    }
    if (confirmingReset) {
        val resetDoneMsg = stringResource(R.string.web_tweaks_reset_done)
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text(text = stringResource(R.string.web_tweaks_reset_title)) },
            text = { Text(text = stringResource(R.string.web_tweaks_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingReset = false
                        onResetDefaults()
                        Toast.makeText(context, resetDoneMsg, Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text(text = stringResource(R.string.web_tweaks_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingReset = false }) {
                    Text(text = stringResource(R.string.web_tweaks_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(
    @StringRes text: Int,
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
