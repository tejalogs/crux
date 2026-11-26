package org.crux.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import org.crux.ui.route.Route
import org.crux.ui.screen.crux.CruxPlatform
import org.crux.ui.screen.crux.PlatformBadge
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.PrivacyCheckupItem
import org.crux.ui.screen.home.PrivacyCheckupKind
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.segmentedShapes2

/**
 * Per-site Privacy Checkup: a short list of each site's own privacy settings pages.
 *
 * Crux only navigates. Each row opens the page in that site's own Crux tab, and the person makes
 * the change on the site. Crux never toggles anything inside an account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyCheckupScreen(
    onBack: () -> Unit,
    onOpenRoute: (Route) -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = { Text(text = stringResource(id = R.string.privacy_checkup_title)) },
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
                text = stringResource(id = R.string.privacy_checkup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(id = R.string.privacy_checkup_sign_in_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            checkupPlatforms().forEach { (platform, site) ->
                CheckupSiteCard(
                    platform = platform,
                    site = site,
                    onOpen = { item -> onOpenRoute(privacyCheckupRoute(site, item)) },
                )
            }
        }
    }
}

@Composable
private fun CheckupSiteCard(
    platform: CruxPlatform,
    site: LiveWebSite,
    onOpen: (PrivacyCheckupItem) -> Unit,
) {
    val items = site.privacyCheckup
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlatformBadge(platform = platform, size = 32.dp)
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            items.forEachIndexed { index, item ->
                val openDescription = stringResource(R.string.privacy_checkup_open_cd, platform.displayName)
                SegmentedListItem(
                    onClick = { onOpen(item) },
                    shapes = ListItemDefaults.segmentedShapes2(index = index, count = items.size),
                    content = { Text(text = stringResource(item.kind.titleRes())) },
                    supportingContent = { Text(text = stringResource(item.kind.whyRes())) },
                    trailingContent = {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.ChevronRight,
                            contentDescription = openDescription,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Plain-language limits of a client that shows a signed-in site. Shown on the Privacy screen so the
 * protections listed above it are not read as more than they are.
 */
@Composable
internal fun PrivacyLimitsCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(id = R.string.privacy_limits_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            PRIVACY_LIMIT_LINES.forEach { res ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(id = res),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val PRIVACY_LIMIT_LINES =
    listOf(
        R.string.privacy_limits_telemetry,
        R.string.privacy_limits_ads,
        R.string.privacy_limits_activity,
        R.string.privacy_limits_linking,
        R.string.privacy_limits_sso,
    )

/** Sites in the same order as the rest of Crux, each with its platform for the name and badge. */
internal fun checkupPlatforms(): List<Pair<CruxPlatform, LiveWebSite>> =
    CruxPlatform.entries
        .mapNotNull { platform -> platform.site?.let { platform to it } }
        .filter { (_, site) -> site.privacyCheckup.isNotEmpty() }

/** Opens the settings page in the site's own Crux screen, the same way the AI-training button does. */
internal fun privacyCheckupRoute(
    site: LiveWebSite,
    item: PrivacyCheckupItem,
): Route = Route.LiveWeb(site = site.name, initialUrl = item.url)

@StringRes
internal fun PrivacyCheckupKind.titleRes(): Int =
    when (this) {
        PrivacyCheckupKind.AiTraining -> R.string.privacy_checkup_ai_training
        PrivacyCheckupKind.AdPersonalization -> R.string.privacy_checkup_ad_personalization
        PrivacyCheckupKind.OffSiteActivity -> R.string.privacy_checkup_off_site_activity
        PrivacyCheckupKind.PartnerSharing -> R.string.privacy_checkup_partner_sharing
        PrivacyCheckupKind.Location -> R.string.privacy_checkup_location
        PrivacyCheckupKind.ContactsAndDiscoverability -> R.string.privacy_checkup_contacts
        PrivacyCheckupKind.InformationAndPermissions -> R.string.privacy_checkup_info_permissions
        PrivacyCheckupKind.AccountPrivacy -> R.string.privacy_checkup_account_privacy
        PrivacyCheckupKind.PrivacyAndReach -> R.string.privacy_checkup_privacy_reach
        PrivacyCheckupKind.DownloadData -> R.string.privacy_checkup_download_data
    }

@StringRes
internal fun PrivacyCheckupKind.whyRes(): Int =
    when (this) {
        PrivacyCheckupKind.AiTraining -> R.string.privacy_checkup_ai_training_why
        PrivacyCheckupKind.AdPersonalization -> R.string.privacy_checkup_ad_personalization_why
        PrivacyCheckupKind.OffSiteActivity -> R.string.privacy_checkup_off_site_activity_why
        PrivacyCheckupKind.PartnerSharing -> R.string.privacy_checkup_partner_sharing_why
        PrivacyCheckupKind.Location -> R.string.privacy_checkup_location_why
        PrivacyCheckupKind.ContactsAndDiscoverability -> R.string.privacy_checkup_contacts_why
        PrivacyCheckupKind.InformationAndPermissions -> R.string.privacy_checkup_info_permissions_why
        PrivacyCheckupKind.AccountPrivacy -> R.string.privacy_checkup_account_privacy_why
        PrivacyCheckupKind.PrivacyAndReach -> R.string.privacy_checkup_privacy_reach_why
        PrivacyCheckupKind.DownloadData -> R.string.privacy_checkup_download_data_why
    }
