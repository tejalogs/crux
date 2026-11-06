package org.crux.ui.screen.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.PlatformBadge
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
        SiteTweak.XPromoted -> R.string.web_tweak_x_promoted
        SiteTweak.XWhoToFollow -> R.string.web_tweak_x_who_to_follow
        SiteTweak.XTrends -> R.string.web_tweak_x_trends
        SiteTweak.XPremiumGrok -> R.string.web_tweak_x_premium_grok
        SiteTweak.XAppPrompts -> R.string.web_tweak_x_app_prompts
        SiteTweak.XHideForYou -> R.string.web_tweak_x_hide_for_you
        SiteTweak.LinkedInPromoted -> R.string.web_tweak_linkedin_promoted
        SiteTweak.LinkedInSuggested -> R.string.web_tweak_linkedin_suggested
        SiteTweak.LinkedInPremium -> R.string.web_tweak_linkedin_premium
        SiteTweak.LinkedInAppPrompts -> R.string.web_tweak_linkedin_app_prompts
        SiteTweak.LinkedInNews -> R.string.web_tweak_linkedin_news
        SiteTweak.RedditPromoted -> R.string.web_tweak_reddit_promoted
        SiteTweak.RedditNags -> R.string.web_tweak_reddit_nags
        SiteTweak.RedditRecommended -> R.string.web_tweak_reddit_recommended
    }

@StringRes
private fun SiteTweak.descriptionRes(): Int? =
    when (this) {
        SiteTweak.XHideForYou -> R.string.web_tweak_x_hide_for_you_description
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyTweaksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { LiveWebPrivacyStore.get(context) }
    val settings by store.settings.collectAsState()
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
            CruxPlatform.entries
                .mapNotNull { platform -> platform.site?.let { platform to it } }
                .forEach { (platform, site) ->
                    SiteCard(platform = platform, site = site, settings = settings, onChange = store::update)
                }
            GlobalCard(settings = settings, onChange = store::update)
        }
    }
}

@Composable
private fun SiteCard(
    platform: CruxPlatform,
    site: LiveWebSite,
    settings: WebPrivacySettings,
    onChange: ((WebPrivacySettings) -> WebPrivacySettings) -> Unit,
) {
    val siteSettings = settings.site(site)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlatformBadge(platform = platform, size = 36.dp)
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

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
                text = stringResource(if (strict) R.string.web_tweaks_strict_description else R.string.web_tweaks_standard_description),
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
            SiteTweak.forSite(site).forEach { tweak ->
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

            ClearDataButton(platform = platform, site = site)
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
                        site.clearSession()
                        pages?.reload(site)
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
) {
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
        }
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
        modifier = Modifier.fillMaxWidth(),
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
