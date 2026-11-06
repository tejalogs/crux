package org.crux.ui.screen.home

/** Third-party ad and analytics hosts refused on any site set to Strict. */
internal val GLOBAL_TRACKER_HOSTS: Set<String> =
    setOf(
        "googletagmanager.com",
        "google-analytics.com",
        "analytics.google.com",
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "scorecardresearch.com",
        "quantserve.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "moatads.com",
        "hotjar.com",
        "bat.bing.com",
        "clarity.ms",
    )

internal fun hostMatchesAny(
    host: String?,
    hosts: Set<String>,
): Boolean = host != null && hosts.any { host == it || host.endsWith(".$it") }

/** Hosts refused at this level. Standard refuses nothing, whatever the site. */
internal fun blockedHostsFor(
    site: LiveWebSite,
    level: ProtectionLevel,
): Set<String> =
    when (level) {
        ProtectionLevel.Standard -> emptySet()
        ProtectionLevel.Strict -> GLOBAL_TRACKER_HOSTS + site.trackerHosts
    }

internal fun shouldBlockHost(
    site: LiveWebSite,
    level: ProtectionLevel,
    host: String?,
): Boolean = hostMatchesAny(host, blockedHostsFor(site, level))
