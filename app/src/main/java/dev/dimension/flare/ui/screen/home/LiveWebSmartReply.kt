package dev.dimension.flare.ui.screen.home

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.WandMagicSparkles
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class LiveWebPost(
    val text: String,
    val author: String,
)

/**
 * Runs once per tap and only reads: the selected text if there is any, otherwise the post nearest
 * the middle of the screen. It never types into the page or presses anything.
 */
internal const val READ_POST_SCRIPT = """
(function(){
  var clean=function(t){return (t||'').replace(/[ \t]+/g,' ').replace(/\n{2,}/g,'\n').trim().slice(0,1500)};
  var sel=clean(String(window.getSelection()));
  if(sel.length>3)return JSON.stringify({text:sel,author:''});
  var posts=document.querySelectorAll('article,[role=listitem],shreddit-post');
  var mid=innerHeight/2,best=null,gap=1e9;
  posts.forEach(function(p){
    var r=p.getBoundingClientRect();
    if(r.height<40||r.width<100||r.bottom<0||r.top>innerHeight)return;
    var d=(r.top<=mid&&r.bottom>=mid)?0:Math.min(Math.abs(r.top-mid),Math.abs(r.bottom-mid));
    if(d<gap){gap=d;best=p}
  });
  if(!best)return '';
  // Reddit wraps each shreddit-post in an article; the details are on the inner element.
  var post=best.querySelector('shreddit-post')||best;
  var body=post.querySelector('[data-testid=tweetText]');
  var name=post.querySelector('[data-testid=User-Name],a[href]');
  var title=post.getAttribute('post-title');
  var author=post.getAttribute('author')||(name?name.innerText:'');
  return JSON.stringify({text:clean(title||(body||post).innerText),author:clean(author).split('\n')[0]});
})()
"""

/** evaluateJavascript hands back the script's string as a JSON string literal. */
internal fun parseLiveWebPost(result: String?): LiveWebPost? =
    runCatching {
        val inner = Json.parseToJsonElement(result.orEmpty()).jsonPrimitive.content
        val post = Json.parseToJsonElement(inner).jsonObject
        LiveWebPost(
            text = post.getValue("text").jsonPrimitive.content,
            author = post["author"]?.jsonPrimitive?.content.orEmpty(),
        )
    }.getOrNull()?.takeIf { it.text.isNotBlank() }

@Composable
internal fun SmartReplyButton(
    site: LiveWebSite,
    page: LiveWebPage,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val noPost = stringResource(R.string.live_web_no_post)
    SmallFloatingActionButton(
        onClick = {
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
                                authorHandle = site.name.lowercase(),
                            ).toUri(),
                    )
                }
            }
        },
        modifier = modifier,
    ) {
        FAIcon(
            FontAwesomeIcons.Solid.WandMagicSparkles,
            contentDescription = stringResource(R.string.live_web_smart_reply),
            modifier = Modifier.size(16.dp),
        )
    }
}
