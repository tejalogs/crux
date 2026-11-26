package org.crux.ui.screen.home

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the real [POST_PARTS_JS] under node against a tiny fake DOM (simple selectors, element
 * boxes and innerText only), so the post-picking rules are checked by behaviour, not by text.
 * Skipped when node is not installed.
 */
class LiveWebPostScriptBehaviourTest {
    @Test
    fun xStatusPagePicksTheOpenedPostOverTheReplyAtMidScreen() {
        val r = cases()["xFocal"]!!
        assertEquals("main", r.str("picked"))
    }

    @Test
    fun xQuotedPostIsNotTakenForTheOpenedPost() {
        // The reply quotes the opened post: its quoted time link must not make it "own" the id.
        assertEquals("main", cases()["xQuoteNotOwner"]!!.str("picked"))
    }

    @Test
    fun xPostPartsKeepsTheAuthorsHandleAndMarksQuotedText() {
        val r = cases()["xQuoteParts"]!!
        assertEquals("Alice", r.str("name"))
        assertEquals("@alice", r.str("handle"))
        assertEquals("↪ @bob: quoted words", r.str("text"))
        val own = cases()["xOwnParts"]!!
        assertEquals("@alice", own.str("handle"))
        assertEquals("my own words", own.str("text"))
    }

    @Test
    fun otherSitesNeverTakeThePostPageBranch() {
        listOf("redditTv", "redditTvComments", "redditP", "redditUserTv", "xHandleTvMedia").forEach {
            assertEquals(it, "null", cases()[it]!!.str("picked"))
        }
    }

    @Test
    fun instagramPicksThePostNotACommentLinkingToIt() {
        assertEquals("post", cases()["igCommentFirst"]!!.str("picked"))
        assertEquals("ownItem", cases()["igLikersItem"]!!.str("picked"))
    }

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: "null"

    private fun cases(): Map<String, JsonObject> {
        val node = listOf("node", "/usr/local/bin/node", "/opt/homebrew/bin/node").firstOrNull { canRun(it) }
        assumeTrue("node is not installed", node != null)
        val dir = kotlin.io.path.createTempDirectory("postscripts").toFile()
        try {
            val file = File(dir, "harness.js").apply { writeText(HARNESS_HEAD + POST_PARTS_JS + HARNESS_TAIL + CASES) }
            val process = ProcessBuilder(node, file.path).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue("harness did not finish", process.waitFor(30, TimeUnit.SECONDS))
            assertEquals("harness failed:\n$output", 0, process.exitValue())
            val root = Json.parseToJsonElement(output.trim().lines().last()).jsonObject
            return root.mapValues { it.value.jsonObject }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun canRun(command: String): Boolean =
        runCatching {
            val p = ProcessBuilder(command, "--version").redirectErrorStream(true).start()
            p.inputStream.readBytes()
            p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
        }.getOrDefault(false)

    private companion object {
        /** Fake elements: E(tag, attrs, children, {text, top, bottom, key}). */
        const val HARNESS_HEAD = """
function E(tag,attrs,kids,o){
  o=o||{};
  var el={tagName:tag.toUpperCase(),attrs:attrs||{},children:kids||[],parentElement:null,text:o.text||'',box:o.top!=null?{top:o.top,bottom:o.bottom}:null,key:o.key||null};
  el.children.forEach(function(k){k.parentElement=el});
  el.getAttribute=function(n){return Object.prototype.hasOwnProperty.call(el.attrs,n)?el.attrs[n]:null};
  Object.defineProperty(el,'innerText',{get:function(){return el.children.length?el.children.map(function(c){return c.innerText}).join('\n'):el.text}});
  el.getBoundingClientRect=function(){for(var u=el;u;u=u.parentElement){if(u.box)return {top:u.box.top,bottom:u.box.bottom,height:u.box.bottom-u.box.top}}return {top:0,bottom:0,height:0}};
  el.all=function(){var out=[];(function walk(n){n.children.forEach(function(c){out.push(c);walk(c)})})(el);return out};
  el.matches=function(sel){return sel.split(',').some(function(one){return matchOne(el,one.trim())})};
  el.querySelectorAll=function(sel){return el.all().filter(function(c){return c.matches(sel)})};
  el.querySelector=function(sel){return el.querySelectorAll(sel)[0]||null};
  el.closest=function(sel){for(var u=el;u;u=u.parentElement){if(u.matches(sel))return u}return null};
  return el;
}
function matchOne(el,sel){
  var m=sel.match(/^([a-z0-9]*)((?:\[[^\]]+\])*)$/i);
  if(!m)throw new Error('unsupported selector '+sel);
  if(m[1]&&el.tagName!==m[1].toUpperCase())return false;
  var re=/\[([\w-]+)(?:([*^]?=)"?([^"\]]*)"?)?\]/g,a;
  while((a=re.exec(m[2]))){
    var v=el.getAttribute(a[1]);
    if(v==null)return false;
    if(a[2]==='='&&v!==a[3])return false;
    if(a[2]==='*='&&v.indexOf(a[3])<0)return false;
    if(a[2]==='^='&&v.indexOf(a[3])!==0)return false;
  }
  return true;
}
function scope(host,path){
  var location={hostname:host,pathname:path};
  var innerHeight=800;
"""

        const val HARNESS_TAIL = """
  return {focalPost:focalPost,xPostParts:xPostParts};
}
function xName(name,handle){return E('div',{'data-testid':'User-Name'},[E('span',{},[],{text:name}),E('span',{},[],{text:handle})])}
function xTime(href){return E('a',{href:href},[E('time',{},[],{text:'1h'})])}
function xText(t){return E('div',{'data-testid':'tweetText'},[],{text:t})}
function xQuote(name,handle,href,t){return E('div',{role:'link'},[xName(name,handle),xTime(href),xText(t)])}
function tweet(key,top,kids,attrs){var a=attrs||{};a['data-testid']='tweet';return E('article',a,kids,{key:key,top:top,bottom:top+200})}
function pick(host,path,list){var p=scope(host,path).focalPost(list);return {picked:p?p.key:'null'}}
var out={};
"""

        const val CASES = """
out.xFocal=pick('x.com','/nasa/status/12',[
  tweet('main',60,[xName('NASA','@nasa'),xTime('/nasa/status/12'),xText('launch')]),
  tweet('reply',300,[xName('Bob','@bob'),xTime('/bob/status/13'),xText('nice')])
]);
out.xQuoteNotOwner=pick('x.com','/nasa/status/12',[
  tweet('quoter',60,[xName('Bob','@bob'),xTime('/bob/status/13'),xQuote('NASA','@nasa','/nasa/status/12','launch')]),
  tweet('main',300,[xName('NASA','@nasa'),xTime('/nasa/status/12'),xText('launch')],{tabindex:'-1'})
]);
out.xQuoteParts=scope('x.com','/alice/status/1').xPostParts(
  tweet('q',60,[xName('Alice','@alice'),xTime('/alice/status/1'),xQuote('Bob','@bob','/bob/status/2','quoted words')]));
out.xOwnParts=scope('x.com','/alice/status/1').xPostParts(
  tweet('o',60,[xName('Alice','@alice'),xText('my own words'),xQuote('Bob','@bob','/bob/status/2','quoted words')]));
var feed=function(){return [E('shreddit-post',{},[],{key:'top',top:60,bottom:260}),E('shreddit-post',{},[],{key:'mid',top:300,bottom:500})]};
out.redditTv=pick('www.reddit.com','/r/tv/new/',feed());
out.redditTvComments=pick('www.reddit.com','/r/tv/comments/abc/x/',feed());
out.redditP=pick('www.reddit.com','/r/p/top/',feed());
out.redditUserTv=pick('www.reddit.com','/u/tv/posts/',feed());
out.xHandleTvMedia=pick('x.com','/tv/media',feed());
out.igCommentFirst=pick('www.instagram.com','/p/ABC/',[
  E('div',{role:'listitem'},[E('a',{href:'/p/ABC/c/999/'},[],{text:'2h'})],{key:'comment',top:60,bottom:160}),
  E('article',{},[E('a',{href:'https://www.instagram.com/natgeo/p/ABC/?img_index=1'},[E('time',{},[],{text:'1d'})])],{key:'post',top:200,bottom:700})
]);
out.igLikersItem=pick('www.instagram.com','/p/ABC/',[
  E('div',{role:'listitem'},[E('a',{href:'/p/ABC/liked_by/'},[],{text:'Liked by'})],{key:'likers',top:60,bottom:160}),
  E('div',{role:'listitem'},[E('a',{href:'/p/ABC/'},[],{text:'1d'})],{key:'ownItem',top:200,bottom:400})
]);
console.log(JSON.stringify(out));
"""
    }
}
