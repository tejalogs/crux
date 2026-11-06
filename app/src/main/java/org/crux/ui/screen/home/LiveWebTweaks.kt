package org.crux.ui.screen.home

/**
 * How aggressively a site's WebView is locked down.
 *
 * [Standard] only changes what is displayed (CSS and label hiding) and never touches a request.
 * [Strict] also refuses requests to known tracker and ad hosts, which on a signed-in account can
 * look like a modified client, so it is opt-in there.
 */
internal enum class ProtectionLevel {
    Standard,
    Strict,
}

/** A display-only tweak the person can switch on or off for one site. */
internal enum class SiteTweak(
    val site: LiveWebSite,
    val defaultOn: Boolean = true,
) {
    InstagramAds(LiveWebSite.Instagram),
    InstagramSuggested(LiveWebSite.Instagram),
    InstagramAppPrompts(LiveWebSite.Instagram),
    InstagramReels(LiveWebSite.Instagram),
    InstagramExplore(LiveWebSite.Instagram),

    XPromoted(LiveWebSite.X),
    XWhoToFollow(LiveWebSite.X),
    XTrends(LiveWebSite.X),
    XPremiumGrok(LiveWebSite.X),
    XAppPrompts(LiveWebSite.X),
    XHideForYou(LiveWebSite.X, defaultOn = false),

    LinkedInPromoted(LiveWebSite.LinkedIn),
    LinkedInSuggested(LiveWebSite.LinkedIn),
    LinkedInPremium(LiveWebSite.LinkedIn),
    LinkedInAppPrompts(LiveWebSite.LinkedIn),
    LinkedInNews(LiveWebSite.LinkedIn),

    RedditPromoted(LiveWebSite.Reddit),
    RedditNags(LiveWebSite.Reddit),
    RedditRecommended(LiveWebSite.Reddit),
    ;

    companion object {
        fun forSite(site: LiveWebSite): List<SiteTweak> = entries.filter { it.site == site }
    }
}

/** What one site is configured to do. */
internal data class SiteWebSettings(
    val protection: ProtectionLevel,
    val tweaks: Set<SiteTweak>,
    /** True keeps WebView's own rule that media only plays after a tap. */
    val blockAutoplay: Boolean,
) {
    fun isOn(tweak: SiteTweak): Boolean = tweak in tweaks

    fun withProtection(level: ProtectionLevel): SiteWebSettings = copy(protection = level)

    fun withTweak(
        tweak: SiteTweak,
        on: Boolean,
    ): SiteWebSettings = copy(tweaks = if (on) tweaks + tweak else tweaks - tweak)

    fun withBlockAutoplay(on: Boolean): SiteWebSettings = copy(blockAutoplay = on)

    companion object {
        fun defaultsFor(site: LiveWebSite): SiteWebSettings =
            SiteWebSettings(
                protection = site.defaultProtection,
                tweaks = SiteTweak.forSite(site).filter { it.defaultOn }.toSet(),
                blockAutoplay = true,
            )
    }
}

/** Everything the Privacy & Tweaks screen edits. */
internal data class WebPrivacySettings(
    val sites: Map<LiveWebSite, SiteWebSettings>,
    /** Unwrap tracking redirects and drop tracking parameters on links that leave a site. */
    val cleanOutgoingLinks: Boolean = true,
    /** FLAG_SECURE on the main window: no screenshots and a blank recents thumbnail. */
    val secureWindow: Boolean = false,
    /** Sends navigator.globalPrivacyControl, but only on sites set to Strict. */
    val sendGlobalPrivacyControl: Boolean = false,
) {
    fun site(site: LiveWebSite): SiteWebSettings = sites[site] ?: SiteWebSettings.defaultsFor(site)

    fun updateSite(
        site: LiveWebSite,
        transform: (SiteWebSettings) -> SiteWebSettings,
    ): WebPrivacySettings = copy(sites = sites + (site to transform(site(site))))

    fun configFor(site: LiveWebSite): LiveWebConfig {
        val settings = site(site)
        return LiveWebConfig(
            protection = settings.protection,
            tweaks = settings.tweaks,
            blockAutoplay = settings.blockAutoplay,
            cleanLinks = cleanOutgoingLinks,
            sendGpc = sendGlobalPrivacyControl,
        )
    }

    companion object {
        val Default: WebPrivacySettings =
            WebPrivacySettings(sites = LiveWebSite.entries.associateWith { SiteWebSettings.defaultsFor(it) })
    }
}

/**
 * The settings one WebView is built from. Comparing two of these tells whether a site's page has
 * to be recreated: scripts and request blocking are fixed when the WebView is created.
 */
internal data class LiveWebConfig(
    val protection: ProtectionLevel,
    val tweaks: Set<SiteTweak>,
    val blockAutoplay: Boolean,
    val cleanLinks: Boolean,
    val sendGpc: Boolean,
) {
    fun blocks(
        site: LiveWebSite,
        host: String?,
    ): Boolean = shouldBlockHost(site, protection, host)

    /** Scripts to run at document start, in order. */
    fun documentStartScripts(site: LiveWebSite): List<String> =
        listOfNotNull(
            buildSiteScript(site, tweaks),
            gpcScript().takeIf { protection == ProtectionLevel.Strict && sendGpc },
        )

    companion object {
        fun defaultsFor(site: LiveWebSite): LiveWebConfig = WebPrivacySettings.Default.configFor(site)
    }
}

internal fun gpcScript(): String =
    """
    (function(){
      try{
        Object.defineProperty(Navigator.prototype,'globalPrivacyControl',{get:function(){return true},configurable:true});
      }catch(e){}
    })();
    """.trimIndent()
