package org.crux.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.crux.R
import org.crux.data.privacy.PrivacyCategory
import org.crux.data.privacy.PrivacyLog
import org.crux.data.privacy.PrivacyLogEntry
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.FAIcon
import org.crux.ui.screen.home.ContactedHost
import org.crux.ui.screen.home.LiveWebContactLog
import org.crux.ui.screen.home.LiveWebSite
import org.crux.ui.screen.home.MAX_CONTACTED_HOSTS_PER_SITE
import org.crux.ui.screen.home.SiteContacts
import org.crux.ui.screen.home.label
import org.crux.ui.theme.item
import org.crux.ui.theme.last
import org.crux.ui.theme.screenHorizontalPadding

/**
 * On-device inspector of every request Crux made, and of the hosts each site tab's page
 * contacted today ("Contacted today", in memory only).
 *
 * The point of this screen is legibility: the user should be able to see which hosts their
 * accounts talk to and what the client removed, without trusting a marketing claim. Only
 * parameter *names* are ever recorded, so this log cannot itself become a store of tracking ids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacyTrafficLogScreen(onBack: () -> Unit) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val entries by PrivacyLog.get().entries.collectAsState()
    // The web tabs' hosts change on every request, so the tally is re-read every few seconds
    // while this screen is open, and at once when a new host appears.
    val contactsVersion by LiveWebContactLog.changes.collectAsState()
    // Filled off the main thread by the effect below; sorting up to 300 hosts per site does not
    // belong in the first frame.
    var contacts by remember { mutableStateOf(emptyList<SiteContacts>()) }
    var expandedSites by remember { mutableStateOf(emptySet<LiveWebSite>()) }
    LaunchedEffect(contactsVersion) {
        while (true) {
            contacts = withContext(Dispatchers.Default) { LiveWebContactLog.snapshot() }
            delay(CONTACTS_REFRESH_MS)
        }
    }

    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_privacy_traffic_log_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                PrivacyLog.get().clear()
                            },
                        ) {
                            FAIcon(
                                FontAwesomeIcons.Solid.Trash,
                                contentDescription = stringResource(id = R.string.settings_app_logging_clear),
                            )
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        if (entries.isEmpty() && contacts.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.settings_privacy_traffic_log_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@CruxScaffold
        }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (contacts.isNotEmpty()) {
                contactedTodaySection(
                    contacts = contacts,
                    expandedSites = expandedSites,
                    onToggleSite = { site ->
                        expandedSites = if (site in expandedSites) expandedSites - site else expandedSites + site
                    },
                    onClear = {
                        LiveWebContactLog.clear()
                        contacts = emptyList()
                    },
                )
            }
            if (entries.isNotEmpty()) {
                item(key = "summary") {
                    val blocked = entries.count { it.blocked }
                    val stripped = entries.sumOf { it.strippedParameters.size }
                    Column(
                        modifier =
                            Modifier.padding(
                                horizontal = screenHorizontalPadding,
                                vertical = 12.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_privacy_traffic_log_summary, entries.size),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text =
                                stringResource(
                                    id = R.string.settings_privacy_traffic_log_counts,
                                    blocked,
                                    stripped,
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(entries.asReversed(), key = { it.sequence }) { entry ->
                    PrivacyLogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun PrivacyLogRow(entry: PrivacyLogEntry) {
    SegmentedListItem(
        onClick = {},
        enabled = false,
        shapes = ListItemDefaults.item(),
        content = {
            Text(
                text = entry.host,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text =
                    buildString {
                        append(entry.method)
                        append(' ')
                        append(entry.path.take(72))
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.strippedParameters.isNotEmpty()) {
                Text(
                    text =
                        stringResource(
                            id = R.string.settings_privacy_traffic_log_stripped,
                            entry.strippedParameters.joinToString(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.category.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = entry.category.color(MaterialTheme.colorScheme),
                )
                if (entry.blocked) {
                    Text(
                        text = stringResource(id = R.string.settings_privacy_traffic_log_blocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (entry.status != null) {
                    Text(
                        text = entry.status.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private fun PrivacyCategory.label(): String =
    when (this) {
        PrivacyCategory.Content -> "content"
        PrivacyCategory.Media -> "media"
        PrivacyCategory.Telemetry -> "telemetry"
        PrivacyCategory.Profiling -> "profiling"
        PrivacyCategory.Auth -> "auth"
        PrivacyCategory.Unknown -> "other"
    }

private fun PrivacyCategory.color(scheme: androidx.compose.material3.ColorScheme) =
    when (this) {
        PrivacyCategory.Telemetry, PrivacyCategory.Profiling -> scheme.error
        PrivacyCategory.Auth -> scheme.tertiary
        PrivacyCategory.Media -> scheme.secondary
        PrivacyCategory.Content -> scheme.primary
        PrivacyCategory.Unknown -> scheme.onSurfaceVariant
    }

private const val CONTACTS_REFRESH_MS = 2_000L

/** Hosts shown per site before "Show all". */
private const val CONTACTS_COLLAPSED_ROWS = 12

/**
 * "Contacted today": for each site tab, every host its page (and its service workers and popups)
 * reached since midnight, third parties first. Hosts only: no path or query is ever kept.
 */
private fun LazyListScope.contactedTodaySection(
    contacts: List<SiteContacts>,
    expandedSites: Set<LiveWebSite>,
    onToggleSite: (LiveWebSite) -> Unit,
    onClear: () -> Unit,
) {
    item(key = "contacted_header") {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = screenHorizontalPadding, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_privacy_contacted_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(id = R.string.settings_privacy_contacted_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClear) {
                Text(text = stringResource(id = R.string.settings_privacy_contacted_clear))
            }
        }
    }
    contacts.forEach { siteContacts ->
        val site = siteContacts.site
        val expanded = site in expandedSites
        val shown = if (expanded) siteContacts.hosts else siteContacts.hosts.take(CONTACTS_COLLAPSED_ROWS)
        item(key = "contacted_site_${site.name}") {
            Column(
                modifier = Modifier.padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = site.label, style = MaterialTheme.typography.labelLarge)
                Text(
                    text =
                        stringResource(
                            id = R.string.settings_privacy_contacted_site_summary,
                            siteContacts.hosts.size,
                            siteContacts.thirdPartyHosts,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shown, key = { "contacted_${site.name}_${it.host}" }) { host ->
            ContactedHostRow(host)
        }
        if (siteContacts.hosts.size > CONTACTS_COLLAPSED_ROWS) {
            item(key = "contacted_toggle_${site.name}") {
                TextButton(
                    onClick = { onToggleSite(site) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        text =
                            if (expanded) {
                                stringResource(id = R.string.settings_privacy_contacted_show_less)
                            } else {
                                stringResource(id = R.string.settings_privacy_contacted_show_all, siteContacts.hosts.size)
                            },
                    )
                }
            }
        }
        if (siteContacts.droppedRequests > 0) {
            item(key = "contacted_dropped_${site.name}") {
                Text(
                    text =
                        stringResource(
                            id = R.string.settings_privacy_contacted_overflow,
                            siteContacts.droppedRequests,
                            MAX_CONTACTED_HOSTS_PER_SITE,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = screenHorizontalPadding, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ContactedHostRow(host: ContactedHost) {
    SegmentedListItem(
        onClick = {},
        enabled = false,
        shapes = ListItemDefaults.item(),
        content = {
            Text(
                text = host.host,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(id = R.string.settings_privacy_contacted_requests, host.requests),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text =
                        stringResource(
                            id =
                                if (host.thirdParty) {
                                    R.string.settings_privacy_contacted_third_party
                                } else {
                                    R.string.settings_privacy_contacted_first_party
                                },
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (host.thirdParty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (host.blocked > 0) {
                    Text(
                        text = stringResource(id = R.string.settings_privacy_traffic_log_blocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}
