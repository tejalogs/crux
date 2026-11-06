package org.crux.ui.screen.home

/**
 * What one tweak hides. Everything here is display-only: selectors become `display:none` rules
 * and text matches hide an element. Nothing blocks a request or rewrites fetch/XHR, because that
 * is what gets a signed-in account flagged.
 */
internal data class TweakRule(
    /** Selectors hidden with display:none, one rule each so one bad selector cannot drop the rest. */
    val css: List<String> = emptyList(),
    /** Short texts that mark a whole post (or timeline cell) as unwanted, e.g. "Sponsored". */
    val postLabels: List<String> = emptyList(),
    /** Module headings; the module around a matching heading is hidden. */
    val headingLabels: List<String> = emptyList(),
    /** Tabs (role=tab) hidden by their label. */
    val tabLabels: List<String> = emptyList(),
    /** Button or link labels that mean "use the app"; their dialog or banner is hidden. */
    val promptLabels: List<String> = emptyList(),
    val js: String = "",
)

/** How the generic engine finds posts and modules on one site. */
internal data class SiteScriptProfile(
    val postSelector: String,
    /** Wrapper hidden instead of the post itself, when the site lists posts inside cells. */
    val cellSelector: String? = null,
    /** Nearest ancestors treated as one module when its heading matches. */
    val moduleSelector: String = "section,aside",
    /** Text inside these is never treated as a label, so a post's own words cannot hide it. */
    val labelExcludeSelector: String = "",
    /** Complete CSS rules always applied (layout fixes, not hiding). */
    val baseRules: List<String> = emptyList(),
    val baseJs: String = "",
)

// Instagram: the page is a React app with hashed class names, so selectors use hrefs, roles and
// the visible labels Instagram itself shows ("Sponsored", "Suggested for you").
private const val IG_EXPLORE_FLAG_JS =
    "document.documentElement.setAttribute('data-cx-explore',location.pathname.indexOf('/explore')===0?'1':'0');"

private val INSTAGRAM_PROFILE =
    SiteScriptProfile(
        postSelector = "article",
        baseRules =
            listOf(
                "div._aagw, div._aagv > div:not(img){pointer-events:none!important}",
                "img{-webkit-user-select:auto!important;user-select:auto!important;pointer-events:auto!important}",
            ),
    )

private val LINKEDIN_PROFILE =
    SiteScriptProfile(
        postSelector = "[role=\"listitem\"],div.feed-shared-update-v2,div[data-id*=\"activity\"]",
        moduleSelector = "[role=\"listitem\"],li,section,aside",
        labelExcludeSelector = ".update-components-text,.feed-shared-text,[data-test-id=\"main-feed-activity-card__commentary\"]",
        baseRules =
            listOf(
                "div:has(>footer){grid-template-columns:minmax(0,1fr)!important}",
                "footer,main{min-width:0!important}",
                "footer li{min-width:0!important;flex:1 1 0!important}",
            ),
    )

private val X_PROFILE =
    SiteScriptProfile(
        postSelector = "article[data-testid=\"tweet\"]",
        cellSelector = "div[data-testid=\"cellInnerDiv\"]",
        moduleSelector = "div[data-testid=\"cellInnerDiv\"],aside,section",
        labelExcludeSelector = "[data-testid=\"tweetText\"],[data-testid=\"User-Name\"]",
    )

private val REDDIT_PROFILE =
    SiteScriptProfile(
        postSelector = "shreddit-post,[data-testid=\"ad-post\"],[data-adclicklocation]",
        baseJs =
            """
            document.querySelectorAll('button[slot="show-more-comments"],faceplate-expandable-section-helper button').forEach(function(b){
              var txt=(b.textContent||'').trim();
              if(/view more comments|show more comments|continue this thread/i.test(txt)){b.click()}
            });
            document.querySelectorAll('shreddit-post[nsfw]').forEach(function(p){p.removeAttribute('nsfw')});
            if(document.documentElement.style.overflow==='hidden')document.documentElement.style.overflow='';
            """.trimIndent(),
    )

internal fun LiveWebSite.scriptProfile(): SiteScriptProfile =
    when (this) {
        LiveWebSite.Instagram -> INSTAGRAM_PROFILE
        LiveWebSite.LinkedIn -> LINKEDIN_PROFILE
        LiveWebSite.X -> X_PROFILE
        LiveWebSite.Reddit -> REDDIT_PROFILE
    }

private const val IG_APP_PROMPT_JS =
    "document.querySelectorAll('div[role=dialog]').forEach(function(d){" +
        "if(d.querySelector('a[href*=\"play.google.com\"],a[href*=\"apps.apple.com\"],a[href*=\"instagram://\"]')){" +
        "d.style.setProperty('display','none','important');" +
        "if(document.body)document.body.style.overflow='';}});"

private val USE_APP_LABELS =
    listOf("Use the app", "Use app", "Open app", "Open in app", "Get the app", "Open the app", "Continue in app")

/** The rule each tweak contributes. Selectors are the least obfuscated handles the sites expose. */
internal val SiteTweak.rule: TweakRule
    get() =
        when (this) {
            SiteTweak.InstagramAds -> {
                TweakRule(
                    css = listOf("article:has(a[href*=\"/ads/\"])", "article:has(a[href*=\"ig_redirect\"])"),
                    postLabels = listOf("Sponsored", "Paid partnership"),
                )
            }

            SiteTweak.InstagramSuggested -> {
                TweakRule(
                    css = listOf("div:has(> div > a[href*=\"/explore/people/\"])"),
                    postLabels = listOf("Suggested for you", "Suggested posts", "Suggested Reels"),
                )
            }

            SiteTweak.InstagramAppPrompts -> {
                TweakRule(
                    css =
                        listOf(
                            "div[role=\"dialog\"]:has(a[href*=\"play.google.com\"])",
                            "div[role=\"dialog\"]:has(a[href*=\"apps.apple.com\"])",
                            "div:has(> a[href*=\"instagram://\"])",
                            "div:has(> a[href*=\"onelink.me\"])",
                            "header a[href*=\"instagram.com/download/\"]",
                        ),
                    promptLabels = USE_APP_LABELS + listOf("Open Instagram"),
                    js = IG_APP_PROMPT_JS,
                )
            }

            SiteTweak.InstagramReels -> {
                TweakRule(
                    css = listOf("a[href=\"/reels/\"]", "a[href^=\"/reels/\"]", "a[aria-label=\"Reels\"]"),
                )
            }

            SiteTweak.InstagramExplore -> {
                // The Search tab is kept; only the recommended grid on /explore is hidden.
                TweakRule(
                    css = listOf("html[data-cx-explore=\"1\"] a[href^=\"/p/\"]", "html[data-cx-explore=\"1\"] a[href^=\"/reel/\"]"),
                    js = IG_EXPLORE_FLAG_JS,
                )
            }

            SiteTweak.XPromoted -> {
                TweakRule(
                    css =
                        listOf(
                            "div[data-testid=\"cellInnerDiv\"]:has([data-testid=\"placementTracking\"])",
                            "article[data-testid=\"tweet\"]:has([data-testid=\"placementTracking\"])",
                            "div[data-testid=\"cellInnerDiv\"]:has([data-testid=\"top-impression-pixel\"])",
                        ),
                    postLabels = listOf("Ad", "Promoted"),
                )
            }

            SiteTweak.XWhoToFollow -> {
                TweakRule(
                    css =
                        listOf(
                            "aside[aria-label*=\"Who to follow\"]",
                            "aside[aria-label*=\"Relevant people\"]",
                            "aside[aria-label*=\"Topics to follow\"]",
                            "div[data-testid=\"cellInnerDiv\"]:has(aside[aria-label*=\"Who to follow\"])",
                            "div[data-testid=\"cellInnerDiv\"]:has(aside[aria-label*=\"Relevant people\"])",
                            "div[data-testid=\"cellInnerDiv\"]:has(aside[aria-label*=\"Topics to follow\"])",
                        ),
                    headingLabels = listOf("Who to follow", "Relevant people", "Topics to follow", "Creators for you"),
                )
            }

            SiteTweak.XTrends -> {
                TweakRule(
                    css =
                        listOf(
                            "div[data-testid=\"cellInnerDiv\"]:has([data-testid=\"trend\"])",
                            "[data-testid=\"trend\"]",
                            "[aria-label*=\"Timeline: Trending\"]",
                        ),
                    headingLabels = listOf("What's happening", "Trends for you"),
                )
            }

            SiteTweak.XPremiumGrok -> {
                TweakRule(
                    css =
                        listOf(
                            "[data-testid=\"GrokDrawer\"]",
                            "[data-testid=\"AppTabBar_Grok_Link\"]",
                            "a[href=\"/i/grok\"]",
                            "a[aria-label=\"Grok\"]",
                            "button[aria-label=\"Grok actions\"]",
                            "a[href=\"/i/premium_sign_up\"]",
                            "a[href*=\"/verified-orgs-signup\"]",
                            "a[href=\"/i/verified-choose\"]",
                            "aside[aria-label*=\"Subscribe to Premium\"]",
                            "div[data-testid=\"cellInnerDiv\"]:has(aside[aria-label*=\"Subscribe to Premium\"])",
                        ),
                    headingLabels = listOf("Subscribe to Premium"),
                )
            }

            SiteTweak.XAppPrompts -> {
                TweakRule(
                    css =
                        listOf(
                            "div[data-testid=\"inline_prompt\"]",
                            "div:has(> a[href*=\"app.link\"])",
                            "div:has(> a[href*=\"play.google.com\"])",
                        ),
                    promptLabels = USE_APP_LABELS + listOf("Download app", "Switch to the app"),
                )
            }

            SiteTweak.XHideForYou -> {
                // Hides the tab only. X remembers the tab last picked, so choose Following once.
                TweakRule(tabLabels = listOf("For you"))
            }

            SiteTweak.LinkedInPromoted -> {
                TweakRule(
                    css =
                        listOf(
                            "div[data-urn*=\"sponsored\"]",
                            "div.feed-shared-update-v2__sponsored-content",
                            ".ad-banner-container",
                            ".feed-shared-ad",
                        ),
                    postLabels = listOf("Promoted", "Sponsored"),
                )
            }

            SiteTweak.LinkedInSuggested -> {
                TweakRule(
                    css = listOf("div:has(> .feed-shared-celebration-card)"),
                    postLabels = listOf("Suggested", "Recommended for you"),
                )
            }

            SiteTweak.LinkedInPremium -> {
                TweakRule(
                    css = listOf(".premium-upsell-card", ".premium-feature-promo", ".top-upsell-banner"),
                    postLabels = listOf("Try Premium"),
                )
            }

            SiteTweak.LinkedInAppPrompts -> {
                TweakRule(
                    css =
                        listOf(
                            ".app-upsell-banner",
                            ".modal-upsell",
                            "div[data-tracking-control-name=\"mobile_app_upsell\"]",
                            "div[data-tracking-control-name=\"app_interstitial\"]",
                        ),
                    promptLabels = USE_APP_LABELS,
                )
            }

            SiteTweak.LinkedInNews -> {
                TweakRule(
                    css = listOf(".feed-shared-news-module", "aside[aria-label*=\"LinkedIn News\"]"),
                    headingLabels = listOf("LinkedIn News", "Today's news and views", "Add to your feed", "Today's puzzles"),
                )
            }

            SiteTweak.RedditPromoted -> {
                TweakRule(
                    css =
                        listOf(
                            "shreddit-ad-post",
                            "shreddit-comments-page-ad",
                            "comment-tree-ad",
                            "[data-testid=\"ad-post\"]",
                        ),
                    postLabels = listOf("Promoted", "Sponsored"),
                )
            }

            SiteTweak.RedditNags -> {
                TweakRule(
                    css =
                        listOf(
                            "configured-xpromo-modal",
                            "[id^=configured-xpromo]",
                            "xpromo-app-selector",
                            "xpromo-bottom-sheet",
                            "xpromo-nsfw-blocking-container",
                            "shreddit-async-loader[bundlename=\"xpromo_nsfw_blocking_modal\"]",
                            "shreddit-async-loader[bundlename=\"bottom_sheet\"]",
                            "a[href*=onelink]",
                            "a[href*=\"reddit.app.link\"]",
                            "a[href*=\"apps.apple.com\"]",
                            "a[href*=\"play.google.com\"]",
                            "shreddit-app-rating",
                            "#login-button",
                            "#signup-button",
                            "a[href^=\"/login\"]",
                            "a[href^=\"/register\"]",
                            "auth-flow-link",
                        ),
                    promptLabels = USE_APP_LABELS + listOf("Open in Reddit", "View in app", "Join"),
                    js = "if(document.body&&document.body.style.overflow==='hidden')document.body.style.overflow='';",
                )
            }

            SiteTweak.RedditRecommended -> {
                TweakRule(
                    css = listOf("shreddit-post[view-type=\"recommendation\"]", "community-recommendation"),
                    postLabels =
                        listOf("Recommended", "Popular near you", "Because you visited*", "Because you've shown interest*"),
                )
            }
        }

private fun escapeJs(text: String): String =
    buildString {
        append('"')
        text.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("")
                else -> append(ch)
            }
        }
        append('"')
    }

/** Regex source matching the whole text, whichever apostrophe the site uses; a trailing `*` means "starts with". */
internal fun labelPattern(labels: List<String>): String {
    if (labels.isEmpty()) return ""
    val body =
        labels.joinToString("|") { label ->
            val prefix = label.endsWith("*")
            val escaped =
                label
                    .removeSuffix("*")
                    .replace(Regex("[.*+?^$()|\\[\\]\\\\{}]")) { "\\" + it.value }
                    .replace("'", "['’]")
            if (prefix) "$escaped.*" else escaped
        }
    return "^[·•\\s]*(?:$body)[·•\\s]*$"
}

private val ENGINE_JS =
    """
    (function(){
      if(window.__cruxTweaks)return;
      window.__cruxTweaks=true;
      var css=__CSS__;
      var postSel=__POSTSEL__,cellSel=__CELLSEL__,moduleSel=__MODULESEL__,excludeSel=__EXCLUDESEL__;
      var rx=function(s){return s?new RegExp(s,'i'):null};
      var postLabels=rx(__POSTLABELS__),headingLabels=rx(__HEADINGLABELS__),tabLabels=rx(__TABLABELS__),prompts=rx(__PROMPTS__);
      var styleEl=null;
      var ensureStyle=function(){
        try{
          if(!css||(styleEl&&styleEl.isConnected))return;
          styleEl=document.createElement('style');
          styleEl.textContent=css;
          (document.head||document.documentElement).appendChild(styleEl);
        }catch(e){}
      };
      var hide=function(e){if(e&&e.style)e.style.setProperty('display','none','important')};
      var clean=function(t){return (t||'').replace(/[​-‍﻿ ]/g,' ').trim()};
      var bar=function(e){
        // Climb only through wrappers that hold nothing else clickable: a site header that also has
        // the menu or search must stay, so stop at it.
        var clickable='a,button,input,select,textarea,[role=button],[role=search],[role=navigation]';
        while(e.parentElement&&e.parentElement!==document.body&&e.parentElement.getBoundingClientRect().height<=120&&!/^(HEADER|NAV)$/.test(e.parentElement.tagName)&&!/header/i.test(e.parentElement.tagName)&&e.parentElement.querySelectorAll(clickable).length<=1){e=e.parentElement}
        return e;
      };
      var scan=function(){
        ensureStyle();
        try{
          if(postLabels&&postSel){
            document.querySelectorAll(postSel).forEach(function(p){
              if(p.getAttribute('data-cx-hidden'))return;
              var len=(p.textContent||'').length;
              if(p.__cxLen===len)return;
              p.__cxLen=len;
              var t=p.querySelectorAll('span,div,a,p');
              for(var i=0;i<t.length;i++){
                var el=t[i];
                if(el.children.length)continue;
                var txt=clean(el.textContent);
                if(txt&&txt.length<=60&&postLabels.test(txt)&&!(excludeSel&&el.closest(excludeSel))){
                  var cell=(cellSel&&p.closest(cellSel))||p;
                  hide(cell);
                  cell.setAttribute('data-cx-hidden','1');
                  p.setAttribute('data-cx-hidden','1');
                  break;
                }
              }
            });
          }
          if(headingLabels){
            document.querySelectorAll('h1,h2,h3,h4,[role="heading"]').forEach(function(h){
              if(h.getAttribute('data-cx-hidden'))return;
              var txt=clean(h.textContent);
              if(txt&&txt.length<=40&&headingLabels.test(txt)){
                var m=h.closest(moduleSel);
                if(m){hide(m);m.setAttribute('data-cx-hidden','1')}
                h.setAttribute('data-cx-hidden','1');
              }
            });
          }
          if(tabLabels){
            document.querySelectorAll('[role="tab"]').forEach(function(t){
              if(t.getAttribute('data-cx-hidden'))return;
              if(tabLabels.test(clean(t.textContent))){
                var w=t.closest('[role="presentation"]')||t;
                hide(w);
                w.setAttribute('data-cx-hidden','1');
                t.setAttribute('data-cx-hidden','1');
              }
            });
          }
          if(prompts){
            document.querySelectorAll('button,a,[role=button]').forEach(function(b){
              if(b.getAttribute('data-cx-hidden'))return;
              if(!prompts.test(clean(b.textContent)))return;
              b.setAttribute('data-cx-hidden','1');
              var d=b.closest('[role=dialog]');
              if(d){hide(d);if(document.body)document.body.style.overflow=''}else{hide(bar(b))}
            });
          }
        }catch(e){}
        try{
    __JS__
        }catch(e){}
      };
      scan();
      setTimeout(scan,300);
      setTimeout(scan,1000);
      setTimeout(scan,2500);
      var timer=0;
      var schedule=function(){if(!timer)timer=setTimeout(function(){timer=0;scan()},200)};
      new MutationObserver(schedule).observe(document,{childList:true,subtree:true});
      window.addEventListener('scroll',schedule,true);
      setInterval(scan,1200);
    })();
    """.trimIndent()

/**
 * Builds the one page script for [site] from the tweaks that are on, or null when there is
 * nothing to do. Pure: the same inputs always give the same script.
 */
internal fun buildSiteScript(
    site: LiveWebSite,
    enabled: Set<SiteTweak>,
): String? {
    val profile = site.scriptProfile()
    val rules = SiteTweak.forSite(site).filter { it in enabled }.map { it.rule }
    val css =
        (profile.baseRules + rules.flatMap { it.css }.distinct().map { "$it{display:none!important}" })
            .joinToString(" ")
    val postLabels = rules.flatMap { it.postLabels }.distinct()
    val headingLabels = rules.flatMap { it.headingLabels }.distinct()
    val tabLabels = rules.flatMap { it.tabLabels }.distinct()
    val promptLabels = rules.flatMap { it.promptLabels }.distinct()
    val js = (listOf(profile.baseJs) + rules.map { it.js }).filter { it.isNotBlank() }.joinToString("\n")
    val nothingToDo =
        css.isBlank() && postLabels.isEmpty() && headingLabels.isEmpty() && tabLabels.isEmpty() &&
            promptLabels.isEmpty() && js.isBlank()
    if (nothingToDo) return null
    return ENGINE_JS
        .replace("__CSS__", escapeJs(css))
        .replace("__POSTSEL__", escapeJs(profile.postSelector))
        .replace("__CELLSEL__", profile.cellSelector?.let(::escapeJs) ?: "null")
        .replace("__MODULESEL__", escapeJs(profile.moduleSelector))
        .replace("__EXCLUDESEL__", escapeJs(profile.labelExcludeSelector))
        .replace("__POSTLABELS__", escapeJs(labelPattern(postLabels)))
        .replace("__HEADINGLABELS__", escapeJs(labelPattern(headingLabels)))
        .replace("__TABLABELS__", escapeJs(labelPattern(tabLabels)))
        .replace("__PROMPTS__", escapeJs(labelPattern(promptLabels)))
        .replace("__JS__", js)
}
