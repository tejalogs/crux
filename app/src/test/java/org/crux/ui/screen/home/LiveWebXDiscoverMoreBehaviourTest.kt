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
 * Runs the real [X_DISCOVER_MORE_JS] under node against a tiny fake DOM (tag and attribute
 * selectors with descendant steps), so the run's stop rules and its bound are checked by
 * behaviour, not by text. Each cell ends "none" (collapsed), "invisible" (keeps its height) or
 * "shown". Skipped when node is not installed.
 */
class LiveWebXDiscoverMoreBehaviourTest {
    @Test
    fun theRecommendationsAfterTheHeadingGoButTheRepliesAboveStay() {
        val r = cases()["basic"]!!
        assertEquals("shown", r.str("reply"))
        assertEquals("none", r.str("heading"))
        assertEquals("none", r.str("rec1"))
        assertEquals("none", r.str("rec2"))
    }

    @Test
    fun aShowMoreRepliesCellEndsTheRunAndStays() {
        val r = cases()["showMore"]!!
        assertEquals("none", r.str("heading"))
        assertEquals("none", r.str("rec1"))
        assertEquals("shown", r.str("more"))
        assertEquals("a cell after the stop is never reached", "shown", r.str("rec2"))
    }

    @Test
    fun aRetryCellEndsTheRunAndStays() {
        val r = cases()["retry"]!!
        assertEquals("none", r.str("heading"))
        assertEquals("shown", r.str("retry"))
        assertEquals("shown", r.str("rec1"))
    }

    @Test
    fun aSeparatorATwoPostCellOrAFieldEndTheRun() {
        val r = cases()["stops"]!!
        assertEquals("shown", r.str("sep"))
        assertEquals("shown", r.str("after"))
        val two = cases()["twoPosts"]!!
        assertEquals("shown", two.str("pair"))
        assertEquals("shown", two.str("after"))
        val field = cases()["field"]!!
        assertEquals("shown", field.str("box"))
    }

    @Test
    fun emptySpacersAreSteppedOverAndXsSpinnerGoesWithTheRun() {
        val r = cases()["spinner"]!!
        assertEquals("the spacer is left as it is", "shown", r.str("spacer"))
        assertEquals("none", r.str("rec1"))
        assertEquals("none", r.str("loader"))
    }

    @Test
    fun onlyABoundedRunCollapsesAndTheRestKeepTheirHeight() {
        val r = cases()["bound"]!!
        val collapsed = (1..15).count { r.str("rec$it") == "none" }
        val invisible = (1..15).count { r.str("rec$it") == "invisible" }
        assertEquals(X_DISCOVER_MORE_MAX_COLLAPSED, collapsed)
        assertEquals(15 - X_DISCOVER_MORE_MAX_COLLAPSED, invisible)
        assertEquals("the first ones collapse", "none", r.str("rec1"))
        assertEquals("invisible", r.str("rec15"))
        // A later scan with more cells loaded never collapses past the bound.
        assertEquals("invisible", r.str("late1"))
        assertEquals("invisible", r.str("late2"))
        assertEquals("$X_DISCOVER_MORE_MAX_COLLAPSED", r.str("collapsed"))
    }

    @Test
    fun theRunCarriesOnAfterXUnmountsTheHeadingCell() {
        val r = cases()["unmounted"]!!
        assertEquals("none", r.str("rec2"))
        assertEquals("none", r.str("rec3"))
    }

    @Test
    fun nothingHappensOffAPostsOwnPageOrForAHeadingInsideAPost() {
        val likes = cases()["likesPage"]!!
        assertEquals("shown", likes.str("heading"))
        assertEquals("shown", likes.str("rec1"))
        val inPost = cases()["headingInPost"]!!
        assertEquals("shown", inPost.str("quoting"))
        assertEquals("shown", inPost.str("rec1"))
    }

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: "null"

    private fun cases(): Map<String, JsonObject> {
        val node = listOf("node", "/usr/local/bin/node", "/opt/homebrew/bin/node").firstOrNull { canRun(it) }
        assumeTrue("node is not installed", node != null)
        val dir = kotlin.io.path.createTempDirectory("discovermore").toFile()
        try {
            val body = HARNESS_HEAD + "function discover(location,document,keep,hiddenEls){\n" + X_DISCOVER_MORE_JS + "\n}\n" + CASES
            val file = File(dir, "harness.js").apply { writeText(body) }
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
        /** Fake elements: E(tag, attrs, children, text). The engine's helpers are stubbed as it defines them. */
        const val HARNESS_HEAD = """
function E(tag,attrs,kids,text){
  var el={tagName:tag.toUpperCase(),attrs:attrs||{},children:[],parentElement:null,text:text||'',root:false};
  el.style={props:{},setProperty:function(k,v){el.style.props[k]=v}};
  el.getAttribute=function(n){return Object.prototype.hasOwnProperty.call(el.attrs,n)?el.attrs[n]:null};
  el.append=function(k){k.parentElement=el;el.children.push(k);return el};
  el.remove=function(){var p=el.parentElement;if(p){p.children.splice(p.children.indexOf(el),1);el.parentElement=null}};
  Object.defineProperty(el,'textContent',{get:function(){return el.text+el.children.map(function(c){return c.textContent}).join('')}});
  Object.defineProperty(el,'nextElementSibling',{get:function(){var p=el.parentElement;if(!p)return null;return p.children[p.children.indexOf(el)+1]||null}});
  Object.defineProperty(el,'isConnected',{get:function(){for(var u=el;u;u=u.parentElement){if(u.root)return true}return false}});
  el.all=function(){var out=[];(function walk(n){n.children.forEach(function(c){out.push(c);walk(c)})})(el);return out};
  el.matches=function(sel){return sel.split(',').some(function(one){return matchChain(el,one.trim().split(/\s+/))})};
  el.querySelectorAll=function(sel){return el.all().filter(function(c){return c.matches(sel)})};
  el.querySelector=function(sel){return el.querySelectorAll(sel)[0]||null};
  el.closest=function(sel){for(var u=el;u;u=u.parentElement){if(u.matches(sel))return u}return null};
  (kids||[]).forEach(function(k){el.append(k)});
  return el;
}
function matchChain(el,steps){
  if(!matchOne(el,steps[steps.length-1]))return false;
  if(steps.length===1)return true;
  for(var u=el.parentElement;u;u=u.parentElement){if(matchChain(u,steps.slice(0,-1)))return true}
  return false;
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
var clean=function(t){return (t||'').replace(/[​-‍﻿ ]/g,' ').trim()};
var postSel='article[data-testid="tweet"]';
var canHide=function(e,allowPost){
  var n=e.querySelectorAll(postSel).length;
  if(!allowPost&&n)return false;
  if(allowPost&&n>1)return false;
  return true;
};
var hide=function(e,allowPost){if(!canHide(e,allowPost))return false;e.style.setProperty('display','none','important');return true};
function cell(kids,text){return E('div',{'data-testid':'cellInnerDiv'},kids||[],text)}
function post(t){return cell([E('div',{},[E('article',{'data-testid':'tweet'},[E('span',{},[],t||'a post')])])])}
function heading(t){return cell([E('div',{},[E('h2',{role:'heading'},[E('span',{},[],t||'Discover more'),E('span',{},[],'Sourced from across X')])])])}
function button(t){return cell([E('div',{role:'button'},[E('span',{},[],t)])])}
function page(path,named){
  var list=E('div',{'aria-label':'Timeline: Conversation'},[]);
  var body=E('body',{},[list]);
  body.root=true;
  named.forEach(function(p){list.append(p[1])});
  var state={location:{pathname:path},document:{querySelectorAll:function(s){return body.querySelectorAll(s)}},keep:{},hidden:new Set(),list:list,named:named};
  state.run=function(){discover(state.location,state.document,state.keep,state.hidden)};
  state.result=function(){
    var out={};
    state.named.forEach(function(p){
      var s=p[1].style.props;
      out[p[0]]=s.display==='none'?'none':(s.visibility==='hidden'&&s.opacity==='0'?'invisible':'shown');
    });
    return out;
  };
  return state;
}
var out={};
"""

        const val CASES = """
(function(){
  var s=page('/jack/status/1',[['reply',post('a reply')],['heading',heading()],['rec1',post()],['rec2',post()]]);
  s.run();out.basic=s.result();
})();
(function(){
  var s=page('/jack/status/1',[['heading',heading()],['rec1',post()],['more',button('Show more replies')],['rec2',post()]]);
  s.run();s.run();out.showMore=s.result();
})();
(function(){
  var retry=cell([E('span',{},[],'Something went wrong. Try reloading.'),E('button',{},[E('span',{},[],'Retry')])]);
  var s=page('/jack/status/1',[['heading',heading()],['retry',retry],['rec1',post()]]);
  s.run();out.retry=s.result();
})();
(function(){
  var s=page('/jack/status/1',[['heading',heading()],['sep',cell([E('div',{role:'separator'},[])])],['after',post()]]);
  s.run();out.stops=s.result();
  var pair=cell([E('article',{'data-testid':'tweet'},[],'one'),E('article',{'data-testid':'tweet'},[],'two')]);
  var t=page('/jack/status/1',[['heading',heading()],['pair',pair],['after',post()]]);
  t.run();out.twoPosts=t.result();
  var f=page('/jack/status/1',[['heading',heading()],['box',cell([E('input',{},[])])]]);
  f.run();out.field=f.result();
})();
(function(){
  var loader=cell([E('div',{role:'progressbar'},[E('svg',{},[])])]);
  var s=page('/jack/status/1',[['heading',heading()],['spacer',cell([E('div',{},[])])],['rec1',post()],['loader',loader]]);
  s.run();out.spinner=s.result();
})();
(function(){
  var named=[['heading',heading()]];
  for(var i=1;i<=15;i++)named.push(['rec'+i,post()]);
  var s=page('/jack/status/1',named);
  s.run();
  var late1=post(),late2=post();
  s.list.append(late1);s.list.append(late2);
  s.named.push(['late1',late1],['late2',late2]);
  s.run();s.run();
  out.bound=s.result();
  out.bound.collapsed=String(s.keep.xDiscoverLast.collapsed);
})();
(function(){
  var h=heading(),r1=post();
  var s=page('/jack/status/1',[['heading',h],['rec1',r1]]);
  s.run();
  h.remove();
  var r2=post(),r3=post();
  s.list.append(r2);s.list.append(r3);
  s.named.push(['rec2',r2],['rec3',r3]);
  s.run();out.unmounted=s.result();
})();
(function(){
  var s=page('/jack/status/1/likes',[['heading',heading()],['rec1',post()]]);
  s.run();out.likesPage=s.result();
  var quoting=cell([E('article',{'data-testid':'tweet'},[E('h2',{role:'heading'},[],'Discover more')])]);
  var t=page('/jack/status/1',[['quoting',quoting],['rec1',post()]]);
  t.run();out.headingInPost=t.result();
})();
console.log(JSON.stringify(out));
"""
    }
}
