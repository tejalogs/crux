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
    InstagramSuggested(LiveWebSite.Instagram, defaultOn = false),
    InstagramAppPrompts(LiveWebSite.Instagram),
    InstagramReels(LiveWebSite.Instagram),
    InstagramExplore(LiveWebSite.Instagram),
    InstagramAi(LiveWebSite.Instagram),
    InstagramStories(LiveWebSite.Instagram, defaultOn = false),

    XPromoted(LiveWebSite.X),
    XWhoToFollow(LiveWebSite.X),
    XTrends(LiveWebSite.X),
    XPremiumGrok(LiveWebSite.X),
    XSpaces(LiveWebSite.X),
    XAppPrompts(LiveWebSite.X),
    XHideForYou(LiveWebSite.X, defaultOn = false),

    LinkedInPromoted(LiveWebSite.LinkedIn),
    LinkedInSuggested(LiveWebSite.LinkedIn, defaultOn = false),
    LinkedInPremium(LiveWebSite.LinkedIn),
    LinkedInAppPrompts(LiveWebSite.LinkedIn),
    LinkedInNews(LiveWebSite.LinkedIn),
    LinkedInAi(LiveWebSite.LinkedIn),
    LinkedInJobsAndGames(LiveWebSite.LinkedIn),

    RedditPromoted(LiveWebSite.Reddit),
    RedditNags(LiveWebSite.Reddit),
    RedditRecommended(LiveWebSite.Reddit),
    RedditAi(LiveWebSite.Reddit),
    RedditTrending(LiveWebSite.Reddit),
    // Opt-in: mature content stays behind Reddit's own blur unless the person chooses otherwise.
    RedditUnblurNsfw(LiveWebSite.Reddit, defaultOn = false),

    MastodonTrending(LiveWebSite.Mastodon),

    // Appended, never inserted: entries keep their order for anything that stored them by position.
    // On a post's page, the "Discover more" run of strangers' posts that starts once the replies end.
    XDiscoverMore(LiveWebSite.X),
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
        /**
         * Sites whose feed videos wait for a tap by default, so scrolling stays calm and light on
         * data. Only the default: a value the person saved for a site is read first and wins.
         */
        private val TAP_TO_PLAY_BY_DEFAULT = setOf(LiveWebSite.X, LiveWebSite.Reddit, LiveWebSite.LinkedIn)

        fun defaultsFor(site: LiveWebSite): SiteWebSettings =
            SiteWebSettings(
                protection = site.defaultProtection,
                tweaks = SiteTweak.forSite(site).filter { it.defaultOn }.toSet(),
                blockAutoplay = site in TAP_TO_PLAY_BY_DEFAULT,
            )
    }
}

/** Everything the Privacy & Tweaks screen edits. */
internal data class WebPrivacySettings(
    val sites: Map<LiveWebSite, SiteWebSettings>,
    /** Unwrap tracking redirects and drop tracking parameters on links that leave a site. */
    val cleanOutgoingLinks: Boolean = true,
    /** Open shared article links inside Crux's built-in reader instead of an external browser. */
    val openLinksInReader: Boolean = false,
    /** Refuses third-party cookies inside site WebViews. */
    val blockThirdPartyCookies: Boolean = false,
    /** Remembers each tab's last same-site URL and vertical scroll offset across process death. */
    val rememberTabPosition: Boolean = true,
    /** Hides like, repost, upvote, and view numbers across feeds. */
    val hideEngagementCounts: Boolean = false,
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
            openInReader = openLinksInReader,
            blockThirdPartyCookies = blockThirdPartyCookies,
            rememberTabPosition = rememberTabPosition,
            hideEngagementCounts = hideEngagementCounts,
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
    val openInReader: Boolean = false,
    val blockThirdPartyCookies: Boolean = false,
    val rememberTabPosition: Boolean = true,
    val hideEngagementCounts: Boolean = false,
    val sendGpc: Boolean,
) {
    fun blocks(
        site: LiveWebSite,
        host: String?,
    ): Boolean = shouldBlockHost(site, protection, host)

    /** Scripts to run at document start, in order. */
    fun documentStartScripts(
        site: LiveWebSite,
        bridgeToken: String = "",
    ): List<String> =
        listOfNotNull(
            buildSiteScript(site, tweaks, hideEngagementCounts, bridgeToken),
            gpcScript().takeIf { protection == ProtectionLevel.Strict && sendGpc },
            tapToPlayScript().takeIf { blockAutoplay },
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

/**
 * Blocks muted feed-video autoplay that Chromium still allows under mediaPlaybackRequiresUserGesture.
 * Allows playback as soon as the user performs a real tap (not a scroll fling) or keypress.
 */
internal fun tapToPlayScript(): String =
    """
    (function(){
      try{
        var proto=window.HTMLMediaElement&&window.HTMLMediaElement.prototype;
        if(!proto||typeof proto.play!=='function')return;
        var origPlay=proto.play;
        var allowed=new WeakSet();
        var lastTap=0,tx=0,ty=0,moved=false;
        var markTap=function(ev){if(ev&&ev.isTrusted)lastTap=Date.now();};
        window.addEventListener('touchstart',function(ev){
          var t=ev.touches&&ev.touches[0];
          if(t){tx=t.clientX;ty=t.clientY;moved=false;}
        },{capture:true,passive:true});
        window.addEventListener('touchmove',function(ev){
          if(moved)return;
          var t=ev.touches&&ev.touches[0];
          if(t&&(Math.abs(t.clientX-tx)>12||Math.abs(t.clientY-ty)>12)){moved=true;lastTap=0;}
        },{capture:true,passive:true});
        window.addEventListener('touchend',function(ev){if(!moved)markTap(ev);},{capture:true,passive:true});
        window.addEventListener('click',markTap,{capture:true,passive:true});
        window.addEventListener('keydown',markTap,{capture:true,passive:true});
        proto.play=function(){
          if(this&&this.tagName==='VIDEO'&&!allowed.has(this)){
            if(Date.now()-lastTap<3000){
              allowed.add(this);
            }else{
              try{this.autoplay=false;this.pause();}catch(e){}
              return Promise.reject(new DOMException('Playback requires user interaction','NotAllowedError'));
            }
          }
          return origPlay.apply(this,arguments);
        };
        document.addEventListener('play',function(ev){
          var v=ev&&ev.target;
          if(!v||v.tagName!=='VIDEO'||allowed.has(v))return;
          if(Date.now()-lastTap<3000){allowed.add(v);return;}
          try{v.autoplay=false;v.pause();}catch(e){}
        },true);
      }catch(e){}
    })();
    """.trimIndent()
