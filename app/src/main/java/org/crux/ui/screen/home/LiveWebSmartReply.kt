package org.crux.ui.screen.home

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.crux.R
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri

internal data class LiveWebPost(
    val text: String,
    val author: String,
    val handle: String = "",
)

/**
 * Shared, read-only helpers for the smart-reply and bookmark scripts (plain ES5, no :has()).
 *
 * focalPost: on a post's own page (X /status/<id>, Instagram /p/<id>/ or /reel/<id>/, LinkedIn
 * /feed/update/<urn>/) the post that was opened, while it is on screen. On X that is the article
 * whose own time link (outside any quoted post) points at the id in the URL, else X's focused
 * article; on Instagram the one carrying the post's own link (exactly /p|reel/<id>/, never a
 * comment, likers or comments link), an article before a list item; on LinkedIn the one linking to
 * (or carrying) that urn; else the first on screen, since the opened post comes before its
 * comments there. Each path is matched only on its own site's host, so a Reddit /r/tv/ or
 * /r/p/ page never takes this branch.
 *
 * xPostParts: an X post's display name, @handle and own text. A quoted post's text is used only
 * when the post has none, marked with the quoted author's handle.
 *
 * igAuthor / igCaption: an Instagram post's username and caption, skipping the likes and
 * comment-count lines and the likers and comments links.
 */
internal const val POST_PARTS_JS = """
  var onScreen=function(el){var r=el.getBoundingClientRect();return r.height>=40&&r.bottom>40&&r.top<innerHeight-40};
  var xQuoteOf=function(el,root){for(var up=el.parentElement;up&&up!==root;up=up.parentElement){if(up.getAttribute&&up.getAttribute('role')==='link'&&up.querySelector('[data-testid="User-Name"]'))return up}return null};
  var focalPost=function(list){
    try{
      var fh=(location.hostname||'').toLowerCase();
      var onHost=function(d){return fh===d||fh.slice(-(d.length+1))==='.'+d};
      var path=location.pathname||'';
      try{path=decodeURIComponent(path)}catch(e){}
      var xm=(onHost('x.com')||onHost('twitter.com'))?path.match(/\/status\/(\d+)/):null;
      var im=onHost('instagram.com')?path.match(/^\/(?:[A-Za-z0-9._]+\/)?(?:p|reels?|tv)\/([A-Za-z0-9_-]+)/):null;
      var lm=onHost('linkedin.com')?path.match(/^\/feed\/update\/(urn:li:[A-Za-z_]+:\d+)/):null;
      if(!xm&&!im&&!lm)return null;
      var owns=function(el){
        if(xm){
          var own=new RegExp('/status/'+xm[1]+'(?:[/?#]|$)');
          var as=el.querySelectorAll('a[href*="/status/'+xm[1]+'"]');
          for(var i=0;i<as.length;i++){if(own.test(as[i].getAttribute('href')||'')&&as[i].querySelector('time')&&!xQuoteOf(as[i],el))return true}
          return false;
        }
        if(im){
          var ownIg=new RegExp('^(?:/[A-Za-z0-9._]+)?/(?:p|reels?|tv)/'+im[1]+'/?$');
          var ias=el.querySelectorAll('a[href*="/'+im[1]+'"]');
          for(var a=0;a<ias.length;a++){
            var h=(ias[a].getAttribute('href')||'').replace(/^https?:\/\/[^\/]+/i,'').replace(/[?#].*$/,'');
            if(ownIg.test(h))return true;
          }
          return false;
        }
        var u=el.getAttribute('data-urn')||el.getAttribute('data-id')||'';
        if(u===lm[1])return true;
        return !!el.querySelector('[data-urn="'+lm[1]+'"],a[href*="/feed/update/'+lm[1]+'"],a[href*="/feed/update/'+encodeURIComponent(lm[1])+'"]');
      };
      var first=null,ownedItem=null;
      for(var j=0;j<list.length;j++){
        var el=list[j];
        if(!onScreen(el))continue;
        if(owns(el)){
          if(!im||el.tagName==='ARTICLE')return el;
          if(!ownedItem)ownedItem=el;
        }
        if(!first)first=el;
      }
      if(ownedItem)return ownedItem;
      if(xm){
        for(var k=0;k<list.length;k++){if(list[k].getAttribute('tabindex')==='-1'&&list[k].matches('article[data-testid="tweet"]')&&onScreen(list[k]))return list[k]}
        return null;
      }
      return first;
    }catch(e){return null}
  };
  var xHandleOf=function(un){var ps=((un&&un.innerText)||'').split('\n');for(var i=0;i<ps.length;i++){var t=ps[i].trim();if(t.charAt(0)==='@'&&t.length>1&&t.indexOf(' ')===-1)return t}return ''};
  var xPostParts=function(root){
    var names=root.querySelectorAll('[data-testid="User-Name"]');
    var un=null;
    for(var i=0;i<names.length&&!un;i++){if(!xQuoteOf(names[i],root))un=names[i]}
    if(!un&&names.length)un=names[0];
    var lines=((un&&un.innerText)||'').split('\n');
    var name='';
    for(var n=0;n<lines.length&&!name;n++)name=lines[n].trim();
    var texts=root.querySelectorAll('[data-testid="tweetText"]');
    var own='',quoted='',quotedBy='';
    for(var t=0;t<texts.length;t++){
      var q=xQuoteOf(texts[t],root);
      var tx=(texts[t].innerText||'').trim();
      if(!q){if(!own)own=tx}
      else if(!quoted){quoted=tx;quotedBy=xHandleOf(q.querySelector('[data-testid="User-Name"]'))}
    }
    var text=own||(quoted?('↪ '+(quotedBy?quotedBy+': ':'')+quoted):'');
    return {name:name,handle:xHandleOf(un),text:text};
  };
  var igAuthor=function(root){
    var links=root.querySelectorAll('header a, a[role="link"]');
    for(var i=0;i<links.length;i++){
      var t=(links[i].innerText||'').trim();
      if(t&&t.length<40&&t.indexOf(' ')===-1&&!/^(Follow|Following|Audio|Original)$/i.test(t))return t.replace(/^@/,'');
    }
    return '';
  };
  var igCaption=function(root,author){
    var spans=root.querySelectorAll('span[dir="auto"], h1');
    var longest='';
    for(var s=0;s<spans.length;s++){
      var sp=spans[s];
      if(sp.closest&&sp.closest('a[href*="/liked_by/"],a[href*="/comments/"]'))continue;
      var st=(sp.innerText||'').trim();
      if(!st||st===author||/^(Liked by|View all|View \d|\d[\d,.]*\s*[KkMm]?\s*(likes?|comments?|views?|plays?)\b)/i.test(st))continue;
      if(st.length>longest.length)longest=st;
    }
    return longest;
  };
"""

/**
 * Runs once per tap and only reads: the selected text if there is any, otherwise the post tapped
 * in the last few seconds, the opened post on a post's own page, or the post nearest the middle
 * of the screen. It never types into the page or presses anything.
 */
internal val READ_POST_SCRIPT: String = """
(function(){
  var clean=function(t){return (t||'').replace(/[ \t]+/g,' ').replace(/\n{2,}/g,'\n').trim().slice(0,1500)};
  var sel=clean(String(window.getSelection()));
  if(sel.length>3)return JSON.stringify({text:sel,author:'',handle:''});
$POST_PARTS_JS
  var host=(location.hostname||'').toLowerCase();
  var liCard='div[role=listitem][componentkey^="update-card"]';
  var postSel='article[data-testid=tweet],shreddit-post,'+liCard+',div.feed-shared-update-v2,div[data-id*="activity"],div[data-urn*="activity"],article,[role=listitem]';
  var posts=document.querySelectorAll(postSel);
  var best=null;
  var tapAsk={};
  try{document.dispatchEvent(new CustomEvent('${EngineMarks.tapEvent}',{detail:tapAsk}));}catch(e){}
  var tapped=tapAsk.el;
  try{if(tapped&&tapped.nodeType===1&&tapped.isConnected&&tapped.matches&&tapped.matches(postSel+',[role=article]'))best=tapped}catch(e){}
  if(!best)best=focalPost(posts);
  var mid=innerHeight/2,gap=1e9;
  if(!best)posts.forEach(function(p){
    var r=p.getBoundingClientRect();
    if(r.height<40||r.width<100||r.bottom<0||r.top>innerHeight)return;
    var d=(r.top<=mid&&r.bottom>=mid)?0:Math.min(Math.abs(r.top-mid),Math.abs(r.bottom-mid));
    if(d<gap){gap=d;best=p}
  });
  if(!best)return '';
  // LinkedIn's signed-in mobile feed: hashed class names, so read its stable test ids instead.
  var card=(best.closest&&best.closest(liCard))||(best.querySelectorAll(liCard).length===1?best.querySelector(liCard):null);
  if(card){
    var strip=function(t){return clean(t).replace(/^Feed post\b\s*/i,'').split('\n')[0].trim()};
    var box=card.querySelector('[data-testid="expandable-text-box"]');
    var liText='';
    if(box){
      liText=clean(box.innerText);
      var more=box.querySelector('[data-testid="expandable-text-button"]');
      var moreText=more?clean(more.innerText):'';
      if(moreText&&liText.slice(-moreText.length)===moreText)liText=liText.slice(0,-moreText.length).trim();
    }
    var liAuthor='';
    // Skip social-context headers ("Jane Doe likes this", "X reposted this"): that link is not the author.
    var social=/\b(likes|liked|loves|celebrates|supports|finds this|is curious about|commented on|reposted|replied to|follows)\b/i;
    var inSocialLine=function(el,t){
      for(var up=el.parentElement,n=0;up&&n<3;up=up.parentElement,n++){
        var ls=(up.innerText||up.textContent||'').split('\n');
        for(var k=0;k<ls.length;k++){var ln=ls[k].replace(/\s+/g,' ').trim();if(ln!==t&&ln.indexOf(t)!==-1&&social.test(ln))return true}
        if(up===card)break;
      }
      return false;
    };
    var heads=card.querySelectorAll('h1,h2,h3,h4,h5,h6,[role=heading],a[href*="/in/"],a[href*="/company/"]');
    for(var i=0;i<heads.length&&!liAuthor;i++){
      var t=strip(heads[i].innerText||heads[i].textContent);
      if(t&&t.length<=80&&!/^(Promoted|Follow|Following)$/i.test(t)&&!social.test(t)&&!inSocialLine(heads[i],t))liAuthor=t;
    }
    if(!liAuthor)liAuthor=strip(card.innerText);
    if(!liText)liText=clean(card.innerText).replace(/^Feed post\b\s*/i,'');
    return JSON.stringify({text:liText,author:liAuthor,handle:''});
  }
  // X: the post's own author, @handle and text, not a quoted post's.
  var tweet=(best.matches&&best.matches('article[data-testid=tweet]'))?best:best.querySelector('article[data-testid=tweet]');
  if(tweet){
    var xp=xPostParts(tweet);
    return JSON.stringify({text:clean(xp.text||tweet.innerText),author:clean(xp.name),handle:xp.handle});
  }
  if(host.indexOf('instagram.com')!==-1){
    var igName=igAuthor(best);
    var igText=igCaption(best,igName);
    return JSON.stringify({text:clean(igText||best.innerText),author:igName,handle:igName?'@'+igName:''});
  }
  // Reddit wraps each shreddit-post in an article; the details are on the inner element.
  var post=best.querySelector('shreddit-post')||best;
  var body=post.querySelector('[slot="text-body"],shreddit-post-text-body,[data-testid=tweetText],[data-testid="expandable-text-box"],.update-components-text,.feed-shared-update-v2__description,.attributed-text-segment-list__content,.status__content__text,.status__content');
  var name=post.querySelector('[data-testid=User-Name],.update-components-actor__title span[aria-hidden="true"],.update-components-actor__name,.feed-shared-actor__name,.display-name__html,a[href]');
  var title=post.getAttribute('post-title');
  var bodyText=body?clean(body.innerText):'';
  var headline=title?clean(title):'';
  var combined=headline?(bodyText&&bodyText!==headline?headline+'\n'+bodyText:headline):(bodyText||clean(post.innerText));
  var author=post.getAttribute('author')||(name?name.innerText:'');
  var acct=post.querySelector('.display-name__account');
  var handle=acct?clean(acct.innerText):'';
  return JSON.stringify({text:combined,author:clean(author).split('\n')[0],handle:handle});
})()
"""

/**
 * The @handle to address in a reply (X, Instagram, Mastodon's @user@server): trimmed, one word,
 * at most 64 characters. Results without a handle fall back to an author that already is one.
 */
internal fun smartReplyHandle(post: LiveWebPost): String {
    val candidate = post.handle.trim().ifEmpty { post.author.trim() }
    return candidate.takeIf { it.length in 2..64 && it.startsWith("@") && it.none(Char::isWhitespace) }.orEmpty()
}

/**
 * LinkedIn social-context headers name someone who reacted, commented or reposted, not the author.
 * Both page scripts (smart reply and bookmark) embed this exact pattern as a JS regex literal.
 */
internal const val LINKEDIN_SOCIAL_CONTEXT_PATTERN =
    """\b(likes|liked|loves|celebrates|supports|finds this|is curious about|commented on|reposted|replied to|follows)\b"""

/** LinkedIn's mobile feed labels each post "Feed post <Author>" for screen readers. */
private val LINKEDIN_FEED_POST_PREFIX = Regex("^\\s*Feed post\\b\\s*", RegexOption.IGNORE_CASE)

/** Drops LinkedIn's "Feed post " screen-reader label from the start of an author or post text. */
internal fun stripLinkedInFeedPostPrefix(text: String): String = text.replace(LINKEDIN_FEED_POST_PREFIX, "").trim()

/**
 * evaluateJavascript hands back the script's string as a JSON string literal. On LinkedIn the
 * "Feed post " label is dropped from the author too, in case the page script missed it.
 */
internal fun parseLiveWebPost(
    result: String?,
    site: LiveWebSite? = null,
): LiveWebPost? =
    runCatching {
        val inner = Json.parseToJsonElement(result.orEmpty()).jsonPrimitive.content
        val post = Json.parseToJsonElement(inner).jsonObject
        val author = post["author"]?.jsonPrimitive?.content.orEmpty()
        LiveWebPost(
            text = post.getValue("text").jsonPrimitive.content,
            author = if (site == LiveWebSite.LinkedIn) stripLinkedInFeedPostPrefix(author) else author,
            handle = post["handle"]?.jsonPrimitive?.content.orEmpty(),
        )
    }.getOrNull()?.takeIf { it.text.isNotBlank() }

/** Reads the post in view and opens the smart reply sheet for it. */
@Composable
internal fun rememberSmartReplyAction(
    site: LiveWebSite,
    page: LiveWebPage,
): () -> Unit {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val noPost = stringResource(R.string.live_web_no_post)
    return remember(site, page, uriHandler, noPost) {
        {
            page.readPostInView { post ->
                if (post == null) {
                    Toast.makeText(context, noPost, Toast.LENGTH_SHORT).show()
                } else {
                    uriHandler.openUri(
                        DeeplinkRoute.Status
                            .SmartReply(
                                statusKey = MicroBlogKey(post.text.hashCode().toString(), site.name.lowercase()),
                                accountType = AccountType.Guest,
                                initialText = post.text,
                                authorName = post.author,
                                authorHandle = smartReplyHandle(post),
                            ).toUri(),
                    )
                }
            }
        }
    }
}
