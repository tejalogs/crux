package org.crux.ui.screen.home

import java.security.SecureRandom
import java.util.Random

/**
 * Every name the page engine leaves where a site can see it: attributes, window property keys,
 * event types and the console bridge prefix. Each name is drawn on its own from a SecureRandom the
 * first time it is asked for and then kept for the process, so a script built for one tab and the
 * CSS or Kotlin that reads its marks always agree.
 *
 * Nothing links one name to another or to what it is for: no shared token, no hash of a public
 * string a site could recompute, and no fixed layout (lengths and the number of word parts vary).
 * No name contains "cx" or "crux", none starts with 'x' or ends with 'c' (so none spells "cx" next
 * to the text around it), and no two are the same.
 */
internal object PageMarkers {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
    private const val PART_MIN = 4
    private const val PART_MAX = 8
    private const val PROP_MIN = 6
    private const val PROP_MAX = 12

    private val random = SecureRandom()
    private val names = HashMap<String, String>()
    private val taken = HashSet<String>()

    /** A page attribute for [name], for example `data-qorvel` or `data-tibmo-akrusen`. */
    fun attr(name: String): String = nameFor("attr|$name") { newAttrName(random) }

    /** A window property key, element id or event type for [name]: a plain lowercase identifier. */
    fun prop(name: String): String = nameFor("prop|$name") { newPropName(random) }

    /** Start of every console bridge message, followed by the page's bridge token. */
    val consolePrefix: String = prop("console")

    /**
     * The console message for one bridge [kind]. Kotlin compares a received message only with this,
     * so the page token keeps another page's script from posting it.
     */
    fun bridgeMessage(
        kind: String,
        bridgeToken: String,
    ): String {
        val tail = prop("bridge|$kind")
        return if (bridgeToken.isEmpty()) "$consolePrefix:$tail" else "$consolePrefix:$bridgeToken:$tail"
    }

    /** `data-` and one or two random lowercase parts of 4 to 8 letters, joined by '-'. */
    internal fun newAttrName(random: Random): String {
        val parts = 1 + random.nextInt(2)
        return "data-" + (0 until parts).joinToString("-") { letters(random, PART_MIN, PART_MAX) }
    }

    /** 6 to 12 random lowercase letters. */
    internal fun newPropName(random: Random): String = letters(random, PROP_MIN, PROP_MAX)

    /** True when [name] can be used: it never spells cx or crux, alone or next to other text. */
    internal fun isClean(name: String): Boolean {
        val word = name.removePrefix("data-")
        return !name.contains("cx", ignoreCase = true) &&
            !name.contains("crux", ignoreCase = true) &&
            !word.startsWith("x") &&
            !word.endsWith("c")
    }

    @Synchronized
    private fun nameFor(
        key: String,
        make: () -> String,
    ): String =
        names.getOrPut(key) {
            generateSequence { make() }.first { isClean(it) && it !in taken }.also { taken += it }
        }

    private fun letters(
        random: Random,
        min: Int,
        max: Int,
    ): String {
        val length = min + random.nextInt(max - min + 1)
        return buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    }
}

/** The engine's own marks, built once from [PageMarkers] so CSS and JS use the same names. */
internal object EngineMarks {
    /**
     * On html: "1" while the URL is a sign-in, sign-up or recovery page. Set only on a page whose
     * script reads it (some Instagram and Reddit CSS), never on the other sites.
     */
    val auth: String = PageMarkers.attr("auth")

    /** On html (Instagram): "1" on /explore itself. */
    val explore: String = PageMarkers.attr("explore")

    /** On html (Instagram): "1" on a Reel. */
    val reels: String = PageMarkers.attr("reels")

    /** On a Reddit NSFW gate or overlay the NSFW tweak dismissed. */
    val nsfwDismissed: String = PageMarkers.attr("nsfw-dismissed")

    /** On html (Reddit): "1" while posts are loaded outside any gate. */
    val nsfwContent: String = PageMarkers.attr("nsfw-content")

    /** On a Reddit blurred container the NSFW tweak revealed. */
    val unblurred: String = PageMarkers.attr("unblurred")

    /**
     * Key passed to Symbol.for for the guard that keeps the engine from running twice in a document.
     * It is set only when the script runs after the page loaded (the evaluateJavascript fallback);
     * a document-start script runs once per document and leaves nothing on window.
     */
    val guard: String = PageMarkers.prop("engine")

    /** Event the bookmark script dispatches to ask the engine for the post tapped last. */
    val tapEvent: String = PageMarkers.prop("tap")
}

/**
 * What one tweak hides. Everything here is display-only: selectors become `display:none` rules
 * and text matches hide an element. Nothing blocks a request or rewrites fetch/XHR, because that
 * is what gets a signed-in account flagged.
 */
internal data class TweakRule(
    /** Selectors hidden with display:none, one rule each so one bad selector cannot drop the rest. */
    val css: List<String> = emptyList(),
    /** Complete CSS rules applied when this tweak is on (for layout or unblur rules other than display:none). */
    val rawCss: List<String> = emptyList(),
    /** Short texts that mark a whole post (or timeline cell) as unwanted, e.g. "Sponsored". */
    val postLabels: List<String> = emptyList(),
    /** Module headings; the module around a matching heading is hidden. */
    val headingLabels: List<String> = emptyList(),
    /** Tabs (role=tab) hidden by their label. */
    val tabLabels: List<String> = emptyList(),
    /** Button or link labels that mean "use the app"; their dialog or banner is hidden. */
    val promptLabels: List<String> = emptyList(),
    /**
     * Upsell button or link labels. Only the button itself is hidden, never its dialog or bar, and
     * never one whose own text, card (up to four levels up) or dialog mentions a limit, payment or billing
     * ([ACCOUNT_NOTICE_PATTERN]), so account warnings keep their explanation and action.
     */
    val buttonLabels: List<String> = emptyList(),
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
    /**
     * Regex source (plain syntax, matched case-insensitively against the URL path) for the page of
     * one opened post. On such a page post labels never hide the opened post. When the pattern has a
     * capture group it captures that post's id: the other posts there (replies) are still checked
     * and only the opened one is skipped. Without a group no post on the page is label-checked.
     */
    val postDetailPattern: String = "",
    /** Regex source for the URL paths where [TweakRule.tabLabels] apply; empty means every page. */
    val tabPathPattern: String = "",
    /**
     * JavaScript boolean expression over the candidate button `b`: true keeps an upsell button
     * ([TweakRule.buttonLabels]) the site uses for something else, such as a creator's own
     * Subscribe button. Re-checked on every scan, since the page around a button can change.
     */
    val upsellKeepJs: String = "false",
)

/**
 * Regex source for sign-in, sign-up, 2FA and account-recovery URL paths on every site. Shared by
 * the page engine's isAuthPage (JavaScript) and LiveWebSessionState (Kotlin), so the two lists
 * cannot drift. Plain syntax only (no lookbehind, no named groups, no backslashes) so the same
 * text is valid in both engines; match it case-insensitively against the URL path.
 */
internal const val AUTH_PATH_PATTERN: String =
    "(^|/)(i/flow|login|signin|sign-in|logout|signup|register|authwall|checkpoint|challenge|" +
        "uas/login|uas/login-submit|uas/request-password-reset|" +
        "accounts/login|accounts/onetap|accounts/password|accounts/emailsignup|accounts/suspended|" +
        "auth_platform|two_step|two_factor|account/access|account/login_verification|" +
        "sign_in|sign_up|sign_out|auth/password|auth/confirmation|oauth/authorize)(/|$)"

/** Attribute names, or type/reason values, that mark a Reddit community-barrier as NSFW. */
internal const val NSFW_BARRIER_ATTR_PATTERN: String = "nsfw|over-?18|over_18|mature"

/**
 * Value of an NSFW-named flag attribute that means "on": present with no value, "true" or "1". A
 * gate that renders `nsfw="false"` or `is-nsfw="0"` is not an NSFW gate.
 */
internal const val NSFW_FLAG_ON_PATTERN: String = "^(|true|1)$"

/** NSFW words in a free-form source or data-testid value; the bare word "mature" is not enough. */
internal const val NSFW_ID_VALUE_PATTERN: String = "nsfw|over-?18|over_18"

/** Barrier kinds that always keep their explanation, even when the value also says nsfw or mature. */
internal const val NON_NSFW_BARRIER_PATTERN: String = "quarantin|private|banned|gated|suspended"

/**
 * Heading or button text on an NSFW community-barrier. Every phrase has a space or "+", which a
 * Reddit community name cannot contain, so a name like r/maturegamers never matches.
 */
internal const val NSFW_BARRIER_TEXT_PATTERN: String = "18[+]|over 18|mature content|adult content"

/**
 * Words that mark an account notice (a limit, payment, billing or subscription warning). An upsell
 * button next to these words is left alone. Plain syntax so it is valid in Kotlin and JavaScript.
 */
internal const val ACCOUNT_NOTICE_PATTERN: String =
    "limit|payment|billing|billed|subscription|invoice|credit|card"

// Instagram: the page is a React app with hashed class names, so selectors use hrefs, roles and
// the visible labels Instagram itself shows ("Sponsored", "Suggested for you").
// Only hide the recommended grid on /explore itself — keep explicit user searches (/explore/tags/, /explore/search/).
// The tweak registers the flag as a route hook: Instagram's init script calls it the moment the path
// changes (tap, back, scroll, touch), so the flag never waits for a debounced scan, and a page whose
// tweak is off never carries the flag's name at all.
private val IG_EXPLORE_FLAG_JS =
    "if(!keep.igRouteHook)keep.igRouteHook=function(){var r=document.documentElement;if(!r)return;" +
        "if(/^\\/explore\\/?${'$'}/i.test(location.pathname||'')){if(r.getAttribute('${EngineMarks.explore}')!=='1')r.setAttribute('${EngineMarks.explore}','1');}" +
        "else if(r.hasAttribute('${EngineMarks.explore}'))r.removeAttribute('${EngineMarks.explore}');};" +
        "keep.igRouteHook();"

/** Post and Reel links on Explore, in every shape Instagram uses: /p/, /reel/, /username/p/, /username/reel/. */
internal val IG_EXPLORE_GRID_CSS: List<String> =
    listOf(
        "html[${EngineMarks.explore}=\"1\"] a[href^=\"/p/\"]",
        "html[${EngineMarks.explore}=\"1\"] a[href^=\"/reel/\"]",
        "html[${EngineMarks.explore}=\"1\"] main a[href*=\"/p/\"]",
        "html[${EngineMarks.explore}=\"1\"] main a[href*=\"/reel/\"]",
    )

/**
 * One opened Instagram post or reel (/p/abc/, /p/abc/comments/, /user/reel/abc/). No group: no post
 * on that page is label-checked, so a "Sponsored" or "Paid partnership" post the person opened
 * is never blanked under them.
 */
internal const val IG_POST_DETAIL_PATTERN: String = "^/(?:[^/]+/)?(?:p|reel)/[^/]+"

private val INSTAGRAM_PROFILE =
    SiteScriptProfile(
        postSelector = "article",
        labelExcludeSelector = "h1,textarea,[contenteditable]",
        postDetailPattern = IG_POST_DETAIL_PATTERN,
        baseRules =
            listOf(
                "html,body{overflow-x:clip!important;max-width:100%!important;overscroll-behavior-x:none!important}",
                "html,body,button,a,[role=\"button\"],input,textarea{touch-action:manipulation!important}",
                // Lock Reels to vertical panning so tapping an expanded Reel caption and swiping up
                // never shifts the viewport left into blank horizontal overflow space.
                "html[${EngineMarks.reels}=\"1\"],html[${EngineMarks.reels}=\"1\"] body{overflow-x:clip!important;max-width:100vw!important;touch-action:pan-y!important;overscroll-behavior-x:none!important}",
                "html[${EngineMarks.reels}=\"1\"] [id^=\"mount_0_0\"]{overflow-x:clip!important;max-width:100vw!important;touch-action:pan-y!important}",
                "html[${EngineMarks.reels}=\"1\"] main,html[${EngineMarks.reels}=\"1\"] section,html[${EngineMarks.reels}=\"1\"] article,html[${EngineMarks.reels}=\"1\"] button,html[${EngineMarks.reels}=\"1\"] a,html[${EngineMarks.reels}=\"1\"] [role=\"button\"]{max-width:100vw!important;touch-action:pan-y!important}",
                "html[${EngineMarks.reels}=\"1\"] main,html[${EngineMarks.reels}=\"1\"] section,html[${EngineMarks.reels}=\"1\"] article,html[${EngineMarks.reels}=\"1\"] [role=\"presentation\"],html[${EngineMarks.reels}=\"1\"] [role=\"dialog\"]{overflow-x:clip!important;max-width:100vw!important}",
                "html[${EngineMarks.reels}=\"1\"] span[dir=\"auto\"],html[${EngineMarks.reels}=\"1\"] h1,html[${EngineMarks.reels}=\"1\"] p{overflow-wrap:anywhere!important;max-width:100%!important}",
                "div._aagw{pointer-events:none!important}",
                "img{-webkit-user-select:auto!important;user-select:auto!important;pointer-events:auto!important}",
                "*{-webkit-tap-highlight-color:transparent!important}",
                "html,body{-webkit-font-smoothing:antialiased!important;-moz-osx-font-smoothing:grayscale!important}",
            ),
        // Runs once per document inside the engine, so its state lives in the engine's closure.
        initJs =
            """
            var igIsReel=function(){return /^\/reels?(\/|${'$'})|^\/[^\/]+\/reel(\/|${'$'})/i.test(location.pathname||'')};
            var igSyncReel=function(){
              var on=igIsReel();
              var flag=on?'1':null;
              if(document.documentElement&&document.documentElement.getAttribute('${EngineMarks.reels}')!==flag){
                if(flag)document.documentElement.setAttribute('${EngineMarks.reels}',flag);
                else document.documentElement.removeAttribute('${EngineMarks.reels}');
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
            igSyncReel();
            var igLastPath='';
            var igClampReel=function(ev){
              if(location.pathname!==igLastPath){
                igLastPath=location.pathname;
                igSyncReel();
                if(keep.igRouteHook)keep.igRouteHook();
              }else if(igIsReel()){
                igSyncReel();
              }
              if(!igIsReel())return;
              var t=ev&&ev.target;
              if(t&&t.nodeType===1&&t!==document.documentElement&&t!==document.body&&(t.scrollLeft||0)>0){
                if(!t.closest||!t.closest('[role="dialog"],[role="tablist"]')){
                  t.scrollLeft=0;
                }
              }
            };
            window.addEventListener('scroll',igClampReel,{capture:true,passive:true});
            window.addEventListener('touchstart',igClampReel,{capture:true,passive:true});
            window.addEventListener('touchend',igClampReel,{capture:true,passive:true});
            // A tap is seen before Instagram pushes the new path, so the path is read again just after.
            var igAfterTap=function(){igClampReel(null)};
            window.addEventListener('click',function(ev){igClampReel(ev);nLater(igAfterTap,0);nLater(igAfterTap,250);},{capture:true,passive:true});
            window.addEventListener('popstate',igClampReel,{passive:true});
            window.addEventListener('hashchange',igClampReel,{passive:true});
            """.trimIndent(),
        baseJs = "if(igSyncReel)igSyncReel();",
    )

/**
 * One post in LinkedIn's signed-in mobile feed. That markup uses hashed class names; the stable
 * handles are the list item role and its componentkey ("update-card-focus-..."), inside
 * div[role=list][data-testid="mainFeed"].
 */
internal const val LINKEDIN_MOBILE_POST_SELECTOR: String = "div[role=\"listitem\"][componentkey^=\"update-card\"]"

/** The post's own words in LinkedIn's signed-in mobile feed; never read as a label. */
internal const val LINKEDIN_MOBILE_POST_TEXT_SELECTOR: String = "[data-testid=\"expandable-text-box\"]"

private val LINKEDIN_PROFILE =
    SiteScriptProfile(
        // The desktop and guest selectors stay for the pages that still render them.
        postSelector =
            "div.feed-shared-update-v2,div[data-id*=\"activity\"],div[data-urn*=\"activity\"],article,$LINKEDIN_MOBILE_POST_SELECTOR",
        moduleSelector = "aside,div.feed-shared-news-module,div.feed-shared-update-v2,$LINKEDIN_MOBILE_POST_SELECTOR",
        labelExcludeSelector =
            ".update-components-text,.feed-shared-text,[data-test-id=\"main-feed-activity-card__commentary\"],$LINKEDIN_MOBILE_POST_TEXT_SELECTOR",
        baseRules =
            listOf(
                "html,body{overflow-x:clip!important;max-width:100%!important;overscroll-behavior-x:none!important}",
                "html,body,button,a,[role=\"button\"],input,textarea{touch-action:manipulation!important}",
                "*{-webkit-tap-highlight-color:transparent!important}",
                "html,body{-webkit-font-smoothing:antialiased!important;-moz-osx-font-smoothing:grayscale!important}",
                "div.feed-shared-update-v2:active{background-color:rgba(128,128,128,0.04)!important}",
                "$LINKEDIN_MOBILE_POST_SELECTOR:active{background-color:rgba(128,128,128,0.04)!important}",
            ),
        // A submitted sign-in form is remembered for the rest of the document in the engine's closure.
        initJs = "var liSubmitted=false;document.addEventListener('submit',function(){liSubmitted=true},true);",
        baseJs =
            """
            if(!liSubmitted&&/^\/(uas\/)?login/.test(location.pathname)){
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

/**
 * Parts of an X post whose words are never read as a label: the post's text, the links of its
 * author line (display name, handle, time), and its media, cards and polls. A video pre-roll inside
 * an organic post carries its own small "Ad" badge in the player, which must never hide the post
 * around it. The author line itself is not excluded, only its links: X may put the "Ad" label in
 * that line, and a person's name is always a link there, so a name like "Ad" is never read.
 */
internal const val X_LABEL_EXCLUDE_SELECTOR: String =
    "[data-testid=\"tweetText\"],[data-testid=\"User-Name\"] a,[data-testid=\"videoPlayer\"],[data-testid=\"videoComponent\"]," +
        "[data-testid=\"tweetPhoto\"],[data-testid=\"card.wrapper\"],[data-testid=\"cardPoll\"]"

/**
 * The end of an X post path: the post itself or its photo or video viewer, never the lists hung
 * off it (/retweets, /likes, /quotes, /retweets/with_comments), whose rows are their content.
 */
private const val X_POST_PATH_END: String = "(?:/(?:photo|video)/[0-9]+)?/?$"

/** One opened X post (/user/status/123, /i/web/status/123); the group captures the post's id. */
internal const val X_POST_DETAIL_PATTERN: String = "^/(?:i/web|[^/]+)/status/([0-9]+)$X_POST_PATH_END"

/**
 * X timelines where a module's rows follow its heading as sibling cells and are never what the
 * person came for: home and a post's page. Never followers, following, search or connect pages,
 * where account rows are the content.
 */
internal const val X_MODULE_WALK_PATTERN: String = "^/home/?$|^/(?:i/web|[^/]+)/status/[0-9]+$X_POST_PATH_END"

/** X's tab labels are hidden only on the home timeline (never Explore's own "For You"). */
internal const val X_TAB_PATH_PATTERN: String = "^/home/?$"

/**
 * Upsell buttons X uses for something else stay: anything inside a post, anything on a creator
 * subscriptions page, and on a profile page any "Subscribe" outside the menu that is not a link
 * to Premium itself (a creator's own Subscribe button).
 */
private const val X_UPSELL_KEEP_JS =
    "b.closest('article')||/(^|\\/)creator-subscriptions(\\/|$)|^\\/i\\/(monetization|subscriptions)(\\/|$)/i.test(location.pathname||'')||" +
        "(!!document.querySelector('[data-testid=\"UserName\"]')&&!b.closest('nav,[role=\"navigation\"]')&&" +
        "!/\\/i\\/premium|premium_sign_up|\\/i\\/verified/i.test(b.getAttribute('href')||''))"

private val X_PROFILE =
    SiteScriptProfile(
        postSelector = "article[data-testid=\"tweet\"]",
        cellSelector = "div[data-testid=\"cellInnerDiv\"]",
        moduleSelector = "div[data-testid=\"cellInnerDiv\"],aside,section",
        labelExcludeSelector = X_LABEL_EXCLUDE_SELECTOR,
        postDetailPattern = X_POST_DETAIL_PATTERN,
        tabPathPattern = X_TAB_PATH_PATTERN,
        upsellKeepJs = X_UPSELL_KEEP_JS,
        baseRules =
            listOf(
                "html,body{overflow-x:clip!important;max-width:100%!important;overscroll-behavior-x:none!important}",
                "html,body,button,a,[role=\"button\"],input,textarea{touch-action:manipulation!important}",
                "*{-webkit-tap-highlight-color:transparent!important}",
                "html,body{-webkit-font-smoothing:antialiased!important;-moz-osx-font-smoothing:grayscale!important}",
            ),
    )

/**
 * Reddit's app nags and in-post NSFW prompts: never a modal the person has to answer. The generic
 * "View in Reddit App" sheet is a plain div (id xpromo-bottom-sheet, class rpl-bottom-sheet,
 * display:contents), not the xpromo-bottom-sheet element.
 */
internal val REDDIT_NAG_SHEET_SELECTOR: String =
    "#xpromo-bottom-sheet,[id=\"xpromo-bottom-sheet\"],xpromo-bottom-sheet,xpromo-app-selector,xpromo-nsfw-blocking-container,[${EngineMarks.nsfwDismissed}]"

/** Links that open the Reddit app (AppsFlyer onelink or Branch app.link). */
internal const val REDDIT_APP_LINK_SELECTOR: String = "a[href*=\"onelink.me\"],a[href*=\"app.link\"]"

/**
 * A bottom sheet that asks for input (sign-in, account, report) is never an app nag, even when it
 * also offers the app.
 */
internal const val REDDIT_SHEET_FORM_SELECTOR: String = "input,form,faceplate-text-input,[type=\"password\"]"

/** A bottom sheet with no input in it: only such a sheet may be hidden as an app nag. */
internal const val REDDIT_APP_SHEET_GUARD: String =
    ".rpl-bottom-sheet:not(:has(input)):not(:has(form)):not(:has(faceplate-text-input))"

/** Attribute the Reddit script sets on html while no genuine modal is open; CSS then lets body scroll. */
internal val REDDIT_SCROLL_FREE_ATTR: String = PageMarkers.attr("scroll-free")

/** Attribute on a post's media box whose NSFW embed Reddit withheld; the empty box is collapsed. */
internal val REDDIT_NSFW_EMPTY_ATTR: String = PageMarkers.attr("nsfw-empty")

/**
 * How long a gated NSFW media box may stay empty before it counts as withheld, so a player that is
 * still loading is not collapsed.
 */
internal const val REDDIT_NSFW_EMPTY_GRACE_MS: Int = 2500

/**
 * JS expression: true while a Reddit dialog the person still has to answer is open, so page scroll
 * and inert are left as Reddit set them. Reddit's NSFW gate and quarantine notice (#blocking-modal,
 * #nsfw-qr-dialog) count while they are visible; only one the NSFW tweak dismissed
 * ([EngineMarks.nsfwDismissed]) does not, so with the tweak off the page behind the gate never scrolls free.
 * Alert dialogs, aria-modal elements, faceplate-dialog and any other visible rpl-bottom-sheet (share,
 * sort, report, sign-in) are genuine. App nags are not: the generic "View in Reddit App" sheet
 * (#xpromo-bottom-sheet, or an rpl-bottom-sheet that links to the app and asks for no input), the
 * in-post NSFW media prompt and anything inside a gate the tweak dismissed, also when the dialog
 * sits in one of their shadow roots.
 */
internal val REDDIT_OPEN_OVERLAY_JS: String =
    "(function(){" +
        "var nag=function(n){while(n){if(n.closest){if(n.closest('$REDDIT_NAG_SHEET_SELECTOR'))return true;" +
        "var b=n.closest('.rpl-bottom-sheet');if(b&&b.querySelector('$REDDIT_APP_LINK_SELECTOR')&&!b.querySelector('$REDDIT_SHEET_FORM_SELECTOR'))return true;}" +
        "var r=n.getRootNode&&n.getRootNode();n=r&&r.host;}return false;};" +
        // display:contents has no box of its own: its light or shadow children show whether it is up.
        "var vis=function(e){if(e.getClientRects().length)return true;" +
        "try{if(getComputedStyle(e).display!=='contents')return false;}catch(x){return false;}" +
        "var k=[].slice.call(e.children).concat(e.shadowRoot?[].slice.call(e.shadowRoot.children):[]);" +
        "for(var j=0;j<k.length;j++){if(k[j].getClientRects().length)return true;}return false;};" +
        "var o=document.querySelectorAll('[role=\"dialog\"]:not([${EngineMarks.nsfwDismissed}]),[role=\"alertdialog\"]:not([${EngineMarks.nsfwDismissed}]),[aria-modal=\"true\"]:not([${EngineMarks.nsfwDismissed}]),dialog[open]:not([${EngineMarks.nsfwDismissed}]),faceplate-dialog:not([${EngineMarks.nsfwDismissed}]),faceplate-modal[open]:not([${EngineMarks.nsfwDismissed}]),#blocking-modal:not([${EngineMarks.nsfwDismissed}]),#nsfw-qr-dialog:not([${EngineMarks.nsfwDismissed}]),.rpl-bottom-sheet:not([${EngineMarks.nsfwDismissed}]),reddit-search-large[open],reddit-search-large:focus-within');" +
        "for(var i=0;i<o.length;i++){var e=o[i];" +
        "if(hiddenEls.has(e)||nag(e))continue;" +
        // An open search counts as it is; any other candidate only while it is on screen and not
        // inside a gate the NSFW tweak dismissed.
        "if(e.tagName!=='REDDIT-SEARCH-LARGE'&&(!vis(e)||(e.closest&&e.closest('[${EngineMarks.nsfwDismissed}]'))))continue;" +
        "return true;}" +
        "return false;})()"

/**
 * Reddit withholds the player of an NSFW post on mobile web unless the signed-in account has its
 * own "Show mature content" setting on; xpromo-nsfw-blocking-container then wraps an empty embed and
 * an app prompt. Display only: the empty media box is collapsed, never the gate host itself, and the
 * app is told once per URL so it can explain the setting. Nothing is rendered in the embed's place.
 */
private val REDDIT_NSFW_MEDIA_JS =
    """
    if(hasShadowDom){
      var qSized=function(e){var r=e.getBoundingClientRect();return r.width>2&&r.height>2;};
      // Media that is really there: a sized iframe, video or image (a 1x1 pixel or a hidden poster
      // is not). Nothing in a box this script collapsed has a size, so there only a loaded player
      // counts: an iframe with a real src, or a video with frames.
      var qIsMedia=function(e,collapsed){
        var t=e.tagName;
        if(t==='IFRAME'){var src=e.getAttribute('src')||'';return collapsed?(!!src&&!/^about:/i.test(src)):qSized(e);}
        if(t==='VIDEO')return (e.videoWidth||0)>2||(!collapsed&&qSized(e));
        if(t==='IMG')return !collapsed&&qSized(e)&&!(e.complete&&(e.naturalWidth||0)<=2);
        if(t==='SHREDDIT-PLAYER-2'||t==='SHREDDIT-PLAYER'||t==='GALLERY-CAROUSEL'||t==='SHREDDIT-EMBED'){
          return !!(e.getAttribute('src')||e.getAttribute('packaged-media-json')||(!collapsed&&qSized(e)));
        }
        return false;
      };
      // Media inside the gate's default slot, or in a component's shadow root. Light children
      // assigned to a named slot (the view-in-app button) are not media.
      var qDeepMedia=function(root,d,collapsed){
        var all=root.querySelectorAll('*');
        for(var i=0;i<all.length;i++){
          var e=all[i];
          if(d===0){var s=e.closest('[slot]');if(s&&s!==root&&root.contains(s)&&s.getAttribute('slot'))continue;}
          if(qIsMedia(e,collapsed))return true;
          if(e.shadowRoot&&d<3&&qDeepMedia(e.shadowRoot,d+1,collapsed))return true;
        }
        return false;
      };
      // Reddit reuses the container for posts it lets through; it gates one only while its shadow
      // prompt is up. The class is read, not the computed display: with the NSFW tweak on, the
      // engine hides the prompt itself.
      var qGatedNow=function(c){
        var p=c.shadowRoot?c.shadowRoot.querySelectorAll('.prompt'):[];
        for(var i=0;i<p.length;i++){if(!p[i].classList.contains('hidden'))return true;}
        return false;
      };
      var qInView=function(el){
        var r=el.getBoundingClientRect(),vh=window.innerHeight||document.documentElement.clientHeight||0;
        return r.height>0&&r.bottom>0&&r.top<vh;
      };
      // The box to collapse: the post's media container, or else the nearest wrapper that holds only
      // the media. Never the gate host itself, and never anything holding the post or its text.
      var qMediaBox=function(c){
        var box=c.closest('[slot="post-media-container"]');
        if(box)return box;
        var w=c.closest('div[id${'$'}="-aspect-ratio"],shreddit-aspect-ratio')||c.parentElement;
        if(!w||w===document.body||w===document.documentElement)return null;
        if(w.matches('shreddit-post,article,[slot="text-body"],[slot="title"]'))return null;
        if(w.querySelector('shreddit-post,[slot="text-body"],[slot="title"],shreddit-post-text-body'))return null;
        return w;
      };
      var qSeen=keep.nsfwSeen||(keep.nsfwSeen=new WeakMap());
      var qHealed=keep.nsfwHealed||(keep.nsfwHealed=new WeakSet());
      var qNow=Date.now(),qCollapsed=false,qWait=false,qOff=false;
      document.querySelectorAll('xpromo-nsfw-blocking-container').forEach(function(c){
        var box=qMediaBox(c);
        var was=!!(box&&box.hasAttribute('$REDDIT_NSFW_EMPTY_ATTR'));
        var gated=qGatedNow(c);
        if(!gated||qDeepMedia(c,0,was)){
          // Reddit let the media through, or the player showed up: the box comes back, and one that
          // came back with a player is never collapsed again.
          if(was){box.removeAttribute('$REDDIT_NSFW_EMPTY_ATTR');if(gated)qHealed.add(c);}
          qSeen.delete(c);
          return;
        }
        if(!box||qHealed.has(c))return;
        if(was){qCollapsed=true;return;}
        // The grace period starts only once the box is on screen, so a player that loads on view
        // gets its chance.
        var first=qSeen.get(c);
        if(first===undefined){
          if(!qInView(box)){qOff=true;return;}
          first=qNow;qSeen.set(c,first);
        }
        if(qNow-first<$REDDIT_NSFW_EMPTY_GRACE_MS){qWait=true;return;}
        box.setAttribute('$REDDIT_NSFW_EMPTY_ATTR','1');
        qCollapsed=true;
      });
      if(qWait&&!keep.nsfwRecheck){
        keep.nsfwRecheck=nLater(function(){keep.nsfwRecheck=0;schedule();},${REDDIT_NSFW_EMPTY_GRACE_MS + 100});
      }
      if(qOff&&!keep.nsfwScrollHook){
        keep.nsfwScrollHook=1;
        try{nListen.call(window,'scroll',function(){schedule();},{capture:true,passive:true});}catch(e){}
      }
      if(qCollapsed&&keep.nsfwNoteUrl!==location.href){
        keep.nsfwNoteUrl=location.href;
        nPost(__BRIDGE_NSFW_NOTE__);
      }
    }
    """.trimIndent()

private val REDDIT_PROFILE =
    SiteScriptProfile(
        postSelector = "shreddit-post,[data-testid=\"ad-post\"],[data-adclicklocation]",
        // A comments page is never label-checked at all, as before.
        postDetailPattern = "/comments/",
        baseRules =
            listOf(
                // Keep document root scrolling on Chromium's compositor thread; setting overflow-y:auto
                // on both html and body creates nested scroll containers and lags Reddit feed scrolling.
                "html{overflow-x:clip!important}",
                "html,body,button,a,[role=\"button\"]{touch-action:manipulation!important}",
                "shreddit-post{-webkit-tap-highlight-color:transparent!important}",
                "[slot=\"post-media-container\"] .relative{height:100%!important}",
                // Reddit locks body (rpl-scroll-lock) for its app sheet and NSFW prompt too; while no
                // genuine modal is open the script sets the flag and the page keeps scrolling.
                "html[$REDDIT_SCROLL_FREE_ATTR=\"1\"] body.rpl-scroll-lock{overflow:visible!important}",
                // A media box whose NSFW player Reddit withheld shows nothing but an app prompt.
                "[slot=\"post-media-container\"][$REDDIT_NSFW_EMPTY_ATTR]{display:none!important}",
                // A post without a media container collapses the wrapper around its gate; the gate host
                // itself is never hidden.
                "[$REDDIT_NSFW_EMPTY_ATTR]:not(xpromo-nsfw-blocking-container){display:none!important}",
            ),
        initJs =
            """
            var rsState=new WeakMap();
            var rsActiveEl=function(){
              var a=document.activeElement;
              while(a&&a.shadowRoot&&a.shadowRoot.activeElement){a=a.shadowRoot.activeElement;}
              return a;
            };
            var rsSearchInput=function(ev){
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
            var rsFixCaret=function(el,expected){
              var restore=function(){
                try{
                  var v=el.value||'';
                  if(el===rsActiveEl()&&typeof el.selectionStart==='number'&&el.selectionStart===0&&el.selectionEnd===0&&expected>0&&expected<=v.length){
                    el.setSelectionRange(expected,expected);
                  }
                }catch(e){}
              };
              nMicro(restore);
              nLater(restore,0);
            };
            document.addEventListener('beforeinput',function(ev){
              var el=rsSearchInput(ev);
              if(!el)return;
              if(ev.inputType==='insertText'||ev.inputType==='insertCompositionText'){
                var prevVal=el.value||'';
                var prevSel=typeof el.selectionStart==='number'?el.selectionStart:prevVal.length;
                var insLen=typeof ev.data==='string'&&ev.data.length>0?ev.data.length:1;
                rsState.set(el,{prevVal:prevVal,prevSel:prevSel,insLen:insLen,active:true});
              }else{
                rsState.delete(el);
              }
            },true);
            document.addEventListener('input',function(ev){
              var el=rsSearchInput(ev);
              if(!el)return;
              var st=rsState.get(el);
              if(!st||!st.active)return;
              st.active=false;
              var val=el.value||'';
              if(val.length<=st.prevVal.length)return;
              var delta=val.length-st.prevVal.length;
              var expected=st.prevSel+Math.max(st.insLen,delta);
              if(expected>val.length)expected=val.length;
              rsFixCaret(el,expected);
            },true);
            document.addEventListener('compositionend',function(ev){
              var el=rsSearchInput(ev);
              if(!el)return;
              var val=el.value||'';
              if(val.length>0&&typeof el.selectionStart==='number'&&el.selectionStart===0&&el.selectionEnd===0){
                rsFixCaret(el,val.length);
              }
            },true);
            """.trimIndent(),
        baseJs =
            """
            var hasOpenOverlay=isEditableFocused()||$REDDIT_OPEN_OVERLAY_JS;
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
            var qRoot=document.documentElement;
            if(qRoot){
              var qFree=qRoot.getAttribute('$REDDIT_SCROLL_FREE_ATTR')==='1';
              if(hasOpenOverlay){if(qFree)qRoot.removeAttribute('$REDDIT_SCROLL_FREE_ATTR');}
              else if(!qFree)qRoot.setAttribute('$REDDIT_SCROLL_FREE_ATTR','1');
            }
            """.trimIndent() + "\n" + REDDIT_NSFW_MEDIA_JS,
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

// Never on sign-in pages, and never a dialog with a field in it: a code-entry or 2FA step that also
// shows "Get the app" badges must stay visible.
private const val IG_APP_PROMPT_JS =
    "if(!isAuthPage())document.querySelectorAll('div[role=dialog]').forEach(function(d){" +
        "if(d.querySelector('input,form,textarea'))return;" +
        "if(d.querySelector('a[href*=\"play.google.com\"],a[href*=\"apps.apple.com\"],a[href*=\"instagram://\"],a[href*=\"onelink.me\"]')){" +
        "d.style.setProperty('display','none','important');" +
        "if(document.body)document.body.style.overflow='';}});"

/** Appended to an Instagram selector: never a field, form or search box, nor anything holding one. */
internal const val IG_NOT_A_FIELD: String =
    ":not(input):not(textarea):not(form):not([role=\"search\"]):not([role=\"searchbox\"]):not([role=\"combobox\"])" +
        ":not([role=\"textbox\"]):not([contenteditable]):not(:has(input,textarea,form,[role=\"search\"],[role=\"searchbox\"]," +
        "[role=\"combobox\"],[role=\"textbox\"],[contenteditable]))"

/** Titles of the people and Reels carousels Instagram puts between feed posts. */
internal val IG_SUGGESTED_BLOCK_LABELS = listOf("Suggested for you", "Suggested reels")

/**
 * The "Suggested for you" profile carousel (and "Suggested reels" strip) between feed posts. It
 * sits outside any article, so post labels never reach it. From its title or its "See all" link
 * the script climbs to the largest wrapper that holds no post, and hides that wrapper only when
 * its parent holds the feed's posts, it holds no field, dialog, nav or main, and it is no taller
 * than one and a half screens. Never on Instagram's own people page, a sign-in page or a Reel.
 */
private val IG_SUGGESTED_BLOCK_JS =
    """
    (function(){
      if(isImmersive()||isAuthPage()||/^\/explore\/people/i.test(location.pathname||''))return;
      var main=document.querySelector('main,[role="main"]');
      if(!main)return;
      var seen=keep.igSuggestSeen||(keep.igSuggestSeen=new WeakSet());
      var lx=new RegExp(${escapeJs(labelPattern(IG_SUGGESTED_BLOCK_LABELS))},'i');
      var found=[].slice.call(main.querySelectorAll('a[href^="/explore/people"]'));
      main.querySelectorAll('span,h2,h3,h4,[role="heading"]').forEach(function(s){
        if(seen.has(s))return;
        if(s.children.length){seen.add(s);return;}
        var txt=clean(s.textContent);
        if(txt&&txt.length<=40&&lx.test(txt))found.push(s);else seen.add(s);
      });
      var vh=window.innerHeight||0;
      found.forEach(function(el){
        if(hiddenEls.has(el)||el.closest('article,[role="dialog"],nav,header,form'))return;
        var box=el;
        while(box.parentElement&&box.parentElement!==main&&!box.parentElement.querySelector('article'))box=box.parentElement;
        var p=box.parentElement;
        if(!p||hiddenEls.has(box)||!p.querySelector('article'))return;
        if(box.querySelector('article,input,textarea,form,main,nav,[role="dialog"],[role="main"]'))return;
        if(vh&&(box.offsetHeight||0)>vh*1.5)return;
        if(hide(box,false)){hiddenEls.add(box);hiddenEls.add(el);}
      });
    })();
    """.trimIndent()

/** Instagram app-prompt dialog: never on a sign-in page and never one holding a field. */
private fun igAppDialog(has: String): String =
    "html:not([${EngineMarks.auth}=\"1\"]) div[role=\"dialog\"]:not(:has(input,form,textarea)):has($has)"

/**
 * Reddit's own Log In / Sign Up controls are hidden only in the page chrome. Inside a dialog (the
 * auth modal a signed-out tap opens) they stay, so nobody gets stuck in the sign-up sheet.
 */
private const val OUTSIDE_DIALOGS =
    ":not(dialog *):not([role=\"dialog\"] *):not([aria-modal=\"true\"] *):not(faceplate-modal *):not(faceplate-dialog *)"

/**
 * Hides X's header "Subscribe" / "Get Premium" button by its own text. Only the link or button
 * itself is hidden, never a wrapper, so a header that holds the menu or search stays.
 */
internal val X_PREMIUM_BUTTON_LABELS = listOf("Subscribe", "Get Premium", "Subscribe to Premium", "Try Premium")

/**
 * Every X Premium or verification link the guarded scan looks at. Outside the menus none of these
 * goes by CSS: a Premium link can be the action of a limit or payment notice.
 */
internal const val X_PREMIUM_LINK_SELECTOR: String =
    "a[href*=\"/i/premium\"],a[href*=\"premium_sign_up\"],a[href*=\"/i/verified\"],a[href*=\"/verified-orgs-signup\"]," +
        "a[aria-label=\"Get Premium\"],[data-testid=\"premium-signup-tab\"]"

/**
 * A cell X marks with its ad impression pixel, unless it holds the post opened on its own page
 * (X renders that one with tabindex -1), so an ad the person opened stays readable.
 */
internal const val X_IMPRESSION_PIXEL_CELL: String =
    "div[data-testid=\"cellInnerDiv\"]:has([data-testid=\"top-impression-pixel\"]):not(:has(article[tabindex=\"-1\"]))"

/**
 * Links to X Premium or verification outside the menus. Each goes only by itself, never inside a
 * post, never when its own words or its card mention a limit, payment, billing or subscription
 * ([ACCOUNT_NOTICE_PATTERN]), and never on X's settings, Premium or monetization pages, so a
 * payment-failed or lapsed-subscription notice keeps its action. A link once found inside a post is
 * not read again.
 */
private val X_PREMIUM_LINK_JS =
    """
    (function(){
      if(/^\/(settings|i\/premium|i\/verified|i\/monetization)(\/|_|${'$'})/i.test(location.pathname||''))return;
      var seen=keep.xPremiumSeen||(keep.xPremiumSeen=new WeakSet());
      document.querySelectorAll('$X_PREMIUM_LINK_SELECTOR').forEach(function(a){
        if(seen.has(a)||hiddenEls.has(a))return;
        if(a.closest('article')){seen.add(a);return;}
        if(accountNotice){
          if(accountNotice.test(clean(a.textContent)))return;
          var ctx=noticeCtx(a);
          if(ctx&&accountNotice.test(clean(ctx.textContent)))return;
        }
        if(hide(a,false))hiddenEls.add(a);
      });
    })();
    """.trimIndent()

/** Headings of X's account-suggestion modules in the timeline. */
internal val X_WHO_TO_FOLLOW_LABELS = listOf("Who to follow", "Relevant people", "Topics to follow", "Creators for you")

/** A timeline cell of X's: the rows of a module are separate sibling cells after the heading's. */
private const val X_CELL = "div[data-testid=\"cellInnerDiv\"]"

/** Appended to an X app-prompt selector: never a sheet or layer that holds a field or form. */
internal const val X_NO_FIELD: String = ":not(:has(input,form))"

/** A cell that ends a module: a post, a separator, or anything with a field. */
internal const val X_MODULE_END_SELECTOR: String = "article[data-testid=\"tweet\"],[role=\"separator\"],input,form,textarea"

/** A cell that is one of a suggestion module's rows: an account, its "Show more" link or a topic. */
internal const val X_SUGGESTION_ROW_SELECTOR: String =
    "[data-testid=\"UserCell\"],a[href^=\"/i/connect_people\"],a[href*=\"/i/topics/\"]"

/**
 * X's mobile timeline renders "Who to follow" as a heading cell followed by separate cells for each
 * account and the "Show more" link. Once the engine has hidden the heading's cell, this hides the
 * following sibling cells while each is a suggestion row, and stops at the first cell that is a
 * post, a separator, a field or anything else. Only on home and a post's page
 * ([X_MODULE_WALK_PATTERN]), never on followers, following or people search.
 */
private val X_WHO_TO_FOLLOW_ROWS_JS =
    """
    (function(){
      var wx=keep.xWalkRx||(keep.xWalkRx=new RegExp(${escapeJs(X_MODULE_WALK_PATTERN)},'i'));
      if(!wx.test(location.pathname||''))return;
      var hx=keep.xFollowRx||(keep.xFollowRx=new RegExp(${escapeJs(labelPattern(X_WHO_TO_FOLLOW_LABELS))},'i'));
      var cs='$X_CELL';
      document.querySelectorAll(cs+' h2,'+cs+' [role="heading"]').forEach(function(h){
        if(h.closest('article'))return;
        var txt=clean(h.textContent);
        if(!txt||txt.length>40||!hx.test(txt))return;
        var c=h.closest(cs);
        if(!c||!hiddenEls.has(c))return;
        for(var n=c.nextElementSibling;n&&n.matches&&n.matches(cs);n=n.nextElementSibling){
          if(hiddenEls.has(n))continue;
          if(n.querySelector('$X_MODULE_END_SELECTOR'))break;
          if(!n.querySelector('$X_SUGGESTION_ROW_SELECTOR'))break;
          if(hide(n,false))hiddenEls.add(n);else break;
        }
      });
    })();
    """.trimIndent()

/** Heading of the recommendations X adds under a post once its replies end. */
internal val X_DISCOVER_MORE_LABELS = listOf("Discover more*", "Sourced from across X")

/** One opened X post's page, where "Discover more" appears. */
internal const val X_POST_PAGE_PATTERN: String = "^/(?:i/web|[^/]+)/status/[0-9]+$X_POST_PATH_END"

/**
 * How many recommendation cells one post's page collapses. X loads the next page of its list when
 * the end of the list comes near the screen, and collapsed cells take no room, so collapsing every
 * one would keep the end in view and make X fetch page after page while the person reads. Up to
 * this many collapse, so a short run ends the page cleanly; every later one only turns invisible
 * and keeps its height, so the end moves away and X fetches no more than it would with the tweak
 * off, only as the person scrolls.
 */
internal const val X_DISCOVER_MORE_MAX_COLLAPSED: Int = 10

/**
 * On a post's page, X ends the replies with a "Discover more" heading ("Sourced from across X")
 * and then recommended posts from strangers, each in its own sibling cell. This hides the
 * heading's cell and the run of cells after it; the replies above it are never touched.
 *
 * The run takes only a cell with exactly one post, a cell that is only X's loading spinner, and
 * steps over empty spacer cells. Any other cell ends it: a "Show more replies" or "Retry" cell, a
 * button outside a post, a separator, a field, or a cell with two or more posts. The first
 * [X_DISCOVER_MORE_MAX_COLLAPSED] cells collapse; later ones keep their height but are invisible
 * (visibility, plus opacity so a child X marks visible stays unseen) and take no taps.
 * Display only: nothing is clicked or loaded.
 *
 * X's list unmounts cells far above the screen, the heading's among them, so the last cell the run
 * reached is remembered for this post's path and the next scan carries on from it while it is still
 * on the page.
 */
internal val X_DISCOVER_MORE_JS =
    """
    (function(){
      var path=location.pathname||'';
      var px=keep.xPostRx||(keep.xPostRx=new RegExp(${escapeJs(X_POST_PAGE_PATTERN)},'i'));
      if(!px.test(path))return;
      var dx=keep.xDiscoverRx||(keep.xDiscoverRx=new RegExp(${escapeJs(labelPattern(X_DISCOVER_MORE_LABELS))},'i'));
      var cs='$X_CELL';
      var last=keep.xDiscoverLast;
      if(!last||last.path!==path)last=keep.xDiscoverLast={path:path,cell:null,collapsed:0};
      var c=null;
      var hs=document.querySelectorAll(cs+' h2,'+cs+' [role="heading"]');
      for(var i=0;i<hs.length;i++){
        var h=hs[i];
        if(h.closest('article'))continue;
        var txt=clean(h.textContent);
        if(!txt||txt.length>60||!dx.test(txt))continue;
        var hc=h.closest(cs);
        if(!hc)continue;
        if(!hiddenEls.has(hc)){
          if(hc.querySelector('input,form,textarea')||!hide(hc,false))continue;
          hiddenEls.add(hc);
        }
        c=hc;
        break;
      }
      if(!c&&last.cell&&last.cell.isConnected&&hiddenEls.has(last.cell))c=last.cell;
      if(!c)return;
      var buttonOutsidePost=function(n){
        var b=n.querySelectorAll('button,[role="button"]');
        for(var j=0;j<b.length;j++){if(!b[j].closest('article'))return true;}
        return false;
      };
      var reached=c;
      for(var n=c.nextElementSibling;n&&n.matches&&n.matches(cs);n=n.nextElementSibling){
        if(hiddenEls.has(n)){reached=n;continue;}
        if(n.querySelector('input,form,textarea,[role="separator"]')||buttonOutsidePost(n))break;
        var posts=n.querySelectorAll('article[data-testid="tweet"]').length;
        if(posts===0){
          if(!n.querySelector('[role="progressbar"]')){
            if(!clean(n.textContent)&&!n.querySelector('a,img,video'))continue;
            break;
          }
        }else if(posts>1)break;
        if(!canHide(n,true))break;
        if(last.collapsed<$X_DISCOVER_MORE_MAX_COLLAPSED){
          n.style.setProperty('display','none','important');
          last.collapsed++;
        }else{
          n.style.setProperty('visibility','hidden','important');
          n.style.setProperty('opacity','0','important');
          n.style.setProperty('pointer-events','none','important');
        }
        hiddenEls.add(n);
        reached=n;
      }
      last.cell=reached;
    })();
    """.trimIndent()

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
                    js = IG_SUGGESTED_BLOCK_JS,
                )
            }

            SiteTweak.InstagramAppPrompts -> {
                TweakRule(
                    css =
                        listOf(
                            igAppDialog("a[href*=\"play.google.com\"]"),
                            igAppDialog("a[href*=\"apps.apple.com\"]"),
                            igAppDialog("a[href*=\"instagram://\"]"),
                            igAppDialog("a[href*=\"onelink.me\"]"),
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
                    css = IG_EXPLORE_GRID_CSS,
                    js = IG_EXPLORE_FLAG_JS,
                )
            }

            SiteTweak.InstagramAi -> {
                // Never a field, form or search box, nor anything holding one: Instagram labels its
                // search and message fields "Ask Meta AI or Search", and those must stay.
                TweakRule(
                    css =
                        listOf(
                            "a[href*=\"meta.ai\"]",
                            "[aria-label*=\"Meta AI\"]$IG_NOT_A_FIELD",
                            "div[role=\"button\"]:has(svg[aria-label*=\"Meta AI\"])$IG_NOT_A_FIELD",
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
                // Only X's own "Ad" / "Promoted" label marks an ad. placementTracking is a structural
                // wrapper X also puts around organic video players and profile buttons, so it is
                // never a marker. The label is never read from the post's text, author line, media,
                // cards or polls (X_LABEL_EXCLUDE_SELECTOR), so a pre-roll badge inside an organic
                // video cannot match, and the post opened on its own page is never hidden.
                TweakRule(
                    css = listOf(X_IMPRESSION_PIXEL_CELL),
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
                        ),
                    headingLabels = X_WHO_TO_FOLLOW_LABELS,
                    js = X_WHO_TO_FOLLOW_ROWS_JS,
                )
            }

            SiteTweak.XTrends -> {
                TweakRule(
                    css =
                        listOf(
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
                            // Premium links go at once only in the menus and the drawer, where no
                            // account notice lives; anywhere else X_PREMIUM_LINK_JS hides them with
                            // the account-notice guard. A Subscribe button is never hidden by
                            // aria-label or test id: creators use the same button.
                            "nav a[href*=\"/i/premium\"]",
                            "[role=\"navigation\"] a[href*=\"/i/premium\"]",
                            "nav a[href*=\"/i/verified\"]",
                            "[role=\"navigation\"] a[href*=\"/i/verified\"]",
                            "nav a[aria-label=\"Get Premium\"]",
                            "[role=\"navigation\"] a[aria-label=\"Get Premium\"]",
                            "nav [data-testid=\"premium-signup-tab\"]",
                            "[role=\"navigation\"] [data-testid=\"premium-signup-tab\"]",
                            "nav a[href*=\"/verified-orgs-signup\"]",
                            "[role=\"navigation\"] a[href*=\"/verified-orgs-signup\"]",
                            "aside[aria-label*=\"Subscribe to Premium\"]",
                        ),
                    headingLabels = listOf("Subscribe to Premium"),
                    // The engine's upsell scan: only the button goes, it is skipped next to a limit,
                    // payment or billing notice, inside a post and for creator subscriptions
                    // (X_UPSELL_KEEP_JS), and each button is read once.
                    buttonLabels = X_PREMIUM_BUTTON_LABELS,
                    js = X_PREMIUM_LINK_JS,
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
                        ),
                )
            }

            SiteTweak.XAppPrompts -> {
                TweakRule(
                    css =
                        listOf(
                            "div[data-testid=\"inline_prompt\"]",
                            // The cheap child-link test comes first, so the subtree test for a field
                            // runs only on the few divs that hold an app link; one :has for both
                            // field kinds instead of two walks. Same elements as before.
                            "#layers div:has(> a[href*=\"app.link\"])$X_NO_FIELD",
                            "#layers div:has(> a[href*=\"play.google.com\"])$X_NO_FIELD",
                            "div[data-testid=\"sheetDialog\"]:has(a[href*=\"play.google.com\"])$X_NO_FIELD",
                            "div[data-testid=\"sheetDialog\"]:has(a[href*=\"apps.apple.com\"])$X_NO_FIELD",
                        ),
                    promptLabels = USE_APP_LABELS + listOf("Download app", "Switch to the app"),
                )
            }

            SiteTweak.XHideForYou -> {
                // Only on /home (X_TAB_PATH_PATTERN) and only while another tab is selected: when X
                // falls back to For you the tab shows again, so the feed is never unlabelled.
                TweakRule(
                    tabLabels = listOf("For you"),
                )
            }

            SiteTweak.XDiscoverMore -> {
                TweakRule(
                    js = X_DISCOVER_MORE_JS,
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
                    // "Promoted by <Company>" marks Thought Leader ads.
                    postLabels = listOf("Promoted", "Sponsored", "Promoted by*"),
                )
            }

            SiteTweak.LinkedInSuggested -> {
                TweakRule(
                    css = listOf(".feed-shared-celebration-card"),
                    postLabels = listOf("Suggested", "Recommended for you"),
                    headingLabels = listOf("People you may know", "More suggestions for you"),
                )
            }

            SiteTweak.LinkedInPremium -> {
                // Only the upsell button or link itself goes ("Try Premium for $0"), never a dialog,
                // bar, card or post: LinkedIn's search, InMail and commercial-use limit notices carry
                // the same button. Win-back labels ("Retry", "Reactivate Premium") stay, since they
                // sit on the person's own lapsed-subscription notices.
                TweakRule(
                    buttonLabels = listOf("Try Premium*"),
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
                            // Anchored to LinkedIn's own paths so an external article whose URL has
                            // /advice/ in it stays.
                            "a[href^=\"/advice/\"]",
                            "a[href^=\"https://www.linkedin.com/advice/\"]",
                        ),
                    headingLabels = listOf("Collaborative articles", "Explore collaborative articles"),
                    promptLabels = listOf("Rewrite with AI", "Draft with AI", "Get key takeaways", "Try Coach"),
                )
            }

            SiteTweak.LinkedInJobsAndGames -> {
                TweakRule(
                    css =
                        listOf(
                            "a[href^=\"/games/\"]",
                            "a[href^=\"https://www.linkedin.com/games/\"]",
                            "aside:has(a[href^=\"/games/\"])",
                            "aside:has(a[href^=\"https://www.linkedin.com/games/\"])",
                            "aside:has(a[href^=\"/jobs/collections/\"])",
                            "aside:has(a[href^=\"https://www.linkedin.com/jobs/collections/\"])",
                        ),
                    headingLabels =
                        listOf(
                            "Today's puzzles",
                            "Games",
                            "Jobs recommended for you",
                            "Jobs you may be interested in",
                            "Recommended jobs",
                        ),
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
                            // Reddit's NSFW gate reuses the xpromo ids and bundles; it stays unless
                            // the person turned on the NSFW tweak.
                            "configured-xpromo-modal:not([id*=nsfw])",
                            "[id^=configured-xpromo]:not([id*=nsfw])",
                            "xpromo-app-selector",
                            "xpromo-bottom-sheet",
                            // The generic "View in Reddit App" sheet is a plain div with this id.
                            "div#xpromo-bottom-sheet",
                            "[id=\"xpromo-bottom-sheet\"]",
                            // Any other bottom sheet only when it links to the app.
                            // A sheet that asks for input (sign-in, account) stays even if it offers the app.
                            "$REDDIT_APP_SHEET_GUARD:has(a[href*=\"onelink.me\"])",
                            "$REDDIT_APP_SHEET_GUARD:has(a[href*=\"app.link\"])",
                            "xpromo-bottom-bar",
                            // community-barrier is never hidden here: it also explains private,
                            // banned and quarantined communities.
                            "xpromo-floating-pill",
                            "shreddit-async-loader[bundlename*=\"xpromo\"]:not([bundlename*=\"nsfw\"])",
                            "shreddit-async-loader[bundlename=\"bottom_sheet\"]",
                            // faceplate-banner also carries removed, locked and archived notices, so
                            // only a banner that links to the app goes.
                            "faceplate-banner:has(a[href*=\"app.link\"])",
                            "faceplate-banner:has(a[href*=\"onelink\"])",
                            "faceplate-banner:has(a[href^=\"intent:\"])",
                            "faceplate-banner:has(a[href*=\"xpromo\"])",
                            "[slot=\"bottom-banner\"]",
                            "a[href*=onelink]",
                            "a[href*=\"reddit.app.link\"]",
                            "a[href*=\"apps.apple.com\"][href*=\"reddit\"]",
                            "a[href*=\"play.google.com/store/apps/details?id=com.reddit\"]",
                            "shreddit-app-rating",
                            "html:not([${EngineMarks.auth}=\"1\"]) #login-button$OUTSIDE_DIALOGS",
                            "html:not([${EngineMarks.auth}=\"1\"]) #signup-button$OUTSIDE_DIALOGS",
                            "html:not([${EngineMarks.auth}=\"1\"]) a[href^=\"/login\"]$OUTSIDE_DIALOGS",
                            "html:not([${EngineMarks.auth}=\"1\"]) a[href^=\"/register\"]$OUTSIDE_DIALOGS",
                        ),
                    promptLabels =
                        USE_APP_LABELS +
                            listOf("Open in Reddit", "View in Reddit App", "Continue in Reddit App", "Use Reddit App", "View in app"),
                    js =
                        "if(!isEditableFocused()&&!$REDDIT_OPEN_OVERLAY_JS&&document.body&&document.body.style.overflow==='hidden')document.body.style.overflow='';",
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
                    rawCss =
                        listOf(
                            "shreddit-blurred-container:not([reason=\"spoiler\"]) [slot=\"revealed\"]{display:block!important;filter:none!important;-webkit-filter:none!important;opacity:1!important;visibility:visible!important}",
                            "shreddit-blurred-container:not([reason=\"spoiler\"]) > :not([slot=\"blurred\"]):not([slot=\"blur-toggle\"]){filter:none!important;-webkit-filter:none!important;opacity:1!important;visibility:visible!important}",
                            "faceplate-img.thumbnail-blur{filter:none!important;-webkit-filter:none!important}",
                        ),
                    css =
                        listOf(
                            "[slot=\"nsfw-banner\"]",
                            // Blocking overlays go only once the script has marked them
                            // as dismissed: Reddit has loaded posts behind them and the overlay
                            // itself is explicitly about NSFW. Any other modal or backdrop stays, and
                            // so does the gate on an empty page, so the person can answer Reddit's
                            // own age question.
                            "#blocking-modal[${EngineMarks.nsfwDismissed}]",
                            "#nsfw-qr-dialog[${EngineMarks.nsfwDismissed}]",
                            "[role=\"dialog\"][${EngineMarks.nsfwDismissed}]",
                            // Marked only when it holds no media (qWrapsMedia).
                            "#configured-xpromo-blocking_xpromo_nsfw_blocking[${EngineMarks.nsfwDismissed}]",
                            "#configured-xpromo-blocking_xpromo_nsfw_blocking_desktop[${EngineMarks.nsfwDismissed}]",
                            "#configured-xpromo-blocking_xpromo_nsfw_blocking_desktop_cms[${EngineMarks.nsfwDismissed}]",
                            "body > div[${EngineMarks.nsfwDismissed}]",
                            "shreddit-blurred-container[${EngineMarks.unblurred}] [slot=\"blurred\"]",
                            "shreddit-blurred-container[${EngineMarks.unblurred}] [slot=\"blur-toggle\"]",
                            // Only marked parts inside the in-post gate go, never the gate host: its
                            // default slot holds the post's media.
                            "xpromo-nsfw-blocking-container [${EngineMarks.nsfwDismissed}]",
                            "community-barrier[${EngineMarks.nsfwDismissed}]",
                            "faceplate-tracker[source*=\"nsfw\"][${EngineMarks.nsfwDismissed}]",
                            "shreddit-async-loader[bundlename*=\"nsfw_blocking\"][${EngineMarks.nsfwDismissed}]",
                            "shreddit-async-loader[bundlename*=\"nsfw-blocking\"][${EngineMarks.nsfwDismissed}]",
                            "shreddit-async-loader[bundlename*=\"nsfw_modal\"][${EngineMarks.nsfwDismissed}]",
                        ),
                    // Display only: reveals what Reddit already loaded. It never presses Reddit's own
                    // age, consent or "View Community" buttons and never writes Reddit's preferences,
                    // so a signed-in account's settings are only ever changed by the person.
                    js =
                        """
                        // NSFW only on explicit signals: a barrier attribute, or heading/button text.
                        // Community names and descriptions are never read, so a private community
                        // called r/maturegamers keeps its explanation.
                        // An nsfw-named flag counts only when it is on (nsfw="false" is not NSFW); a kind
                        // or id value counts only when it does not also name a quarantine, private or
                        // banned barrier; free-form source and testid values need nsfw or over-18.
                        var qIsNsfwAttr=function(an,av){
                          av=(av||'').trim();
                          if(/${NSFW_BARRIER_ATTR_PATTERN}/i.test(an))return /${NSFW_FLAG_ON_PATTERN}/i.test(av);
                          if(/${NON_NSFW_BARRIER_PATTERN}/i.test(av))return false;
                          if(/^(type|reason|barrier-type|variant|kind)$/i.test(an))return /${NSFW_BARRIER_ATTR_PATTERN}/i.test(av);
                          if(/^(source|data-testid)$/i.test(an))return /${NSFW_ID_VALUE_PATTERN}/i.test(av);
                          return false;
                        };
                        var qIsNsfwBarrier=function(h){
                          var at=h.attributes||[];
                          for(var a=0;a<at.length;a++){
                            if(qIsNsfwAttr(at[a].name||'',at[a].value))return true;
                          }
                          var roots=h.shadowRoot?[h,h.shadowRoot]:[h];
                          for(var r=0;r<roots.length;r++){
                            var els=roots[r].querySelectorAll('h1,h2,h3,[slot="title"],[slot="heading"],button,[role="button"]');
                            for(var i=0;i<els.length;i++){
                              if(/${NSFW_BARRIER_TEXT_PATTERN}/i.test(els[i].textContent||''))return true;
                            }
                          }
                          return false;
                        };
                        // Only dismiss a barrier or blocking dialog when Reddit has already loaded
                        // posts outside it; an empty page keeps Reddit's own button to continue.
                        var qHasLoadedPosts=function(){
                          var ps=document.querySelectorAll('shreddit-post,[slot="text-body"],shreddit-post-text-body');
                          for(var i=0;i<ps.length;i++){
                            if(!ps[i].closest('community-barrier,#blocking-modal,#nsfw-qr-dialog,[role="dialog"],dialog'))return true;
                          }
                          return false;
                        };
                        var qLoaded=qHasLoadedPosts();
                        if(qLoaded)document.documentElement.setAttribute('${EngineMarks.nsfwContent}','1');
                        else document.documentElement.removeAttribute('${EngineMarks.nsfwContent}');
                        var qHideNow=function(e){
                          if(e&&e.style){if(e.style.setProperty)e.style.setProperty('display','none','important');else e.style.display='none';}
                        };
                        // A gate counts as NSFW only on an explicit signal: its own tag, an id, source or
                        // bundle name that says nsfw, or an NSFW heading or button (qIsNsfwBarrier).
                        var qNsfwInside='xpromo-nsfw-blocking-container,#nsfw-qr-dialog,[id^="configured-xpromo"][id*="nsfw"],faceplate-tracker[source*="nsfw"],shreddit-async-loader[bundlename*="nsfw"]';
                        var qIsNsfwGate=function(h){
                          if(!h||!h.getAttribute)return false;
                          if(h.tagName==='XPROMO-NSFW-BLOCKING-CONTAINER')return true;
                          if(/nsfw/i.test(h.id||'')||/nsfw/i.test(h.getAttribute('source')||'')||/nsfw/i.test(h.getAttribute('bundlename')||''))return true;
                          if(qIsNsfwBarrier(h))return true;
                          return !!(h.querySelector&&h.querySelector(qNsfwInside));
                        };
                        // qDismissed and qNew cover only gates newly dismissed in this scan: an older,
                        // already hidden gate never lets a later, unrelated backdrop go.
                        var qDismissed=false;
                        var qNew=[];
                        // An element that holds the post's media (or the in-post gate around it) is
                        // never marked: hiding it would leave an empty box where the media goes.
                        var qWrapsMedia=function(h){
                          if(h.matches&&h.matches('[slot="post-media-container"]'))return true;
                          return !!(h.querySelector&&h.querySelector('xpromo-nsfw-blocking-container,[slot="post-media-container"],shreddit-embed,shreddit-player,shreddit-player-2,shreddit-aspect-ratio,gallery-carousel,video,iframe'));
                        };
                        var qMark=function(e){e.setAttribute('${EngineMarks.nsfwDismissed}','1');qNew.push(e);qDismissed=true;};
                        if(qLoaded)document.querySelectorAll('community-barrier,xpromo-nsfw-blocking-container,faceplate-tracker[source*="nsfw"],#blocking-modal,#nsfw-qr-dialog,[id^="configured-xpromo"][id*="nsfw"],shreddit-async-loader[bundlename*="nsfw"],shreddit-async-loader[bundlename*="barrier"]').forEach(function(h){
                          if(h.getAttribute('${EngineMarks.nsfwDismissed}'))return;
                          if(h.tagName==='XPROMO-NSFW-BLOCKING-CONTAINER'){
                            // The gate wraps the post itself: only its overlay goes, and the wrapped
                            // content is shown through a slot.
                            if(h.shadowRoot){
                              if(!h.shadowRoot.querySelector('slot')){
                                try{h.shadowRoot.appendChild(document.createElement('slot'));}catch(e){}
                              }
                              h.shadowRoot.querySelectorAll('.prompt,.overlay,.bg-scrim,.viewInApp').forEach(function(o){
                                // A wrapper around the default slot holds the media itself.
                                if(o.querySelector('slot:not([name])'))return;
                                o.setAttribute('${EngineMarks.nsfwDismissed}','1');
                                qHideNow(o);
                              });
                            }
                            h.querySelectorAll('.prompt,.overlay,.bg-scrim,.viewInApp').forEach(function(o){
                              if(!o.querySelector('shreddit-post,[slot="text-body"]')&&!qWrapsMedia(o))o.setAttribute('${EngineMarks.nsfwDismissed}','1');
                            });
                          }
                          var hasUi=h.children.length>0||(h.shadowRoot&&h.shadowRoot.children.length>0);
                          if(!hasUi)return;
                          if(h.id==='blocking-modal'||h.id==='nsfw-qr-dialog'){
                            var dlg=h.closest('[role="dialog"]')||h;
                            // Reddit also uses #blocking-modal for quarantine and other notices.
                            if(!qIsNsfwGate(h)&&!qIsNsfwGate(dlg))return;
                            if(!dlg.querySelector('shreddit-post,[slot="text-body"]')){
                              qMark(dlg);
                              qHideNow(dlg);
                            }
                          }else if(h.tagName==='COMMUNITY-BARRIER'){
                            // A private, banned or quarantined community keeps its explanation.
                            if(!qIsNsfwBarrier(h))return;
                            qMark(h);
                            qHideNow(h);
                          }else if(h.tagName==='XPROMO-NSFW-BLOCKING-CONTAINER'){
                            // The host stays: only its shadow prompt went above.
                          }else if(qIsNsfwGate(h)&&!h.querySelector('shreddit-post,[slot="text-body"]')&&!qWrapsMedia(h)){
                            qMark(h);
                          }
                        });
                        // A modal the person still has to answer: visible, not one this tweak dismissed,
                        // and not itself an NSFW gate.
                        var qOpenModalSel='dialog[open],[role="dialog"],[role="alertdialog"],[aria-modal="true"],faceplate-dialog,faceplate-modal[open],#blocking-modal,#nsfw-qr-dialog';
                        var qIsOpenModal=function(m){
                          if(m.getAttribute('${EngineMarks.nsfwDismissed}')||(m.closest&&m.closest('[${EngineMarks.nsfwDismissed}]')))return false;
                          if(!m.getClientRects().length)return false;
                          return !qIsNsfwGate(m);
                        };
                        // True when the backdrop wraps, or sits right next to, a gate dismissed in this scan.
                        var qNextToNew=function(d){
                          for(var j=0;j<qNew.length;j++){
                            var g=qNew[j],top=g;
                            while(top.parentElement&&top.parentElement!==document.body)top=top.parentElement;
                            if(d.contains(g)||d.nextElementSibling===top||d.previousElementSibling===top)return true;
                          }
                          return false;
                        };
                        // A blurred backdrop goes only with an NSFW gate: it holds one, or it wraps or sits
                        // next to a gate dismissed in this scan. It never goes while it, or the page, still
                        // shows another open modal (login, report, share, quarantine).
                        if(qLoaded)document.querySelectorAll('body > div[style*="backdrop-filter"],body > div[style*="blur(4px)"]').forEach(function(d){
                          if(d.getAttribute('${EngineMarks.nsfwDismissed}')||d.querySelector('shreddit-post,[slot="text-body"]'))return;
                          var inner=d.querySelectorAll(qOpenModalSel);
                          for(var k=0;k<inner.length;k++){if(qIsOpenModal(inner[k]))return;}
                          var ownGate=qIsNsfwGate(d);
                          if(!ownGate){
                            if(!qDismissed)return;
                            if(!qNextToNew(d))return;
                            var open=document.querySelectorAll(qOpenModalSel);
                            for(var i=0;i<open.length;i++){if(qIsOpenModal(open[i]))return;}
                          }
                          d.setAttribute('${EngineMarks.nsfwDismissed}','1');
                        });
                        // Reveal through attributes and inline styles only: no component property or
                        // method of Reddit's is written or called.
                        document.querySelectorAll('shreddit-post[nsfw],shreddit-post [slot="text-body"],shreddit-post-text-body,[data-post-click-location="text-body"]').forEach(function(p){
                          if(p.tagName==='SHREDDIT-POST'){
                            p.removeAttribute('nsfw');
                            p.removeAttribute('blurred');
                            p.removeAttribute('is-blurred');
                            p.removeAttribute('embed-obscured');
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
                          p.removeAttribute('nsfw');
                          p.removeAttribute('blurred');
                          p.removeAttribute('is-blurred');
                          p.removeAttribute('embed-obscured');
                          p.setAttribute('mode','revealed');
                          p.setAttribute('${EngineMarks.unblurred}','1');
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
                          c.removeAttribute('is-blurred');
                        });
                        document.querySelectorAll('devvit2-blur-gate').forEach(function(g){
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
      // A document-start script runs once per document and leaves nothing on window. Only the
      // fallback that runs after load (where it may run again) keeps a guard, under a symbol key.
      var guard=Symbol.for('${EngineMarks.guard}');
      if(window[guard])return;
      if(document.readyState!=='loading'){try{Object.defineProperty(window,guard,{value:true})}catch(e){}}
      // Page scripts may later wrap console, timers or listeners (error reporters, zone.js); the
      // engine keeps the originals so its bridge messages and callbacks never pass through them.
      var nDebug=console.debug,nCon=console;
      var nPost=function(m){try{nDebug.call(nCon,m)}catch(e){}};
      var nTimeout=window.setTimeout;
      var nLater=function(f,ms){return nTimeout.call(window,f,ms)};
      var nFrameFn=window.requestAnimationFrame;
      var nFrame=nFrameFn?function(f){return nFrameFn.call(window,f)}:function(f){return nLater(f,16)};
      var nIdleFn=window.requestIdleCallback;
      var nIdle=nIdleFn?function(f,o){return nIdleFn.call(window,f,o)}:null;
      var nMicroFn=window.queueMicrotask;
      var nMicro=nMicroFn?function(f){nMicroFn.call(window,f)}:function(f){Promise.resolve().then(f)};
      var nListen=window.addEventListener;
      var NObserver=window.MutationObserver;
      // State that has to outlive one scan (site scripts keep theirs here, never on window).
      var keep={};
      // Elements the engine hid, so later scans skip them; nothing is written on the element.
      var hiddenEls=new WeakSet();
      try{
    __INIT_JS__
      }catch(e){}
      var css=__CSS__;
      var postSel=__POSTSEL__,cellSel=__CELLSEL__,moduleSel=__MODULESEL__,excludeSel=__EXCLUDESEL__;
      var rx=function(s){return s?new RegExp(s,'i'):null};
      var postLabels=rx(__POSTLABELS__),headingLabels=rx(__HEADINGLABELS__),tabLabels=rx(__TABLABELS__),prompts=rx(__PROMPTS__),buttons=rx(__BUTTONS__);
      var accountNotice=rx(__ACCOUNTNOTICE__);
      var styleEl=null;
      // Only Reddit renders with shadow DOM; walking every element elsewhere is wasted work.
      var hasShadowDom=/(^|\.)reddit\.com$/.test(location.hostname);
      var isVirtualized=/(^|\.)(x|twitter)\.com$/.test(location.hostname);
      // Full-screen video viewers (Reels, Stories, Shorts-style pages) and direct messages have no feed posts to filter,
      // and their DOM churns as each clip/message loads, so they only get the stylesheet and prompt checks.
      var isImmersive=function(){return /^\/(reels?|stories|direct|messages|messaging|chat|shorts)(\/|$)|^\/[^\/]+\/reel(\/|$)|^\/i\/(chat|messages)(\/|$)/i.test(location.pathname)};
      // On one opened post's page: null elsewhere, '' where no post is label-checked, else the
      // opened post's id, so only that post is skipped and the replies are still checked.
      var detailRx=rx(__DETAILRX__);
      var postDetailId=function(){var m=detailRx&&detailRx.exec(location.pathname||'');return m?(m[1]||''):null};
      var isOpenedPost=function(p,id){
        if(p.getAttribute&&p.getAttribute('tabindex')==='-1')return true;
        return !!(p.querySelector&&p.querySelector('a[href${'$'}="/'+id+'"],a[href*="/'+id+'/"],a[href*="/'+id+'?"]'));
      };
      var tabPathRx=rx(__TABPATHRX__);
      var keepUpsell=function(b){try{return !!(__KEEPUPSELL__)}catch(e){return true}};
      var authRx=new RegExp(__AUTHRX__,'i');
      var isAuthPage=function(){return authRx.test(location.pathname||'')};
      // Only a page whose CSS or script reads the sign-in mark gets it on html.
      var authAttr=__AUTHATTR__;
      var syncAuthAttr=function(){
        if(!authAttr)return;
        try{
          var root=document.documentElement;
          if(!root)return;
          if(isAuthPage())root.setAttribute('${EngineMarks.auth}','1');
          else if(root.hasAttribute('${EngineMarks.auth}'))root.removeAttribute('${EngineMarks.auth}');
        }catch(e){}
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
      var tapPost=null,tapAt=0;
      var checkedPrompts=new WeakSet();
      var checkedButtons=new WeakSet();
      var checkedHeadings=new WeakSet();
      var checkedShadowHosts=new WeakSet();
      // The style the engine added to each shadow root, kept here instead of an id on the element.
      var shadowStyles=new WeakMap();
      var scannedPosts=new WeakMap();
      var markScroll=function(){lastScroll=Date.now()};
      // Pull-to-refresh bridge. Some sites scroll an inner element, not the document (LinkedIn's
      // feed scrolls main#workspace many levels above the post text), so the scroller is found on
      // the whole event path, through shadow roots, with no depth cap.
      var ptrScrollers=[],ptrKnown=[],ptrBase=null,ptrRaf=0;
      var postPtr=function(allow){
        if(lastPtrState===allow)return;
        lastPtrState=allow;
        nPost(allow?__BRIDGE_PTR_1__:__BRIDGE_PTR_0__);
      };
      var docTop=function(){
        var se=document.scrollingElement||document.documentElement;
        return window.scrollY||window.pageYOffset||(se&&se.scrollTop)||(document.body&&document.body.scrollTop)||0;
      };
      var isDocScroller=function(n){return n===document||n===document.documentElement||n===document.body||n===document.scrollingElement};
      // A vertical scroll container that is currently scrolled down: checking scrollTop first avoids
      // forcing synchronous layout (scrollHeight/clientHeight/getComputedStyle) on every touchstart.
      var isVScroller=function(n){
        try{
          if((n.scrollTop||0)<=1)return false;
          var oy=getComputedStyle(n).overflowY;
          return oy==='auto'||oy==='scroll'||oy==='overlay';
        }catch(e){return false}
      };
      var eventPath=function(ev){
        var path=ev.composedPath?ev.composedPath():[];
        if(path&&path.length)return path;
        path=[];
        var n=ev.target;
        while(n){path.push(n);n=n.assignedSlot||n.parentNode||n.host;}
        return path;
      };
      // A scroller big enough to be the page's feed, still on screen. Once one leaves the top it is
      // remembered, so a touch on a fixed bar, nav or pill outside it never reports top while the
      // feed is mid-scroll.
      var ptrIsMain=function(n){
        try{
          if(!n.isConnected)return false;
          var r=n.getBoundingClientRect(),vh=window.innerHeight||0;
          return r.height>=vh*0.4&&r.bottom>0&&r.top<vh;
        }catch(e){return false}
      };
      var ptrRemember=function(n){
        if(ptrKnown.indexOf(n)>=0||!ptrIsMain(n))return;
        ptrKnown=ptrKnown.concat([n]).slice(-6);
      };
      var ptrAtTop=function(){
        if(docTop()>1)return false;
        for(var i=0;i<ptrScrollers.length;i++){if((ptrScrollers[i].scrollTop||0)>1)return false;}
        ptrKnown=ptrKnown.filter(ptrIsMain);
        for(var j=0;j<ptrKnown.length;j++){if((ptrKnown[j].scrollTop||0)>1)return false;}
        return true;
      };
      try{
        window.addEventListener('touchstart',function(ev){
          try{
            var base=!isAuthPage()&&!isImmersive();
            if(!base||docTop()>1){ptrBase=base;ptrScrollers=[];postPtr(false);return;}
            var path=eventPath(ev),scrollers=[];
            for(var i=0;i<path.length;i++){
              var n=path[i];
              if(!n||n.nodeType!==1||isDocScroller(n))continue;
              if(base&&n.matches&&n.matches('[role="dialog"],[aria-modal="true"],reddit-search-large'))base=false;
              if(isVScroller(n)){scrollers.push(n);if((n.scrollTop||0)>1)ptrRemember(n);}
            }
            // A touch on a fixed bar or pill has no scroller on its path: on inner-scroll sites
            // (LinkedIn) the scroller under the middle of the screen is looked up once if not yet known.
            if(!scrollers.length&&!ptrKnown.length&&document.elementFromPoint&&/(^|\.)linkedin\.com$/.test(location.hostname)){
              var mid=document.elementFromPoint((window.innerWidth||0)/2,(window.innerHeight||0)/2);
              while(mid&&!isDocScroller(mid)){
                if(mid.nodeType===1&&isVScroller(mid))ptrRemember(mid);
                mid=mid.nodeType===11?mid.host:(mid.assignedSlot||mid.parentNode);
              }
            }
            ptrScrollers=scrollers;
            ptrBase=base;
            postPtr(base&&ptrAtTop());
          }catch(e){}
        },{capture:true,passive:true});
        // Element scroll events do not bubble, so this listens in the capture phase: leaving the top
        // turns pull-to-refresh off at once, and it comes back on (once a frame) only when the
        // scrollers under the last touch, the remembered feed scrollers and the document are all at
        // the top again.
        document.addEventListener('scroll',function(ev){
          try{
            var t=ev.target;
            var top=isDocScroller(t)?docTop():((t&&t.scrollTop)||0);
            if(top>1){if(!isDocScroller(t)&&t&&t.nodeType===1)ptrRemember(t);postPtr(false);return;}
            if(lastPtrState!==false||ptrBase!==true||ptrRaf)return;
            ptrRaf=nFrame(function(){
              ptrRaf=0;
              try{if(ptrBase===true&&!isAuthPage()&&!isImmersive()&&ptrAtTop())postPtr(true);}catch(e){}
            });
          }catch(e){}
        },{capture:true,passive:true});
        window.addEventListener('scroll',markScroll,{capture:true,passive:true});
        window.addEventListener('touchmove',markScroll,{capture:true,passive:true});
        window.addEventListener('wheel',markScroll,{capture:true,passive:true});
        window.addEventListener('focusout',function(){schedule();},{capture:true,passive:true});
        // :has() is left out where the WebView cannot parse it, so a click never throws into the
        // page's error reporters.
        var hasSel=false;
        try{hasSel=!!(window.CSS&&CSS.supports&&CSS.supports('selector(:has(a))'));}catch(e){}
        var tapPostSel='article[data-testid="tweet"],shreddit-post,div.feed-shared-update-v2,div[data-id*="activity"],article,[role="article"],div[role="listitem"][componentkey^="update-card"]';
        var bmSel='[data-testid="bookmark"],button[aria-label="Save" i],button[aria-label="Bookmark" i],'+(hasSel?'div[role="button"]:has(svg[aria-label="Save" i]),':'')+'shreddit-post [slot*="save"]';
        nListen.call(window,'click',function(ev){
          try{
            var t=ev.target;
            if(!t||!t.closest)return;
            var p=t.closest(tapPostSel);
            // The tapped post stays in the closure; the bookmark script asks for it with an event.
            if(p){tapPost=p;tapAt=Date.now();}
            var bm=t.closest(bmSel);
            if(bm){nPost(__BRIDGE_BM_TAP__);}
          }catch(e){}
        },{capture:true,passive:true});
        // On window in the capture phase, added at document start, so it is the first listener on
        // the event's path; the event then stops, so no page listener sees it or can answer it.
        nListen.call(window,'${EngineMarks.tapEvent}',function(ev){
          try{
            var d=ev.detail;
            if(d&&typeof d==='object'&&tapPost&&tapPost.isConnected&&Date.now()-tapAt<6000)d.el=tapPost;
            ev.stopImmediatePropagation();
          }catch(e){}
        },true);
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
      // True only when the element was actually hidden; callers mark the element hidden only then, so a
      // refused element is looked at again on a later scan.
      var hide=function(e,allowPost){
        if(!canHide(e,allowPost))return false;
        e.style.setProperty('display','none','important');
        return true;
      };
      var clean=function(t){return (t||'').replace(/[\u200B-\u200D\uFEFF\u00A0]/g,' ').trim()};
      var bar=function(e){
        // Climb only through wrappers that hold nothing else clickable: a site header that also has
        // the menu or search must stay, so stop at it.
        var clickable='a,button,input,select,textarea,[role=button],[role=search],[role=navigation]';
        while(e.parentElement&&e.parentElement!==document.body&&(e.parentElement.clientHeight||0)<=120&&!/^(HEADER|NAV|FOOTER)$/.test(e.parentElement.tagName)&&!/header|footer|nav/i.test(e.parentElement.tagName)&&e.parentElement.getAttribute('role')!=='navigation'&&!e.parentElement.querySelector('nav,header,footer,[role="navigation"]')&&e.parentElement.querySelectorAll(clickable).length<=1){e=e.parentElement}
        return e;
      };
      // Notices that also explain something to the person (Reddit's community barrier and its NSFW
      // gate): an app prompt inside one hides only its own button.
      var noticeSel='community-barrier,xpromo-nsfw-blocking-container,#blocking-modal,#nsfw-qr-dialog,[id*="nsfw"],shreddit-async-loader[bundlename*="nsfw"],shreddit-async-loader[bundlename*="barrier"]';
      // Walks out through every shadow root, so a banner nested in a gate's shadow root counts too.
      var inNotice=function(b,host,d){
        try{
          var chain=[b,host];
          for(var c=0;c<chain.length;c++){
            var n=chain[c];
            while(n){
              if(n.closest&&n.closest(noticeSel))return true;
              var r=n.getRootNode&&n.getRootNode();
              n=r&&r.host;
            }
          }
          return !!(d&&d.querySelector(noticeSel));
        }catch(e){return true}
      };
      var scanPromptsInRoot=function(root,host){
        if(!prompts)return;
        root.querySelectorAll('button,a,[role=button]').forEach(function(b){
          if(checkedPrompts.has(b)||hiddenEls.has(b))return;
          if(!prompts.test(clean(b.textContent))){checkedPrompts.add(b);return;}
          var done=false;
          var d=b.closest('[role=dialog]');
          if(inNotice(b,host,d)){done=hide(b,false)}
          else if(d){
            if(d.querySelector('input,form,textarea,[data-testid*="login"],[data-testid*="Login"],[data-testid*="auth"]'))return;
            done=hide(d,false);
            if(done&&document.body)document.body.style.overflow='';
          }
          else if(host&&bar(b)===b.getRootNode().firstElementChild){done=hide(host,false)}
          else{done=hide(bar(b),false)}
          if(done)hiddenEls.add(b);
        });
      };
      // The block an upsell button belongs to: its dialog, else the nearest card-like ancestor within
      // a few levels (a limit card keeps its heading in a sibling block), stopping before the feed,
      // main or body so one word elsewhere on the page never counts.
      var cardSel='section,article,aside,li,[role=article],[class*=card],[class*=paywall],[class*=upsell],[class*=banner]';
      var noticeCtx=function(b){
        var dlg=b.closest('[role=dialog],[role=alertdialog],[role=alert],[role=status],dialog,[aria-modal="true"]');
        if(dlg)return dlg;
        var e=b.parentElement,ctx=e;
        for(var i=0;e&&i<4;i++){
          if(/^(MAIN|BODY|HTML)$/.test(e.tagName))break;
          var role=e.getAttribute&&e.getAttribute('role');
          if(role==='main'||role==='feed'||e.id==='main-content')break;
          ctx=e;
          if(e.matches&&e.matches(cardSel)&&clean(e.textContent)!==clean(b.textContent))break;
          e=e.parentElement;
        }
        return ctx;
      };
      // Upsell buttons: only the button goes, never its dialog or bar, and never one next to a
      // limit, payment or billing notice.
      var scanButtons=function(){
        if(!buttons)return;
        document.querySelectorAll('button,a,[role=button]').forEach(function(b){
          if(checkedButtons.has(b)||hiddenEls.has(b))return;
          var t=clean(b.textContent);
          if(!buttons.test(t)){checkedButtons.add(b);return;}
          if(accountNotice&&accountNotice.test(t)){checkedButtons.add(b);return;}
          // Not cached: the same button can sit on a profile now and in the home header later.
          if(keepUpsell(b))return;
          var ctx=noticeCtx(b);
          if(accountNotice&&ctx&&accountNotice.test(clean(ctx.textContent)))return;
          if(hide(b,false))hiddenEls.add(b);
        });
      };
      var shadowBannerSel='configured-xpromo-modal,xpromo-app-selector,xpromo-bottom-sheet,xpromo-bottom-bar,xpromo-nsfw-blocking-container,community-barrier,shreddit-blurred-container,faceplate-banner,shreddit-async-loader[bundlename*="xpromo"],shreddit-async-loader[bundlename="bottom_sheet"],shreddit-async-loader[bundlename*="nsfw_blocking"],shreddit-async-loader[bundlename*="nsfw-blocking"],shreddit-async-loader[bundlename*="nsfw_modal"],shreddit-async-loader[bundlename*="barrier"]';
      var walkShadowRoots=function(root){
        // Reddit's NSFW gate is only ever opened by the NSFW tweak's own script, never here.
        root.querySelectorAll(shadowBannerSel).forEach(function(el){
          if(el.shadowRoot){
            var sr=el.shadowRoot,own=shadowStyles.get(sr);
            if(css&&!(own&&own.parentNode===sr)){
              try{var s=document.createElement('style');s.textContent=css;sr.appendChild(s);shadowStyles.set(sr,s)}catch(e){}
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
          var detailId=postDetailId();
          if(postLabels&&postSel&&!immersive&&detailId!==''){
            document.querySelectorAll(postSel).forEach(function(p){
              if(hiddenEls.has(p))return;
              if(detailId&&isOpenedPost(p,detailId))return;
              var len=(p.textContent||'').length;
              var prevLen=scannedPosts.get(p);
              if(prevLen!==undefined&&Math.abs(len-prevLen)<25)return;
              scannedPosts.set(p,len);
              var t=p.querySelectorAll('span,div,a,p');
              for(var i=0;i<t.length;i++){
                var el=t[i];
                if(el.children.length)continue;
                var raw=el.textContent;
                if(!raw||raw.length>70)continue;
                var txt=clean(raw);
                if(txt&&txt.length<=60&&postLabels.test(txt)&&!(excludeSel&&el.closest(excludeSel))){
                  var cell=(cellSel&&p.closest(cellSel))||p;
                  // A refused post stays unmarked but keeps its length throttle, so it is looked
                  // at again only once its content changes.
                  if(hide(cell,true)){
                    hiddenEls.add(cell);
                    hiddenEls.add(p);
                  }
                  break;
                }
              }
            });
          }
          if(headingLabels&&!immersive){
            document.querySelectorAll('h1,h2,h3,h4,[role="heading"]').forEach(function(h){
              if(checkedHeadings.has(h)||hiddenEls.has(h))return;
              var txt=clean(h.textContent);
              if(txt&&txt.length<=40&&headingLabels.test(txt)){
                var m=h.closest(moduleSel);
                if(m&&!m.querySelector('input,form,[data-testid*="login"],[data-testid*="Login"]')&&hide(m,false)){
                  hiddenEls.add(m);
                  hiddenEls.add(h);
                }
              }else{
                checkedHeadings.add(h);
              }
            });
          }
          if(tabLabels&&!auth){
            var tabsHere=!tabPathRx||tabPathRx.test(location.pathname||'');
            document.querySelectorAll('[role="tab"]').forEach(function(t){
              var w=t.closest('[role="presentation"]')||t;
              // The selected tab always shows, so the person can see which feed they are reading,
              // and so does every tab off the pages the labels are scoped to.
              var keepTab=!tabsHere||t.getAttribute('aria-selected')==='true';
              if(hiddenEls.has(t)){
                if(keepTab){
                  w.style.removeProperty('display');
                  hiddenEls.delete(w);
                  hiddenEls.delete(t);
                }
                return;
              }
              if(keepTab)return;
              if(tabLabels.test(clean(t.textContent))){
                if(hide(w,false)){
                  hiddenEls.add(w);
                  hiddenEls.add(t);
                }
              }
            });
          }
          if(prompts&&!auth){
            scanPromptsInRoot(document,null);
            if(hasShadowDom)walkShadowRoots(document);
          }
          if(buttons&&!auth)scanButtons();
        }catch(e){}
        try{
    __JS__
        }catch(e){}
      };
      scan();
      nLater(scan,400);
      nLater(scan,1500);
      var timer=0;
      // Scans wait until scrolling and typing pause, with a minimum gap so feeds scroll smoothly.
      var SCROLL_IDLE_MS=isVirtualized?550:420;
      var MIN_SCAN_GAP_MS=(hasShadowDom||isVirtualized)?650:350;
      var schedule=function(){
        if(timer)return;
        // A tab in the background does no work; one catch-up scan runs when it is shown again.
        if(document.hidden)return;
        timer=nLater(function run(){
          var now=Date.now();
          var sinceScroll=now-lastScroll;
          if(sinceScroll<SCROLL_IDLE_MS){timer=nLater(run,SCROLL_IDLE_MS-sinceScroll);return}
          if(isEditableFocused()){timer=0;return}
          var sinceScan=now-lastScan;
          if(sinceScan<MIN_SCAN_GAP_MS){timer=nLater(run,MIN_SCAN_GAP_MS-sinceScan);return}
          timer=0;
          if(nIdle){
            nIdle(scan,{timeout:500});
          }else{
            nFrame(scan);
          }
        },320);
      };
      try{
        document.addEventListener('visibilitychange',function(){if(!document.hidden)schedule();},{passive:true});
      }catch(e){}
      new NObserver(schedule).observe(document,{childList:true,subtree:true});
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

internal fun bridgeBookmarkMessage(bridgeToken: String = ""): String = PageMarkers.bridgeMessage("bm-tap", bridgeToken)

/**
 * Posted once per URL by the Reddit script when an NSFW post's media box is empty because Reddit
 * withheld its player; the app answers with a native note about Reddit's own setting.
 */
internal fun bridgeRedditNsfwNoteMessage(bridgeToken: String = ""): String = PageMarkers.bridgeMessage("nsfw-note", bridgeToken)

internal fun bridgePtrMessage(
    allow: Boolean,
    bridgeToken: String = "",
): String = PageMarkers.bridgeMessage(if (allow) "ptr-1" else "ptr-0", bridgeToken)

/**
 * Drops whole-line `//` comments, so notes written for this code never reach a page (a page can
 * read a callback's source once it holds a reference to it). Code lines are left exactly as written.
 */
private fun stripLineComments(js: String): String =
    js.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")

/**
 * Builds the one page script for [site] from the tweaks that are on, or null when there is
 * nothing to do. Pure within a process: the same inputs always give the same script, and every
 * name it leaves in the page comes from [PageMarkers], so nothing in it says Crux.
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
        (baseRules + rules.flatMap { it.rawCss }.distinct() + (rules.flatMap { it.css } + metricRules).distinct().map { "$it{display:none!important}" })
            .joinToString(" ")
    val postLabels = rules.flatMap { it.postLabels }.distinct()
    val headingLabels = rules.flatMap { it.headingLabels }.distinct()
    val tabLabels = rules.flatMap { it.tabLabels }.distinct()
    val promptLabels = rules.flatMap { it.promptLabels }.distinct()
    val buttonLabels = rules.flatMap { it.buttonLabels }.distinct()
    val js = (listOf(profile.baseJs) + rules.map { it.js }).filter { it.isNotBlank() }.joinToString("\n")
    val nothingToDo =
        css.isBlank() && postLabels.isEmpty() && headingLabels.isEmpty() && tabLabels.isEmpty() &&
            promptLabels.isEmpty() && buttonLabels.isEmpty() && js.isBlank() && profile.initJs.isBlank()
    if (nothingToDo) return null
    val usesAuthAttr = listOf(css, js, profile.initJs).any { it.contains(EngineMarks.auth) }
    return ENGINE_JS
        .replace("__INIT_JS__", profile.initJs)
        .replace("__AUTHRX__", escapeJs(AUTH_PATH_PATTERN))
        .replace("__AUTHATTR__", if (usesAuthAttr) "true" else "false")
        .replace("__CSS__", escapeJs(css))
        .replace("__POSTSEL__", escapeJs(profile.postSelector))
        .replace("__CELLSEL__", profile.cellSelector?.let(::escapeJs) ?: "null")
        .replace("__MODULESEL__", escapeJs(profile.moduleSelector))
        .replace("__EXCLUDESEL__", escapeJs(profile.labelExcludeSelector))
        .replace("__DETAILRX__", escapeJs(profile.postDetailPattern))
        .replace("__TABPATHRX__", escapeJs(profile.tabPathPattern))
        // The keep test runs only inside the upsell scan, so a page with no upsell labels never carries it.
        .replace("__KEEPUPSELL__", profile.upsellKeepJs.takeIf { buttonLabels.isNotEmpty() }?.ifBlank { null } ?: "false")
        .replace("__POSTLABELS__", escapeJs(labelPattern(postLabels)))
        .replace("__HEADINGLABELS__", escapeJs(labelPattern(headingLabels)))
        .replace("__TABLABELS__", escapeJs(labelPattern(tabLabels)))
        .replace("__PROMPTS__", escapeJs(labelPattern(promptLabels)))
        .replace("__BUTTONS__", escapeJs(labelPattern(buttonLabels)))
        .replace("__ACCOUNTNOTICE__", escapeJs(ACCOUNT_NOTICE_PATTERN))
        .replace("__BRIDGE_BM_TAP__", escapeJs(bridgeBookmarkMessage(bridgeToken)))
        .replace("__BRIDGE_PTR_0__", escapeJs(bridgePtrMessage(allow = false, bridgeToken = bridgeToken)))
        .replace("__BRIDGE_PTR_1__", escapeJs(bridgePtrMessage(allow = true, bridgeToken = bridgeToken)))
        .replace("__JS__", js)
        .replace("__BRIDGE_NSFW_NOTE__", escapeJs(bridgeRedditNsfwNoteMessage(bridgeToken)))
        .let(::stripLineComments)
}
