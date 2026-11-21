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
    /** Script that runs immediately at document start before anything else. */
    val initJs: String = "",
)

// Instagram: the page is a React app with hashed class names, so selectors use hrefs, roles and
// the visible labels Instagram itself shows ("Sponsored", "Suggested for you").
// Only hide the recommended grid on /explore itself — keep explicit user searches (/explore/tags/, /explore/search/).
private const val IG_EXPLORE_FLAG_JS =
    "document.documentElement.setAttribute('data-cx-explore',/^\\/explore\\/?$/i.test(location.pathname)?'1':'0');"

private val INSTAGRAM_PROFILE =
    SiteScriptProfile(
        postSelector = "article",
        labelExcludeSelector = "h1,textarea,[contenteditable]",
        baseRules =
            listOf(
                "html,body{overflow-x:clip!important;max-width:100%!important;overscroll-behavior-x:none!important}",
                "html,body,button,a,[role=\"button\"],input,textarea{touch-action:manipulation!important}",
                // Lock Reels to vertical panning so tapping an expanded Reel caption and swiping up
                // never shifts the viewport left into blank horizontal overflow space.
                "html[data-cx-reels=\"1\"],html[data-cx-reels=\"1\"] body{overflow-x:clip!important;max-width:100vw!important;touch-action:pan-y!important;overscroll-behavior-x:none!important}",
                "html[data-cx-reels=\"1\"] [id^=\"mount_0_0\"]{overflow-x:clip!important;max-width:100vw!important;touch-action:pan-y!important}",
                "html[data-cx-reels=\"1\"] main,html[data-cx-reels=\"1\"] section,html[data-cx-reels=\"1\"] article,html[data-cx-reels=\"1\"] button,html[data-cx-reels=\"1\"] a,html[data-cx-reels=\"1\"] [role=\"button\"]{max-width:100vw!important;touch-action:pan-y!important}",
                "html[data-cx-reels=\"1\"] main,html[data-cx-reels=\"1\"] section,html[data-cx-reels=\"1\"] article,html[data-cx-reels=\"1\"] [role=\"presentation\"],html[data-cx-reels=\"1\"] [role=\"dialog\"]{overflow-x:clip!important;max-width:100vw!important}",
                "html[data-cx-reels=\"1\"] span[dir=\"auto\"],html[data-cx-reels=\"1\"] h1,html[data-cx-reels=\"1\"] p{overflow-wrap:anywhere!important;max-width:100%!important}",
                "div._aagw{pointer-events:none!important}",
                "img{-webkit-user-select:auto!important;user-select:auto!important;pointer-events:auto!important}",
                "*{-webkit-tap-highlight-color:transparent!important}",
                "html,body{-webkit-font-smoothing:antialiased!important;-moz-osx-font-smoothing:grayscale!important}",
            ),
        initJs =
            """
            if(!window.__cxIgReelLock){
              window.__cxIgReelLock=true;
              var __cxIsIgReel=function(){return /^\/reels?(\/|$)|^\/[^\/]+\/reel(\/|$)/i.test(location.pathname||'')};
              var __cxSyncIgReel=function(){
                var on=__cxIsIgReel();
                var flag=on?'1':'0';
                if(document.documentElement&&document.documentElement.getAttribute('data-cx-reels')!==flag){
                  document.documentElement.setAttribute('data-cx-reels',flag);
                }
                if(on){
                  var se=document.scrollingElement||document.documentElement;
                  if((window.scrollX||0)!==0||(se&&se.scrollLeft!==0)||(document.body&&document.body.scrollLeft!==0)){
                    if(se)se.scrollLeft=0;
                    if(document.body)document.body.scrollLeft=0;
                    try{window.scrollTo(0,window.scrollY||0);}catch(e){}
                  }
                }
              };
              window.__cxSyncIgReel=__cxSyncIgReel;
              __cxSyncIgReel();
              var __cxLastIgPath='';
              var __cxClampReel=function(ev){
                if(location.pathname!==__cxLastIgPath){
                  __cxLastIgPath=location.pathname;
                  __cxSyncIgReel();
                }else if(__cxIsIgReel()){
                  __cxSyncIgReel();
                }
                if(!__cxIsIgReel())return;
                var t=ev&&ev.target;
                if(t&&t.nodeType===1&&t!==document.documentElement&&t!==document.body&&(t.scrollLeft||0)>0){
                  if(!t.closest||!t.closest('[role="dialog"],[role="tablist"]')){
                    t.scrollLeft=0;
                  }
                }
              };
              window.addEventListener('scroll',__cxClampReel,{capture:true,passive:true});
              window.addEventListener('touchstart',__cxClampReel,{capture:true,passive:true});
              window.addEventListener('touchend',__cxClampReel,{capture:true,passive:true});
              window.addEventListener('click',__cxClampReel,{capture:true,passive:true});
              window.addEventListener('popstate',__cxClampReel,{passive:true});
              window.addEventListener('hashchange',__cxClampReel,{passive:true});
            }
            """.trimIndent(),
        baseJs = "if(window.__cxSyncIgReel)window.__cxSyncIgReel();",
    )

private val LINKEDIN_PROFILE =
    SiteScriptProfile(
        postSelector = "div.feed-shared-update-v2,div[data-id*=\"activity\"],div[data-urn*=\"activity\"],article",
        moduleSelector = "aside,div.feed-shared-news-module,div.feed-shared-update-v2",
        labelExcludeSelector = ".update-components-text,.feed-shared-text,[data-test-id=\"main-feed-activity-card__commentary\"]",
        baseRules =
            listOf(
                "html,body{overflow-x:clip!important;max-width:100%!important;overscroll-behavior-x:none!important}",
                "html,body,button,a,[role=\"button\"],input,textarea{touch-action:manipulation!important}",
                "*{-webkit-tap-highlight-color:transparent!important}",
                "html,body{-webkit-font-smoothing:antialiased!important;-moz-osx-font-smoothing:grayscale!important}",
                "div.feed-shared-update-v2:active{background-color:rgba(128,128,128,0.04)!important}",
            ),
        baseJs =
            """
            if(!window.__cxLiSubBind){
              window.__cxLiSubBind=true;
              document.addEventListener('submit',function(){window.__cxLiSub=true},true);
            }
            if(!window.__cxLiSub&&/^\/(uas\/)?login/.test(location.pathname)){
              document.querySelectorAll('[role="alert"],.artdeco-toast-item,#artdeco-global-alert-container,.toast,div,section').forEach(function(el){
                if(el.children.length>3||el.querySelector('input,form,main'))return;
                var h=el.clientHeight||0;
                if(h>160)return;
                var txt=(el.textContent||'').replace(/\s+/g,' ').trim();
                if(/^An unexpected error has occurred\.\s*Please try again\b/i.test(txt)){
                  el.style.setProperty('display','none','important');
                }
              });
            }
            """.trimIndent(),
    )

private val X_PROFILE =
    SiteScriptProfile(
        postSelector = "article[data-testid=\"tweet\"]",
        cellSelector = "div[data-testid=\"cellInnerDiv\"]",
        moduleSelector = "div[data-testid=\"cellInnerDiv\"],aside,section",
        labelExcludeSelector = "[data-testid=\"tweetText\"],[data-testid=\"User-Name\"]",
        baseRules =
            listOf(
                "html,body{overflow-x:clip!important;max-width:100%!important;overscroll-behavior-x:none!important}",
                "html,body,button,a,[role=\"button\"],input,textarea{touch-action:manipulation!important}",
                "*{-webkit-tap-highlight-color:transparent!important}",
                "html,body{-webkit-font-smoothing:antialiased!important;-moz-osx-font-smoothing:grayscale!important}",
            ),
    )

private val REDDIT_PROFILE =
    SiteScriptProfile(
        postSelector = "shreddit-post,[data-testid=\"ad-post\"],[data-adclicklocation]",
        baseRules =
            listOf(
                // Keep document root scrolling on Chromium's compositor thread; setting overflow-y:auto
                // on both html and body creates nested scroll containers and lags Reddit feed scrolling.
                "html{overflow-x:clip!important}",
                "html,body,button,a,[role=\"button\"]{touch-action:manipulation!important}",
                "shreddit-post{-webkit-tap-highlight-color:transparent!important}",
                "shreddit-blurred-container:not([reason=\"spoiler\"]) [slot=\"revealed\"]{display:block!important;filter:none!important;-webkit-filter:none!important;opacity:1!important;visibility:visible!important}",
                "shreddit-blurred-container:not([reason=\"spoiler\"]) > :not([slot=\"blurred\"]):not([slot=\"blur-toggle\"]){filter:none!important;-webkit-filter:none!important;opacity:1!important;visibility:visible!important}",
                "[slot=\"post-media-container\"] .relative{height:100%!important}",
                "faceplate-img.thumbnail-blur{filter:none!important;-webkit-filter:none!important}",
            ),
        initJs =
            """
            if(!window.__cxRedditSearchGuard){
              window.__cxRedditSearchGuard=true;
              var __cxSearchState=new WeakMap();
              var __cxActiveEl=function(){
                var a=document.activeElement;
                while(a&&a.shadowRoot&&a.shadowRoot.activeElement){a=a.shadowRoot.activeElement;}
                return a;
              };
              var __cxResolveSearchInput=function(ev){
                if(!ev)return null;
                var path=ev.composedPath?ev.composedPath():null;
                var el=(path&&path[0])||ev.target;
                if(!el||el.tagName!=='INPUT')return null;
                var host=el.closest?el.closest('reddit-search-large,faceplate-search-input,faceplate-text-input'):null;
                if(!host&&el.getRootNode){
                  var rn=el.getRootNode();
                  if(rn&&rn.host&&rn.host.closest&&rn.host.closest('reddit-search-large,faceplate-search-input,faceplate-text-input')){host=rn.host;}
                }
                if(!host&&el.type!=='search')return null;
                return el;
              };
              var __cxFixCaret=function(el,expected){
                var restore=function(){
                  try{
                    var v=el.value||'';
                    if(el===__cxActiveEl()&&typeof el.selectionStart==='number'&&el.selectionStart===0&&el.selectionEnd===0&&expected>0&&expected<=v.length){
                      el.setSelectionRange(expected,expected);
                    }
                  }catch(e){}
                };
                queueMicrotask(restore);
                setTimeout(restore,0);
              };
              document.addEventListener('beforeinput',function(ev){
                var el=__cxResolveSearchInput(ev);
                if(!el)return;
                if(ev.inputType==='insertText'||ev.inputType==='insertCompositionText'){
                  var prevVal=el.value||'';
                  var prevSel=typeof el.selectionStart==='number'?el.selectionStart:prevVal.length;
                  var insLen=typeof ev.data==='string'&&ev.data.length>0?ev.data.length:1;
                  __cxSearchState.set(el,{prevVal:prevVal,prevSel:prevSel,insLen:insLen,active:true});
                }else{
                  __cxSearchState.delete(el);
                }
              },true);
              document.addEventListener('input',function(ev){
                var el=__cxResolveSearchInput(ev);
                if(!el)return;
                var st=__cxSearchState.get(el);
                if(!st||!st.active)return;
                st.active=false;
                var val=el.value||'';
                if(val.length<=st.prevVal.length)return;
                var delta=val.length-st.prevVal.length;
                var expected=st.prevSel+Math.max(st.insLen,delta);
                if(expected>val.length)expected=val.length;
                __cxFixCaret(el,expected);
              },true);
              document.addEventListener('compositionend',function(ev){
                var el=__cxResolveSearchInput(ev);
                if(!el)return;
                var val=el.value||'';
                if(val.length>0&&typeof el.selectionStart==='number'&&el.selectionStart===0&&el.selectionEnd===0){
                  __cxFixCaret(el,val.length);
                }
              },true);
            }
            """.trimIndent(),
        baseJs =
            """
            var hasOpenOverlay=isEditableFocused()||!!document.querySelector('[role="dialog"]:not([data-cx-hidden]):not([cx-nsfw-dismissed]):not(#blocking-modal):not(#nsfw-qr-dialog),dialog[open]:not([cx-nsfw-dismissed]),faceplate-modal[open]:not([cx-nsfw-dismissed]),reddit-search-large[open],reddit-search-large:focus-within');
            if(!hasOpenOverlay){
              document.querySelectorAll('html,body,shreddit-app,main').forEach(function(el){
                if(el.hasAttribute('inert'))el.removeAttribute('inert');
                if(el.getAttribute('aria-hidden')==='true')el.removeAttribute('aria-hidden');
              });
              if(document.documentElement.style.overflow==='hidden')document.documentElement.style.overflow='';
              if(document.body){
                if(document.body.style.overflow==='hidden')document.body.style.overflow='';
                if(document.body.style.position==='fixed')document.body.style.position='';
                document.body.classList.remove('scroll-disabled','no-scroll');
              }
            }
            """.trimIndent(),
    )

internal fun LiveWebSite.scriptProfile(): SiteScriptProfile =
    when (this) {
        LiveWebSite.Instagram -> INSTAGRAM_PROFILE
        LiveWebSite.LinkedIn -> LINKEDIN_PROFILE
        LiveWebSite.X -> X_PROFILE
        LiveWebSite.Reddit -> REDDIT_PROFILE
        LiveWebSite.Mastodon -> MASTODON_PROFILE
    }

// Mastodon: one article per status; no ads or promoted posts to hide.
private val MASTODON_PROFILE =
    SiteScriptProfile(
        postSelector = "article,.status",
        labelExcludeSelector = ".status__content",
    )

private const val IG_APP_PROMPT_JS =
    "document.querySelectorAll('div[role=dialog]').forEach(function(d){" +
        "if(d.querySelector('a[href*=\"play.google.com\"],a[href*=\"apps.apple.com\"],a[href*=\"instagram://\"]')){" +
        "d.style.setProperty('display','none','important');" +
        "if(document.body)document.body.style.overflow='';}});"

/**
 * Hides X's header "Subscribe" / "Get Premium" button by its own text. Only the link or button
 * itself is hidden, never a wrapper, so a header that holds the menu or search stays.
 */
internal val X_PREMIUM_BUTTON_LABELS = listOf("Subscribe", "Get Premium", "Subscribe to Premium", "Try Premium")

private val X_SUBSCRIBE_BUTTON_JS =
    "(function(){var rx=new RegExp(" + escapeJs(labelPattern(X_PREMIUM_BUTTON_LABELS)) + ",'i');" +
        "document.querySelectorAll('a,button,[role=button]').forEach(function(b){" +
        "var t=(b.textContent||'').replace(/\\s+/g,' ').trim();" +
        "if(t&&t.length<=24&&rx.test(t)&&!b.closest('article')){b.style.setProperty('display','none','important')}});})();"

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
                    css = listOf("aside:has(a[href*=\"/explore/people/\"])"),
                    postLabels = listOf("Suggested for you", "Suggested posts", "Suggested Reels"),
                )
            }

            SiteTweak.InstagramAppPrompts -> {
                TweakRule(
                    css =
                        listOf(
                            "div[role=\"dialog\"]:has(a[href*=\"play.google.com\"])",
                            "div[role=\"dialog\"]:has(a[href*=\"apps.apple.com\"])",
                            "div[role=\"dialog\"]:has(a[href*=\"instagram://\"])",
                            "div[role=\"dialog\"]:has(a[href*=\"onelink.me\"])",
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

            SiteTweak.InstagramAi -> {
                TweakRule(
                    css =
                        listOf(
                            "a[href*=\"meta.ai\"]",
                            "[aria-label*=\"Meta AI\"]",
                            "div[role=\"button\"]:has(svg[aria-label*=\"Meta AI\"])",
                        ),
                    promptLabels = listOf("Ask Meta AI", "Meta AI"),
                )
            }

            SiteTweak.InstagramStories -> {
                TweakRule(
                    css =
                        listOf(
                            "[data-pagelet=\"story_tray\"]",
                            "div[role=\"menu\"]:has(button[aria-label*=\"Story by\"])",
                            "ul:has(button[aria-label*=\"Story by\"])",
                            "[aria-label=\"Stories\"]",
                        ),
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
                            "[data-testid=\"grokImgGen\"]",
                            "a[href=\"/i/grok\"]",
                            "a[href*=\"/i/grok\"]",
                            "a[aria-label=\"Grok\"]",
                            "button[aria-label*=\"Grok\"]",
                            "[aria-label*=\"Ask Grok\"]",
                            "a[href=\"/i/premium_sign_up\"]",
                            "a[href*=\"/i/premium\"]",
                            "a[href*=\"premium_sign_up\"]",
                            "a[href*=\"/i/verified\"]",
                            "a[aria-label=\"Subscribe\"]",
                            "button[aria-label=\"Subscribe\"]",
                            "a[aria-label=\"Get Premium\"]",
                            "[data-testid=\"premium-signup-tab\"]",
                            "[data-testid=\"subscribe\"]",
                            "a[href*=\"/verified-orgs-signup\"]",
                            "a[href=\"/i/verified-choose\"]",
                            "aside[aria-label*=\"Subscribe to Premium\"]",
                            "div[data-testid=\"cellInnerDiv\"]:has(aside[aria-label*=\"Subscribe to Premium\"])",
                        ),
                    headingLabels = listOf("Subscribe to Premium"),
                    js = X_SUBSCRIBE_BUTTON_JS,
                )
            }

            SiteTweak.XSpaces -> {
                TweakRule(
                    css =
                        listOf(
                            "[data-testid=\"spacesBar\"]",
                            "[data-testid=\"fleetline\"]",
                            "[aria-label=\"Spaces\"]",
                            "a[href^=\"/i/spaces/\"]",
                            "a[href*=\"/i/broadcasts/\"]",
                            "div[data-testid=\"cellInnerDiv\"]:has(a[href^=\"/i/spaces/\"])",
                            "div[data-testid=\"cellInnerDiv\"]:has(a[href*=\"/i/broadcasts/\"])",
                        ),
                )
            }

            SiteTweak.XAppPrompts -> {
                TweakRule(
                    css =
                        listOf(
                            "div[data-testid=\"inline_prompt\"]",
                            "#layers div:not(:has(input)):not(:has(form)):has(> a[href*=\"app.link\"])",
                            "#layers div:not(:has(input)):not(:has(form)):has(> a[href*=\"play.google.com\"])",
                            "div[data-testid=\"sheetDialog\"]:not(:has(input)):not(:has(form)):has(a[href*=\"play.google.com\"])",
                            "div[data-testid=\"sheetDialog\"]:not(:has(input)):not(:has(form)):has(a[href*=\"apps.apple.com\"])",
                        ),
                    promptLabels = USE_APP_LABELS + listOf("Download app", "Switch to the app"),
                )
            }

            SiteTweak.XHideForYou -> {
                TweakRule(
                    tabLabels = listOf("For you"),
                )
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
                    css = listOf(".feed-shared-celebration-card"),
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
                            ".app-banner",
                            "div[data-tracking-control-name=\"mobile_app_upsell\"]",
                            "div[data-tracking-control-name=\"app_interstitial\"]",
                        ),
                    promptLabels = USE_APP_LABELS,
                )
            }

            SiteTweak.LinkedInNews -> {
                TweakRule(
                    css = listOf(".feed-shared-news-module", "aside[aria-label*=\"LinkedIn News\"]"),
                    headingLabels = listOf("LinkedIn News", "Today's news and views", "Add to your feed"),
                )
            }

            SiteTweak.LinkedInAi -> {
                TweakRule(
                    css =
                        listOf(
                            "[data-view-name*=\"ai-coach\"]",
                            "[data-view-name*=\"collaborative-article\"]",
                            "button[aria-label*=\"Rewrite with AI\"]",
                            "button[aria-label*=\"Draft with AI\"]",
                            "button[aria-label*=\"Get key takeaways\"]",
                            "a[href*=\"/advice/\"]",
                        ),
                    headingLabels = listOf("Collaborative articles", "Explore collaborative articles"),
                    promptLabels = listOf("Rewrite with AI", "Draft with AI", "Get key takeaways", "Try Coach"),
                )
            }

            SiteTweak.LinkedInJobsAndGames -> {
                TweakRule(
                    css =
                        listOf(
                            "a[href*=\"/games/\"]",
                            "aside:has(a[href*=\"/games/\"])",
                        ),
                    headingLabels = listOf("Today's puzzles", "Games"),
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
                            "xpromo-bottom-bar",
                            "community-barrier",
                            "xpromo-floating-pill",
                            "shreddit-async-loader[bundlename*=\"xpromo\"]",
                            "shreddit-async-loader[bundlename=\"bottom_sheet\"]",
                            "faceplate-banner",
                            "[slot=\"bottom-banner\"]",
                            "a[href*=onelink]",
                            "a[href*=\"reddit.app.link\"]",
                            "a[href*=\"apps.apple.com\"][href*=\"reddit\"]",
                            "a[href*=\"play.google.com/store/apps/details?id=com.reddit\"]",
                            "shreddit-app-rating",
                            "html:not([data-cx-auth=\"1\"]) #login-button",
                            "html:not([data-cx-auth=\"1\"]) #signup-button",
                            "html:not([data-cx-auth=\"1\"]) a[href^=\"/login\"]",
                            "html:not([data-cx-auth=\"1\"]) a[href^=\"/register\"]",
                            "html:not([data-cx-auth=\"1\"]) auth-flow-link",
                        ),
                    promptLabels =
                        USE_APP_LABELS +
                            listOf("Open in Reddit", "View in Reddit App", "Continue in Reddit App", "Use Reddit App", "View in app"),
                    js =
                        "if(!isEditableFocused()&&!document.querySelector('[role=\"dialog\"]:not([data-cx-hidden]):not([cx-nsfw-dismissed]):not(#blocking-modal):not(#nsfw-qr-dialog),dialog[open]:not([cx-nsfw-dismissed]),faceplate-modal[open]:not([cx-nsfw-dismissed]),reddit-search-large[open],reddit-search-large:focus-within')&&document.body&&document.body.style.overflow==='hidden')document.body.style.overflow='';",
                )
            }

            SiteTweak.RedditRecommended -> {
                TweakRule(
                    css = listOf("shreddit-post[view-type=\"recommendation\"]", "community-recommendation"),
                    postLabels =
                        listOf("Recommended", "Popular near you", "Because you visited*", "Because you've shown interest*"),
                )
            }

            SiteTweak.RedditAi -> {
                TweakRule(
                    css =
                        listOf(
                            "reddit-answers",
                            "shreddit-ai-summary",
                            "[data-testid*=\"reddit-answers\"]",
                            "a[href^=\"/answers\"]",
                            "[id*=\"reddit-answers\"]",
                        ),
                    headingLabels = listOf("Reddit Answers", "Ask Reddit Answers"),
                )
            }

            SiteTweak.MastodonTrending -> {
                TweakRule(
                    css =
                        listOf(
                            ".getting-started__trends",
                            ".trends",
                            ".explore__links",
                            "a[href=\"/explore/tags\"]",
                            "a[href=\"/explore/links\"]",
                        ),
                    headingLabels = listOf("Trending now", "Trending hashtags", "News"),
                )
            }

            SiteTweak.RedditTrending -> {
                TweakRule(
                    css =
                        listOf(
                            "trending-carousel",
                            "[data-testid=\"trending-posts\"]",
                            "aside[aria-label*=\"Trending\"]",
                        ),
                    headingLabels = listOf("Trending Today"),
                )
            }

            SiteTweak.RedditUnblurNsfw -> {
                TweakRule(
                    css =
                        listOf(
                            "[slot=\"nsfw-banner\"]",
                            "#blocking-modal",
                            "#nsfw-qr-dialog",
                            "#configured-xpromo-blocking_xpromo_nsfw_blocking",
                            "#configured-xpromo-blocking_xpromo_nsfw_blocking_desktop",
                            "#configured-xpromo-blocking_xpromo_nsfw_blocking_desktop_cms",
                            "body > div[style*=\"backdrop-filter\"]",
                            "body > div[style*=\"blur(4px)\"]",
                            "shreddit-blurred-container[cx-unblurred] [slot=\"blurred\"]",
                            "shreddit-blurred-container[cx-unblurred] [slot=\"blur-toggle\"]",
                            "xpromo-nsfw-blocking-container[cx-nsfw-dismissed]",
                            "xpromo-nsfw-blocking-container .prompt",
                            "xpromo-nsfw-blocking-container .overlay",
                            "xpromo-nsfw-blocking-container .bg-scrim",
                            "xpromo-nsfw-blocking-container .viewInApp",
                            "community-barrier[cx-nsfw-dismissed]",
                            "faceplate-tracker[source*=\"nsfw\"][cx-nsfw-dismissed]",
                            "shreddit-async-loader[bundlename*=\"nsfw_blocking\"][cx-nsfw-dismissed]",
                            "shreddit-async-loader[bundlename*=\"nsfw-blocking\"][cx-nsfw-dismissed]",
                            "shreddit-async-loader[bundlename*=\"nsfw_modal\"][cx-nsfw-dismissed]",
                        ),
                    js =
                        """
                        try{
                          document.cookie='over18=1; path=/; domain=.reddit.com; max-age=31536000; SameSite=Lax';
                          document.cookie='over18=1; path=/; domain=reddit.com; max-age=31536000; SameSite=Lax';
                          document.cookie='over18=1; path=/; max-age=31536000; SameSite=Lax';
                          localStorage.setItem('over18','1');
                          localStorage.setItem('has_seen_nsfw_modal','true');
                          if(window.cookieStore&&typeof window.cookieStore.set==='function'){
                            cookieStore.set({name:'over18',value:'1',path:'/',domain:'reddit.com'}).catch(function(){});
                          }
                        }catch(e){}
                        var cxNsfwClick=function(root,inBlockingModal){
                          if(!root||!root.querySelectorAll)return;
                          root.querySelectorAll('button,a[role="button"],[role="button"]').forEach(function(b){
                            if(b.getAttribute('data-cx-nsfw-clicked'))return;
                            var txt=(b.textContent||'').replace(/\s+/g,' ').trim();
                            var matched=/^(Yes,\s*I['’]?m over 18|I am over 18|View NSFW( content)?|Click to see nsfw|See NSFW|Show post|Show content|Yes,\s*continue|View Community)$/i.test(txt)||
                              (inBlockingModal&&/^(Continue|Continue in browser|View in browser|Yes|I am over 18|18\+|View Community)$/i.test(txt));
                            if(matched){
                              b.setAttribute('data-cx-nsfw-clicked','1');
                              b.click();
                            }
                          });
                        };
                        cxNsfwClick(document,false);
                        document.querySelectorAll('community-barrier,xpromo-nsfw-blocking-container,faceplate-tracker[source*="nsfw"],#blocking-modal,#nsfw-qr-dialog,shreddit-async-loader[bundlename*="nsfw"],shreddit-async-loader[bundlename*="barrier"]').forEach(function(h){
                          cxNsfwClick(h,true);
                          if(h.shadowRoot)cxNsfwClick(h.shadowRoot,true);
                          if(h.tagName==='XPROMO-NSFW-BLOCKING-CONTAINER'&&h.shadowRoot){
                            if(!h.shadowRoot.querySelector('slot')){
                              try{h.shadowRoot.appendChild(document.createElement('slot'));}catch(e){}
                            }
                            var p=h.shadowRoot.querySelector('.prompt,.overlay,.bg-scrim,.viewInApp');
                            if(p&&p.style){if(p.style.setProperty)p.style.setProperty('display','none','important');else p.style.display='none';}
                          }
                          var hasUi=h.children.length>0||(h.shadowRoot&&h.shadowRoot.children.length>0);
                          if(!hasUi)return;
                          if(h.id==='blocking-modal'||h.id==='nsfw-qr-dialog'){
                            var dlg=h.closest('[role="dialog"]')||h;
                            if(!dlg.querySelector('shreddit-post,[slot="text-body"]')){
                              dlg.setAttribute('cx-nsfw-dismissed','1');
                              if(dlg.style){if(dlg.style.setProperty)dlg.style.setProperty('display','none','important');else dlg.style.display='none';}
                            }
                          }else if(h.tagName==='COMMUNITY-BARRIER'){
                            h.setAttribute('cx-nsfw-dismissed','1');
                            if(h.style){if(h.style.setProperty)h.style.setProperty('display','none','important');else h.style.display='none';}
                          }else if(!h.querySelector('shreddit-post,[slot="text-body"]')){
                            h.setAttribute('cx-nsfw-dismissed','1');
                          }
                        });
                        document.querySelectorAll('shreddit-post[nsfw],shreddit-post [slot="text-body"],shreddit-post-text-body,[data-post-click-location="text-body"]').forEach(function(p){
                          if(p.tagName==='SHREDDIT-POST'){
                            p.removeAttribute('nsfw');
                            p.removeAttribute('blurred');
                            p.removeAttribute('is-blurred');
                            p.removeAttribute('embed-obscured');
                            p.blurred=false;
                          }else if(p.hasAttribute&&p.hasAttribute('hidden')){
                            p.removeAttribute('hidden');
                          }
                          if(p.style){
                            if(p.style.display==='none')p.style.display='';
                            p.style.filter='none';
                            p.style.webkitFilter='none';
                            p.style.opacity='1';
                            p.style.visibility='visible';
                          }
                        });
                        document.querySelectorAll('shreddit-blurred-container:not([reason="spoiler"])').forEach(function(p){
                          p.blurred=false;
                          if(p.__blurred!==undefined)p.__blurred=false;
                          p.removeAttribute('nsfw');
                          p.removeAttribute('blurred');
                          p.removeAttribute('is-blurred');
                          p.removeAttribute('embed-obscured');
                          p.setAttribute('mode','revealed');
                          p.setAttribute('cx-unblurred','1');
                          if(typeof p.unblur==='function'){try{p.unblur();}catch(e){}}
                          var tog=p.querySelector('[slot="blur-toggle"] button,[slot="blur-toggle"],button')||(p.shadowRoot&&p.shadowRoot.querySelector('[slot="blur-toggle"] button,button,[role="button"]'));
                          if(tog&&!tog.getAttribute('data-cx-nsfw-clicked')){
                            tog.setAttribute('data-cx-nsfw-clicked','1');
                            tog.click();
                          }
                          var rev=p.querySelector('[slot="revealed"],[slot="text-body"],shreddit-post-text-body,[data-post-click-location="text-body"],[property="schema:articleBody"]');
                          if(!rev){
                            for(var i=0;i<p.children.length;i++){
                              var c=p.children[i];
                              var sl=c.getAttribute&&c.getAttribute('slot');
                              if(sl!=='blurred'&&sl!=='blur-toggle'&&c.tagName!=='TEMPLATE'){rev=c;break;}
                            }
                          }
                          if(rev){
                            if(rev.hasAttribute&&rev.hasAttribute('hidden'))rev.removeAttribute('hidden');
                            if(rev.style){
                              rev.style.setProperty('display','block','important');
                              rev.style.setProperty('filter','none','important');
                              rev.style.setProperty('opacity','1','important');
                              rev.style.setProperty('visibility','visible','important');
                            }
                          }
                          var blr=p.querySelector('[slot="blurred"]');
                          if(blr&&blr.style){blr.style.setProperty('display','none','important');}
                          if(p.shadowRoot){
                            p.shadowRoot.querySelectorAll('.blurred,[class*="blur"],slot').forEach(function(s){
                              if(s.style){s.style.filter='none';s.style.webkitFilter='none';s.style.backdropFilter='none';}
                              if(s.tagName==='SLOT'){
                                var sn=s.getAttribute('name');
                                if(sn==='blurred'||sn==='blur-toggle'){s.style.display='none';}
                                else if(!sn||sn==='revealed'){s.style.display='block';}
                              }
                            });
                          }
                          if(p.style){p.style.filter='none';p.style.webkitFilter='none';}
                        });
                        document.querySelectorAll('faceplate-img.thumbnail-blur').forEach(function(img){
                          img.classList.remove('thumbnail-blur');
                          if(img.style){img.style.filter='none';img.style.webkitFilter='none';}
                        });
                        document.querySelectorAll('community-highlight-card').forEach(function(c){
                          c.isBlurred=false;
                          c.removeAttribute('is-blurred');
                        });
                        document.querySelectorAll('devvit2-blur-gate').forEach(function(g){
                          g._blur=false;
                          g.removeAttribute('blur');
                        });
                        """.trimIndent(),
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
      try{
    __INIT_JS__
      }catch(e){}
      var css=__CSS__;
      var postSel=__POSTSEL__,cellSel=__CELLSEL__,moduleSel=__MODULESEL__,excludeSel=__EXCLUDESEL__;
      var rx=function(s){return s?new RegExp(s,'i'):null};
      var postLabels=rx(__POSTLABELS__),headingLabels=rx(__HEADINGLABELS__),tabLabels=rx(__TABLABELS__),prompts=rx(__PROMPTS__);
      var styleEl=null;
      // Only Reddit renders with shadow DOM; walking every element elsewhere is wasted work.
      var hasShadowDom=/(^|\.)reddit\.com$/.test(location.hostname);
      // Full-screen video viewers (Reels, Stories, Shorts-style pages) and direct messages have no feed posts to filter,
      // and their DOM churns as each clip/message loads, so they only get the stylesheet and prompt checks.
      var isImmersive=function(){return /^\/(reels?|stories|direct|messages|messaging|chat|shorts)(\/|$)|^\/[^\/]+\/reel(\/|$)/i.test(location.pathname)};
      var isPostDetail=function(){return /\/comments\//.test(location.pathname)};
      var isAuthPage=function(){return /(^|\/)(i\/flow|login|accounts\/login|uas\/login|checkpoint|challenge|account\/access|account\/login_verification)(\/|$)/i.test(location.pathname)};
      var syncAuthAttr=function(){
        try{if(document.documentElement)document.documentElement.setAttribute('data-cx-auth',isAuthPage()?'1':'0');}catch(e){}
      };
      syncAuthAttr();
      var deepActiveElement=function(){
        var a=document.activeElement;
        while(a&&a.shadowRoot&&a.shadowRoot.activeElement){a=a.shadowRoot.activeElement;}
        return a;
      };
      var isEditableFocused=function(){
        var a=deepActiveElement();
        if(!a)return false;
        if(a.isContentEditable)return true;
        var t=a.tagName;
        return t==='INPUT'||t==='TEXTAREA'||t==='SELECT';
      };
      var lastScroll=0;
      var lastScan=0;
      var lastPtrState=null;
      var checkedPrompts=new WeakSet();
      var checkedHeadings=new WeakSet();
      var checkedShadowHosts=new WeakSet();
      var scannedPosts=new WeakMap();
      var markScroll=function(){lastScroll=Date.now()};
      try{
        window.addEventListener('touchstart',function(ev){
          try{
            var se=document.scrollingElement||document.documentElement;
            var winTop=window.scrollY||window.pageYOffset||(se&&se.scrollTop)||(document.body&&document.body.scrollTop)||0;
            var allow=winTop<=2&&!isAuthPage()&&!isImmersive();
            if(allow){
              var el=ev.target;
              if(el&&el.closest&&el.closest('[role="dialog"],[aria-modal="true"],reddit-search-large')){allow=false;}
              var depth=0;
              while(allow&&el&&el!==document.documentElement&&el!==document.body&&depth<8){
                depth++;
                var tag=el.tagName;
                if(tag!=='SPAN'&&tag!=='A'&&tag!=='IMG'&&tag!=='SVG'&&tag!=='PATH'&&tag!=='P'&&(el.scrollTop||0)>2){allow=false;break;}
                el=el.parentElement;
              }
            }
            if(lastPtrState!==allow){
              lastPtrState=allow;
              console.debug(allow?__BRIDGE_PTR_1__:__BRIDGE_PTR_0__);
            }
          }catch(e){}
        },{capture:true,passive:true});
        window.addEventListener('scroll',markScroll,{capture:true,passive:true});
        window.addEventListener('touchmove',markScroll,{capture:true,passive:true});
        window.addEventListener('wheel',markScroll,{capture:true,passive:true});
        window.addEventListener('focusout',function(){schedule();},{capture:true,passive:true});
        window.addEventListener('click',function(ev){
          var t=ev.target;
          if(!t||!t.closest)return;
          var p=t.closest('article[data-testid="tweet"],shreddit-post,div.feed-shared-update-v2,div[data-id*="activity"],article,[role="article"]');
          if(p){try{p.setAttribute('data-cx-tapped',String(Date.now()));}catch(e){}}
          var bm=t.closest('[data-testid="bookmark"],button[aria-label="Save" i],button[aria-label="Bookmark" i],div[role="button"]:has(svg[aria-label="Save" i]),shreddit-post [slot*="save"]');
          if(bm){console.debug(__BRIDGE_BM_TAP__);}
        },{capture:true,passive:true});
      }catch(e){}
      var ensureStyle=function(){
        try{
          if(!css||(styleEl&&styleEl.isConnected))return;
          styleEl=document.createElement('style');
          styleEl.textContent=css;
          (document.head||document.documentElement).appendChild(styleEl);
        }catch(e){}
      };
      var canHide=function(e,allowPost){
        if(!e||!e.style||e===document.body||e===document.documentElement)return false;
        if(/^(MAIN|BODY|HTML|HEADER|NAV|FOOTER)$/i.test(e.tagName))return false;
        var role=e.getAttribute&&e.getAttribute('role');
        if(role==='main'||role==='navigation'||role==='banner'||role==='contentinfo'||e.id==='main-content')return false;
        if(e.querySelector&&e.querySelector('[slot="text-body"],shreddit-post-text-body'))return false;
        if(!allowPost&&postSel&&e.querySelector&&e.querySelector(postSel))return false;
        if(allowPost&&postSel&&e.querySelectorAll&&e.querySelectorAll(postSel).length>1)return false;
        return true;
      };
      var hide=function(e,allowPost){if(canHide(e,allowPost))e.style.setProperty('display','none','important')};
      var clean=function(t){return (t||'').replace(/[\u200B-\u200D\uFEFF\u00A0]/g,' ').trim()};
      var bar=function(e){
        // Climb only through wrappers that hold nothing else clickable: a site header that also has
        // the menu or search must stay, so stop at it.
        var clickable='a,button,input,select,textarea,[role=button],[role=search],[role=navigation]';
        while(e.parentElement&&e.parentElement!==document.body&&(e.parentElement.clientHeight||0)<=120&&!/^(HEADER|NAV|FOOTER)$/.test(e.parentElement.tagName)&&!/header|footer|nav/i.test(e.parentElement.tagName)&&e.parentElement.getAttribute('role')!=='navigation'&&!e.parentElement.querySelector('nav,header,footer,[role="navigation"]')&&e.parentElement.querySelectorAll(clickable).length<=1){e=e.parentElement}
        return e;
      };
      var scanPromptsInRoot=function(root,host){
        if(!prompts)return;
        root.querySelectorAll('button,a,[role=button]').forEach(function(b){
          if(checkedPrompts.has(b)||b.getAttribute('data-cx-hidden'))return;
          if(!prompts.test(clean(b.textContent))){checkedPrompts.add(b);return;}
          b.setAttribute('data-cx-hidden','1');
          var d=b.closest('[role=dialog]');
          if(d){
            if(d.querySelector('input,form,textarea,[data-testid*="login"],[data-testid*="Login"],[data-testid*="auth"]'))return;
            hide(d,false);
            if(document.body)document.body.style.overflow='';
          }
          else if(host&&bar(b)===b.getRootNode().firstElementChild){hide(host,false)}
          else{hide(bar(b),false)}
        });
      };
      var shadowBannerSel='configured-xpromo-modal,xpromo-app-selector,xpromo-bottom-sheet,xpromo-bottom-bar,xpromo-nsfw-blocking-container,community-barrier,shreddit-blurred-container,faceplate-banner,shreddit-async-loader[bundlename*="xpromo"],shreddit-async-loader[bundlename="bottom_sheet"],shreddit-async-loader[bundlename*="nsfw_blocking"],shreddit-async-loader[bundlename*="nsfw-blocking"],shreddit-async-loader[bundlename*="nsfw_modal"],shreddit-async-loader[bundlename*="barrier"]';
      var walkShadowRoots=function(root){
        root.querySelectorAll(shadowBannerSel).forEach(function(el){
          if(el.tagName==='XPROMO-NSFW-BLOCKING-CONTAINER'&&el.shadowRoot){
            if(!el.shadowRoot.querySelector('slot')){
              try{el.shadowRoot.appendChild(document.createElement('slot'));}catch(e){}
            }
            var p=el.shadowRoot.querySelector('.prompt,.overlay,.bg-scrim,.viewInApp');
            if(p&&p.style){if(p.style.setProperty)p.style.setProperty('display','none','important');else p.style.display='none';}
          }
          if(el.shadowRoot){
            if(css&&!el.shadowRoot.getElementById('cx-shadow-hide')){
              try{var s=document.createElement('style');s.id='cx-shadow-hide';s.textContent=css;el.shadowRoot.appendChild(s)}catch(e){}
            }
            if(!checkedShadowHosts.has(el)){
              checkedShadowHosts.add(el);
              scanPromptsInRoot(el.shadowRoot,el);
            }
            walkShadowRoots(el.shadowRoot);
          }
        });
      };
      var scan=function(){
        ensureStyle();
        syncAuthAttr();
        if(isEditableFocused())return;
        lastScan=Date.now();
        var auth=isAuthPage();
        var immersive=isImmersive()||auth;
        try{
          if(postLabels&&postSel&&!immersive&&!isPostDetail()){
            document.querySelectorAll(postSel).forEach(function(p){
              if(p.getAttribute('data-cx-hidden'))return;
              var len=(p.textContent||'').length;
              var prevLen=scannedPosts.get(p);
              if(prevLen!==undefined&&Math.abs(len-prevLen)<25)return;
              scannedPosts.set(p,len);
              var t=p.querySelectorAll('span,div,a,p');
              for(var i=0;i<t.length;i++){
                var el=t[i];
                if(el.children.length)continue;
                var txt=clean(el.textContent);
                if(txt&&txt.length<=60&&postLabels.test(txt)&&!(excludeSel&&el.closest(excludeSel))){
                  var cell=(cellSel&&p.closest(cellSel))||p;
                  hide(cell,true);
                  cell.setAttribute('data-cx-hidden','1');
                  p.setAttribute('data-cx-hidden','1');
                  break;
                }
              }
            });
          }
          if(headingLabels&&!immersive){
            document.querySelectorAll('h1,h2,h3,h4,[role="heading"]').forEach(function(h){
              if(checkedHeadings.has(h)||h.getAttribute('data-cx-hidden'))return;
              var txt=clean(h.textContent);
              if(txt&&txt.length<=40&&headingLabels.test(txt)){
                var m=h.closest(moduleSel);
                if(m&&!m.querySelector('input,form,[data-testid*="login"],[data-testid*="Login"]')){
                  hide(m,false);
                  m.setAttribute('data-cx-hidden','1');
                }
                h.setAttribute('data-cx-hidden','1');
              }else{
                checkedHeadings.add(h);
              }
            });
          }
          if(tabLabels&&!auth){
            document.querySelectorAll('[role="tab"]').forEach(function(t){
              if(t.getAttribute('data-cx-hidden'))return;
              if(tabLabels.test(clean(t.textContent))){
                var w=t.closest('[role="presentation"]')||t;
                hide(w,false);
                w.setAttribute('data-cx-hidden','1');
                t.setAttribute('data-cx-hidden','1');
              }
            });
          }
          if(prompts&&!auth){
            scanPromptsInRoot(document,null);
            if(hasShadowDom)walkShadowRoots(document);
          }
        }catch(e){}
        try{
    __JS__
        }catch(e){}
      };
      scan();
      setTimeout(scan,400);
      setTimeout(scan,1500);
      var timer=0;
      // Scans wait until scrolling and typing pause, with a minimum gap so feeds scroll smoothly.
      var SCROLL_IDLE_MS=420;
      var MIN_SCAN_GAP_MS=hasShadowDom?550:350;
      var schedule=function(){
        if(timer)return;
        timer=setTimeout(function run(){
          var now=Date.now();
          var sinceScroll=now-lastScroll;
          if(sinceScroll<SCROLL_IDLE_MS){timer=setTimeout(run,SCROLL_IDLE_MS-sinceScroll);return}
          if(isEditableFocused()){timer=0;return}
          var sinceScan=now-lastScan;
          if(sinceScan<MIN_SCAN_GAP_MS){timer=setTimeout(run,MIN_SCAN_GAP_MS-sinceScan);return}
          timer=0;
          if(window.requestIdleCallback){
            requestIdleCallback(scan,{timeout:500});
          }else if(window.requestAnimationFrame){
            requestAnimationFrame(scan);
          }else{
            scan();
          }
        },320);
      };
      new MutationObserver(schedule).observe(document,{childList:true,subtree:true});
    })();
    """.trimIndent()

private fun calmMetricsCss(site: LiveWebSite): List<String> =
    when (site) {
        LiveWebSite.Instagram -> {
            listOf("a[href$=\"/liked_by/\"]", "section span:has(> a[href$=\"/liked_by/\"])")
        }

        LiveWebSite.X -> {
            listOf(
                "[data-testid=\"like\"] [data-testid=\"app-text-transition-container\"]",
                "[data-testid=\"retweet\"] [data-testid=\"app-text-transition-container\"]",
                "[data-testid=\"reply\"] [data-testid=\"app-text-transition-container\"]",
                "[data-testid=\"bookmark\"] [data-testid=\"app-text-transition-container\"]",
                "a[href$=\"/analytics\"]",
            )
        }

        LiveWebSite.LinkedIn -> {
            listOf(".social-details-social-counts")
        }

        LiveWebSite.Reddit -> {
            listOf("[data-testid=\"vote-count\"]", "faceplate-number")
        }

        LiveWebSite.Mastodon -> {
            listOf(".status__action-bar .icon-button__counter")
        }
    }

internal fun bridgeBookmarkMessage(bridgeToken: String = ""): String =
    if (bridgeToken.isEmpty()) "__crux_bm_tap__" else "__crux_${bridgeToken}_bm_tap__"

internal fun bridgePtrMessage(
    allow: Boolean,
    bridgeToken: String = "",
): String {
    val suffix = if (allow) "1" else "0"
    return if (bridgeToken.isEmpty()) "__crux_ptr:$suffix" else "__crux_${bridgeToken}_ptr:$suffix"
}

/**
 * Builds the one page script for [site] from the tweaks that are on, or null when there is
 * nothing to do. Pure: the same inputs always give the same script.
 */
internal fun buildSiteScript(
    site: LiveWebSite,
    enabled: Set<SiteTweak>,
    hideEngagementCounts: Boolean = false,
    bridgeToken: String = "",
): String? {
    val profile = site.scriptProfile()
    val rules = SiteTweak.forSite(site).filter { it in enabled }.map { it.rule }
    val metricRules = if (hideEngagementCounts) calmMetricsCss(site) else emptyList()
    val baseRules = if (site == LiveWebSite.X && rules.isEmpty() && metricRules.isEmpty()) emptyList() else profile.baseRules
    val css =
        (baseRules + (rules.flatMap { it.css } + metricRules).distinct().map { "$it{display:none!important}" })
            .joinToString(" ")
    val postLabels = rules.flatMap { it.postLabels }.distinct()
    val headingLabels = rules.flatMap { it.headingLabels }.distinct()
    val tabLabels = rules.flatMap { it.tabLabels }.distinct()
    val promptLabels = rules.flatMap { it.promptLabels }.distinct()
    val js = (listOf(profile.baseJs) + rules.map { it.js }).filter { it.isNotBlank() }.joinToString("\n")
    val nothingToDo =
        css.isBlank() && postLabels.isEmpty() && headingLabels.isEmpty() && tabLabels.isEmpty() &&
            promptLabels.isEmpty() && js.isBlank() && profile.initJs.isBlank()
    if (nothingToDo) return null
    return ENGINE_JS
        .replace("__INIT_JS__", profile.initJs)
        .replace("__CSS__", escapeJs(css))
        .replace("__POSTSEL__", escapeJs(profile.postSelector))
        .replace("__CELLSEL__", profile.cellSelector?.let(::escapeJs) ?: "null")
        .replace("__MODULESEL__", escapeJs(profile.moduleSelector))
        .replace("__EXCLUDESEL__", escapeJs(profile.labelExcludeSelector))
        .replace("__POSTLABELS__", escapeJs(labelPattern(postLabels)))
        .replace("__HEADINGLABELS__", escapeJs(labelPattern(headingLabels)))
        .replace("__TABLABELS__", escapeJs(labelPattern(tabLabels)))
        .replace("__PROMPTS__", escapeJs(labelPattern(promptLabels)))
        .replace("__BRIDGE_BM_TAP__", escapeJs(bridgeBookmarkMessage(bridgeToken)))
        .replace("__BRIDGE_PTR_0__", escapeJs(bridgePtrMessage(allow = false, bridgeToken = bridgeToken)))
        .replace("__BRIDGE_PTR_1__", escapeJs(bridgePtrMessage(allow = true, bridgeToken = bridgeToken)))
        .replace("__JS__", js)
}
